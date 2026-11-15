package com.musicreader.core.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PitchToolsTest {
    @Test
    fun gateIsTheRoomsMedianPlusTenDecibels() {
        val levels = List(200) { if (it % 10 == 0) -30.0 else -62.0 }  // a few door knocks
        assertEquals(-52.0, NoiseGate.fromLevels(levels))
        assertEquals(NoiseGate.FLOOR_DB, NoiseGate.fromLevels(List(200) { -120.0 }), "digital silence")
        assertEquals(NoiseGate.CEILING_DB, NoiseGate.fromLevels(List(200) { -20.0 }), "very loud room")
    }

    @Test
    fun tunerReadsNoteOctaveAndCents() {
        val a4 = TunerReading.of(69.0)
        assertEquals(69, a4.note)
        assertEquals(4, a4.octave)
        assertEquals(9, a4.pitchClass)
        assertEquals(0.0, a4.cents, 1e-9)
        val sharpE = TunerReading.of(40.12)  // low E, 12 cents sharp
        assertEquals(40, sharpE.note)
        assertEquals(2, sharpE.octave)
        assertEquals(12.0, sharpE.cents, 1e-6)
        val flatC = TunerReading.of(59.7)  // 30 cents under middle C
        assertEquals(60, flatC.note)
        assertEquals(-30.0, flatC.cents, 1e-6)
        assertEquals(4, flatC.octave)
    }

    @Test
    fun traceKeepsTheLastPointsInOrder() {
        val trace = PitchTrace(capacity = 4)
        assertNull(trace.range(5.0))
        trace.add(0.00, 60.0)
        trace.add(0.01, null)
        trace.add(0.02, 62.0)
        trace.add(0.03, 61.0)
        trace.add(0.04, 64.0)  // pushes out the first point
        assertEquals(4, trace.size)
        assertEquals(0.01, trace.time(0))
        assertEquals(true, trace.midi(0).isNaN())
        assertEquals(64f, trace.midi(3))
        assertEquals(0.04, trace.latestTime)
        assertEquals(61f..64f, trace.range(5.0))
        assertEquals(61f..64f, trace.range(0.015))
        val v = trace.version
        trace.clear()
        assertEquals(0, trace.size)
        assertEquals(v + 1, trace.version)
    }
}
