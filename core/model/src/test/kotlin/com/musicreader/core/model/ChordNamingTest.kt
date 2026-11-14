package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordNamingTest {
    @Test
    fun pitchClassesAndEnharmonics() {
        assertEquals(0, Enharmonics.pitchClass(NoteLetter.C))
        assertEquals(1, Enharmonics.pitchClass(NoteLetter.C, Accidental.SHARP))
        assertEquals(1, Enharmonics.pitchClass(NoteLetter.D, Accidental.FLAT))
        assertEquals(11, Enharmonics.pitchClass(NoteLetter.C, Accidental.FLAT))
        assertEquals(5, Enharmonics.pitchClass(NoteLetter.E, Accidental.SHARP))
        for ((sharp, flat) in listOf("C#" to "Db", "D#" to "Eb", "F#" to "Gb", "G#" to "Ab", "A#" to "Bb")) {
            assertTrue(Enharmonics.sameNote(Enharmonics.parse(sharp)!!, Enharmonics.parse(flat)!!), "$sharp/$flat")
            val pc = Enharmonics.pitchClass(Enharmonics.parse(sharp)!!)
            assertEquals(sharp, Enharmonics.spell(pc, AccidentalPreference.SHARPS).toString())
            assertEquals(flat, Enharmonics.spell(pc, AccidentalPreference.FLATS).toString())
        }
        assertNull(Enharmonics.parse("H"))
        assertNull(Enharmonics.parse("C##"))
    }

    @Test
    fun dictionarySuffixes() {
        assertEquals("" to null, ChordNaming.fromDictionarySuffix("major"))
        assertEquals("m" to null, ChordNaming.fromDictionarySuffix("minor"))
        assertEquals("maj7" to null, ChordNaming.fromDictionarySuffix("maj7"))
        assertEquals("" to NoteName(NoteLetter.E), ChordNaming.fromDictionarySuffix("/E"))
        assertEquals("m" to NoteName(NoteLetter.B), ChordNaming.fromDictionarySuffix("m/B"))
        assertEquals("m9" to NoteName(NoteLetter.B, Accidental.FLAT), ChordNaming.fromDictionarySuffix("m9/Bb"))
        assertEquals("7" to NoteName(NoteLetter.G), ChordNaming.fromDictionarySuffix("7/G"))
        assertEquals("D#9", ChordNaming.symbol(NoteName(NoteLetter.D, Accidental.SHARP), "9").toString())
        assertEquals("C/E", ChordNaming.symbol(NoteName(NoteLetter.C), "/E").toString())
        val dSlashFSharp = ChordSymbol(NoteLetter.D, bass = NoteName(NoteLetter.F, Accidental.SHARP))
        assertTrue(ChordNaming.matches(dSlashFSharp, "/F#"))
        assertTrue(ChordNaming.matches(dSlashFSharp.copy(bass = NoteName(NoteLetter.G, Accidental.FLAT)), "/F#"))
        assertFalse(ChordNaming.matches(dSlashFSharp, "major"))
        assertFalse(ChordNaming.matches(ChordSymbol(NoteLetter.D), "/F#"))
    }

    @Test
    fun voicingIds() {
        assertEquals("Eb|9|2", ChordNaming.voicingId("Eb", "9", 2))
        assertEquals(Triple("C", "m/B", 1), ChordNaming.parseVoicingId("C|m/B|1"))
        assertNull(ChordNaming.parseVoicingId("C-1"))
        assertEquals("Bb|9|0", ChordNaming.firstVoicingId(ChordSymbol(NoteLetter.B, Accidental.FLAT, "9")))
        assertEquals("C|minor|0", ChordNaming.firstVoicingId(ChordSymbol(NoteLetter.C, suffix = "m")))
        assertEquals("D|/F#|0", ChordNaming.firstVoicingId(ChordSymbol(NoteLetter.D, bass = NoteName(NoteLetter.F, Accidental.SHARP))))
    }

    @Test
    fun snapToHalfBeatsInsideTheSection() {
        assertEquals(2.0, ChordOps.snap(2.2, 16))
        assertEquals(2.5, ChordOps.snap(2.3, 16))
        assertEquals(0.0, ChordOps.snap(-1.0, 16))
        assertEquals(15.5, ChordOps.snap(17.0, 16))
        assertEquals(0.0, ChordOps.snap(0.1, 16))
    }

    @Test
    fun addMoveChangeDelete() {
        val c = ChordSymbol(NoteLetter.C)
        val g = ChordSymbol(NoteLetter.G)
        var content = SectionContent(id = "s", lengthBeats = 8)
        content = ChordOps.add(content, 4.2, g, "G|major|0", "g")
        content = ChordOps.add(content, 0.1, c, "C|major|0", "c")
        assertEquals(listOf(0.0 to "C", 4.0 to "G"), content.chords.map { it.beat to it.symbol.toString() })
        // Adding on an occupied beat replaces that chord.
        content = ChordOps.add(content, 3.9, ChordSymbol(NoteLetter.A, suffix = "m"), "A|minor|0", "a")
        assertEquals(listOf("C", "Am"), content.chords.map { it.symbol.toString() })
        content = ChordOps.move(content, "c", 2.6)
        assertEquals(listOf(2.5 to "C", 4.0 to "Am"), content.chords.map { it.beat to it.symbol.toString() })
        content = ChordOps.move(content, "c", 4.0) // onto Am: replaces it
        assertEquals(listOf(4.0 to "C"), content.chords.map { it.beat to it.symbol.toString() })
        content = ChordOps.change(content, "c", g, "G|major|2")
        assertEquals("G|major|2", content.chords.single().voicingId)
        assertEquals(emptyList(), ChordOps.delete(content, "c").chords)
        assertEquals(content, ChordOps.move(content, "missing", 1.0))
    }
}

class ChordOnSyllableTest {
    private fun syl(id: String, text: String) = Syllable(id, text)

    // 8 beats, two lines of 4 beats: "can-ta con" / "mi-go".
    private val content = SectionContent(
        id = "s",
        lengthBeats = 8,
        lyricLines = listOf(
            LyricLine(
                "l1",
                text = "canta con",
                words = listOf(
                    Word("w1", "canta", listOf(syl("can", "can"), syl("ta", "ta"))),
                    Word("w2", "con", listOf(syl("con", "con"))),
                ),
            ),
            LyricLine("l2", text = "migo", words = listOf(Word("w3", "migo", listOf(syl("mi", "mi"), syl("go", "go"))))),
        ),
    )
    private val ts = TimeSignature.FOUR_FOUR
    private val c = ChordSymbol(NoteLetter.C)
    private val g = ChordSymbol(NoteLetter.G)

    @Test
    fun syllableBeatIsEstimatedFromTheLineUntilSung() {
        // Line 1 covers beats 0–4; "canta con" has 9 characters: ta at 3/9, con at 6/9.
        assertEquals(0.0, ChordOps.syllableBeat(content, ts, "can"))
        assertEquals(1.5, ChordOps.syllableBeat(content, ts, "ta")) // 4 × 3/9 = 1.33 → 1.5
        assertEquals(2.5, ChordOps.syllableBeat(content, ts, "con")) // 4 × 6/9 = 2.67 → 2.5
        assertEquals(4.0, ChordOps.syllableBeat(content, ts, "mi"))
        assertNull(ChordOps.syllableBeat(content, ts, "nope"))
        // Once sung, the syllable's own start beat counts.
        val sung = content.copy(
            lyricLines = content.lyricLines.map { l ->
                l.copy(words = l.words.map { w -> w.copy(syllables = w.syllables.mapIndexed { i, s -> s.copy(startBeat = 0.5 + i, durationBeats = 1.0) }) })
            },
        )
        assertEquals(0.5, ChordOps.syllableBeat(sung, ts, "can"))
    }

    @Test
    fun chordsOnSyllablesAreDrawnAboveThemInTheirRow() {
        var x = ChordOps.addOnSyllable(content, ts, "ta", c, "C|major|0", "c1")
        x = ChordOps.addOnSyllable(x, ts, "go", g, "G|major|0", "g1")
        assertEquals(listOf("ta" to 1.5, "go" to 6.0), x.chords.map { it.syllableId to it.beat })
        val rows = SheetLayoutBuilder.rows(x, ts)
        assertEquals(listOf("ta"), rows[0].chords.map { it.syllableId })
        assertEquals(3.0 / 9, rows[0].chords.single().x, 1e-9)
        assertEquals(listOf("go"), rows[1].chords.map { it.syllableId })
        assertEquals(listOf("can", "ta", "con"), rows[0].syllables.map { it.id })
        assertEquals(listOf(true, false, true), rows[0].syllables.map { it.startsWord })
        // A second chord on the same syllable replaces the first.
        x = ChordOps.addOnSyllable(x, ts, "ta", g, "G|major|1", "g2")
        assertEquals(listOf("g2", "g1"), x.chords.map { it.id })
    }

    @Test
    fun gridChordsInLyricRowsAreShownOverTheNearestSyllable() {
        val x = ChordOps.add(content, 2.5, c, "C|major|0", "c")
        val row = SheetLayoutBuilder.rows(x, ts)[0]
        assertEquals("con", row.chords.single().syllableId)
        assertNull(x.chords.single().syllableId)
    }

    @Test
    fun moveToAnotherSyllableAndBackToTheGrid() {
        var x = ChordOps.addOnSyllable(content, ts, "can", c, "C|major|0", "c")
        x = ChordOps.addOnSyllable(x, ts, "mi", g, "G|major|0", "g")
        x = ChordOps.moveToSyllable(x, ts, "c", "mi") // onto G's syllable: replaces it
        assertEquals(listOf("c" to "mi"), x.chords.map { it.id to it.syllableId })
        assertEquals(4.0, x.chords.single().beat)
        x = ChordOps.move(x, "c", 7.2) // dragged on the grid: no longer on a syllable
        assertEquals(listOf(7.0 to null), x.chords.map { it.beat to it.syllableId })
    }

    @Test
    fun realignFollowsLyricChanges() {
        var x = ChordOps.addOnSyllable(content, ts, "con", c, "C|major|0", "c")
        x = ChordOps.addOnSyllable(x, ts, "go", g, "G|major|0", "g")
        // The first line loses "canta": "con" moves to the line start; "go" is deleted.
        val edited = x.copy(
            lyricLines = listOf(
                x.lyricLines[0].copy(text = "con", words = listOf(x.lyricLines[0].words[1])),
                x.lyricLines[1].copy(text = "mi", words = listOf(Word("w3", "mi", listOf(syl("mi", "mi"))))),
            ),
        )
        val realigned = ChordOps.realign(edited, ts)
        assertEquals(listOf(Triple("c", 0.0, "con"), Triple("g", 6.0, null)), realigned.chords.map { Triple(it.id, it.beat, it.syllableId) })
        assertEquals(realigned, ChordOps.realign(realigned, ts))
    }
}
