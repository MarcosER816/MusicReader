package com.musicreader.core.model

import kotlin.math.floor
import kotlin.math.roundToLong

/** Position of a beat inside the bar grid. Both values are 0-based. */
data class BarPosition(val barIndex: Int, val beatInBar: Double) {
    /** 1-based bar number for display. */
    val barNumber: Int get() = barIndex + 1
}

/**
 * Conversions between beats, seconds and bars. A beat is one time-signature denominator note
 * and the tempo counts beats per minute (D-018).
 */
object BeatTime {
    /** seconds = beats × 60 / tempo. */
    fun beatsToSeconds(beats: Double, tempoBpm: Double): Double {
        require(tempoBpm > 0.0) { "Tempo must be positive, was $tempoBpm" }
        return beats * 60.0 / tempoBpm
    }

    /** beats = seconds × tempo / 60. */
    fun secondsToBeats(seconds: Double, tempoBpm: Double): Double {
        require(tempoBpm > 0.0) { "Tempo must be positive, was $tempoBpm" }
        return seconds * tempoBpm / 60.0
    }

    /** Bar index and beat inside the bar of an absolute [beat]. Negative beats (count-in) give negative bars. */
    fun barPosition(beat: Double, timeSignature: TimeSignature): BarPosition {
        val perBar = timeSignature.beatsPerBar.toDouble()
        val bar = floor(beat / perBar)
        return BarPosition(barIndex = bar.toInt(), beatInBar = beat - bar * perBar)
    }

    /** First beat of bar [barIndex] (0-based). */
    fun barStartBeat(barIndex: Int, timeSignature: TimeSignature): Double =
        barIndex.toDouble() * timeSignature.beatsPerBar

    fun barsToBeats(bars: Int, timeSignature: TimeSignature): Int = bars * timeSignature.beatsPerBar

    /** Formats a duration as m:ss, rounding to whole seconds (e.g. 95.4 s → "1:35"). */
    fun formatMinutesSeconds(seconds: Double): String {
        val total = seconds.coerceAtLeast(0.0).roundToLong()
        return "%d:%02d".format(total / 60, total % 60)
    }
}

/** Beat where each section starts, in timeline order. */
fun Song.sectionStartBeats(): List<Int> {
    var start = 0
    return sections.map { section ->
        val s = start
        start += contentOf(section).lengthBeats
        s
    }
}

/** Total length of the song in beats. */
val Song.totalBeats: Int get() = sections.sumOf { contentOf(it).lengthBeats }

/** Total length of the song in bars. */
val Song.totalBars: Int get() = totalBeats / timeSignature.beatsPerBar

/** Total length of the song in seconds at its tempo. */
val Song.durationSeconds: Double get() = BeatTime.beatsToSeconds(totalBeats.toDouble(), tempoBpm)
