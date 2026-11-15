package com.musicreader.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicreader.app.ui.common.appContainer
import com.musicreader.audio.AudioService
import com.musicreader.audio.PitchFrame
import com.musicreader.core.data.SettingsRepository
import com.musicreader.core.dsp.NoiseGate
import com.musicreader.core.dsp.PitchTrace
import com.musicreader.core.dsp.TunerReading
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.PitchMethod
import com.musicreader.core.model.PitchSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TunerUiState(
    val started: Boolean = false,
    val permissionDenied: Boolean = false,
    val settings: PitchSettings = PitchSettings(),
    val accidentals: AccidentalPreference = AccidentalPreference.SHARPS,
    /** Nearest note and cents; null while unvoiced. */
    val reading: TunerReading? = null,
    val hz: Double? = null,
    val confidence: Double = 0.0,
    val levelDb: Double = -120.0,
    /** Frames collected so far while measuring the room noise; null when not measuring. */
    val noiseProgress: Int? = null,
    /** Changes whenever the trace changed, so the trace redraws. */
    val traceVersion: Long = 0,
)

/** Tuner and pitch debug screen (D-030): live pitch, cents, trace, YIN/MPM, room-noise gate. */
class TunerViewModel(
    private val audio: AudioService,
    private val settings: SettingsRepository,
    private val appScope: CoroutineScope,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(TunerUiState())
    val state: StateFlow<TunerUiState> = _state.asStateFlow()

    /** The last [TRACE_SECONDS] of pitch for drawing (read by the screen, written here). */
    val trace = PitchTrace(capacity = (TRACE_SECONDS * 100).toInt() + 20)

    private var collector: Job? = null
    private var noiseLevels: MutableList<Double>? = null

    init {
        viewModelScope.launch {
            settings.settings.collect { s -> _state.update { it.copy(settings = s.pitch, accidentals = s.accidentals) } }
        }
    }

    /**
     * Call when the microphone permission is granted, and again each time the screen is shown
     * (the app going to the background stops the streams).
     */
    fun start() {
        _state.update { it.copy(started = true, permissionDenied = false) }
        if (collector?.isActive != true) collector = viewModelScope.launch { audio.pitch.collect(::onFrame) }
        viewModelScope.launch { audio.startPitch(settings.settings.first().pitch) }
    }

    fun permissionDenied() {
        _state.update { it.copy(permissionDenied = true, started = false) }
    }

    fun setMethod(method: PitchMethod) {
        val next = _state.value.settings.copy(method = method)
        apply(next)
    }

    /** Collects the level of the next 2 s (stay quiet) and sets the gate from it. */
    fun measureNoise() {
        if (noiseLevels != null) return
        noiseLevels = ArrayList(NoiseGate.FRAMES)
        _state.update { it.copy(noiseProgress = 0) }
    }

    private fun apply(next: PitchSettings) {
        _state.update { it.copy(settings = next) }
        audio.updatePitch(next)
        trace.clear()
        appScope.launch { settings.update { it.copy(pitch = next) } }
    }

    private fun onFrame(f: PitchFrame) {
        trace.add(f.seconds, f.midi)
        noiseLevels?.let { levels ->
            levels += f.levelDb
            if (levels.size >= NoiseGate.FRAMES) {
                noiseLevels = null
                val gate = NoiseGate.fromLevels(levels)
                apply(_state.value.settings.copy(noiseGateDb = gate, gateMeasuredAt = clockMillis()))
                _state.update { it.copy(noiseProgress = null) }
            } else {
                _state.update { it.copy(noiseProgress = levels.size) }
            }
        }
        _state.update {
            it.copy(
                reading = f.midi?.let(TunerReading::of),
                hz = f.hz,
                confidence = f.confidence,
                levelDb = f.levelDb,
                traceVersion = trace.version,
            )
        }
    }

    override fun onCleared() {
        if (_state.value.started) audio.stopPitch()
    }

    companion object {
        const val TRACE_SECONDS = 5.0

        val Factory = viewModelFactory {
            initializer {
                val c = appContainer
                TunerViewModel(c.audio, c.settingsRepository, c.appScope)
            }
        }
    }
}
