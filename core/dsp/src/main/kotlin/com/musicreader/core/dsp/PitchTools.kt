package com.musicreader.core.dsp

import kotlin.math.roundToInt

/** The level gate for voiced/unvoiced, from a short recording of room noise (D-030). */
object NoiseGate {
    /** dB above the room's median level that a sound must reach to count. */
    const val MARGIN_DB: Double = 10.0
    /** The gate never goes below this, so a silent room does not let noise through. */
    const val FLOOR_DB: Double = -70.0
    /** …nor above this, so a loud room still lets singing through. */
    const val CEILING_DB: Double = -25.0
    /** 2 s of 10 ms frames. */
    const val FRAMES: Int = 200

    fun fromLevels(levelsDb: List<Double>): Double {
        require(levelsDb.isNotEmpty())
        val sorted = levelsDb.sorted()
        val median = sorted[sorted.size / 2]
        return (median + MARGIN_DB).coerceIn(FLOOR_DB, CEILING_DB)
    }
}

/** Nearest equal-tempered note and the distance to it, for the tuner. */
data class TunerReading(
    /** MIDI number of the nearest note (69 = A4). */
    val note: Int,
    /** −50..+50 cents from that note. */
    val cents: Double,
) {
    /** Octave in scientific pitch notation (A4 = 440 Hz, middle C = C4). */
    val octave: Int get() = Math.floorDiv(note, 12) - 1

    /** 0 = C … 11 = B. */
    val pitchClass: Int get() = Math.floorMod(note, 12)

    companion object {
        fun of(midi: Double): TunerReading {
            val n = midi.roundToInt()
            return TunerReading(n, (midi - n) * 100.0)
        }
    }
}

/**
 * The last seconds of a pitch track for drawing: a fixed ring of (time, midi) points; NaN midi
 * is a gap (unvoiced). Allocated once; adding a point never allocates.
 */
class PitchTrace(val capacity: Int = 600) {
    private val times = DoubleArray(capacity)
    private val midis = FloatArray(capacity)
    private var start = 0
    var size = 0
        private set

    /** Increases with every change, so a screen can redraw. */
    var version = 0L
        private set

    fun add(seconds: Double, midi: Double?) {
        val i = (start + size) % capacity
        times[i] = seconds
        midis[i] = midi?.toFloat() ?: Float.NaN
        if (size < capacity) size++ else start = (start + 1) % capacity
        version++
    }

    fun clear() {
        size = 0
        start = 0
        version++
    }

    /** Point [k] from the oldest (0) to the newest (size − 1). */
    fun time(k: Int): Double = times[(start + k) % capacity]
    fun midi(k: Int): Float = midis[(start + k) % capacity]

    /** Time of the newest point; 0 when empty. */
    val latestTime: Double get() = if (size == 0) 0.0 else time(size - 1)

    /** Lowest and highest voiced note within the last [seconds]; null if none. */
    fun range(seconds: Double): ClosedFloatingPointRange<Float>? {
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        val from = latestTime - seconds
        for (k in 0 until size) {
            if (time(k) < from) continue
            val m = midi(k)
            if (m.isNaN()) continue
            if (m < lo) lo = m
            if (m > hi) hi = m
        }
        return if (lo <= hi) lo..hi else null
    }
}
