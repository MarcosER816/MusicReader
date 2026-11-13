package com.musicreader.core.data

import com.musicreader.core.model.Song
import kotlinx.coroutines.flow.StateFlow

/** What the song library shows for one song. */
data class SongSummary(
    val id: String,
    val title: String,
    val artist: String?,
    val sectionCount: Int,
    /** Epoch milliseconds. */
    val updatedAt: Long,
)

/** Stores songs. All functions are safe to call from the main thread. */
interface SongRepository {
    /** All songs, most recently changed first. Updated after every save and delete. */
    val summaries: StateFlow<List<SongSummary>>

    /** Reads every song file from storage into [summaries]. */
    suspend fun refresh()

    /** The song with [id], or null if there is none (or its file cannot be read). */
    suspend fun load(id: String): Song?

    /** Creates or replaces the song with `song.id`. */
    suspend fun save(song: Song)

    /** Removes the song and all its files (recordings, takes). Does nothing if it does not exist. */
    suspend fun delete(id: String)
}
