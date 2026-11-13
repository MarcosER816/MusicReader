package com.musicreader.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Technique {
    NONE,
    HAMMER_ON,
    PULL_OFF,
    SLIDE_UP,
    SLIDE_DOWN,
    BEND,
    BEND_RELEASE,
    VIBRATO,
    NATURAL_HARMONIC,
    ARTIFICIAL_HARMONIC,
    MUTE,
}

/** One tab note. Beats are relative to the section start. */
@Serializable
data class TabEvent(
    val id: String,
    val beat: Double,
    val durationBeats: Double,
    /** 1 = high e … 6 = low E. */
    val string: Int,
    val fret: Int,
    /** 0 = open, 1–4 = index to pinky. No thumb (D-012). */
    val finger: Int,
    val technique: Technique = Technique.NONE,
    /** For BEND and BEND_RELEASE. */
    val bendQuarterTones: Int? = null,
    /** For slides, hammer-ons and pull-offs. */
    val targetFret: Int? = null,
    /** For harmonics. */
    val harmonicFret: Int? = null,
) {
    companion object {
        const val HIGHEST_STRING: Int = 1
        const val LOWEST_STRING: Int = 6
        const val OPEN: Int = 0
        const val MAX_FINGER: Int = 4
    }
}
