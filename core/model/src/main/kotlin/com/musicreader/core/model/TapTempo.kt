package com.musicreader.core.model

import kotlin.math.roundToInt

/**
 * Tap-tempo state. Each [tap] adds a timestamp (milliseconds from any monotonic clock); the tempo
 * is the average of the intervals between the last [MAX_TAPS] taps. A pause longer than
 * [RESET_AFTER_MS] starts a new measurement.
 */
data class TapTempo(val tapsMs: List<Long> = emptyList()) {

    fun tap(nowMs: Long): TapTempo {
        val last = tapsMs.lastOrNull()
        return if (last == null || nowMs <= last || nowMs - last > RESET_AFTER_MS) {
            TapTempo(listOf(nowMs))
        } else {
            TapTempo((tapsMs + nowMs).takeLast(MAX_TAPS))
        }
    }

    val tapCount: Int get() = tapsMs.size

    /** Tempo rounded to 0.1 BPM and clamped to the allowed range, or null before the second tap. */
    val bpm: Double?
        get() {
            if (tapsMs.size < 2) return null
            val averageIntervalMs = (tapsMs.last() - tapsMs.first()).toDouble() / (tapsMs.size - 1)
            val raw = 60_000.0 / averageIntervalMs
            return roundTempo(raw.coerceIn(Song.MIN_TEMPO_BPM, Song.MAX_TEMPO_BPM))
        }

    companion object {
        const val MAX_TAPS: Int = 8

        /** Longer than one beat at the slowest tempo (30 BPM = 2000 ms). */
        const val RESET_AFTER_MS: Long = 2_500

        /** Rounds a tempo to one decimal. */
        fun roundTempo(bpm: Double): Double = (bpm * 10.0).roundToInt() / 10.0
    }
}
