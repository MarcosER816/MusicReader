package com.musicreader.app.ui

import com.musicreader.core.data.SongRepository
import com.musicreader.core.data.SongSummary
import com.musicreader.core.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory repository that records every save and delete. */
class FakeSongRepository(initial: List<Song> = emptyList()) : SongRepository {
    val songs = initial.associateBy { it.id }.toMutableMap()
    val saved = mutableListOf<Song>()
    val deleted = mutableListOf<String>()
    private val _summaries = MutableStateFlow<List<SongSummary>>(emptyList())
    override val summaries: StateFlow<List<SongSummary>> = _summaries

    init {
        publish()
    }

    override suspend fun refresh() = publish()

    override suspend fun load(id: String): Song? = songs[id]

    override suspend fun save(song: Song) {
        songs[song.id] = song
        saved += song
        publish()
    }

    override suspend fun delete(id: String) {
        songs.remove(id)
        deleted += id
        publish()
    }

    private fun publish() {
        _summaries.value = songs.values
            .map { SongSummary(it.id, it.title, it.artist, it.sections.size, it.updatedAt) }
            .sortedByDescending { it.updatedAt }
    }
}
