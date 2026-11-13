package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TapTempoTest {
    private fun tapAt(vararg times: Long): TapTempo = times.fold(TapTempo()) { t, ms -> t.tap(ms) }

    @Test
    fun oneTapHasNoTempo() {
        assertNull(tapAt(1000).bpm)
    }

    @Test
    fun steadyTapsGiveTheirTempo() {
        assertEquals(120.0, tapAt(0, 500, 1000, 1500).bpm)
        assertEquals(60.0, tapAt(0, 1000).bpm)
        assertEquals(100.0, tapAt(0, 600, 1200, 1800, 2400).bpm)
    }

    @Test
    fun unevenTapsAreAveraged() {
        // Intervals 480, 520, 500 → 500 ms → 120 BPM.
        assertEquals(120.0, tapAt(0, 480, 1000, 1500).bpm)
        // 3 intervals over 1890 ms → 630 ms → 95.238 → 95.2.
        assertEquals(95.2, tapAt(10, 640, 1270, 1900).bpm)
    }

    @Test
    fun onlyTheLastEightTapsCount() {
        val slowThenFast = tapAt(0, 1000, 2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000)
        assertEquals(TapTempo.MAX_TAPS, slowThenFast.tapCount)
        assertEquals(120.0, slowThenFast.bpm)
    }

    @Test
    fun longPauseStartsOver() {
        val t = tapAt(0, 500, 1000, 4000)
        assertEquals(1, t.tapCount)
        assertNull(t.bpm)
        assertEquals(60.0, t.tap(5000).bpm)
    }

    @Test
    fun tempoIsClampedToAllowedRange() {
        assertEquals(300.0, tapAt(0, 100, 200).bpm)
        assertEquals(30.0, tapAt(0, 2400).bpm)
    }

    @Test
    fun clockGoingBackwardsStartsOver() {
        assertEquals(1, tapAt(1000, 500).tapCount)
    }
}
