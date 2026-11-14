package com.musicreader.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.musicreader.app.ui.chords.FretboardDiagram
import com.musicreader.core.model.Accidental
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.NoteLetter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Fretboard diagrams of the first dictionary shape of five chords, compared with reference images. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FretboardScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun capture(name: String, symbol: ChordSymbol, expectedFrets: List<Int>) {
        val shape = TestDictionary.dictionary.find(symbol)!!.shapes.first()
        assertEquals(expectedFrets, shape.frets)
        rule.setContent {
            MusicReaderTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(12.dp)) {
                        Text(symbol.toString(), style = MaterialTheme.typography.headlineSmall)
                        FretboardDiagram(shape = shape, fingerColors = AppSettings.DEFAULT_FINGER_COLORS)
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/chord_$name.png",
            roborazziOptions = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)),
        )
    }

    @Test
    fun openC() = capture("C_open", ChordSymbol(NoteLetter.C), listOf(-1, 3, 2, 0, 1, 0))

    @Test
    fun barreF() = capture("F_barre", ChordSymbol(NoteLetter.F), listOf(1, 3, 3, 2, 1, 1))

    @Test
    fun bFlat9() = capture("Bb9", ChordSymbol(NoteLetter.B, Accidental.FLAT, "9"), listOf(-1, 1, 0, 1, 1, 1))

    @Test
    fun cMaj7() = capture("Cmaj7", ChordSymbol(NoteLetter.C, suffix = "maj7"), TestDictionary.dictionary.find(ChordSymbol(NoteLetter.C, suffix = "maj7"))!!.shapes.first().frets)

    @Test
    fun gMinor7() = capture("Gm7", ChordSymbol(NoteLetter.G, suffix = "m7"), listOf(3, -1, 3, 3, 3, -1))

    @Test
    fun dSharp() = capture("D_sharp", ChordSymbol(NoteLetter.D, Accidental.SHARP), listOf(-1, -1, 1, 3, 4, 3))
}
