package com.musicreader.core.model

import kotlinx.serialization.Serializable

/** Natural note letters. Chord colors are chosen by letter only (D-011). */
@Serializable
enum class NoteLetter { C, D, E, F, G, A, B }

@Serializable
enum class Accidental(val symbol: String) {
    NONE(""),
    SHARP("#"),
    FLAT("b"),
}

@Serializable
data class NoteName(val letter: NoteLetter, val accidental: Accidental = Accidental.NONE) {
    override fun toString(): String = letter.name + accidental.symbol
}

/** A chord name such as Bb9 (root B, FLAT, suffix "9") or D/F# (bass F#). */
@Serializable
data class ChordSymbol(
    val root: NoteLetter,
    val accidental: Accidental = Accidental.NONE,
    val suffix: String = "",
    val bass: NoteName? = null,
) {
    /** Display name, e.g. "D#", "Bb9", "Cmaj7", "D/F#". */
    override fun toString(): String =
        root.name + accidental.symbol + suffix + (bass?.let { "/$it" } ?: "")
}

/**
 * A chord placed in a section. In rows with lyrics it is placed on a syllable ([syllableId],
 * D-025) and drawn right above it; elsewhere it sits on the half-beat grid (D-009).
 */
@Serializable
data class ChordEvent(
    val id: String,
    /**
     * Beat relative to the section start; a multiple of 0.5. For a chord on a syllable this is the
     * syllable's start beat, estimated from its place in the line until the syllable is sung.
     */
    val beat: Double,
    val symbol: ChordSymbol,
    /** Id of the chosen voicing in the chord dictionary (frets and fingers). */
    val voicingId: String,
    /** The main-voice syllable the chord is placed on, or null for a chord on the beat grid. */
    val syllableId: String? = null,
)

@Serializable
enum class StrumDirection { DOWN, UP, MUTE }

/** One stroke of a strumming pattern. [atBeat] is relative to the start of a bar. */
@Serializable
data class StrumStroke(
    val atBeat: Double,
    val dir: StrumDirection,
    val accent: Boolean = false,
)

/** A one-bar strumming pattern that repeats every bar of a section (D-008). */
@Serializable
data class StrumPattern(
    /** Strokes per beat, e.g. 2 = eighths, 4 = sixteenths. */
    val subdivision: Int,
    val strokes: List<StrumStroke>,
)
