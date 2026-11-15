package com.musicreader.audio

import com.musicreader.core.model.AudioRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A fake engine whose frames advance with a test clock. The microphone "hears" every scheduled
 * sound [latencyFrames] later, as a 1.0 sample (enough for the fake analysis below).
 */
class FakeAudioEngine(private val nowMillis: () -> Long, var latencyFrames: Long = 1200) : AudioEngineApi {
    override val sampleRate = 48000
    var startError: String? = null
    var started = 0
    var stopped = 0
    var restarted = 0
    var withInput = false
    var disconnected = false
    var deviceId = 3
    var micWorks = true
    private var running = false
    private var startMillis = 0L
    private var baseFrame = 0L
    private var inputRead = 0L
    private var inputStart = -1L
    val scheduled = mutableListOf<Long>()
    var grid: BeatGridInfo? = null
    val metronomeCalls = mutableListOf<String>()

    private fun frameNow(): Long = if (running) baseFrame + (nowMillis() - startMillis) * sampleRate / 1000 else baseFrame

    override fun start(withInput: Boolean): String? {
        startError?.let { return it }
        if (running) stop()
        started++
        this.withInput = withInput
        running = true
        startMillis = nowMillis()
        inputStart = if (withInput) baseFrame else -1
        inputRead = 0
        grid = null
        return null
    }

    override fun stop() {
        if (running) baseFrame = frameNow()
        running = false
        stopped++
        grid = null
    }

    override fun restart(): String? {
        restarted++
        disconnected = false
        return null
    }

    override val isRunning get() = running
    override fun restartNeeded() = disconnected
    override fun presentedFrame() = (frameNow() - 960).toDouble()
    override fun writtenFrames() = frameNow()

    override fun metronomeStart(bpm: Double, beatsPerBar: Int, leadMs: Double): Long {
        val anchor = frameNow() + (leadMs * sampleRate / 1000).toLong()
        grid = BeatGridInfo(anchor, 0, sampleRate * 60.0 / bpm, beatsPerBar, 0)
        metronomeCalls += "start $bpm $beatsPerBar"
        return anchor
    }

    override fun metronomeUpdate(bpm: Double, beatsPerBar: Int, leadMs: Double) {
        metronomeCalls += "update $bpm $beatsPerBar"
        grid = grid?.copy(framesPerBeat = sampleRate * 60.0 / bpm, beatsPerBar = beatsPerBar)
    }

    override fun metronomeStop() {
        metronomeCalls += "stop"
        grid = null
    }

    override fun metronomeGrid() = grid

    override fun scheduleSounds(sound: EngineSound, count: Int, intervalMs: Double, leadMs: Double): LongArray {
        val first = frameNow() + (leadMs * sampleRate / 1000).toLong()
        val frames = LongArray(count) { first + (it * intervalMs * sampleRate / 1000).toLong() }
        scheduled += frames.toList()
        return frames
    }

    override fun readInput(dst: FloatArray): InputChunk {
        if (!running || inputStart < 0 || !micWorks) return InputChunk(0, -1)
        val available = frameNow() - (inputStart + inputRead)
        val n = minOf(available, dst.size.toLong()).toInt()
        val first = inputStart + inputRead
        for (i in 0 until n) dst[i] = if (scheduled.any { it + latencyFrames == first + i }) 1f else 0f
        inputRead += n
        return InputChunk(n, first)
    }

    override fun discardInput() {
        if (inputStart >= 0) inputRead = frameNow() - inputStart
    }

    override fun diagnostics() = EngineDiagnostics(running = running, sampleRate = sampleRate, deviceId = deviceId)

    /** Finds the 1.0 samples and returns their median distance to the planned frames. */
    private fun analyze(recording: FloatArray, length: Int, startFrame: Long, frames: LongArray): LatencyMeasurement {
        val hits = (0 until length).filter { recording[it] == 1f }.map { startFrame + it }
        val lags = frames.toList().mapNotNull { f -> hits.firstOrNull { it >= f }?.let { it - f } }
        if (lags.isEmpty()) return LatencyMeasurement(null, 0.0, 0, frames.size, 0.0)
        val ms = lags.sorted()[lags.size / 2] * 1000.0 / sampleRate
        return LatencyMeasurement(ms, lags.size.toDouble() / frames.size, lags.size, frames.size, 0.0)
    }

    override fun analyzeChirps(recording: FloatArray, length: Int, startFrame: Long, chirpFrames: LongArray) =
        analyze(recording, length, startFrame, chirpFrames)

    override fun analyzeTaps(recording: FloatArray, length: Int, startFrame: Long, clickFrames: LongArray) =
        analyze(recording, length, startFrame, clickFrames)
}

class FakeRouteSource(var route: AudioRoute = AudioRoute.SPEAKER) : RouteSource {
    val changeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override fun routeOf(deviceId: Int) = route
    override val changes: Flow<Unit> = changeEvents
}
