package com.musicreader.app

import android.app.Application
import android.util.Log
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.musicreader.audio.AndroidRouteSource
import com.musicreader.audio.AudioController
import com.musicreader.audio.AudioService
import com.musicreader.audio.NativeAudio
import com.musicreader.core.data.ChordDictionary
import com.musicreader.core.data.ChordDictionaryRepository
import com.musicreader.core.data.DataStoreSettingsRepository
import com.musicreader.core.data.FileSongRepository
import com.musicreader.core.data.SettingsRepository
import com.musicreader.core.data.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.io.File

/** Objects shared by the whole app (manual dependency injection). */
class AppContainer(application: Application) {
    /** Outlives screens, so pending saves finish after a screen is closed. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val songRepository: SongRepository = FileSongRepository(
        songsDir = File(application.filesDir, "songs"),
        onUnreadableFile = { file, e -> Log.w("MusicReader", "Cannot read song file $file", e) },
    )

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(application.settingsDataStore)

    private val chordDictionaryRepository = ChordDictionaryRepository({ application.assets.open(ChordDictionaryRepository.ASSET_PATH) })

    /** The audio engine (D-029); the native library loads the first time audio starts. */
    val audio: AudioService = AudioController(NativeAudio, AndroidRouteSource(application), appScope)

    /** The chord dictionary, loaded once in the background at start; null until it is ready. */
    val chordDictionary: StateFlow<ChordDictionary?> = flow { emit(chordDictionaryRepository.dictionary()) }
        .stateIn(appScope, SharingStarted.Eagerly, null)
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MusicReaderApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
