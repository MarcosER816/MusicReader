package com.musicreader.app.ui

import androidx.lifecycle.ViewModel
import com.musicreader.app.BuildConfig
import com.musicreader.audio.AudioCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Top-level screens of the app. */
enum class Screen { HOME, ABOUT }

/** Information shown on the About screen. */
data class AboutInfo(
    val versionName: String,
    val versionCode: Int,
    val audioCore: String,
)

/** Whole UI state of the app shell. */
data class AppUiState(
    val screen: Screen = Screen.HOME,
    val about: AboutInfo,
)

class AppViewModel : ViewModel() {
    private val _state = MutableStateFlow(
        AppUiState(
            about = AboutInfo(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                audioCore = AudioCore.oboeVersion(),
            ),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    fun openAbout() = _state.update { it.copy(screen = Screen.ABOUT) }

    fun goBack() = _state.update { it.copy(screen = Screen.HOME) }
}
