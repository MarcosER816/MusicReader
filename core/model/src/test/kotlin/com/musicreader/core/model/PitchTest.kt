package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PitchTest {
    @Test
    fun a4Is440Hz() {
        assertEquals(440.0, Pitch.midiToHz(69.0), 1e-9)
    }

    @Test
    fun octaveDoublesFrequency() {
        assertEquals(880.0, Pitch.midiToHz(81.0), 1e-9)
        assertEquals(220.0, Pitch.midiToHz(57.0), 1e-9)
    }

    @Test
    fun guitarOpenStringsInStandardTuning() {
        // Low E (MIDI 40) to high e (MIDI 64), see DATA_MODEL.md.
        val expectedHz = listOf(82.4069, 110.0, 146.8324, 195.9977, 246.9417, 329.6276)
        listOf(40, 45, 50, 55, 59, 64).zip(expectedHz).forEach { (midi, hz) ->
            assertEquals(hz, Pitch.midiToHz(midi.toDouble()), 1e-3)
        }
    }

    @Test
    fun hzToMidiIsInverseOfMidiToHz() {
        for (midi in listOf(40.0, 52.5, 64.27, 69.0, 88.1)) {
            assertEquals(midi, Pitch.hzToMidi(Pitch.midiToHz(midi)), 1e-9)
        }
    }

    @Test
    fun hzToMidiRejectsNonPositiveFrequency() {
        assertFailsWith<IllegalArgumentException> { Pitch.hzToMidi(0.0) }
    }
}
