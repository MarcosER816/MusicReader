package com.musicreader.core.data

import com.musicreader.core.model.TakeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Accepted lyric-capture takes of each song (D-031): `songs/<id>/takes/`, newest first. */
interface TakeRepository {
    suspend fun list(songId: String): List<TakeInfo>

    /** Saves the take and its recording; keeps only the newest [KEEP] per song. */
    suspend fun add(songId: String, take: TakeInfo, samples: FloatArray, sampleRate: Int)

    suspend fun markUndone(songId: String, takeId: String)

    /** The recording of a take, or null if it was pruned. */
    suspend fun audio(songId: String, takeId: String): FloatArray?

    companion object {
        const val KEEP = 10
    }
}

class FileTakeRepository(private val songsDir: File) : TakeRepository {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(TakeInfo.serializer())

    private fun dir(songId: String): File {
        require(songId.isNotEmpty() && songId.none { it == '/' || it == '\\' } && songId != "." && songId != "..") { "bad id" }
        return File(File(songsDir, songId), "takes")
    }

    private fun readList(songId: String): List<TakeInfo> {
        val f = File(dir(songId), INDEX)
        if (!f.exists()) return emptyList()
        return runCatching { json.decodeFromString(serializer, f.readText()) }.getOrDefault(emptyList())
    }

    private fun writeList(songId: String, takes: List<TakeInfo>) {
        val d = dir(songId).apply { mkdirs() }
        val tmp = File(d, "$INDEX.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(json.encodeToString(serializer, takes).toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(File(d, INDEX))) error("Could not save takes of $songId")
    }

    override suspend fun list(songId: String): List<TakeInfo> = withContext(Dispatchers.IO) { mutex.withLock { readList(songId) } }

    override suspend fun add(songId: String, take: TakeInfo, samples: FloatArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val d = dir(songId).apply { mkdirs() }
            Wav.write(File(d, take.audioFile), samples, sampleRate)
            val all = listOf(take) + readList(songId).filter { it.id != take.id }
            val kept = all.take(TakeRepository.KEEP)
            all.drop(TakeRepository.KEEP).forEach { File(d, it.audioFile).delete() }
            writeList(songId, kept)
        }
    }

    override suspend fun markUndone(songId: String, takeId: String) = withContext(Dispatchers.IO) {
        mutex.withLock { writeList(songId, readList(songId).map { if (it.id == takeId) it.copy(undone = true) else it }) }
    }

    override suspend fun audio(songId: String, takeId: String): FloatArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val take = readList(songId).firstOrNull { it.id == takeId } ?: return@withLock null
            val f = File(dir(songId), take.audioFile)
            if (f.exists()) Wav.read(f) else null
        }
    }

    companion object {
        const val INDEX = "takes.json"
    }
}

/** 16-bit PCM mono WAV. */
object Wav {
    fun encode(samples: FloatArray, sampleRate: Int): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
        val bytes = ByteArrayOutputStream(44 + samples.size * 2)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2)
            putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples.size * 2)
        }
        DataOutputStream(bytes).use {
            it.write(header.array())
            it.write(data.array())
        }
        return bytes.toByteArray()
    }

    fun write(file: File, samples: FloatArray, sampleRate: Int) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(encode(samples, sampleRate))
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) error("Could not save $file")
    }

    /** Samples of a file written by [write]. */
    fun read(file: File): FloatArray = decode(file.readBytes())

    fun decode(bytes: ByteArray): FloatArray {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val n = b.getInt(40) / 2
        return FloatArray(n) { b.getShort(44 + it * 2) / 32767f }
    }

    fun sampleRate(bytes: ByteArray): Int = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(24)
}
