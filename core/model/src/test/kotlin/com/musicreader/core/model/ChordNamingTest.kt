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
