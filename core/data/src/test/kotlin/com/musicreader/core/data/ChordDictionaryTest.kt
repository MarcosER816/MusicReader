package com.musicreader.core.data

import com.musicreader.core.model.Accidental
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.ChordNaming
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.Instrument
import com.musicreader.core.model.NoteLetter
import com.musicreader.core.model.NoteName
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChordDictionaryTest {
    private val dictionary: ChordDictionary = runBlocking {
        ChordDictionaryRepository({ File("src/main/assets/chords/guitar.json").inputStream() }).dictionary()
    }

    private fun chord(root: NoteLetter, accidental: Accidental = Accidental.NONE, suffix: String = "", bass: NoteName? = null) =
        ChordSymbol(root, accidental, suffix, bass)

    @Test
    fun loadsTheWholeDictionary() {
        assertEquals(828, dictionary.chords.size)
        assertEquals(3283, dictionary.shapeCount)
        assertEquals(4, dictionary.fretsOnChord)
        assertEquals(ChordNaming.DICTIONARY_KEYS.toSet(), dictionary.chords.map { it.dictionaryKey }.toSet())
        assertTrue(dictionary.chords.all { it.shapes.size >= 2 })
    }

    @Test
    fun relativeFretsBecomeAbsolute() {
        assertEquals(-1, ChordShape.absoluteFret(-1, 5))
        assertEquals(0, ChordShape.absoluteFret(0, 5))
        assertEquals(5, ChordShape.absoluteFret(1, 5))
        assertEquals(8, ChordShape.absoluteFret(4, 5))
        assertEquals(3, ChordShape.absoluteFret(3, 1))
        // C major, 2nd shape: chords-db frets [-1,1,3,3,3,1] at baseFret 3 → A-shape barre at fret 3.
        val c2 = dictionary.shape("C|major|1")!!
        assertEquals(listOf(-1, 3, 5, 5, 5, 3), c2.frets)
        assertEquals(listOf(Barre(fret = 3, fromString = 5, toString = 1, finger = 1)), c2.barres)
    }

    @Test
    fun everyShapeSoundsItsMidiNotesInStandardTuning() {
        val tuning = Instrument.STANDARD_TUNING
        for (chord in dictionary.chords) for (shape in chord.shapes) {
            val sounding = shape.frets.indices.filter { shape.frets[it] >= 0 }.map { tuning[it] + shape.frets[it] }
            assertEquals(shape.midi, sounding, shape.voicingId)
        }
    }

    @Test
    fun onlyTheKnownShapesLackTheirRoot() {
        // Rootless jazz voicings and a few upstream data slips, kept as published (D-024).
        val rootless = dictionary.chords.flatMap { chord ->
            val root = ChordNaming.DICTIONARY_KEYS.indexOf(chord.dictionaryKey)
            chord.shapes.filter { shape -> shape.midi.none { it % 12 == root } }.map { it.voicingId }
        }
        assertEquals(
            listOf(
                "C#|7b5|3", "C#|7b9|3", "C#|9#11|0", "Eb|maj9|3", "Eb|m9|1", "Eb|mmaj9|1", "E|maj9|3",
                "E|madd9|0", "E|madd9|1", "E|madd9|2", "E|madd9|3", "F|maj9|1", "F|maj9|3", "F#|m9|3",
                "G|maj9|2", "G|add9|1", "Ab|aug9|2", "Ab|7b9|1", "Bb|m7|0", "Bb|m9|0", "B|maj9|1",
            ).sorted(),
            rootless.sorted(),
        )
    }

    @Test
    fun openCAndBarreF() {
        val c = dictionary.find(chord(NoteLetter.C))!!.shapes.first()
        assertEquals(listOf(-1, 3, 2, 0, 1, 0), c.frets)
        assertEquals(listOf(0, 3, 2, 0, 1, 0), c.fingers)
        assertEquals(listOf(48, 52, 55, 60, 64), c.midi)
        val f = dictionary.find(chord(NoteLetter.F))!!.shapes.first()
        assertEquals(listOf(1, 3, 3, 2, 1, 1), f.frets)
        assertEquals(listOf(Barre(fret = 1, fromString = 6, toString = 1, finger = 1)), f.barres)
    }

    @Test
    fun enharmonicRootsFindTheSameChords() {
        val dSharp = dictionary.find(chord(NoteLetter.D, Accidental.SHARP))!!
        val eFlat = dictionary.find(chord(NoteLetter.E, Accidental.FLAT))!!
        assertEquals(dSharp, eFlat)
        assertEquals("Eb", dSharp.dictionaryKey)
        assertEquals("major", dSharp.dictionarySuffix)
        for ((sharp, flat) in listOf(NoteLetter.C to NoteLetter.D, NoteLetter.F to NoteLetter.G, NoteLetter.G to NoteLetter.A, NoteLetter.A to NoteLetter.B)) {
            assertEquals(
                dictionary.chordsFor(NoteName(sharp, Accidental.SHARP)),
                dictionary.chordsFor(NoteName(flat, Accidental.FLAT)),
            )
        }
        // The chord keeps the user's spelling.
        assertEquals("D#9", dictionary.find(chord(NoteLetter.D, Accidental.SHARP, "9"))!!.symbol(NoteName(NoteLetter.D, Accidental.SHARP)).toString())
    }

    @Test
    fun requiredChordsExist() {
        for (symbol in listOf(
            chord(NoteLetter.B, Accidental.FLAT, "9"),
            chord(NoteLetter.C, suffix = "maj7"),
            chord(NoteLetter.D, Accidental.SHARP),
            chord(NoteLetter.A, suffix = "m"),
            chord(NoteLetter.G, suffix = "7"),
            chord(NoteLetter.E, suffix = "sus4"),
            chord(NoteLetter.C, bass = NoteName(NoteLetter.E)),
            chord(NoteLetter.D, bass = NoteName(NoteLetter.F, Accidental.SHARP)),
            chord(NoteLetter.A, suffix = "m", bass = NoteName(NoteLetter.G)),
        )) {
            val found = dictionary.find(symbol)
            assertNotNull(found, "$symbol")
            val shape = dictionary.shapeFor(symbol, "unknown")!!
            assertEquals(found.shapes.first(), shape)
        }
        assertNull(dictionary.find(chord(NoteLetter.C, suffix = "nonsense")))
    }

    @Test
    fun voicingIdsRoundTrip() {
        val bb9 = dictionary.find(chord(NoteLetter.B, Accidental.FLAT, "9"))!!
        val second = bb9.shapes[1]
        assertEquals("Bb|9|1", second.voicingId)
        assertEquals(second, dictionary.shape("Bb|9|1"))
        assertEquals(second, dictionary.shapeFor(chord(NoteLetter.A, Accidental.SHARP, "9"), "Bb|9|1"))
        assertEquals("Eb|major|0", ChordNaming.firstVoicingId(chord(NoteLetter.D, Accidental.SHARP)))
        assertNotNull(dictionary.shape(ChordNaming.firstVoicingId(chord(NoteLetter.D, bass = NoteName(NoteLetter.F, Accidental.SHARP)))))
        assertNotNull(dictionary.shape(ChordNaming.firstVoicingId(chord(NoteLetter.A, suffix = "m"))))
    }

    @Test
    fun pickerTypesStartWithTheCommonOnesAndKeepEveryType() {
        val types = dictionary.typesFor(NoteName(NoteLetter.C))
        assertEquals(listOf("major", "minor", "7", "maj7", "m7", "9"), types.take(6).map { it.dictionarySuffix })
        assertEquals(dictionary.chordsFor(NoteName(NoteLetter.C)).toSet(), types.toSet())
        assertTrue(types.takeLast(5).all { '/' in it.dictionarySuffix })
    }

    @Test
    fun rootsFollowThePreference() {
        assertEquals("C C# D D# E F F# G G# A A# B", ChordDictionary.roots(AccidentalPreference.SHARPS).joinToString(" "))
        assertEquals("C Db D Eb E F Gb G Ab A Bb B", ChordDictionary.roots(AccidentalPreference.FLATS).joinToString(" "))
    }
}
