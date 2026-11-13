package com.musicreader.app

import android.app.Application
import android.util.Log
import com.musicreader.core.data.FileSongRepository
import com.musicreader.core.data.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Objects shared by the whole app (manual dependency injection). */
class AppContainer(application: Application) {
    /** Outlives screens, so pending saves finish after a screen is closed. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val songRepository: SongRepository = FileSongRepository(
        songsDir = File(application.filesDir, "songs"),
        onUnreadableFile = { file, e -> Log.w("MusicReader", "Cannot read song file $file", e) },
    )
}

class MusicReaderApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
