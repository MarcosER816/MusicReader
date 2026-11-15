package com.musicreader.core.data

import com.musicreader.core.model.Syllable
import com.musicreader.core.model.TakeInfo
import com.musicreader.core.model.TakeMethod
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TakeRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun take(i: Int) = TakeInfo(
        id = "t$i", createdAt = i.toLong(), contentId = "c", voice = 0, syllableIds = listOf("s1"),
        previous = listOf(Syllable("s1", "la", startBeat = i.toDouble())), method = TakeMethod.SUNG,
        tempoBpm = 100.0, latencyMs = 85.6, audioFile = "t$i.wav",
    )

    @Test
    fun keepsTheNewestTenAndSurvivesANewInstance() = runBlocking {
        val repo = FileTakeRepository(tmp.root)
        for (i in 1..12) repo.add("song", take(i), FloatArray(480) { (it % 10) / 10f }, 48000)
        val takesDir = File(tmp.root, "song/takes")
        val list = FileTakeRepository(tmp.root).list("song")
        assertEquals((12 downTo 3).map { "t$it" }, list.map { it.id })
        assertTrue(!File(takesDir, "t1.wav").exists() && !File(takesDir, "t2.wav").exists(), "pruned recordings are deleted")
        assertTrue(File(takesDir, "t12.wav").exists())
        assertEquals(3.0, list.last().previous.single().startBeat, "undo values are kept")
    }

    @Test
    fun undoneFlagAndAudio() = runBlocking {
        val repo = FileTakeRepository(tmp.root)
        val samples = FloatArray(4800) { kotlin.math.sin(it / 10.0).toFloat() * 0.5f }
        repo.add("song", take(1), samples, 48000)
        repo.markUndone("song", "t1")
        assertTrue(FileTakeRepository(tmp.root).list("song").single().undone)
        val back = assertNotNull(repo.audio("song", "t1"))
        assertEquals(samples.size, back.size)
        for (i in samples.indices) assertEquals(samples[i], back[i], 1e-4f)
        assertNull(repo.audio("song", "nope"))
        assertEquals(emptyList(), repo.list("other"))
    }

    @Test
    fun wavHeader() {
        val bytes = Wav.encode(floatArrayOf(0f, 1f, -1f, 2f), 48000)
        assertEquals(44 + 8, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(48000, Wav.sampleRate(bytes))
        assertEquals(listOf(0f, 1f, -1f, 1f), Wav.decode(bytes).toList())
    }
}
