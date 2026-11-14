package com.musicreader.audio

/** Sounds the engine can schedule (same ids as the C++ Renderer). */
enum class EngineSound(val id: Int) { CLICK(0), ACCENT_CLICK(1), CHIRP(2) }

/**
 * The metronome's beat grid in engine frames (the C++ BeatGrid). Each beat frame is computed
 * from the anchor, so it never drifts.
 */
data class BeatGridInfo(
    val anchorFrame: Long,
    val anchorBeat: Long,
    val framesPerBeat: Double,
    val beatsPerBar: Int,
    val barOriginBeat: Long,
) {
    /** Fractional beat at [frame]; negative before beat 0. */
    fun beatAt(frame: Double): Double = anchorBeat + (frame - anchorFrame) / framesPerBeat

    /** 0-based position of [beat] in its bar (0 = the accented beat 1). */
    fun beatInBar(beat: Long): Int = Math.floorMod(beat - barOriginBeat, beatsPerBar.toLong()).toInt()

    companion object {
        fun fromArray(a: DoubleArray?): BeatGridInfo? = a?.let {
            BeatGridInfo(it[0].toLong(), it[1].toLong(), it[2], it[3].toInt(), it[4].toLong())
        }
    }
}

/** Result of one calibration analysis (the C++ LatencyResult). */
data class LatencyMeasurement(
    /** Round-trip ms; null when nothing usable was found. */
    val latencyMs: Double?,
    val confidence: Double,
    val found: Int,
    val expected: Int,
    val spreadMs: Double,
) {
    companion object {
        fun fromArray(a: DoubleArray) = LatencyMeasurement(
            latencyMs = a[0].takeIf { it >= 0.0 },
            confidence = a[1],
            found = a[2].toInt(),
            expected = a[3].toInt(),
            spreadMs = a[4],
        )
    }
}

enum class SharingMode { EXCLUSIVE, SHARED, UNKNOWN }
enum class PerformanceMode { NONE, POWER_SAVING, LOW_LATENCY, UNKNOWN }
enum class AudioApiName { AAUDIO, OPENSL_ES, UNSPECIFIED }

/** What the Diagnostics screen shows (the C++ EngineDiagnostics). */
data class EngineDiagnostics(
    val running: Boolean = false,
    val sampleRate: Int = 0,
    val channelCount: Int = 0,
    val framesPerBurst: Int = 0,
    val bufferSize: Int = 0,
    val bufferCapacity: Int = 0,
    val sharingMode: SharingMode = SharingMode.UNKNOWN,
    val performanceMode: PerformanceMode = PerformanceMode.UNKNOWN,
    val audioApi: AudioApiName = AudioApiName.UNSPECIFIED,
    /** Underruns since the stream opened; null if the API does not count them. */
    val xRunCount: Int? = null,
    val deviceId: Int = 0,
    val inputOpen: Boolean = false,
    val inputSharingMode: SharingMode = SharingMode.UNKNOWN,
    val inputDeviceId: Int = 0,
    val inputFramesPerBurst: Int = 0,
    /** Output latency from the stream's timestamps; null until known. */
    val outputLatencyMs: Double? = null,
    val callbacks: Long = 0,
    val inputShortReads: Long = 0,
    val inputOverflows: Long = 0,
    val restarts: Int = 0,
) {
    fun framesToMs(frames: Int): Double = if (sampleRate > 0) frames * 1000.0 / sampleRate else 0.0

    companion object {
        private fun sharing(v: Double) = when (v.toInt()) {
            0 -> SharingMode.EXCLUSIVE
            1 -> SharingMode.SHARED
            else -> SharingMode.UNKNOWN
        }

        fun fromArray(a: DoubleArray) = EngineDiagnostics(
            running = a[0] != 0.0,
            sampleRate = a[1].toInt(),
            channelCount = a[2].toInt(),
            framesPerBurst = a[3].toInt(),
            bufferSize = a[4].toInt(),
            bufferCapacity = a[5].toInt(),
            sharingMode = sharing(a[6]),
            performanceMode = when (a[7].toInt()) {
                10 -> PerformanceMode.NONE
                11 -> PerformanceMode.POWER_SAVING
                12 -> PerformanceMode.LOW_LATENCY
                else -> PerformanceMode.UNKNOWN
            },
            audioApi = when (a[8].toInt()) {
                1 -> AudioApiName.OPENSL_ES
                2 -> AudioApiName.AAUDIO
                else -> AudioApiName.UNSPECIFIED
            },
            xRunCount = a[9].toInt().takeIf { it >= 0 },
            deviceId = a[10].toInt(),
            inputOpen = a[11] != 0.0,
            inputSharingMode = sharing(a[12]),
            inputDeviceId = a[13].toInt(),
            inputFramesPerBurst = a[14].toInt(),
            outputLatencyMs = a[15].takeIf { it >= 0.0 },
            callbacks = a[16].toLong(),
            inputShortReads = a[17].toLong(),
            inputOverflows = a[18].toLong(),
            restarts = a[19].toInt(),
        )
    }
}

/** A block of microphone samples; sample 0 has engine frame [firstFrame]. */
data class InputChunk(val count: Int, val firstFrame: Long)

/**
 * The audio engine as Kotlin sees it. [NativeAudio] is the real one; tests use a fake.
 * Frames are engine frames: one counter for output and (aligned) input, continuous across
 * stream restarts.
 */
interface AudioEngineApi {
    val sampleRate: Int

    /** Null on success, otherwise the error text. */
    fun start(withInput: Boolean): String?
    fun stop()
    fun restart(): String?
    val isRunning: Boolean
    fun restartNeeded(): Boolean

    /** The audio clock: the engine frame being heard now. */
    fun presentedFrame(): Double
    fun writtenFrames(): Long

    fun metronomeStart(bpm: Double, beatsPerBar: Int, leadMs: Double): Long
    fun metronomeUpdate(bpm: Double, beatsPerBar: Int, leadMs: Double)
    fun metronomeStop()
    fun metronomeGrid(): BeatGridInfo?

    fun scheduleSounds(sound: EngineSound, count: Int, intervalMs: Double, leadMs: Double): LongArray

    fun readInput(dst: FloatArray): InputChunk
    fun discardInput()

    fun diagnostics(): EngineDiagnostics

    fun analyzeChirps(recording: FloatArray, length: Int, startFrame: Long, chirpFrames: LongArray): LatencyMeasurement
    fun analyzeTaps(recording: FloatArray, length: Int, startFrame: Long, clickFrames: LongArray): LatencyMeasurement
}
