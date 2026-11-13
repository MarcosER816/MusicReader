package com.musicreader.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A song as stored in `filesDir/songs/<id>.json` (docs/DATA_MODEL.md, schemaVersion 1).
 *
 * [sections] is the song timeline in order. Each [Section] points at a [SectionContent] in
 * [sectionContents]; linked copies of a section share the same content (D-013).
 */
@Serializable
data class Song(
    val id: String,
    val schemaVersion: Int = SongJson.CURRENT_SCHEMA_VERSION,
    val title: String,
    val artist: String? = null,
    val language: Language = Language.EN,
    val instrument: Instrument = Instrument(),
    val tempoBpm: Double = DEFAULT_TEMPO_BPM,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val sections: List<Section> = emptyList(),
    val sectionContents: List<SectionContent> = emptyList(),
    /** Epoch milliseconds. */
    val createdAt: Long,
    /** Epoch milliseconds. */
    val updatedAt: Long,
) {
    companion object {
        const val MIN_TEMPO_BPM: Double = 30.0
        const val MAX_TEMPO_BPM: Double = 300.0
        const val DEFAULT_TEMPO_BPM: Double = 120.0
    }
}

/** Lyrics language; drives syllable splitting (D-007). */
@Serializable
enum class Language {
    @SerialName("es")
    ES,

    @SerialName("en")
    EN,
}

@Serializable
enum class InstrumentType { GUITAR_6_STANDARD }

/**
 * The instrument of a song. Version 1 only supports a 6-string guitar in standard tuning
 * without capo (D-005); the fields exist so that later versions need no schema change.
 */
@Serializable
data class Instrument(
    val type: InstrumentType = InstrumentType.GUITAR_6_STANDARD,
    /** MIDI note of each open string, from low E (string 6) to high e (string 1). */
    val tuning: List<Int> = STANDARD_TUNING,
    val capo: Int = 0,
) {
    companion object {
        val STANDARD_TUNING: List<Int> = listOf(40, 45, 50, 55, 59, 64)
    }
}

/**
 * Time signature. A "beat" in MusicReader is one [den] note: a 6/8 bar has 6 beats and the
 * tempo counts eighth notes per minute (D-018).
 */
@Serializable
data class TimeSignature(val num: Int, val den: Int) {
    init {
        require(num in MIN_NUM..MAX_NUM) { "Numerator must be in $MIN_NUM..$MAX_NUM, was $num" }
        require(den in ALLOWED_DENOMINATORS) { "Denominator must be one of $ALLOWED_DENOMINATORS, was $den" }
    }

    /** Beats in one bar. */
    val beatsPerBar: Int get() = num

    override fun toString(): String = "$num/$den"

    companion object {
        const val MIN_NUM: Int = 1
        const val MAX_NUM: Int = 32
        val ALLOWED_DENOMINATORS: List<Int> = listOf(1, 2, 4, 8, 16, 32)

        val TWO_FOUR = TimeSignature(2, 4)
        val THREE_FOUR = TimeSignature(3, 4)
        val FOUR_FOUR = TimeSignature(4, 4)
        val SIX_EIGHT = TimeSignature(6, 8)
        val TWELVE_EIGHT = TimeSignature(12, 8)

        /** The presets offered in song settings; anything else is "custom". */
        val PRESETS: List<TimeSignature> = listOf(TWO_FOUR, THREE_FOUR, FOUR_FOUR, SIX_EIGHT, TWELVE_EIGHT)

        fun isValid(num: Int, den: Int): Boolean = num in MIN_NUM..MAX_NUM && den in ALLOWED_DENOMINATORS
    }
}
