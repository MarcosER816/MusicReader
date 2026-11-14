package com.musicreader.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.Layer
import com.musicreader.core.model.LayerVisibility
import com.musicreader.core.model.NoteLetter
import com.musicreader.core.model.SettingsOps
import com.musicreader.core.model.UiLanguage
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    @Test
    fun colorsLanguageAndResetSurviveRestart() {
        val file = File(tmp.root, "settings.preferences_pb")
        withRepository(file) { repo ->
            repo.update { SettingsOps.setChordColor(it, NoteLetter.C, 0xFF00FF00) }
            repo.update { SettingsOps.setChordColor(it, NoteLetter.G, 0xFF123456) }
            repo.update { SettingsOps.setFingerColor(it, 2, 0xFFABCDEF) }
            repo.update { it.copy(uiLanguage = UiLanguage.ES, leftHanded = true) }
        }
        withRepository(file) { repo ->
            val saved = repo.settings.first()
            assertEquals(0xFF00FF00, saved.chordColors[NoteLetter.C])
            assertEquals(0xFF123456, saved.chordColors[NoteLetter.G])
            assertEquals(AppSettings.DEFAULT_CHORD_COLORS[NoteLetter.D], saved.chordColors[NoteLetter.D])
            assertEquals(0xFFABCDEF, saved.fingerColors[2])
            assertEquals(UiLanguage.ES, saved.uiLanguage)
            assertEquals(true, saved.leftHanded)
            repo.update { SettingsOps.resetChordColor(it, NoteLetter.C) }
            repo.update(SettingsOps::resetFingerColors)
        }
        withRepository(file) { repo ->
            val saved = repo.settings.first()
            assertEquals(AppSettings.DEFAULT_CHORD_COLORS[NoteLetter.C], saved.chordColors[NoteLetter.C])
            assertEquals(0xFF123456, saved.chordColors[NoteLetter.G], "resetting C keeps G")
            assertEquals(AppSettings.DEFAULT_FINGER_COLORS, saved.fingerColors)
            assertEquals(UiLanguage.ES, saved.uiLanguage, "resetting colors keeps the language")
        }
    }

    @Test
    fun missingColorsGetDefaultsAndBrokenJsonGivesDefaults() {
        val file = File(tmp.root, "settings.preferences_pb")
        val key = stringPreferencesKey("app_settings_json")
        // A stored value that lost some colors (e.g. written by an older version).
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            runBlocking {
                store.edit { it[key] = """{"chordColors":{"C":4278255360},"fingerColors":{"1":4278190335,"7":1}}""" }
                val s = DataStoreSettingsRepository(store).settings.first()
                assertEquals(4278255360L, s.chordColors[NoteLetter.C])
                assertEquals(NoteLetter.entries.toSet(), s.chordColors.keys)
                assertEquals(4278190335L, s.fingerColors[1])
                assertEquals(setOf(0, 1, 2, 3, 4), s.fingerColors.keys, "unknown finger 7 dropped, missing ones filled")
                store.edit { it[key] = "not json" }
                assertEquals(AppSettings(), DataStoreSettingsRepository(store).settings.first())
            }
        } finally {
            scope.cancel()
        }
    }
}
