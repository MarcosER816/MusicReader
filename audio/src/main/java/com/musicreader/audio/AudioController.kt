package com.musicreader.audio

import com.musicreader.core.model.AudioRoute
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Tempo and meter the metronome plays. */
data class MetronomeSettings(val bpm: Double, val beatsPerBar: Int)

/** Where the metronome is now, by the audio clock. */
data class MetronomeBeat(
    /** Beat number since start (negative during the short lead-in). */
    val beat: Long,
    /** 0 = beat 1 of the bar (accented). */
    val beatInBar: Int,
    /** 0..1 through the current beat. */
    val phase: Double,
)

/** The metronome as screens see it. */
interface MetronomeControl {
    /** Null while off. */
    val metronome: StateFlow<MetronomeSettings?>
    fun startMetronome(settings: MetronomeSettings)
    fun updateMetronome(settings: MetronomeSettings)
    fun stopMetronome()

    /** The beat being heard now (audio clock); null when off. */
    fun metronomeBeat(): MetronomeBeat?
}

enum class CalibrationMode { AUTOMATIC, MANUAL }

/** Streams and route, for the screens. */
data class AudioState(
    val running: Boolean = false,
    val withInput: Boolean = false,
    val route: AudioRoute = AudioRoute.SPEAKER,
    /** Text of the last stream error, if starting failed. */
    val error: String? = null,
    val restarts: Int = 0,
)

/** Everything the app's screens use from the audio engine ([AudioController]; fakes in tests). */
interface AudioService : MetronomeControl {
    val state: StateFlow<AudioState>

    /** Keeps the streams running while the diagnostics screen is open. */
    fun startDiagnostics()
    fun stopDiagnostics()
    fun diagnostics(): EngineDiagnostics

    /** The audio clock in seconds; 0 while stopped. */
    fun clockSeconds(): Double

    /** The app went to the background: stop everything. */
    fun stopAll()

    /**
     * Plays chirps (automatic) or clicks to play along with (manual), records the microphone
     * on the same frame counter and measures the round trip. Needs the microphone permission.
     */
    suspend fun calibrate(mode: CalibrationMode, onProgress: (CalibrationProgress) -> Unit): CalibrationOutcome
}

data class CalibrationProgress(val mode: CalibrationMode, val played: Int, val total: Int)

sealed interface CalibrationOutcome {
    data class Measured(val measurement: LatencyMeasurement, val route: AudioRoute) : CalibrationOutcome
    /** The sounds were played but not found in the recording. */
    data class NotHeard(val measurement: LatencyMeasurement) : CalibrationOutcome
    /** The streams could not start, or the microphone sent nothing. */
    data class Failed(val reason: String) : CalibrationOutcome
    /** Stopped by the app going to the background, or the route changed in the middle. */
    data object Interrupted : CalibrationOutcome
}

/**
 * Owns the audio engine. Streams run only while something needs them (metronome, calibration
 * or the diagnostics screen) and stop when the app goes to the background. Streams are
 * reopened when a stream is disconnected or an output device is plugged in or out.
 */
class AudioController(
    private val engine: AudioEngineApi,
    private val routes: RouteSource,
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val analysisDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AudioService {

    enum class User { METRONOME, CALIBRATION, DIAGNOSTICS }

    private val mutex = Mutex()
    private val users = mutableSetOf<User>()
    private var watchdog: Job? = null
    @Volatile private var restarts = 0

    private val _state = MutableStateFlow(AudioState())
    override val state: StateFlow<AudioState> = _state.asStateFlow()

    private val _metronome = MutableStateFlow<MetronomeSettings?>(null)
    override val metronome: StateFlow<MetronomeSettings?> = _metronome.asStateFlow()

    init {
        scope.launch { _state.update { it.copy(route = routes.routeOf(0)) } }
        scope.launch {
            routes.changes.collect {
                delay(ROUTE_SETTLE_MS)  // let Android finish switching the route
                // The native library is only touched once audio was started.
                mutex.withLock { if (_state.value.running) restartLocked() else updateRouteLocked() }
            }
        }
    }

    // --- Metronome ---

    override fun startMetronome(settings: MetronomeSettings) {
        scope.launch {
            mutex.withLock {
                if (!acquireLocked(User.METRONOME)) return@withLock
                engine.metronomeStart(settings.bpm, settings.beatsPerBar, LEAD_MS)
                _metronome.value = settings
            }
        }
    }

    override fun updateMetronome(settings: MetronomeSettings) {
        scope.launch {
            mutex.withLock {
                if (_metronome.value == null || _metronome.value == settings) return@withLock
                engine.metronomeUpdate(settings.bpm, settings.beatsPerBar, LEAD_MS)
                _metronome.value = settings
            }
        }
    }

    override fun stopMetronome() {
        scope.launch { mutex.withLock { stopMetronomeLocked() } }
    }

    override fun metronomeBeat(): MetronomeBeat? {
        if (_metronome.value == null) return null
        val grid = engine.metronomeGrid() ?: return null
        val b = grid.beatAt(engine.presentedFrame())
        val whole = Math.floor(b).toLong()
        return MetronomeBeat(whole, grid.beatInBar(whole), b - whole)
    }

    // --- Diagnostics ---

    override fun startDiagnostics() {
        scope.launch { mutex.withLock { acquireLocked(User.DIAGNOSTICS) } }
    }

    override fun stopDiagnostics() {
        scope.launch { mutex.withLock { releaseLocked(User.DIAGNOSTICS) } }
    }

    override fun diagnostics(): EngineDiagnostics = if (_state.value.running) engine.diagnostics() else EngineDiagnostics()

    override fun clockSeconds(): Double = if (_state.value.running) engine.presentedFrame() / engine.sampleRate else 0.0

    // --- App lifecycle ---

    override fun stopAll() {
        scope.launch {
            mutex.withLock {
                users.clear()
                _metronome.value = null
                if (_state.value.running) stopEngineLocked()
            }
        }
    }

    // --- Calibration ---

    override suspend fun calibrate(mode: CalibrationMode, onProgress: (CalibrationProgress) -> Unit): CalibrationOutcome {
        val started = mutex.withLock {
            stopMetronomeLocked()
            acquireLocked(User.CALIBRATION)
        }
        if (!started) return CalibrationOutcome.Failed(_state.value.error ?: "")
        try {
            return record(mode, onProgress)
        } finally {
            // Also when the screen cancels the calibration: the streams must stop.
            withContext(NonCancellable) { mutex.withLock { releaseLocked(User.CALIBRATION) } }
        }
    }

    private suspend fun record(mode: CalibrationMode, onProgress: (CalibrationProgress) -> Unit): CalibrationOutcome {
        val restartsAtStart = restarts
        delay(WARMUP_MS)
        engine.discardInput()
        val overflowsAtStart = engine.diagnostics().inputOverflows
        val plan = when (mode) {
            CalibrationMode.AUTOMATIC -> engine.scheduleSounds(EngineSound.CHIRP, CHIRP_COUNT, CHIRP_INTERVAL_MS, CHIRP_LEAD_MS)
            CalibrationMode.MANUAL -> engine.scheduleSounds(EngineSound.ACCENT_CLICK, CLICK_COUNT, CLICK_INTERVAL_MS, CLICK_LEAD_MS)
        }
        val rate = engine.sampleRate
        val tail = when (mode) {
            CalibrationMode.AUTOMATIC -> CHIRP_TAIL_MS
            CalibrationMode.MANUAL -> CLICK_INTERVAL_MS
        }
        val endFrame = plan.last() + (tail * rate / 1000.0).toLong()
        val recording = Recording((endFrame - engine.writtenFrames() + rate).toInt().coerceAtLeast(rate))
        val chunk = FloatArray(4096)
        val deadline = clockMillis() + ((endFrame - engine.writtenFrames()) * 1000 / rate) + NO_INPUT_TIMEOUT_MS
        while (true) {
            if (!engine.isRunning || restarts != restartsAtStart) return CalibrationOutcome.Interrupted
            while (true) {
                val c = engine.readInput(chunk)
                if (c.count <= 0) break
                if (!recording.append(chunk, c)) return CalibrationOutcome.Failed(GAP)
            }
            val heard = engine.presentedFrame()
            onProgress(CalibrationProgress(mode, plan.count { it <= heard }, plan.size))
            if (recording.endFrame >= endFrame) break
            if (clockMillis() > deadline) return CalibrationOutcome.Failed(NO_INPUT)
            delay(POLL_MS)
        }
        if (engine.diagnostics().inputOverflows != overflowsAtStart) return CalibrationOutcome.Failed(GAP)
        val m = withContext(analysisDispatcher) {
            when (mode) {
                CalibrationMode.AUTOMATIC -> engine.analyzeChirps(recording.samples, recording.size, recording.startFrame, plan)
                CalibrationMode.MANUAL -> engine.analyzeTaps(recording.samples, recording.size, recording.startFrame, plan)
            }
        }
        return if (m.latencyMs == null) CalibrationOutcome.NotHeard(m) else CalibrationOutcome.Measured(m, _state.value.route)
    }

    /** Microphone samples with the engine frame of the first one. */
    private class Recording(capacity: Int) {
        var samples = FloatArray(capacity)
        var size = 0
        var startFrame = -1L
        val endFrame: Long get() = if (startFrame < 0) Long.MIN_VALUE else startFrame + size

        /** False if the chunk does not follow on (samples were lost). */
        fun append(chunk: FloatArray, c: InputChunk): Boolean {
            if (startFrame < 0) startFrame = c.firstFrame
            if (c.firstFrame != startFrame + size) return false
            if (size + c.count > samples.size) samples = samples.copyOf(maxOf(samples.size * 2, size + c.count))
            chunk.copyInto(samples, size, 0, c.count)
            size += c.count
            return true
        }
    }

    // --- Engine ownership (call with the mutex held) ---

    private fun acquireLocked(user: User): Boolean {
        users += user
        val needInput = User.CALIBRATION in users
        if (engine.isRunning && _state.value.withInput == needInput) return true
        val error = engine.start(needInput)
        if (error != null) {
            users -= user
            _state.update { it.copy(error = error, running = engine.isRunning) }
            return false
        }
        if (needInput) _metronome.value = null  // reopening with the microphone resets the metronome
        _state.update { it.copy(running = true, withInput = needInput, error = null) }
        updateRouteLocked()
        startWatchdog()
        return true
    }

    private fun releaseLocked(user: User) {
        users -= user
        if (users.isEmpty() && _state.value.running) stopEngineLocked()
    }

    private fun stopMetronomeLocked() {
        if (_metronome.value != null) engine.metronomeStop()
        _metronome.value = null
        releaseLocked(User.METRONOME)
    }

    private fun stopEngineLocked() {
        watchdog?.cancel()
        watchdog = null
        engine.stop()
        _state.update { it.copy(running = false, withInput = false) }
    }

    private fun restartLocked() {
        if (users.isEmpty()) return
        val error = engine.restart()
        restarts++
        _state.update { it.copy(running = engine.isRunning, error = error, restarts = restarts) }
        updateRouteLocked()
    }

    private fun updateRouteLocked() {
        val id = if (_state.value.running) engine.diagnostics().deviceId else 0
        _state.update { it.copy(route = routes.routeOf(id)) }
    }

    private fun startWatchdog() {
        if (watchdog?.isActive == true) return
        watchdog = scope.launch {
            while (isActive) {
                delay(WATCHDOG_MS)
                mutex.withLock { if (engine.restartNeeded()) restartLocked() }
            }
        }
    }

    companion object {
        /** Metronome start and tempo changes take effect this far ahead, so no beat is late. */
        const val LEAD_MS = 120.0
        const val WATCHDOG_MS = 200L
        const val ROUTE_SETTLE_MS = 300L
        const val WARMUP_MS = 500L
        const val POLL_MS = 20L
        const val NO_INPUT_TIMEOUT_MS = 2000L

        const val CHIRP_COUNT = 5
        const val CHIRP_INTERVAL_MS = 1000.0
        const val CHIRP_LEAD_MS = 300.0
        /** Recording continues this long after the last chirp (longest latency searched + chirp). */
        const val CHIRP_TAIL_MS = 900.0

        const val CLICK_COUNT = 16
        const val CLICK_INTERVAL_MS = 750.0  // 80 BPM
        /** Time to get ready before the first click. */
        const val CLICK_LEAD_MS = 1500.0

        /** Failure reasons (the screen shows its own text for each). */
        const val NO_INPUT = "no_input"
        const val GAP = "gap"
    }
}
