package com.musicreader.core.model

import kotlinx.serialization.Serializable

/**
 * A lyric line. Voice 0 is the main voice; voice 1 is a second voice sung at the same time
 * (D-003). Each second-voice line belongs to one main line, named by [pairedLineId] (D-022).
 */
@Serializable
data class LyricLine(
    val id: String,
    val voice: Int = MAIN_VOICE,
    val text: String,
    val words: List<Word> = emptyList(),
    /** For a second-voice line: the id of the main line it is sung with. Null for main lines. */
    val pairedLineId: String? = null,
) {
    companion object {
        const val MAIN_VOICE: Int = 0
        const val SECOND_VOICE: Int = 1
    }
}

@Serializable
data class Word(
    val id: String,
    val text: String,
    val syllables: List<Syllable> = emptyList(),
    /** True when the user split this word by hand instead of the automatic splitter. */
    val syllableOverride: Boolean = false,
)

/**
 * A syllable with its sung timing and pitch (D-010). Timing and pitch are null until the
 * syllable is sung or set in the note editor. Beats are relative to the section start.
 */
@Serializable
data class Syllable(
    val id: String,
    val text: String,
    val startBeat: Double? = null,
    val durationBeats: Double? = null,
    /** Initial note, continuous MIDI (e.g. 64.27). */
    val pitchStartMidi: Double? = null,
    /** Final note, continuous MIDI. */
    val pitchEndMidi: Double? = null,
    /** Sung pitch curve; empty when not sung. */
    val pitchCurve: List<CurvePoint> = emptyList(),
    /** True for syllables without a judged pitch. */
    val freestyle: Boolean = false,
    /** The take that produced the timing (last take wins). */
    val takeId: String? = null,
)

/** One point of a syllable's pitch curve. [t] is 0.0 at the syllable start and 1.0 at its end. */
@Serializable
data class CurvePoint(val t: Double, val midi: Double)
