package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetLayoutTest {
    private fun word(id: String, text: String, vararg syllables: Pair<String, Double?>) = Word(
        id = id,
        text = text,
        syllables = if (syllables.isEmpty()) listOf(Syllable("$id-s", text))
        else syllables.mapIndexed { i, (t, beat) -> Syllable("$id-$i", t, startBeat = beat, durationBeats = beat?.let { 1.0 }) },
    )

    private fun chord(beat: Double, root: NoteLetter) = ChordEvent("c$beat", beat, ChordSymbol(root), "v")

    @Test
    fun linesShareTheSectionBeatsAndChordsGoToTheirRow() {
        val content = SectionContent(
            id = "c",
            lengthBeats = 16,
            lyricLines = listOf(
                LyricLine("l1", text = "ab cd", words = listOf(word("w1", "ab"), word("w2", "cd"))),
                LyricLine("l2", text = "efgh", words = listOf(word("w3", "efgh"))),
            ),
            chords = listOf(chord(0.0, NoteLetter.C), chord(4.0, NoteLetter.G), chord(8.0, NoteLetter.A), chord(16.0, NoteLetter.F)),
            tab = listOf(TabEvent("t", 10.0, 1.0, string = 2, fret = 3, finger = 2, technique = Technique.BEND)),
        )
        val rows = SheetLayoutBuilder.rows(content, TimeSignature.FOUR_FOUR)
        assertEquals(2, rows.size)
        assertEquals(0.0 to 8.0, rows[0].startBeat to rows[0].lengthBeats)
        assertEquals(listOf(0.0, 0.5), rows[0].chords.map { it.x })
        assertEquals(listOf(0.0, 0.5), rows[0].barLines)
        // The chord at the very end of the section belongs to the last row.
        assertEquals(listOf(0.0, 1.0), rows[1].chords.map { it.x })
        assertEquals(PlacedTabNote("t", 0.25, 2, 3, 2, Technique.BEND), rows[1].tab.single())
        // Untimed words spread by character position: "ab cd" → ab at 0, cd at 3/5.
        assertEquals(listOf(PlacedText("w1", 0.0, "ab"), PlacedText("w2", 0.6, "cd")), rows[0].lyrics)
        assertEquals(listOf(PlacedText("w3", 0.0, "efgh")), rows[1].lyrics)
    }

    @Test
    fun timedSyllablesUseTheirBeats() {
        val content = SectionContent(
            id = "c",
            lengthBeats = 8,
            lyricLines = listOf(
                LyricLine("l1", text = "can ta", words = listOf(word("w", "canta", "can" to 2.0, "ta" to 6.0))),
            ),
        )
        val row = SheetLayoutBuilder.rows(content, TimeSignature.FOUR_FOUR).single()
        assertEquals(listOf(0.25, 0.75), row.lyrics.map { it.x })
        assertEquals(listOf("can", "ta"), row.lyrics.map { it.text })
    }

    @Test
    fun secondVoiceSitsWithItsMainLine() {
        val content = SectionContent(
            id = "c",
            lengthBeats = 8,
            lyricLines = listOf(
                LyricLine("m1", text = "one", words = listOf(word("a", "one"))),
                LyricLine("m2", text = "two", words = listOf(word("b", "two"))),
                LyricLine("s2", voice = LyricLine.SECOND_VOICE, text = "echo", words = listOf(word("e", "echo")), pairedLineId = "m2"),
            ),
        )
        val rows = SheetLayoutBuilder.rows(content, TimeSignature.FOUR_FOUR)
        assertEquals(2, rows.size, "second-voice lines do not make rows")
        assertTrue(rows[0].secondLyrics.isEmpty())
        assertEquals(listOf("echo"), rows[1].secondLyrics.map { it.text })
    }

    @Test
    fun sectionsWithoutLyricsUseTwoBarRows() {
        val content = SectionContent(id = "c", lengthBeats = 15, chords = listOf(chord(12.0, NoteLetter.E)))
        val rows = SheetLayoutBuilder.rows(content, TimeSignature.THREE_FOUR) // 5 bars of 3/4
        assertEquals(listOf(0.0, 6.0, 12.0), rows.map { it.startBeat })
        assertEquals(listOf(6.0, 6.0, 3.0), rows.map { it.lengthBeats })
        assertEquals(listOf(0.0, 0.5), rows[0].barLines)
        assertEquals(listOf(0.0), rows[2].barLines)
        assertEquals(0.0, rows[2].chords.single().x)
        assertEquals(1, SheetLayoutBuilder.rows(SectionContent("e", 0), TimeSignature.FOUR_FOUR).size)
    }

    @Test
    fun buildKeepsSectionOrderAndNames() {
        val layout = SheetLayoutBuilder.build(TestSongs.fullSong)
        assertEquals(TestSongs.fullSong.sections.map { it.id }, layout.sections.map { it.sectionId })
        assertEquals(listOf(null, 1, 1, 2, 2), layout.sections.map { it.number })
        assertEquals("Chorus", layout.sections[2].label)
    }

    @Test
    fun chordColorFollowsTheRootLetter() {
        val s = AppSettings()
        assertEquals(s.chordColors[NoteLetter.B], s.chordColor(ChordSymbol(NoteLetter.B, Accidental.FLAT, "9")))
        assertEquals(s.chordColors[NoteLetter.D], s.chordColor(ChordSymbol(NoteLetter.D, Accidental.SHARP)))
        assertEquals(0xFFE53935, s.chordColor(ChordSymbol(NoteLetter.C, suffix = "maj7")))
        assertEquals(0xFF66BB6A, s.fingerColor(2))
        assertEquals(0xFFFFFFFF, s.fingerColor(9))
    }
}

class TabNotationTest {
    @Test
    fun techniqueMarks() {
        assertEquals("5", TabNotation.label(5, Technique.NONE))
        assertEquals("5h", TabNotation.label(5, Technique.HAMMER_ON))
        assertEquals("7p", TabNotation.label(7, Technique.PULL_OFF))
        assertEquals("3/", TabNotation.label(3, Technique.SLIDE_UP))
        assertEquals("3\\", TabNotation.label(3, Technique.SLIDE_DOWN))
        assertEquals("7b", TabNotation.label(7, Technique.BEND))
        assertEquals("7br", TabNotation.label(7, Technique.BEND_RELEASE))
        assertEquals("3~", TabNotation.label(3, Technique.VIBRATO))
        assertEquals("<12>", TabNotation.label(12, Technique.NATURAL_HARMONIC))
        assertEquals("[7]", TabNotation.label(7, Technique.ARTIFICIAL_HARMONIC))
        assertEquals("x", TabNotation.label(0, Technique.MUTE))
    }
}
