package com.musicreader.audio

/**
 * The C++ engine (audio/src/main/cpp/engine) through JNI. The native library is loaded on the
 * first call, so code (and JVM tests) that never plays audio never loads it.
 */
object NativeAudio : AudioEngineApi {
    private val library by lazy { System.loadLibrary("musicreader_audio") }

    private inline fun <T> native(block: () -> T): T {
        library
        return block()
    }

    override val sampleRate: Int get() = native { nativeSampleRate() }

    override fun start(withInput: Boolean): String? = native { resultText(nativeStart(withInput)) }
    override fun stop() = native { nativeStop() }
    override fun restart(): String? = native { resultText(nativeRestart()) }
    override val isRunning: Boolean get() = native { nativeIsRunning() }
    override fun restartNeeded(): Boolean = native { nativeRestartNeeded() }

    override fun presentedFrame(): Double = native { nativePresentedFrame() }
    override fun writtenFrames(): Long = native { nativeWrittenFrames() }

    override fun metronomeStart(bpm: Double, beatsPerBar: Int, leadMs: Double): Long =
        native { nativeMetronomeStart(bpm, beatsPerBar, leadMs) }

    override fun metronomeUpdate(bpm: Double, beatsPerBar: Int, leadMs: Double) =
        native { nativeMetronomeUpdate(bpm, beatsPerBar, leadMs) }

    override fun metronomeStop() = native { nativeMetronomeStop() }
    override fun metronomeGrid(): BeatGridInfo? = native { BeatGridInfo.fromArray(nativeMetronomeGrid()) }

    override fun scheduleSounds(sound: EngineSound, count: Int, intervalMs: Double, leadMs: Double): LongArray =
        native { nativeScheduleSounds(sound.id, count, intervalMs, leadMs) }

    override fun readInput(dst: FloatArray): InputChunk = native {
        val first = LongArray(1)
        val n = nativeReadInput(dst, first)
        InputChunk(n, first[0])
    }

    override fun discardInput() = native { nativeDiscardInput() }
    override fun diagnostics(): EngineDiagnostics = native { EngineDiagnostics.fromArray(nativeDiagnostics()) }

    override fun analyzeChirps(recording: FloatArray, length: Int, startFrame: Long, chirpFrames: LongArray) =
        native { LatencyMeasurement.fromArray(nativeAnalyzeChirps(recording, length, startFrame, chirpFrames)) }

    override fun analyzeTaps(recording: FloatArray, length: Int, startFrame: Long, clickFrames: LongArray) =
        native { LatencyMeasurement.fromArray(nativeAnalyzeTaps(recording, length, startFrame, clickFrames)) }

    /** 0 is oboe::Result::OK. */
    private fun resultText(code: Int): String? = if (code == 0) null else nativeResultText(code)

    private external fun nativeStart(withInput: Boolean): Int
    private external fun nativeStop()
    private external fun nativeRestart(): Int
    private external fun nativeIsRunning(): Boolean
    private external fun nativeRestartNeeded(): Boolean
    private external fun nativeResultText(code: Int): String
    private external fun nativeSampleRate(): Int
    private external fun nativePresentedFrame(): Double
    private external fun nativeWrittenFrames(): Long
    private external fun nativeMetronomeStart(bpm: Double, beatsPerBar: Int, leadMs: Double): Long
    private external fun nativeMetronomeUpdate(bpm: Double, beatsPerBar: Int, leadMs: Double)
    private external fun nativeMetronomeStop()
    private external fun nativeMetronomeGrid(): DoubleArray?
    private external fun nativeScheduleSounds(sound: Int, count: Int, intervalMs: Double, leadMs: Double): LongArray
    private external fun nativeReadInput(dst: FloatArray, firstFrame: LongArray): Int
    private external fun nativeDiscardInput()
    private external fun nativeDiagnostics(): DoubleArray
    private external fun nativeAnalyzeChirps(recording: FloatArray, length: Int, startFrame: Long, chirpFrames: LongArray): DoubleArray
    private external fun nativeAnalyzeTaps(recording: FloatArray, length: Int, startFrame: Long, clickFrames: LongArray): DoubleArray
}
