package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BeatTimeTest {
    @Test
    fun beatsToSecondsUsesSixtyOverTempo() {
        assertEquals(0.5, BeatTime.beatsToSeconds(1.0, 120.0), 1e-12)
        assertEquals(8.0, BeatTime.beatsToSeconds(16.0, 120.0), 1e-12)
        assertEquals(2.0, BeatTime.beatsToSeconds(1.0, 30.0), 1e-12)
        assertEquals(0.2, BeatTime.beatsToSeconds(1.0, 300.0), 1e-12)
    }

    @Test
    fun secondsToBeatsIsTheInverse() {
        for (tempo in listOf(30.0, 96.5, 120.0, 300.0)) {
            for (beats in listOf(0.0, 0.25, 3.0, 17.75)) {
                val seconds = BeatTime.beatsToSeconds(beats, tempo)
                assertEquals(beats, BeatTime.secondsToBeats(seconds, tempo), 1e-9)
            }
        }
    }

    @Test
    fun tempoMustBePositive() {
        assertFailsWith<IllegalArgumentException> { BeatTime.beatsToSeconds(1.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { BeatTime.secondsToBeats(1.0, -5.0) }
    }

    @Test
    fun barPositionIn44() {
        val ts = TimeSignature.FOUR_FOUR
        assertEquals(BarPosition(0, 0.0), BeatTime.barPosition(0.0, ts))
        assertEquals(BarPosition(0, 3.5), BeatTime.barPosition(3.5, ts))
        assertEquals(BarPosition(1, 0.0), BeatTime.barPosition(4.0, ts))
        assertEquals(BarPosition(2, 1.5), BeatTime.barPosition(9.5, ts))
        assertEquals(3, BeatTime.barPosition(9.5, ts).barNumber)
    }

    @Test
    fun barPositionIn34And68() {
        assertEquals(BarPosition(2, 1.0), BeatTime.barPosition(7.0, TimeSignature.THREE_FOUR))
        assertEquals(BarPosition(1, 5.0), BeatTime.barPosition(11.0, TimeSignature.SIX_EIGHT))
        assertEquals(BarPosition(0, 11.0), BeatTime.barPosition(11.0, TimeSignature.TWELVE_EIGHT))
    }

    @Test
    fun countInBeatsAreInNegativeBars() {
        assertEquals(BarPosition(-1, 0.0), BeatTime.barPosition(-4.0, TimeSignature.FOUR_FOUR))
        assertEquals(BarPosition(-1, 3.0), BeatTime.barPosition(-1.0, TimeSignature.FOUR_FOUR))
    }

    @Test
    fun barsAndBeats() {
        assertEquals(12.0, BeatTime.barStartBeat(4, TimeSignature.THREE_FOUR))
        assertEquals(24, BeatTime.barsToBeats(4, TimeSignature.SIX_EIGHT))
    }

    @Test
    fun songLengthAndSectionStarts() {
        val song = TestSongs.fullSong // 8 + 32 + 16 + 32 + 16 beats at 96.5 BPM
        assertEquals(listOf(0, 8, 40, 56, 88), song.sectionStartBeats())
        assertEquals(104, song.totalBeats)
        assertEquals(26, song.totalBars)
        assertEquals(104 * 60.0 / 96.5, song.durationSeconds, 1e-9)
    }

    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("0:00", BeatTime.formatMinutesSeconds(0.0))
        assertEquals("0:08", BeatTime.formatMinutesSeconds(8.0))
        assertEquals("1:35", BeatTime.formatMinutesSeconds(95.4))
        assertEquals("1:05", BeatTime.formatMinutesSeconds(64.6))
        assertEquals("12:00", BeatTime.formatMinutesSeconds(720.0))
    }

    @Test
    fun timeSignatureValidation() {
        assertFailsWith<IllegalArgumentException> { TimeSignature(0, 4) }
        assertFailsWith<IllegalArgumentException> { TimeSignature(33, 4) }
        assertFailsWith<IllegalArgumentException> { TimeSignature(5, 6) }
        assertEquals("7/8", TimeSignature(7, 8).toString())
        assertEquals(true, TimeSignature.isValid(5, 4))
        assertEquals(false, TimeSignature.isValid(5, 5))
    }
}
