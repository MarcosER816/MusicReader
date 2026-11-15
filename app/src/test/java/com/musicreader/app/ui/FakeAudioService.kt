package com.musicreader.app.ui

import com.musicreader.audio.AudioService
import com.musicreader.audio.AudioState
import com.musicreader.audio.CalibrationMode
import com.musicreader.audio.CalibrationOutcome
import com.musicreader.audio.CalibrationProgress
import com.musicreader.audio.EngineDiagnostics
import com.musicreader.audio.MetronomeBeat
import com.musicreader.audio.MetronomeSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** Records what the screens ask of the audio engine. */
class FakeAudioService : AudioService {
    override val state = MutableStateFlow(AudioState())
    override val metronome = MutableStateFlow<MetronomeSettings?>(null)
    val calls = mutableListOf<String>()
    var beat: MetronomeBeat? = null
    var diagnostics = EngineDiagnostics(running = true, sampleRate = 48000, framesPerBurst = 96, bufferSize = 192)
    var clock = 12.5

    /** The next calibration's result; the calibration waits until it is completed. */
    var outcome = CompletableDeferred<CalibrationOutcome>()
    var progress: ((CalibrationProgress) -> Unit)? = null

    override fun startMetronome(settings: MetronomeSettings) {
        calls += "start ${settings.bpm} ${settings.beatsPerBar}"
        metronome.value = settings
    }

    override fun updateMetronome(settings: MetronomeSettings) {
        calls += "update ${settings.bpm} ${settings.beatsPerBar}"
        metronome.value = settings
    }

    override fun stopMetronome() {
        calls += "stop"
        metronome.value = null
    }

    override fun metronomeBeat() = beat
    override fun startDiagnostics() {
        calls += "diagnostics on"
        state.value = state.value.copy(running = true)
    }

    override fun stopDiagnostics() {
        calls += "diagnostics off"
        state.value = state.value.copy(running = false)
    }

    override fun diagnostics() = diagnostics
    override fun clockSeconds() = clock
    override fun stopAll() {
        calls += "stop all"
    }

    override suspend fun calibrate(mode: CalibrationMode, onProgress: (CalibrationProgress) -> Unit): CalibrationOutcome {
        calls += "calibrate $mode"
        progress = onProgress
        return outcome.await()
    }
}
