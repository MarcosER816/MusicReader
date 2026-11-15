package com.musicreader.core.dsp

import com.musicreader.core.model.Pitch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YinTest {
    private val rate = 48000
    private val yin = Yin(rate)

    private fun cents(hz: Double, ref: Double) = 1200.0 * log2(hz / ref)

    private fun tone(hz: Double, amps: List<Double>, n: Int = 2048, phase: Double = 0.3) = FloatArray(n) { i ->
        amps.indices.sumOf { h -> amps[h] * sin(2 * PI * hz * (h + 1) * i / rate + phase * (h + 1)) }.toFloat()
    }

    private val pure = listOf(0.5)
    private val harmonic = listOf(0.4, 0.3, 0.2, 0.12, 0.08, 0.05)

    @Test
    fun pureAndHarmonicTonesFrom80To1000HzWithinFiveCents() {
        for (hz in listOf(70.0, 80.0, 82.41, 110.0, 146.83, 196.0, 246.94, 329.63, 440.0, 523.25, 659.26, 880.0, 1000.0, 1100.0)) {
            for ((name, amps) in listOf("pure" to pure, "harmonic" to harmonic)) {
                val e = yin.detect(tone(hz, amps))
                assertTrue(abs(cents(e.hz, hz)) < 5.0, "$name $hz Hz read as ${e.hz}")
                assertTrue(e.confidence >= yin.voicedConfidence, "$name $hz confidence ${e.confidence}")
            }
        }
    }

    @Test
    fun strongSecondHarmonicDoesNotJumpAnOctave() {
        for (hz in listOf(82.41, 110.0, 196.0, 261.63, 440.0, 659.26)) {
            val e = yin.detect(tone(hz, listOf(0.15, 0.45, 0.1)))
            assertTrue(abs(cents(e.hz, hz)) < 5.0, "$hz Hz read as ${e.hz}")
        }
    }

    @Test
    fun noiseAndSilenceAreUnvoiced() {
        assertEquals(0.0, yin.detect(FloatArray(2048)).hz)
        val rnd = Random(5)
        repeat(20) {
            val noise = FloatArray(2048) { (rnd.nextDouble() * 2 - 1).toFloat() * 0.3f }
            assertTrue(yin.detect(noise).confidence < yin.voicedConfidence)
        }
    }

    @Test
    fun vibratoIsFollowedFrameByFrame() {
        // 220 Hz ±50 cents at 5.5 Hz; each 2048 window is compared with the pitch at its center.
        val n = rate
        fun freqAt(t: Double) = 220.0 * 2.0.pow(0.5 / 12.0 * sin(2 * PI * 5.5 * t))
        var phase = 0.0
        val x = FloatArray(n) { i ->
            phase += 2 * PI * freqAt(i.toDouble() / rate) / rate
            (0.4 * sin(phase) + 0.2 * sin(2 * phase)).toFloat()
        }
        var worst = 0.0
        var offset = 0
        while (offset + 2048 <= n) {
            val e = yin.detect(x, offset)
            val truth = freqAt((offset + 1024).toDouble() / rate)
            worst = maxOf(worst, abs(cents(e.hz, truth)))
            offset += 480
        }
        assertTrue(worst < 15.0, "worst $worst cents")
    }

    @Test
    fun pluckedLowE() {
        val x = FloatArray(2048) { i ->
            val t = (i + 4800).toDouble() / rate  // 100 ms after the pluck
            (1..10).sumOf { h -> (0.5 / h) * exp(-t * (1.0 + 0.6 * h)) * sin(2 * PI * 82.41 * h * t + h) }.toFloat()
        }
        val e = yin.detect(x)
        assertTrue(abs(cents(e.hz, 82.41)) < 5.0, "read ${e.hz}")
        assertEquals(40.0, Pitch.hzToMidi(e.hz), 0.05)
    }

    @Test
    fun parabolaFindsTheVertex() {
        // y = (x − 0.3)²  at x = −1, 0, 1
        assertEquals(0.3, Yin.parabolicOffset(1.69, 0.09, 0.49), 1e-9)
        assertEquals(0.0, Yin.parabolicOffset(1.0, 1.0, 1.0))
    }
}
