package com.musicreader.app

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.musicreader.app.navigation.MusicReaderNavHost
import com.musicreader.app.ui.MusicReaderTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MusicReaderTheme { MusicReaderNavHost() }
        }
        // Keep the screen on while audio runs (metronome, calibration).
        val audio = (application as MusicReaderApplication).container.audio
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                audio.state.map { it.running }.distinctUntilChanged().collect { running ->
                    if (running) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // The app went to the background: stop the streams (not when only rotating).
        if (!isChangingConfigurations) (application as MusicReaderApplication).container.audio.stopAll()
    }
}
