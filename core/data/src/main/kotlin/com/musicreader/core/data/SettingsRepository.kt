package com.musicreader.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.musicreader.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

/** User settings (docs/DATA_MODEL.md "Settings"). */
interface SettingsRepository {
    /** The current settings; defaults until something was saved. */
    val settings: Flow<AppSettings>

    /** Changes the settings atomically. */
    suspend fun update(change: (AppSettings) -> AppSettings)
}

/**
 * Stores [AppSettings] in a Preferences DataStore as one JSON value, so new fields get their
 * defaults automatically and unknown fields from newer versions are ignored.
 */
class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[KEY]) }
        .distinctUntilChanged()

    override suspend fun update(change: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val next = change(decode(prefs[KEY]))
            prefs[KEY] = json.encodeToString(AppSettings.serializer(), next)
        }
    }

    private fun decode(text: String?): AppSettings =
        text?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()

    companion object {
        private val KEY = stringPreferencesKey("app_settings_json")
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
