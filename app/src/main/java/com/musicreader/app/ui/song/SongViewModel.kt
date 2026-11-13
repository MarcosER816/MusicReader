package com.musicreader.app.ui.song

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.musicreader.app.navigation.SongRoute
import com.musicreader.app.ui.common.appContainer
import com.musicreader.core.data.SongRepository
import com.musicreader.core.model.BeatTime
import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.Language
import com.musicreader.core.model.SectionType
import com.musicreader.core.model.Song
import com.musicreader.core.model.SongOps
import com.musicreader.core.model.TapTempo
import com.musicreader.core.model.TimeSignature
import com.musicreader.core.model.contentOf
import com.musicreader.core.model.durationSeconds
import com.musicreader.core.model.linkedCount
import com.musicreader.core.model.occurrenceNumbers
import com.musicreader.core.model.sectionStartBeats
import com.musicreader.core.model.totalBars
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One row of the sections list. */
data class SectionRow(
    val id: String,
    val type: SectionType,
    val label: String?,
    /** "2" in "Verse 2"; null when the type appears only once. */
    val number: Int?,
    val bars: Int,
    /** 1-based bar where the section starts. */
    val startBar: Int,
    /** Length as m:ss. */
    val duration: String,
    /** How many sections share this content; 1 = not linked. */
    val linkedCount: Int,
)

data class SongEditorState(
    val song: Song,
    /** Tempo for display: "120" or "96.5". */
    val tempoText: String,
    val sections: List<SectionRow>,
    val totalBars: Int,
    val duration: String,
    /** True for 2/4, 3/4, 4/4, 6/8 and 12/8. */
    val isPresetTimeSignature: Boolean,
    val tapCount: Int,
)

sealed interface SongUiState {
    data object Loading : SongUiState
    data object NotFound : SongUiState
    data class Ready(val editor: SongEditorState) : SongUiState
}

sealed interface SongEvent {
    /** Show the "deleted" snackbar with Undo. */
    data class SectionDeleted(val type: SectionType, val label: String?, val number: Int?) : SongEvent
}

/**
 * Edits one song. Shared by the Sections and Song settings screens. Every change updates the
 * state at once and is saved [SAVE_DELAY_MS] after the last change; pending changes are saved
 * when the screens are closed.
 */
@OptIn(FlowPreview::class)
class SongViewModel(
    private val songId: String,
    private val savedState: SavedStateHandle,
    private val openSettingsRequested: Boolean,
    private val repository: SongRepository,
    private val appScope: CoroutineScope,
    private val ids: IdGenerator = IdGenerator.RANDOM,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private enum class Load { LOADING, NOT_FOUND, READY }

    private val load = MutableStateFlow(Load.LOADING)
    private val song = MutableStateFlow<Song?>(null)
    private val tapTempo = MutableStateFlow(TapTempo())
    private val events = Channel<SongEvent>(Channel.BUFFERED)
    private var lastSaved: Song? = null
    private var undoSnapshot: Song? = null

    val eventFlow: Flow<SongEvent> = events.receiveAsFlow()

    val state: StateFlow<SongUiState> = combine(load, song, tapTempo) { loadState, current, tap ->
        when (loadState) {
            Load.LOADING -> SongUiState.Loading
            Load.NOT_FOUND -> SongUiState.NotFound
            Load.READY -> current?.let { SongUiState.Ready(editorState(it, tap)) } ?: SongUiState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SongUiState.Loading)

    init {
        viewModelScope.launch {
            val loaded = repository.load(songId)
            lastSaved = loaded
            song.value = loaded
            load.value = if (loaded == null) Load.NOT_FOUND else Load.READY
        }
        viewModelScope.launch {
            song.debounce(SAVE_DELAY_MS).filterNotNull().collect { persist(it) }
        }
    }

    /** True once, right after a new song is created, so the screen opens its settings. */
    fun consumeOpenSettingsRequest(): Boolean {
        if (!openSettingsRequested || savedState.get<Boolean>(KEY_SETTINGS_OPENED) == true) return false
        savedState[KEY_SETTINGS_OPENED] = true
        return true
    }

    // --- Song settings -------------------------------------------------------------------------

    fun setTitle(title: String) = edit { s, now -> SongOps.setTitle(s, title, now) }

    fun setArtist(artist: String) = edit { s, now -> SongOps.setArtist(s, artist, now) }

    fun setLanguage(language: Language) = edit { s, now -> SongOps.setLanguage(s, language, now) }

    fun setTempo(bpm: Double) = edit { s, now -> SongOps.setTempo(s, bpm, now) }

    /** From the slider: whole BPM only. */
    fun setTempoFromSlider(value: Float) = setTempo(value.roundToInt().toDouble())

    /** Changes the tempo by [delta] BPM and lands on a whole number (96.5 + 1 → 97). */
    fun nudgeTempo(delta: Int) {
        val current = song.value?.tempoBpm ?: return
        val next = if (delta > 0) kotlin.math.floor(current) + delta else kotlin.math.ceil(current) + delta
        setTempo(next)
    }

    /** One tap of the tap-tempo button at [uptimeMs]; from the second tap the song tempo follows. */
    fun tap(uptimeMs: Long) {
        val bpm = tapTempo.updateAndGet { it.tap(uptimeMs) }.bpm ?: return
        setTempo(bpm)
    }

    fun setTimeSignature(num: Int, den: Int) {
        if (!TimeSignature.isValid(num, den)) return
        edit { s, now -> SongOps.setTimeSignature(s, TimeSignature(num, den), now) }
    }

    // --- Sections -----------------------------------------------------------------------------

    fun addSection(type: SectionType, bars: Int, label: String?) =
        edit { s, now -> SongOps.addSection(s, type, bars, label, ids, now) }

    /** Saves the section editor: type, length and label in one change. */
    fun updateSection(sectionId: String, type: SectionType, bars: Int, label: String?) = edit { s, now ->
        val typed = SongOps.setSectionType(s, sectionId, type, now)
        val sized = SongOps.setSectionBars(typed, sectionId, bars, now)
        SongOps.setSectionLabel(sized, sectionId, label, now)
    }

    fun duplicateSection(sectionId: String, linked: Boolean) =
        edit { s, now -> SongOps.duplicateSection(s, sectionId, linked, ids, now) }

    fun unlinkSection(sectionId: String) = edit { s, now -> SongOps.unlinkSection(s, sectionId, ids, now) }

    fun deleteSection(sectionId: String) {
        val current = song.value ?: return
        val section = current.sections.firstOrNull { it.id == sectionId } ?: return
        val number = current.occurrenceNumbers()[sectionId]
        undoSnapshot = current
        edit { s, now -> SongOps.deleteSection(s, sectionId, now) }
        viewModelScope.launch { events.send(SongEvent.SectionDeleted(section.type, section.label, number)) }
    }

    /** Restores the song as it was before the last section delete. */
    fun undoDeleteSection() {
        val snapshot = undoSnapshot ?: return
        undoSnapshot = null
        song.value = snapshot.copy(updatedAt = clock())
    }

    /** Moves a section while it is dragged over another one. */
    fun moveSection(fromId: String, toId: String) = edit { s, now ->
        val from = s.sections.indexOfFirst { it.id == fromId }
        val to = s.sections.indexOfFirst { it.id == toId }
        SongOps.moveSection(s, from, to, now)
    }

    // --- Internals ------------------------------------------------------------------------------

    private fun edit(change: (Song, Long) -> Song) {
        song.update { current -> current?.let { change(it, clock()) } }
    }

    /**
     * Writes [current] unless it was already written. The write runs in the app scope, so closing
     * the screen never cancels it, and starts undispatched, so writes reach the repository's
     * (first-in, first-out) lock in the order they were made.
     */
    private fun persist(current: Song) {
        if (current == lastSaved) return
        lastSaved = current
        appScope.launch(start = CoroutineStart.UNDISPATCHED) { repository.save(current) }
    }

    override fun onCleared() {
        song.value?.let { persist(it) }
    }

    private fun editorState(s: Song, tap: TapTempo): SongEditorState {
        val starts = s.sectionStartBeats()
        val numbers = s.occurrenceNumbers()
        val beatsPerBar = s.timeSignature.beatsPerBar
        val rows = s.sections.mapIndexed { i, section ->
            val content = s.contentOf(section)
            SectionRow(
                id = section.id,
                type = section.type,
                label = section.label,
                number = numbers[section.id],
                bars = content.bars(s.timeSignature),
                startBar = starts[i] / beatsPerBar + 1,
                duration = BeatTime.formatMinutesSeconds(BeatTime.beatsToSeconds(content.lengthBeats.toDouble(), s.tempoBpm)),
                linkedCount = s.linkedCount(section),
            )
        }
        return SongEditorState(
            song = s,
            tempoText = formatTempo(s.tempoBpm),
            sections = rows,
            totalBars = s.totalBars,
            duration = BeatTime.formatMinutesSeconds(s.durationSeconds),
            isPresetTimeSignature = s.timeSignature in TimeSignature.PRESETS,
            tapCount = tap.tapCount,
        )
    }

    companion object {
        /** "120" for whole numbers, otherwise one decimal ("96.5"). */
        fun formatTempo(bpm: Double): String {
            val tenths = (bpm * 10).roundToInt()
            return if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
        }

        const val SAVE_DELAY_MS: Long = 300
        private const val KEY_SETTINGS_OPENED = "settingsOpened"

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                val handle = createSavedStateHandle()
                val route = handle.toRoute<SongRoute>()
                SongViewModel(
                    songId = route.songId,
                    savedState = handle,
                    openSettingsRequested = route.openSettings,
                    repository = container.songRepository,
                    appScope = container.appScope,
                )
            }
        }
    }
}
