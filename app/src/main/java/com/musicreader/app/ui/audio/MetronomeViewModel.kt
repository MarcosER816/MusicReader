package com.musicreader.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicreader.app.ui.common.appContainer
import com.musicreader.audio.MetronomeBeat
import com.musicreader.audio.MetronomeControl
import com.musicreader.audio.MetronomeSettings
import com.musicreader.core.model.Song
import com.musicreader.core.model.TimeSignature
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MetronomeUiState(
    val running: Boolean = false,
    val bpm: Double = Song.DEFAULT_TEMPO_BPM,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
)

/**
 * The song screen's metronome: plays the song's tempo and time signature (a beat is one
 * denominator note, D-018), follows edits while running, and stops when the song is closed.
 */
class MetronomeViewModel(
    private val song: StateFlow<Song?>,
    private val control: MetronomeControl,
) : ViewModel() {

    val state: StateFlow<MetronomeUiState> = combine(song, control.metronome) { s, m ->
        MetronomeUiState(
            running = m != null,
            bpm = s?.tempoBpm ?: Song.DEFAULT_TEMPO_BPM,
            timeSignature = s?.timeSignature ?: TimeSignature.FOUR_FOUR,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MetronomeUiState())

    init {
        viewModelScope.launch {
            song.filterNotNull().map(::settingsOf).distinctUntilChanged().collect { settings ->
                if (control.metronome.value != null) control.updateMetronome(settings)
            }
        }
    }

    fun toggle() {
        val s = song.value ?: return
        if (control.metronome.value != null) control.stopMetronome() else control.startMetronome(settingsOf(s))
    }

    /** The beat being heard now, from the audio clock; the screen polls it once per frame. */
    fun beatNow(): MetronomeBeat? = control.metronomeBeat()

    override fun onCleared() {
        if (control.metronome.value != null) control.stopMetronome()
    }

    companion object {
        fun settingsOf(song: Song) = MetronomeSettings(song.tempoBpm, song.timeSignature.num)

        fun factory(song: StateFlow<Song?>) = viewModelFactory {
            initializer { MetronomeViewModel(song, appContainer.audio) }
        }
    }
}
