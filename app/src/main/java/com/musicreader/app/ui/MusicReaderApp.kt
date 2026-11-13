package com.musicreader.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MusicReaderApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MusicReaderTheme {
        when (state.screen) {
            Screen.HOME -> HomeScreen(onAbout = viewModel::openAbout)
            Screen.ABOUT -> {
                BackHandler(onBack = viewModel::goBack)
                AboutScreen(info = state.about, onBack = viewModel::goBack)
            }
        }
    }
}
