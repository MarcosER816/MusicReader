package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsTest {
    @Test
    fun defaultColorsMatchDataModel() {
        val s = AppSettings()
        assertEquals(0xFFE53935, s.chordColors[NoteLetter.C])
        assertEquals(0xFFB39DDB, s.chordColors[NoteLetter.B])
        assertEquals(NoteLetter.entries.toSet(), s.chordColors.keys)
        assertEquals(setOf(0, 1, 2, 3, 4), s.fingerColors.keys)
        assertEquals(0xFFEF5350, s.fingerColors[4])
    }

    @Test
    fun practiceLowerLayersNeverShowThePracticedLayer() {
        val d = AppSettings.DEFAULT_PRACTICE_LOWER_LAYERS
        assertFalse(d.getValue(PracticeType.SING).lyrics)
        assertFalse(d.getValue(PracticeType.CHORDS).chords)
        assertFalse(d.getValue(PracticeType.TAB).tab)
    }

    @Test
    fun viewerKeepsAtLeastOneLayer() {
        val onlyLyrics = LayerVisibility(lyrics = true, chords = false, tab = false)
        assertEquals(onlyLyrics, onlyLyrics.toggled(Layer.LYRICS, keepOneVisible = true))
        assertEquals(LayerVisibility(false, false, false), onlyLyrics.toggled(Layer.LYRICS, keepOneVisible = false))
        assertEquals(LayerVisibility(true, true, false), onlyLyrics.toggled(Layer.CHORDS, keepOneVisible = true))
    }

    @Test
    fun chordSymbolNames() {
        assertEquals("D#", ChordSymbol(NoteLetter.D, Accidental.SHARP).toString())
        assertEquals("Bb9", ChordSymbol(NoteLetter.B, Accidental.FLAT, "9").toString())
        assertEquals("Cmaj7", ChordSymbol(NoteLetter.C, suffix = "maj7").toString())
        assertEquals("D/F#", ChordSymbol(NoteLetter.D, bass = NoteName(NoteLetter.F, Accidental.SHARP)).toString())
    }
}
