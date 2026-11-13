package com.musicreader.app.ui.about

import androidx.lifecycle.ViewModel
import com.musicreader.app.BuildConfig
import com.musicreader.audio.AudioCore

/** Information shown on the About screen. */
data class AboutInfo(
    val versionName: String,
    val versionCode: Int,
    val audioCore: String,
)

class AboutViewModel : ViewModel() {
    val info: AboutInfo = AboutInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        audioCore = AudioCore.oboeVersion(),
    )
}
