package com.musicreader.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.musicreader.app.ui.common.SheetRowLayout
import com.musicreader.app.ui.tab.TabStaff
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.ChordEvent
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.NoteLetter
import com.musicreader.core.model.SectionContent
import com.musicreader.core.model.SheetLayoutBuilder
import com.musicreader.core.model.TabEvent
import com.musicreader.core.model.Technique
import com.musicreader.core.model.TimeSignature
import com.musicreader.core.model.chordColor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A two-bar riff using every technique, drawn by the tab staff, compared with a reference image. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class TabScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun n(beat: Double, string: Int, fret: Int, finger: Int, technique: Technique = Technique.NONE, duration: Double = 0.5, bend: Int? = null) =
        TabEvent("$beat-$string", beat, duration, string, fret, finger, technique, bendQuarterTones = bend, harmonicFret = if (technique == Technique.NATURAL_HARMONIC || technique == Technique.ARTIFICIAL_HARMONIC) fret else null)

    private val riff = SectionContent(
        id = "riff",
        lengthBeats = 8,
        chords = listOf(
            ChordEvent("c1", 0.0, ChordSymbol(NoteLetter.A, suffix = "m"), "A|minor|0"),
            ChordEvent("c2", 4.0, ChordSymbol(NoteLetter.G), "G|major|0"),
        ),
        tab = listOf(
            n(0.0, 3, 5, 1, Technique.HAMMER_ON),
            n(0.5, 3, 7, 3, Technique.PULL_OFF),
            n(1.0, 3, 5, 1),
            n(1.5, 2, 5, 1, Technique.SLIDE_UP),
            n(2.0, 2, 8, 4, Technique.SLIDE_DOWN),
            n(2.5, 2, 6, 2),
            n(3.0, 1, 8, 3, Technique.BEND, bend = 4),
            n(4.0, 2, 8, 3, Technique.BEND_RELEASE, duration = 1.0, bend = 2),
            n(5.0, 1, 5, 1, Technique.VIBRATO, duration = 1.0),
            n(6.0, 1, 12, 0, Technique.NATURAL_HARMONIC, duration = 1.0),
            n(6.5, 3, 7, 1, Technique.ARTIFICIAL_HARMONIC),
            n(7.0, 6, 0, 0, Technique.MUTE),
            n(7.5, 5, 0, 0),
        ),
    )

    @Test
    fun everyTechnique() {
        val row = SheetLayoutBuilder.rows(riff, TimeSignature.FOUR_FOUR).single()
        val settings = AppSettings()
        rule.setContent {
            MusicReaderTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        Text("h  p  /  \\  bend  bend+release  ~  <12>  [7]  x  open", style = MaterialTheme.typography.labelMedium)
                        SheetRowLayout(
                            row = row,
                            showChords = true,
                            showTab = true,
                            showLyrics = true,
                            syllableContent = { Text(it.text) },
                            chordContent = { Text(it.symbol.toString(), color = Color(settings.chordColor(it.symbol).toInt()), style = MaterialTheme.typography.titleMedium) },
                            staff = { line -> TabStaff(line, settings.fingerColors, 1.4f) },
                        )
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/tab_every_technique.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)),
        )
    }
}
