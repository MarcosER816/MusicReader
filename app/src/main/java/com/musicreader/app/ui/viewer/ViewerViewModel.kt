package com.musicreader.app.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicreader.app.ui.common.appContainer
import com.musicreader.core.data.ChordDictionary
import com.musicreader.core.data.ChordShape
import com.musicreader.core.data.SettingsRepository
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.Layer
import com.musicreader.core.model.LayerVisibility
import com.musicreader.core.model.PlacedChord
import com.musicreader.core.model.SheetLayout
import com.musicreader.core.model.SheetLayoutBuilder
import com.musicreader.core.model.Song
import com.musicreader.core.model.chordColor
import com.musicreader.core.model.fingerColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow

/** Everything the viewer draws. Colors are ARGB. */
data class ViewerUiState(
    val title: String,
    val layout: SheetLayout,
    val layers: LayerVisibility,
    /** Number of visible layers; the last visible one cannot be switched off. */
    val visibleLayerCount: Int,
    val fontScale: Float,
    val speedDpPerSecond: Double,
    /** Speed as a slider position 0..1 (logarithmic). */
    val speedSliderPosition: Float,
    val playing: Boolean,
    val settings: AppSettings,
    /** The chord dictionary for the long-press diagrams; null while it loads. */
    val dictionary: ChordDictionary? = null,
) {
    /** The chosen shape of a chord (or the first shape of its type when the id is unknown). */
    fun shapeOf(chord: PlacedChord): ChordShape? = dictionary?.shapeFor(chord.symbol, chord.voicingId)

    fun chordColor(symbol: ChordSymbol): Long = settings.chordColor(symbol)
    fun fingerColor(finger: Int): Long = settings.fingerColor(finger)
}

/**
 * The song viewer: read-only view of the song being edited, plus viewer settings (layers, text
 * size, autoscroll speed) stored with DataStore. Autoscroll speed is in dp per second; it is a
 * reading aid, not musical timing (D-023).
 */
class ViewerViewModel(
    song: Flow<Song?>,
    dictionary: Flow<ChordDictionary?>,
    private val settingsRepository: SettingsRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val playing = MutableStateFlow(false)

    private val layout: Flow<Pair<String, SheetLayout>?> = song
        .map { s -> s?.let { it.title to SheetLayoutBuilder.build(it) } }
        .distinctUntilChanged()

    val state: StateFlow<ViewerUiState?> =
        combine(layout, settingsRepository.settings, playing, dictionary) { titled, settings, isPlaying, dict ->
            titled?.let { (title, sheet) ->
                ViewerUiState(
                    title = title,
                    layout = sheet,
                    layers = settings.viewerLayers,
                    visibleLayerCount = Layer.entries.count { settings.viewerLayers.isVisible(it) },
                    fontScale = settings.viewerFontScale,
                    speedDpPerSecond = settings.autoscrollSpeed,
                    speedSliderPosition = speedToSlider(settings.autoscrollSpeed),
                    playing = isPlaying,
                    settings = settings,
                    dictionary = dict,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Switches a layer; switching off the last visible layer is refused (at least one stays on). */
    fun toggleLayer(layer: Layer) = updateSettings {
        it.copy(viewerLayers = it.viewerLayers.toggled(layer, keepOneVisible = true))
    }

    fun setFontScale(scale: Float) = updateSettings {
        it.copy(viewerFontScale = scale.coerceIn(AppSettings.MIN_VIEWER_FONT_SCALE, AppSettings.MAX_VIEWER_FONT_SCALE))
    }

    fun setSpeedFromSlider(position: Float) = updateSettings { it.copy(autoscrollSpeed = sliderToSpeed(position)) }

    fun togglePlaying() = playing.update { !it }

    /** Touching the song or reaching its end pauses the autoscroll. */
    fun pause() {
        playing.value = false
    }

    private fun updateSettings(change: (AppSettings) -> AppSettings) {
        appScope.launch { settingsRepository.update(change) }
    }

    private fun MutableStateFlow<Boolean>.update(f: (Boolean) -> Boolean) {
        value = f(value)
    }

    companion object {
        private const val MIN = AppSettings.MIN_AUTOSCROLL_DP_PER_SECOND
        private const val MAX = AppSettings.MAX_AUTOSCROLL_DP_PER_SECOND

        /** Logarithmic slider, so the slow end has as much room as the fast end. */
        fun sliderToSpeed(position: Float): Double = MIN * (MAX / MIN).pow(position.coerceIn(0f, 1f).toDouble())

        fun speedToSlider(speed: Double): Float =
            (ln(speed.coerceIn(MIN, MAX) / MIN) / ln(MAX / MIN)).toFloat()

        fun factory(song: Flow<Song?>, dictionary: Flow<ChordDictionary?>) = viewModelFactory {
            initializer {
                val container = appContainer
                ViewerViewModel(song, dictionary, container.settingsRepository, container.appScope)
            }
        }
    }
}
