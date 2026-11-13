package com.musicreader.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PracticeType { SING, CHORDS, TAB }

/** A practice recording stored under `songs/<id>/rec/`. At most one official per practice type. */
@Serializable
data class Recording(
    val practiceType: PracticeType,
    /** Path relative to `songs/<id>/rec/`. */
    val file: String,
    val sampleRate: Int = SAMPLE_RATE,
    /** Song beat where the recording starts. */
    val startSongBeat: Double,
    /** Audio frame in the file that corresponds to [startSongBeat]. */
    val startFrameOffset: Long,
    /** Output + input latency that is removed when mixing. */
    val latencyCompensationMs: Double,
    val official: Boolean = false,
    /** Epoch milliseconds. */
    val createdAt: Long,
) {
    companion object {
        const val SAMPLE_RATE: Int = 48_000
    }
}
