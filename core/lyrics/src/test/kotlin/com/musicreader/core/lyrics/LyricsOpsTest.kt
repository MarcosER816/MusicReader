package com.musicreader.core.lyrics

import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.Language
import com.musicreader.core.model.LyricLine
import com.musicreader.core.model.SectionContent
import com.musicreader.core.model.SectionType
import com.musicreader.core.model.SongOps
import com.musicreader.core.model.Syllable
import com.musicreader.core.model.Word
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LyricsOpsTest {
    private var next = 0
    private val ids = IdGenerator { "id${next++}" }
    private val es = WordSyllabifier.forLanguage(Language.ES)
    private val en = WordSyllabifier.forLanguage(Language.EN)
    private val empty = SectionContent(id = "c", lengthBeats = 16)

    private fun SectionContent.words(voice: Int = 0) = lyricLines.filter { it.voice == voice }.flatMap { it.words }
    private fun Word.split() = syllables.joinToString("-") { it.text }

    @Test
    fun pastedTextBecomesLinesWordsAndSyllables() {
        val text = "Canta conmigo esta canción\n\n   Ahora   y siempre,  corazón \n"
        val c = LyricsOps.setVoiceText(empty, 0, text, es, ids)
        assertEquals(listOf("Canta conmigo esta canción", "Ahora y siempre, corazón"), c.lyricLines.map { it.text })
        assertEquals(
            listOf("Can-ta", "con-mi-go", "es-ta", "can-ción", "A-ho-ra", "y", "siem-pre,", "co-ra-zón"),
            c.words().map { it.split() },
        )
        assertTrue(c.words().none { it.syllableOverride })
        assertEquals("Canta conmigo esta canción\nAhora y siempre, corazón", LyricsOps.voiceText(c, 0))
    }

    @Test
    fun editingKeepsUnchangedWordsWithTheirTimingAndManualSplits() {
        var c = LyricsOps.setVoiceText(empty, 0, "Canta conmigo esta canción", es, ids)
        // Time the first word and split "canción" by hand.
        val canta = c.words()[0]
        val timed = canta.copy(syllables = canta.syllables.map { it.copy(startBeat = 1.0, durationBeats = 0.5, pitchStartMidi = 64.2) })
        c = LyricsOps.updateWord(c, canta.id) { timed }
        val cancion = c.words()[3]
        c = LyricsOps.updateWord(c, cancion.id) { LyricsOps.splitSyllable(it, 1, 2, ids) } // can-ci-ón
        val before = c.words()

        c = LyricsOps.setVoiceText(c, 0, "Canta, canta conmigo\nesta nueva canción", es, ids)
        val after = c.words()
        assertEquals(listOf("Canta,", "canta", "conmigo", "esta", "nueva", "canción"), after.map { it.text })
        // "Canta" changed to "Canta," and is new; "conmigo", "esta", "canción" are the same objects.
        assertNotEquals(before[0].id, after[0].id)
        assertSame(before[1], after[2])
        assertSame(before[2], after[3])
        assertSame(before[3], after[5])
        assertEquals("can-ci-ón", after[5].split())
        assertTrue(after[5].syllableOverride)
        assertEquals(2, c.lyricLines.size)
    }

    @Test
    fun timedWordSurvivesWhenOtherWordsChange() {
        var c = LyricsOps.setVoiceText(empty, 0, "hello darkness my old friend", en, ids)
        val darkness = c.words()[1]
        c = LyricsOps.updateWord(c, darkness.id) { w -> w.copy(syllables = w.syllables.mapIndexed { i, s -> s.copy(startBeat = i.toDouble(), durationBeats = 1.0) }) }
        val timed = c.words()[1]
        c = LyricsOps.setVoiceText(c, 0, "hello darkness my dear old friend", en, ids)
        assertSame(timed, c.words()[1])
        assertEquals(listOf(0.0, 1.0), c.words()[1].syllables.map { it.startBeat })
    }

    @Test
    fun unchangedLinesKeepTheirIdsAndRepeatedWordsMatchInOrder() {
        var c = LyricsOps.setVoiceText(empty, 0, "la la la\nsecond line", en, ids)
        val lineIds = c.lyricLines.map { it.id }
        val la = c.words().take(3).map { it.id }
        c = LyricsOps.setVoiceText(c, 0, "la la\nsecond line\nthird", en, ids)
        assertEquals(lineIds[1], c.lyricLines[1].id)
        assertNotEquals(lineIds[0], c.lyricLines[0].id)
        assertEquals(la.take(2), c.words().take(2).map { it.id })
    }

    @Test
    fun secondVoiceIsIndependentOfTheMainVoice() {
        var c = LyricsOps.setVoiceText(empty, LyricLine.MAIN_VOICE, "main words here", en, ids)
        val main = c.lyricLines.single()
        c = LyricsOps.setVoiceText(c, LyricLine.SECOND_VOICE, "echo here", en, ids)
        assertEquals(main, c.lyricLines.first { it.voice == 0 })
        assertEquals("echo here", LyricsOps.voiceText(c, 1))
        c = LyricsOps.setVoiceText(c, LyricLine.SECOND_VOICE, "", en, ids)
        assertEquals(listOf(main), c.lyricLines)
    }

    @Test
    fun splitAndMergeByHand() {
        val word = LyricsOps.newWord("corazón", es, ids) // co-ra-zón
        val split = LyricsOps.splitSyllable(word, 2, 1, ids) // co-ra-z-ón
        assertEquals("co-ra-z-ón", split.split())
        assertTrue(split.syllableOverride)
        assertEquals(word.id, split.id)
        val merged = LyricsOps.mergeSyllables(split, 0, ids) // cora-z-ón
        assertEquals("cora-z-ón", merged.split())
        assertEquals("corazón", merged.syllables.joinToString("") { it.text })
        // Out-of-range taps change nothing.
        assertSame(word, LyricsOps.splitSyllable(word, 0, 0, ids))
        assertSame(word, LyricsOps.splitSyllable(word, 0, 2, ids))
        assertSame(word, LyricsOps.mergeSyllables(word, 2, ids))
        // Reset to automatic.
        val reset = LyricsOps.resetWord(merged, es, ids)
        assertEquals("co-ra-zón", reset.split())
        assertFalse(reset.syllableOverride)
    }

    @Test
    fun mergingTimedSyllablesSpansBoth() {
        val word = Word(
            id = "w", text = "amor",
            syllables = listOf(
                Syllable("a", "a", startBeat = 2.0, durationBeats = 0.5, pitchStartMidi = 60.0, pitchEndMidi = 61.0),
                Syllable("m", "mor", startBeat = 2.5, durationBeats = 1.5, pitchStartMidi = 62.0, pitchEndMidi = 63.0),
            ),
        )
        val merged = LyricsOps.mergeSyllables(word, 0, ids).syllables.single()
        assertEquals(2.0, merged.startBeat)
        assertEquals(2.0, merged.durationBeats)
        assertEquals(60.0, merged.pitchStartMidi)
        assertEquals(63.0, merged.pitchEndMidi)
    }

    @Test
    fun resplitAfterLanguageChangeKeepsManualSplits() {
        var song = SongOps.newSong("s", "S", Language.EN, 0)
        song = SongOps.addSection(song, SectionType.VERSE, 4, null, ids, 0)
        var content = LyricsOps.setVoiceText(song.sectionContents.single(), 0, "animal cantar", en, ids)
        val cantar = content.words()[1]
        content = LyricsOps.updateWord(content, cantar.id) { LyricsOps.splitSyllable(it, 0, 1, ids) }
        song = song.copy(sectionContents = listOf(content))
        val manual = content.words()[1]

        val resplit = LyricsOps.resplitAutomatic(song, es, ids)
        val words = resplit.sectionContents.single().words()
        assertEquals("a-ni-mal", words[0].split())
        assertSame(manual, words[1])
        // Nothing changes when the split is already right.
        assertEquals(resplit, LyricsOps.resplitAutomatic(resplit, es, ids))
    }

    @Test
    fun lcsMatchesInOrder() {
        assertEquals(listOf(0, null, 1, 2), LyricsOps.matchByLcs(listOf("a", "b", "c"), listOf("a", "x", "b", "c")))
        assertEquals(listOf(null, null), LyricsOps.matchByLcs(listOf("a"), listOf("b", "c")))
        assertEquals(listOf(1), LyricsOps.matchByLcs(listOf("x", "a"), listOf("a")))
        assertEquals(emptyList(), LyricsOps.matchByLcs(listOf("a"), emptyList()))
    }
}
