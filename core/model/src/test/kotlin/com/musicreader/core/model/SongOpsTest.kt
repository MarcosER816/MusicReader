package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SongOpsTest {
    private val now = 2_000L
    private val song = TestSongs.fullSong

    private fun Song.sectionIds() = sections.map { it.id }

    @Test
    fun moveSectionDown() {
        val moved = SongOps.moveSection(song, fromIndex = 0, toIndex = 2, now)
        assertEquals(listOf("sec-verse-1", "sec-chorus-1", "sec-intro", "sec-verse-2", "sec-chorus-2"), moved.sectionIds())
        assertEquals(now, moved.updatedAt)
        assertEquals(song.sectionContents, moved.sectionContents)
    }

    @Test
    fun moveSectionUp() {
        val moved = SongOps.moveSection(song, fromIndex = 4, toIndex = 1, now)
        assertEquals(listOf("sec-intro", "sec-chorus-2", "sec-verse-1", "sec-chorus-1", "sec-verse-2"), moved.sectionIds())
    }

    @Test
    fun moveSectionWithInvalidIndicesChangesNothing() {
        assertSame(song, SongOps.moveSection(song, 1, 1, now))
        assertSame(song, SongOps.moveSection(song, -1, 2, now))
        assertSame(song, SongOps.moveSection(song, 0, 5, now))
    }

    @Test
    fun moveIsReversible() {
        val there = SongOps.moveSection(song, 1, 3, now)
        val back = SongOps.moveSection(there, 3, 1, now)
        assertEquals(song.sectionIds(), back.sectionIds())
    }

    @Test
    fun addSectionUsesBarsOfTheTimeSignature() {
        val ids = SequentialIds()
        val s34 = SongOps.setTimeSignature(SongOps.newSong("n", "New", Language.EN, 1), TimeSignature.THREE_FOUR, 1)
        val added = SongOps.addSection(s34, SectionType.BRIDGE, bars = 4, label = "  ", ids = ids, now = now)
        val section = added.sections.single()
        assertEquals(SectionType.BRIDGE, section.type)
        assertNull(section.label)
        assertEquals(12, added.contentOf(section).lengthBeats)
        assertEquals(4, added.contentOf(section).bars(added.timeSignature))
    }

    @Test
    fun addSectionClampsBarsAndInsertsAtIndex() {
        val added = SongOps.addSection(song, SectionType.OUTRO, bars = 0, label = "End", ids = SequentialIds(), now = now, index = 1)
        assertEquals(SectionType.OUTRO, added.sections[1].type)
        assertEquals("End", added.sections[1].label)
        assertEquals(4, added.contentOf(added.sections[1]).lengthBeats)
        val huge = SongOps.addSection(song, SectionType.OUTRO, bars = 5000, label = null, ids = SequentialIds(), now = now)
        assertEquals(SongOps.MAX_SECTION_BARS, huge.contentOf(huge.sections.last()).bars(huge.timeSignature))
    }

    @Test
    fun linkedDuplicateSharesContent() {
        val dup = SongOps.duplicateSection(song, "sec-verse-1", linked = true, ids = SequentialIds(), now = now)
        assertEquals(6, dup.sections.size)
        val copy = dup.sections[2]
        assertEquals(SectionType.VERSE, copy.type)
        assertEquals("content-verse", copy.contentId)
        assertEquals(song.sectionContents, dup.sectionContents)
        assertEquals(2, dup.linkedCount(copy))
    }

    @Test
    fun editingLinkedLengthChangesAllCopies() {
        val changed = SongOps.setSectionBars(song, "sec-chorus-2", bars = 2, now = now)
        val first = changed.sections.first { it.id == "sec-chorus-1" }
        assertEquals(8, changed.contentOf(first).lengthBeats)
    }

    @Test
    fun independentDuplicateDeepCopiesContent() {
        val dup = SongOps.duplicateSection(song, "sec-chorus-1", linked = false, ids = SequentialIds("new"), now = now)
        val copy = dup.sections[3]
        assertNotEquals("content-chorus", copy.contentId)
        val copied = dup.contentOf(copy)
        val original = TestSongs.chorusContent
        assertEquals(original.lengthBeats, copied.lengthBeats)
        assertEquals(original.strum, copied.strum)
        assertEquals(original.chords.map { it.symbol }, copied.chords.map { it.symbol })
        assertEquals(original.lyricLines.map { it.text }, copied.lyricLines.map { it.text })
        val allIds = copied.lyricLines.flatMap { l -> listOf(l.id) + l.words.flatMap { w -> listOf(w.id) + w.syllables.map { it.id } } } +
            copied.chords.map { it.id } + copied.tab.map { it.id }
        assertTrue(allIds.all { it.startsWith("new-") }, "all nested ids are new: $allIds")
        // Changing the copy leaves the original alone.
        val changed = SongOps.setSectionBars(dup, copy.id, 1, now)
        assertEquals(16, changed.contentOf(changed.sections[2]).lengthBeats)
        assertEquals(4, changed.contentOf(changed.sections[3]).lengthBeats)
    }

    @Test
    fun unlinkGivesTheSectionItsOwnContent() {
        val unlinked = SongOps.unlinkSection(song, "sec-chorus-2", SequentialIds("u"), now)
        val a = unlinked.sections.first { it.id == "sec-chorus-1" }
        val b = unlinked.sections.first { it.id == "sec-chorus-2" }
        assertNotEquals(a.contentId, b.contentId)
        assertEquals(1, unlinked.linkedCount(a))
        assertEquals(1, unlinked.linkedCount(b))
        assertEquals(unlinked.contentOf(a).lengthBeats, unlinked.contentOf(b).lengthBeats)
        assertSame(unlinked, SongOps.unlinkSection(unlinked, "sec-chorus-2", SequentialIds(), now))
    }

    @Test
    fun deleteKeepsSharedContentAndPrunesOrphans() {
        val oneChorusGone = SongOps.deleteSection(song, "sec-chorus-2", now)
        assertTrue(oneChorusGone.sectionContents.any { it.id == "content-chorus" })
        val bothGone = SongOps.deleteSection(oneChorusGone, "sec-chorus-1", now)
        assertTrue(bothGone.sectionContents.none { it.id == "content-chorus" })
        val introGone = SongOps.deleteSection(song, "sec-intro", now)
        assertTrue(introGone.sectionContents.none { it.id == "content-intro" })
        assertSame(song, SongOps.deleteSection(song, "missing", now))
    }

    @Test
    fun timeSignatureChangeKeepsBars() {
        val changed = SongOps.setTimeSignature(song, TimeSignature.THREE_FOUR, now)
        assertEquals(song.sections.map { song.contentOf(it).bars(song.timeSignature) },
            changed.sections.map { changed.contentOf(it).bars(changed.timeSignature) })
        assertEquals(listOf(6, 24, 12, 24), changed.sectionContents.map { it.lengthBeats })
        assertEquals(26, changed.totalBars)
        val sixEight = SongOps.setTimeSignature(changed, TimeSignature.SIX_EIGHT, now)
        assertEquals(listOf(12, 48, 24, 48), sixEight.sectionContents.map { it.lengthBeats })
    }

    @Test
    fun tempoIsClampedAndRounded() {
        assertEquals(30.0, SongOps.setTempo(song, 10.0, now).tempoBpm)
        assertEquals(300.0, SongOps.setTempo(song, 999.0, now).tempoBpm)
        assertEquals(97.3, SongOps.setTempo(song, 97.26, now).tempoBpm)
    }

    @Test
    fun textFields() {
        assertNull(SongOps.setArtist(song, "   ", now).artist)
        assertEquals("Ana", SongOps.setArtist(song, "Ana", now).artist)
        assertEquals("New title", SongOps.setTitle(song, "New title", now).title)
        assertEquals(Language.EN, SongOps.setLanguage(song, Language.EN, now).language)
        assertEquals("Final", SongOps.setSectionLabel(song, "sec-intro", " Final ", now).sections[0].label)
        assertNull(SongOps.setSectionLabel(song, "sec-chorus-1", "", now).sections[2].label)
    }

    @Test
    fun sectionTypeCanChangeAfterwards() {
        val changed = SongOps.setSectionType(song, "sec-verse-2", SectionType.PRE_CHORUS, now)
        assertEquals(SectionType.PRE_CHORUS, changed.sections[3].type)
        assertEquals("content-verse-2", changed.sections[3].contentId)
    }

    @Test
    fun occurrenceNumbersOnlyForRepeatedTypes() {
        val numbers = song.occurrenceNumbers()
        assertNull(numbers["sec-intro"])
        assertEquals(1, numbers["sec-verse-1"])
        assertEquals(2, numbers["sec-verse-2"])
        assertEquals(1, numbers["sec-chorus-1"])
        assertEquals(2, numbers["sec-chorus-2"])
    }

    @Test
    fun duplicateSongGetsNewIdAndKeepsContent() {
        val copy = SongOps.duplicateSong(song, "song-2", "Copy", now)
        assertEquals("song-2", copy.id)
        assertEquals("Copy", copy.title)
        assertEquals(now, copy.createdAt)
        assertEquals(song.sections, copy.sections)
        assertEquals(song.sectionContents, copy.sectionContents)
    }
}
