package com.musicreader.core.model

import kotlin.math.log2
import kotlin.math.pow

/** Equal-temperament conversions between continuous MIDI note numbers and frequency in Hz. */
object Pitch {
    /** Reference pitch: A4 = MIDI 69 = 440 Hz. */
    const val A4_MIDI: Double = 69.0
    const val A4_HZ: Double = 440.0

    /** Frequency in Hz of a (continuous) MIDI note number, e.g. 64.27. */
    fun midiToHz(midi: Double): Double = A4_HZ * 2.0.pow((midi - A4_MIDI) / 12.0)

    /** Continuous MIDI note number of a frequency in Hz. [hz] must be > 0. */
    fun hzToMidi(hz: Double): Double {
        require(hz > 0.0) { "Frequency must be positive, was $hz" }
        return A4_MIDI + 12.0 * log2(hz / A4_HZ)
    }
}
