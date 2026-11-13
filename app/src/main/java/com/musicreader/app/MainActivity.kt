package com.musicreader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.musicreader.app.navigation.MusicReaderNavHost
import com.musicreader.app.ui.MusicReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MusicReaderTheme { MusicReaderNavHost() }
        }
    }
}
