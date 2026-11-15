package com.musicreader.core.dsp

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** One pitch estimate: [hz] = 0 when no period was found; [confidence] = 1 − d′(τ). */
data class PitchEstimate(val hz: Double, val confidence: Double)

/**
 * Reference YIN (de Cheveigné & Kawahara 2002) in plain Kotlin, for JVM tests and as the
 * readable description of the C++ detector in audio/src/main/cpp/dsp/pitch. The difference is
 * computed directly (no FFT), so it is simple and slow; same window, range and threshold.
 */
class Yin(
    private val sampleRate: Int = 48000,
    val windowSize: Int = 2048,
    minHz: Double = 60.0,
    maxHz: Double = 1200.0,
    private val threshold: Double = 0.12,
) {
    private val minLag = maxOf(2, floor(sampleRate / maxHz).toInt())
    private val maxLag = minOf(windowSize / 2, ceil(sampleRate / minHz).toInt())
    private val cmnd = DoubleArray(maxLag + 2)

    /** Confidence from which a frame counts as voiced (same as the C++ detector). */
    val voicedConfidence = 0.85

    fun detect(x: FloatArray, offset: Int = 0): PitchEstimate {
        require(x.size - offset >= windowSize) { "need $windowSize samples" }
        var energy = 0.0
        for (i in 0 until windowSize) energy += x[offset + i].toDouble() * x[offset + i]
        if (energy < 1e-12) return PitchEstimate(0.0, 0.0)

        // Step 2–3: difference function and cumulative mean normalized difference.
        val last = maxLag + 1
        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..last) {
            var d = 0.0
            for (j in 0 until windowSize - tau) {
                val diff = x[offset + j].toDouble() - x[offset + j + tau]
                d += diff * diff
            }
            running += d
            cmnd[tau] = if (running > 0.0) d * tau / running else 1.0
        }

        // Step 4: absolute threshold, then down to the bottom of that dip.
        var tau = 0
        var t = minLag
        while (t <= maxLag) {
            if (cmnd[t] < threshold) {
                while (t + 1 <= maxLag && cmnd[t + 1] < cmnd[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau == 0) {
            tau = minLag
            for (k in minLag..maxLag) if (cmnd[k] < cmnd[tau]) tau = k
        }
        // A shallow dip at half the period (strong 2nd harmonic): prefer the full period.
        if (2 * tau <= maxLag && cmnd[tau] > 0.05) {
            var t2 = 2 * tau
            if (cmnd[t2 - 1] < cmnd[t2]) t2--
            if (cmnd[t2 + 1] < cmnd[t2]) t2++
            if (cmnd[t2] < 0.5 * cmnd[tau]) tau = t2
        }
        // Step 5: parabolic interpolation.
        val off = if (tau in 2 until last) parabolicOffset(cmnd[tau - 1], cmnd[tau], cmnd[tau + 1]) else 0.0
        return PitchEstimate(sampleRate / (tau + off), (1.0 - cmnd[tau]).coerceIn(0.0, 1.0))
    }

    companion object {
        /** Offset (−0.5..0.5) of the extremum of a parabola through three neighbouring values. */
        fun parabolicOffset(left: Double, center: Double, right: Double): Double {
            val denom = left - 2.0 * center + right
            if (abs(denom) < 1e-15) return 0.0
            return (0.5 * (left - right) / denom).coerceIn(-0.5, 0.5)
        }
    }
}
