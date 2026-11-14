package com.musicreader.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicreader.app.ui.common.appContainer
import com.musicreader.audio.AudioController
import com.musicreader.audio.AudioService
import com.musicreader.audio.CalibrationMode
import com.musicreader.audio.CalibrationOutcome
import com.musicreader.audio.LatencyMeasurement
import com.musicreader.core.data.SettingsRepository
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.AudioRoute
import com.musicreader.core.model.LatencyMethod
import com.musicreader.core.model.LatencyOps
import com.musicreader.core.model.LatencyProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the calibration screen is. */
sealed interface CalibrationStep {
    data object Ready : CalibrationStep
    data class Running(val mode: CalibrationMode, val played: Int, val total: Int) : CalibrationStep
    data class Done(
        val mode: CalibrationMode,
        val measurement: LatencyMeasurement,
        val route: AudioRoute,
        val saved: Boolean,
    ) : CalibrationStep {
        val lowConfidence: Boolean get() = measurement.confidence < LatencyOps.LOW_CONFIDENCE
    }
    data class NotHeard(val mode: CalibrationMode) : CalibrationStep
    data class Failed(val reason: FailReason, val detail: String) : CalibrationStep
    data object Interrupted : CalibrationStep
    /** The microphone permission was refused. */
    data object PermissionDenied : CalibrationStep
}

enum class FailReason { START, NO_INPUT, GAP }

data class CalibrationUiState(
    val step: CalibrationStep = CalibrationStep.Ready,
    val route: AudioRoute = AudioRoute.SPEAKER,
    /** The saved profile for every route. */
    val profiles: Map<AudioRoute, LatencyProfile> = emptyMap(),
) {
    val bluetooth: Boolean get() = route == AudioRoute.BLUETOOTH
    val currentProfile: LatencyProfile? get() = profiles[route]
}

/** Latency calibration (D-029): measure the round trip of the current route and save it. */
class CalibrationViewModel(
    private val audio: AudioService,
    private val settings: SettingsRepository,
    private val appScope: CoroutineScope,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val step = MutableStateFlow<CalibrationStep>(CalibrationStep.Ready)
    private var job: Job? = null

    val state: StateFlow<CalibrationUiState> = combine(step, audio.state, settings.settings) { st, a, s ->
        CalibrationUiState(st, a.route, s.latencyProfiles)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CalibrationUiState())

    /** Call once the microphone permission is granted. */
    fun start(mode: CalibrationMode) {
        if (job?.isActive == true) return
        val total = if (mode == CalibrationMode.AUTOMATIC) AudioController.CHIRP_COUNT else AudioController.CLICK_COUNT
        step.value = CalibrationStep.Running(mode, 0, total)
        job = viewModelScope.launch {
            val outcome = audio.calibrate(mode) { p -> step.value = CalibrationStep.Running(mode, p.played, p.total) }
            step.value = when (outcome) {
                is CalibrationOutcome.Measured -> CalibrationStep.Done(mode, outcome.measurement, outcome.route, saved = false)
                is CalibrationOutcome.NotHeard -> CalibrationStep.NotHeard(mode)
                is CalibrationOutcome.Failed -> CalibrationStep.Failed(
                    when (outcome.reason) {
                        AudioController.NO_INPUT -> FailReason.NO_INPUT
                        AudioController.GAP -> FailReason.GAP
                        else -> FailReason.START
                    },
                    outcome.reason,
                )
                CalibrationOutcome.Interrupted -> CalibrationStep.Interrupted
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        step.value = CalibrationStep.Ready
    }

    fun permissionDenied() {
        step.value = CalibrationStep.PermissionDenied
    }

    fun back() {
        if (job?.isActive != true) step.value = CalibrationStep.Ready
    }

    /** Saves the measurement as the profile of the route it was measured on. */
    fun save() {
        val done = step.value as? CalibrationStep.Done ?: return
        val ms = done.measurement.latencyMs ?: return
        val method = if (done.mode == CalibrationMode.AUTOMATIC) LatencyMethod.AUTOMATIC else LatencyMethod.MANUAL
        val profile = LatencyProfile(ms, method, done.measurement.confidence, clockMillis())
        appScope.launch { settings.update { LatencyOps.save(it, done.route, profile) } }
        step.value = done.copy(saved = true)
    }

    fun clear(route: AudioRoute) {
        appScope.launch { settings.update { s: AppSettings -> LatencyOps.clear(s, route) } }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = appContainer
                CalibrationViewModel(c.audio, c.settingsRepository, c.appScope)
            }
        }
    }
}
