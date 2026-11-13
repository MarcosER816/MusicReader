package com.musicreader.core.data

import com.musicreader.core.model.Language
import com.musicreader.core.model.SectionType
import com.musicreader.core.model.Song
import com.musicreader.core.model.SongJson
import com.musicreader.core.model.SongOps
import com.musicreader.core.model.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSongRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val songsDir: File get() = File(tmp.root, "songs")

    private fun repository(unreadable: MutableList<File> = mutableListOf()) =
        FileSongRepository(songsDir, Dispatchers.Unconfined) { file, _ -> unreadable += file }

    private var nextId = 0
    private val ids = IdGenerator { "x${nextId++}" }

    private fun songWithSections(id: String, title: String, updatedAt: Long): Song {
        var song = SongOps.newSong(id, title, Language.ES, now = updatedAt)
        song = SongOps.addSection(song, SectionType.INTRO, 2, null, ids, updatedAt)
        song = SongOps.addSection(song, SectionType.VERSE, 8, null, ids, updatedAt)
        song = SongOps.addSection(song, SectionType.CHORUS, 8, "Chorus", ids, updatedAt)
        song = SongOps.duplicateSection(song, song.sections[2].id, linked = true, ids = ids, now = updatedAt)
        return song
    }

    @Test
    fun saveThenLoadInANewRepositoryReturnsTheSameSong() = runBlocking {
        val song = songWithSections("song-a", "Primera", 10)
        repository().save(song)

        val reopened = repository() // like after a force-stop: nothing in memory
        reopened.refresh()
        assertEquals(song, reopened.load("song-a"))
        assertEquals(listOf("song-a"), reopened.summaries.value.map { it.id })
        assertEquals(4, reopened.summaries.value.single().sectionCount)
    }

    @Test
    fun oneJsonFilePerSongAndNoTempFilesLeft() = runBlocking {
        val repo = repository()
        repo.save(songWithSections("a", "A", 1))
        repo.save(songWithSections("b", "B", 2))
        repo.save(songWithSections("a", "A2", 3))
        val names = songsDir.list()!!.sorted()
        assertEquals(listOf("a.json", "b.json"), names)
        assertEquals("A2", SongJson.decode(File(songsDir, "a.json").readText()).title)
    }

    @Test
    fun summariesAreNewestFirstAndFollowChanges() = runBlocking {
        val repo = repository()
        repo.refresh()
        assertEquals(emptyList(), repo.summaries.value)
        repo.save(songWithSections("old", "Old", 100))
        repo.save(songWithSections("new", "New", 200))
        assertEquals(listOf("new", "old"), repo.summaries.value.map { it.id })
        repo.save(SongOps.setTitle(repo.load("old")!!, "Old renamed", 300))
        assertEquals(listOf("Old renamed", "New"), repo.summaries.value.map { it.title })
        repo.delete("new")
        assertEquals(listOf("old"), repo.summaries.value.map { it.id })
    }

    @Test
    fun deleteRemovesFileAndSongFolder() = runBlocking {
        val repo = repository()
        repo.save(songWithSections("gone", "Gone", 1))
        val recDir = File(songsDir, "gone/rec").apply { mkdirs() }
        File(recDir, "sing.wav").writeText("data")
        repo.delete("gone")
        assertFalse(File(songsDir, "gone.json").exists())
        assertFalse(File(songsDir, "gone").exists())
        assertNull(repo.load("gone"))
        repo.delete("gone") // deleting twice is fine
    }

    @Test
    fun interruptedWriteKeepsTheOldSong() = runBlocking {
        val repo = repository()
        val song = songWithSections("s", "Safe", 1)
        repo.save(song)
        // Simulate a force-stop after the temp file was written but before the rename.
        File(songsDir, "s.json.tmp").writeText("{ half written")
        val reopened = repository()
        reopened.refresh()
        assertEquals(song, reopened.load("s"))
        assertFalse(File(songsDir, "s.json.tmp").exists())
    }

    @Test
    fun unreadableFilesAreSkippedAndKept() = runBlocking {
        songsDir.mkdirs()
        File(songsDir, "broken.json").writeText("not json")
        val unreadable = mutableListOf<File>()
        val repo = repository(unreadable)
        repo.save(songWithSections("ok", "Ok", 1))
        assertEquals(listOf("ok"), repo.summaries.value.map { it.id })
        assertEquals(listOf("broken.json"), unreadable.map { it.name })
        assertTrue(File(songsDir, "broken.json").exists())
    }

    @Test
    fun unsafeIdsAreRejected() = runBlocking<Unit> {
        val repo = repository()
        assertFailsWith<IllegalArgumentException> { repo.load("../escape") }
        assertFailsWith<IllegalArgumentException> { repo.delete("a/b") }
        assertFailsWith<IllegalArgumentException> { repo.save(songWithSections("", "Empty", 1)) }
    }
}
