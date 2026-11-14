package com.musicreader.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.Layer
import com.musicreader.core.model.LayerVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun <T> withRepository(file: File, block: suspend (SettingsRepository) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            return runBlocking { block(DataStoreSettingsRepository(store)) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun defaultsUntilSavedThenKeptAcrossInstances() {
        val file = File(tmp.root, "settings.preferences_pb")
        withRepository(file) { repo ->
            assertEquals(AppSettings(), repo.settings.first())
            repo.update { it.copy(viewerFontScale = 1.5f, autoscrollSpeed = 4.0) }
            repo.update { it.copy(viewerLayers = it.viewerLayers.toggled(Layer.TAB, keepOneVisible = true)) }
        }
        // A new DataStore on the same file (like after a restart) reads the saved settings.
        withRepository(file) { repo ->
            val saved = repo.settings.first()
            assertEquals(1.5f, saved.viewerFontScale)
            assertEquals(4.0, saved.autoscrollSpeed)
            assertEquals(LayerVisibility(lyrics = true, chords = true, tab = false), saved.viewerLayers)
            assertEquals(AppSettings.DEFAULT_CHORD_COLORS, saved.chordColors)
        }
    }
}
