package com.musicreader.core.model

import kotlinx.serialization.Serializable

/** How a latency was measured (D-029). */
@Serializable
enum class LatencyMethod { AUTOMATIC, MANUAL }

/**
 * The measured round trip of one audio route: from a sample being written to the output until
 * the microphone hears it, in milliseconds. Practice judging and recordings subtract it.
 */
@Serializable
data class LatencyProfile(
    val roundTripMs: Double,
    val method: LatencyMethod,
    /** 0..1, how much the measurement can be trusted. */
    val confidence: Double,
    /** When it was measured, epoch milliseconds. */
    val measuredAt: Long,
)

object LatencyOps {
    /** Above this round trip, practice timing cannot be judged fairly (Bluetooth is 150–300 ms). */
    const val TOO_SLOW_FOR_PRACTICE_MS: Double = 100.0

    /** Below this confidence the screen asks to measure again. */
    const val LOW_CONFIDENCE: Double = 0.5

    fun save(settings: AppSettings, route: AudioRoute, profile: LatencyProfile): AppSettings =
        settings.copy(latencyProfiles = settings.latencyProfiles + (route to profile))

    fun clear(settings: AppSettings, route: AudioRoute): AppSettings =
        settings.copy(latencyProfiles = settings.latencyProfiles - route)

    /** Bluetooth always buffers too much for practice; other routes only if measured that slow. */
    fun tooSlowForPractice(route: AudioRoute, profile: LatencyProfile?): Boolean =
        route == AudioRoute.BLUETOOTH || (profile != null && profile.roundTripMs > TOO_SLOW_FOR_PRACTICE_MS)
}

/** Pitch detector choice (D-030). */
@Serializable
enum class PitchMethod { YIN, MPM }

/** Pitch detection settings, shared by the tuner and the later singing stages (D-030). */
@Serializable
data class PitchSettings(
    val method: PitchMethod = PitchMethod.YIN,
    /** Frames quieter than this (dBFS) are unvoiced; measured from 2 s of room noise. */
    val noiseGateDb: Double = DEFAULT_NOISE_GATE_DB,
    /** When the room noise was measured (epoch ms); null = default gate. */
    val gateMeasuredAt: Long? = null,
) {
    companion object {
        const val DEFAULT_NOISE_GATE_DB: Double = -55.0
    }
}
