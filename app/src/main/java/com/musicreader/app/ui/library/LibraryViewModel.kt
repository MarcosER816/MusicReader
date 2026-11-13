package com.musicreader.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicreader.app.ui.common.appContainer
import com.musicreader.core.data.SongRepository
import com.musicreader.core.data.SongSummary
import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.Language
import com.musicreader.core.model.SongOps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

data class LibraryUiState(
    val loading: Boolean = true,
    /** Songs to show (a song waiting for its delete to be confirmed is hidden). */
    val songs: List<SongSummary> = emptyList(),
)

sealed interface LibraryEvent {
    data class OpenSong(val songId: String, val openSettings: Boolean) : LibraryEvent

    /** Show the "deleted" snackbar with Undo for [song]. */
    data class SongDeleted(val song: SongSummary) : LibraryEvent
}

class LibraryViewModel(
    private val repository: SongRepository,
    private val appScope: CoroutineScope,
    private val ids: IdGenerator = IdGenerator.RANDOM,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val loading = MutableStateFlow(true)
    private val pendingDelete = MutableStateFlow<SongSummary?>(null)

    /** Songs whose file is being deleted; hidden until the repository drops them. */
    private val deleting = MutableStateFlow<Set<String>>(emptySet())
    private val events = Channel<LibraryEvent>(Channel.BUFFERED)

    val eventFlow: Flow<LibraryEvent> = events.receiveAsFlow()

    val state: StateFlow<LibraryUiState> =
        combine(repository.summaries, pendingDelete, deleting, loading) { songs, pending, gone, isLoading ->
            LibraryUiState(
                loading = isLoading,
                songs = songs.filter { it.id != pending?.id && it.id !in gone },
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    init {
        viewModelScope.launch {
            repository.refresh()
            loading.value = false
        }
    }

    /** Creates a song and opens its settings. A blank title is not allowed (the dialog prevents it). */
    fun createSong(title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val language = if (Locale.getDefault().language == "es") Language.ES else Language.EN
            val song = SongOps.newSong(ids.newId(), clean, language, clock())
            repository.save(song)
            events.send(LibraryEvent.OpenSong(song.id, openSettings = true))
        }
    }

    fun openSong(songId: String) {
        viewModelScope.launch { events.send(LibraryEvent.OpenSong(songId, openSettings = false)) }
    }

    fun renameSong(songId: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val song = repository.load(songId) ?: return@launch
            repository.save(SongOps.setTitle(song, clean, clock()))
        }
    }

    /** Saves a copy titled [copyTitle] (the screen passes the localized "Title (copy)"). */
    fun duplicateSong(songId: String, copyTitle: String) {
        viewModelScope.launch {
            val song = repository.load(songId) ?: return@launch
            repository.save(SongOps.duplicateSong(song, ids.newId(), copyTitle, clock()))
        }
    }

    /**
     * Hides the song and asks the screen to show Undo. The file is deleted only by
     * [confirmDelete]; if the app is killed first, the song is kept.
     */
    fun deleteSong(songId: String) {
        val song = state.value.songs.firstOrNull { it.id == songId } ?: return
        pendingDelete.value?.let { confirmDelete(it.id) }
        pendingDelete.value = song
        viewModelScope.launch { events.send(LibraryEvent.SongDeleted(song)) }
    }

    fun undoDelete(songId: String) {
        if (pendingDelete.value?.id == songId) pendingDelete.value = null
    }

    fun confirmDelete(songId: String) {
        if (pendingDelete.value?.id != songId) return
        deleting.value = deleting.value + songId
        pendingDelete.value = null
        appScope.launch {
            repository.delete(songId)
            deleting.value = deleting.value - songId
        }
    }

    override fun onCleared() {
        pendingDelete.value?.let { confirmDelete(it.id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                LibraryViewModel(container.songRepository, container.appScope)
            }
        }
    }
}
