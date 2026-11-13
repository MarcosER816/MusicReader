package com.musicreader.core.data

import com.musicreader.core.model.Song
import com.musicreader.core.model.SongJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Stores each song as `<songsDir>/<id>.json` (docs/DATA_MODEL.md), plus a folder
 * `<songsDir>/<id>/` for its recordings and takes.
 *
 * Writes are atomic: the JSON goes to `<id>.json.tmp`, is flushed to disk, and is then renamed
 * over the old file, so a crash or force-stop leaves either the old or the new song, never a
 * half-written file. Songs are kept in memory after the first [refresh], so loads after saves
 * never race with the disk.
 */
class FileSongRepository(
    private val songsDir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Called with files that cannot be read (they are left on disk untouched). */
    private val onUnreadableFile: (File, Exception) -> Unit = { _, _ -> },
) : SongRepository {

    private val mutex = Mutex()
    private var cache: MutableMap<String, Song>? = null
    private val _summaries = MutableStateFlow<List<SongSummary>>(emptyList())
    override val summaries: StateFlow<List<SongSummary>> = _summaries.asStateFlow()

    override suspend fun refresh() {
        mutex.withLock { withContext(io) { loadAllLocked() } }
    }

    override suspend fun load(id: String): Song? {
        requireValidId(id)
        return mutex.withLock { withContext(io) { cacheLocked()[id] } }
    }

    override suspend fun save(song: Song) {
        requireValidId(song.id)
        mutex.withLock {
            withContext(io) {
                val songs = cacheLocked()
                writeAtomically(fileFor(song.id), SongJson.encode(song))
                songs[song.id] = song
                publishLocked()
            }
        }
    }

    override suspend fun delete(id: String) {
        requireValidId(id)
        mutex.withLock {
            withContext(io) {
                val songs = cacheLocked()
                val file = fileFor(id)
                if (file.exists() && !file.delete()) throw IOException("Could not delete $file")
                File(songsDir, id).deleteRecursively()
                songs.remove(id)
                publishLocked()
            }
        }
    }

    private fun cacheLocked(): MutableMap<String, Song> = cache ?: loadAllLocked()

    private fun loadAllLocked(): MutableMap<String, Song> {
        songsDir.mkdirs()
        // Leftovers of a write interrupted before its rename: the real file is still intact.
        songsDir.listFiles { f -> f.name.endsWith(TMP_SUFFIX) }?.forEach { it.delete() }
        val songs = mutableMapOf<String, Song>()
        songsDir.listFiles { f -> f.isFile && f.name.endsWith(JSON_SUFFIX) }?.forEach { file ->
            try {
                val song = SongJson.decode(file.readText())
                if (fileFor(song.id).name == file.name) songs[song.id] = song
                else onUnreadableFile(file, IOException("File name does not match song id ${song.id}"))
            } catch (e: Exception) {
                onUnreadableFile(file, e)
            }
        }
        cache = songs
        publishLocked()
        return songs
    }

    private fun publishLocked() {
        _summaries.value = cache.orEmpty().values
            .map { SongSummary(it.id, it.title, it.artist, it.sections.size, it.updatedAt) }
            .sortedWith(compareByDescending<SongSummary> { it.updatedAt }.thenBy { it.id })
    }

    private fun fileFor(id: String): File = File(songsDir, id + JSON_SUFFIX)

    private fun writeAtomically(target: File, text: String) {
        songsDir.mkdirs()
        val tmp = File(target.parentFile, target.name + TMP_SUFFIX)
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun requireValidId(id: String) {
        require(ID_PATTERN.matches(id)) { "Invalid song id: '$id'" }
    }

    companion object {
        private const val JSON_SUFFIX = ".json"
        private const val TMP_SUFFIX = ".json.tmp"

        /** Ids become file names, so only letters, digits, '-' and '_' are allowed. */
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
