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
        val c = LyricsOps.setMainText(empty, text, es, ids)
        assertEquals(listOf("Canta conmigo esta canción", "Ahora y siempre, corazón"), c.lyricLines.map { it.text })
        assertEquals(
            listOf("Can-ta", "con-mi-go", "es-ta", "can-ción", "A-ho-ra", "y", "siem-pre,", "co-ra-zón"),
            c.words().map { it.split() },
        )
        assertTrue(c.words().none { it.syllableOverride })
        assertEquals("Canta conmigo esta canción\nAhora y siempre, corazón", LyricsOps.mainText(c))
    }

    @Test
    fun editingKeepsUnchangedWordsWithTheirTimingAndManualSplits() {
        var c = LyricsOps.setMainText(empty, "Canta conmigo esta canción", es, ids)
        // Time the first word and split "canción" by hand.
        val canta = c.words()[0]
        val timed = canta.copy(syllables = canta.syllables.map { it.copy(startBeat = 1.0, durationBeats = 0.5, pitchStartMidi = 64.2) })
        c = LyricsOps.updateWord(c, canta.id) { timed }
        val cancion = c.words()[3]
        c = LyricsOps.updateWord(c, cancion.id) { LyricsOps.splitSyllable(it, 1, 2, ids) } // can-ci-ón
        val before = c.words()

        c = LyricsOps.setMainText(c, "Canta, canta conmigo\nesta nueva canción", es, ids)
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
        var c = LyricsOps.setMainText(empty, "hello darkness my old friend", en, ids)
        val darkness = c.words()[1]
        c = LyricsOps.updateWord(c, darkness.id) { w -> w.copy(syllables = w.syllables.mapIndexed { i, s -> s.copy(startBeat = i.toDouble(), durationBeats = 1.0) }) }
        val timed = c.words()[1]
        c = LyricsOps.setMainText(c, "hello darkness my dear old friend", en, ids)
        assertSame(timed, c.words()[1])
        assertEquals(listOf(0.0, 1.0), c.words()[1].syllables.map { it.startBeat })
    }

    @Test
    fun unchangedLinesKeepTheirIdsAndRepeatedWordsMatchInOrder() {
        var c = LyricsOps.setMainText(empty, "la la la\nsecond line", en, ids)
        val lineIds = c.lyricLines.map { it.id }
        val la = c.words().take(3).map { it.id }
        c = LyricsOps.setMainText(c, "la la\nsecond line\nthird", en, ids)
        assertEquals(lineIds, c.lyricLines.take(2).map { it.id }) // edited in place / unchanged
        assertNotEquals(lineIds[1], c.lyricLines[2].id)
        assertEquals(la.take(2), c.words().take(2).map { it.id })
    }

    @Test
    fun secondVoiceIsOneLinePerMainLine() {
        var c = LyricsOps.setMainText(empty, "first line here\nsecond line here", en, ids)
        val (first, second) = c.lyricLines.map { it.id }
        c = LyricsOps.setSecondLine(c, second, "echo   two", en, ids)
        c = LyricsOps.setSecondLine(c, first, "echo one", en, ids)
        // Stored main lines first, then second lines in the order of their main lines.
        assertEquals(listOf("first line here", "second line here", "echo one", "echo two"), c.lyricLines.map { it.text })
        assertEquals(first, LyricsOps.secondLineOf(c, first)!!.pairedLineId)
        assertEquals(LyricLine.SECOND_VOICE, LyricsOps.secondLineOf(c, second)!!.voice)
        // Editing a second line keeps its unchanged words.
        val echo = LyricsOps.secondLineOf(c, first)!!.words[0]
        c = LyricsOps.setSecondLine(c, first, "echo one more", en, ids)
        assertSame(echo, LyricsOps.secondLineOf(c, first)!!.words[0])
        // Blank text removes the second line; unknown main lines change nothing.
        c = LyricsOps.setSecondLine(c, second, "  ", en, ids)
        assertEquals(null, LyricsOps.secondLineOf(c, second))
        assertSame(c, LyricsOps.setSecondLine(c, "missing", "x", en, ids))
    }

    @Test
    fun editingAMainLineKeepsItsSecondLine() {
        var c = LyricsOps.setMainText(empty, "one\ntwo\nthree", en, ids)
        val two = c.lyricLines[1].id
        c = LyricsOps.setSecondLine(c, two, "echo", en, ids)
        // "two" is edited in place and a line is inserted before it: the pairing stays.
        c = LyricsOps.setMainText(c, "zero\none\ntwo changed\nthree", en, ids)
        assertEquals(two, c.lyricLines.first { it.text == "two changed" }.id)
        assertEquals("echo", LyricsOps.secondLineOf(c, two)!!.text)
        // Removing the main line removes its second line.
        c = LyricsOps.setMainText(c, "zero\none\nthree", en, ids)
        assertTrue(c.lyricLines.none { it.voice == LyricLine.SECOND_VOICE })
    }

    @Test
    fun olderUnpairedSecondLinesAreAttachedInOrder() {
        var c = LyricsOps.setMainText(empty, "a\nb", en, ids)
        val unpaired = listOf("x", "y", "z").map { LyricLine(id = "s$it", voice = LyricLine.SECOND_VOICE, text = it) }
        c = c.copy(lyricLines = c.lyricLines + unpaired)
        val n = LyricsOps.normalized(c)
        assertEquals(listOf("a", "b", "x", "y"), n.lyricLines.map { it.text })
        assertEquals(n.lyricLines.take(2).map { it.id }, n.lyricLines.drop(2).map { it.pairedLineId })
        assertSame(n, LyricsOps.normalized(n))
    }

    @Test
    fun linesKeepTheirIdWhenEditedInPlace() {
        assertEquals(listOf(0, 1, 2), LyricsOps.matchLines(listOf("a", "b", "c"), listOf("a", "B", "c")))
        assertEquals(listOf(null, 0, 1, 2), LyricsOps.matchLines(listOf("a", "b", "c"), listOf("new", "a", "b", "c")))
        assertEquals(listOf(0, 2), LyricsOps.matchLines(listOf("a", "b", "c"), listOf("a", "c")))
        assertEquals(listOf(0, 1, null), LyricsOps.matchLines(listOf("a", "b"), listOf("x", "y", "z")))
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
        var content = LyricsOps.setMainText(song.sectionContents.single(), "animal cantar", en, ids)
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
