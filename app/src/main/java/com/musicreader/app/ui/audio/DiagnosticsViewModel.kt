package com.musicreader.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicreader.app.ui.common.appContainer
import com.musicreader.audio.AudioService
import com.musicreader.audio.EngineDiagnostics
import com.musicreader.core.data.SettingsRepository
import com.musicreader.core.model.AudioRoute
import com.musicreader.core.model.LatencyProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val running: Boolean = false,
    val engine: EngineDiagnostics = EngineDiagnostics(),
    val route: AudioRoute = AudioRoute.SPEAKER,
    val savedLatency: LatencyProfile? = null,
    /** The audio clock, seconds since the engine first started. */
    val clockSeconds: Double = 0.0,
    val error: String? = null,
) {
    val bluetooth: Boolean get() = route == AudioRoute.BLUETOOTH
}

/** Audio diagnostics: keeps the streams running while open and refreshes twice a second. */
class DiagnosticsViewModel(
    private val audio: AudioService,
    settings: SettingsRepository,
) : ViewModel() {

    private val sample = MutableStateFlow(EngineDiagnostics() to 0.0)

    val state: StateFlow<DiagnosticsUiState> = combine(sample, audio.state, settings.settings) { (d, clock), a, s ->
        DiagnosticsUiState(
            running = a.running,
            engine = d,
            route = a.route,
            savedLatency = s.latencyProfiles[a.route],
            clockSeconds = clock,
            error = a.error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DiagnosticsUiState())

    init {
        audio.startDiagnostics()
        viewModelScope.launch {
            while (isActive) {
                sample.value = audio.diagnostics() to audio.clockSeconds()
                delay(REFRESH_MS)
            }
        }
    }

    /** After the app was in the background (which stops audio), start it again. */
    fun startAgain() = audio.startDiagnostics()

    override fun onCleared() = audio.stopDiagnostics()

    companion object {
        const val REFRESH_MS = 500L

        val Factory = viewModelFactory {
            initializer {
                val c = appContainer
                DiagnosticsViewModel(c.audio, c.settingsRepository)
            }
        }
    }
}
