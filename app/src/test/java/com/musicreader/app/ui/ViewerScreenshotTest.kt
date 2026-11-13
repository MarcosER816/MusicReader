package com.musicreader.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.musicreader.app.ui.viewer.ViewerContent
import com.musicreader.app.ui.viewer.ViewerUiState
import com.musicreader.app.ui.viewer.ViewerViewModel
import com.musicreader.core.lyrics.DemoSong
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.LayerVisibility
import com.musicreader.core.model.SheetLayoutBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the viewer with the demo song for three layer combinations. They are compared
 * with the reference images in app/src/test/screenshots on every test run.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ViewerScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun state(layers: LayerVisibility): ViewerUiState {
        var n = 0
        val song = DemoSong.create("demo", IdGenerator { "d${n++}" }, 0)
        val settings = AppSettings(viewerLayers = layers)
        return ViewerUiState(
            title = song.title,
            layout = SheetLayoutBuilder.build(song),
            layers = layers,
            visibleLayerCount = listOf(layers.lyrics, layers.chords, layers.tab).count { it },
            fontScale = 1f,
            speedDpPerSecond = settings.autoscrollSpeed,
            speedSliderPosition = ViewerViewModel.speedToSlider(settings.autoscrollSpeed),
            playing = false,
            settings = settings,
        )
    }

    private fun capture(name: String, layers: LayerVisibility) {
        // A fixed glisten phase in the resting half of the cycle: stable image, true chord colors.
        val phase = mutableFloatStateOf(0.75f)
        rule.setContent {
            MusicReaderTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ViewerContent(
                        state = state(layers),
                        glistenPhase = phase,
                        onToggleLayer = {},
                        onUserTouch = {},
                        onReachedEnd = {},
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)),
        )
    }

    @Test
    fun allLayers() = capture("viewer_all_layers", LayerVisibility(lyrics = true, chords = true, tab = true))

    @Test
    fun lyricsAndChords() = capture("viewer_lyrics_chords", LayerVisibility(lyrics = true, chords = true, tab = false))

    @Test
    fun tabOnly() = capture("viewer_tab_only", LayerVisibility(lyrics = false, chords = false, tab = true))
}
