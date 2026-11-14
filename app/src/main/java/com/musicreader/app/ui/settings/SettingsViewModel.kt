package com.musicreader.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicreader.app.ui.common.appContainer
import com.musicreader.core.data.SettingsRepository
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.ColorMath
import com.musicreader.core.model.Layer
import com.musicreader.core.model.NoteLetter
import com.musicreader.core.model.SettingsOps
import com.musicreader.core.model.UiLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings,
    /** Pairs of fingers whose colors are nearly the same (lower finger first). */
    val similarFingers: List<Pair<Int, Int>>,
    val visibleLayerCount: Int,
)

/**
 * The settings screen. Every change goes to DataStore at once; every screen that shows colors
 * collects the same settings flow, so it changes everywhere immediately (D-028).
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = repository.settings.map { s ->
        SettingsUiState(
            settings = s,
            similarFingers = ColorMath.similarFingerPairs(s.fingerColors),
            visibleLayerCount = Layer.entries.count { s.viewerLayers.isVisible(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(AppSettings(), emptyList(), 3))

    fun setChordColor(letter: NoteLetter, argb: Long) = update { SettingsOps.setChordColor(it, letter, argb) }
    fun resetChordColor(letter: NoteLetter) = update { SettingsOps.resetChordColor(it, letter) }
    fun resetChordColors() = update(SettingsOps::resetChordColors)
    fun setFingerColor(finger: Int, argb: Long) = update { SettingsOps.setFingerColor(it, finger, argb) }
    fun resetFingerColor(finger: Int) = update { SettingsOps.resetFingerColor(it, finger) }
    fun resetFingerColors() = update(SettingsOps::resetFingerColors)
    fun toggleViewerLayer(layer: Layer) = update { SettingsOps.toggleViewerLayer(it, layer) }
    fun setAccidentals(preference: AccidentalPreference) = update { it.copy(accidentals = preference) }
    fun setLeftHanded(leftHanded: Boolean) = update { it.copy(leftHanded = leftHanded) }

    /** Saves the language; the screen then applies it (the activity is recreated). */
    fun setLanguage(language: UiLanguage) = update { it.copy(uiLanguage = language) }

    private fun update(change: (AppSettings) -> AppSettings) {
        appScope.launch { repository.update(change) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                SettingsViewModel(container.settingsRepository, container.appScope)
            }
        }
    }
}
