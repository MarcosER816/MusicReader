package com.musicreader.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.musicreader.app.ui.capture.ReviewContent
import com.musicreader.app.ui.capture.ReviewState
import com.musicreader.app.ui.capture.ReviewSyllable
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.TakeMethod
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The review after a take (D-031), on a phone-sized screen, compared with a reference image. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ReviewScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val syllables = listOf(
        ReviewSyllable("1", "Cie", 0.0, 0.45, 64.1, 64.1),
        ReviewSyllable("2", "li", 0.5, 0.45, 65.0, 65.0),
        ReviewSyllable("3", "to", 1.0, 0.9, 67.0, 67.0),
        ReviewSyllable("4", "lin", 2.0, 0.45, 65.0, 64.0),
        ReviewSyllable("5", "do", 2.5, 1.4, 62.0, 62.0),
        ReviewSyllable("6", "de", 4.1, 0.4, 60.0, 60.0),
        ReviewSyllable("7", "a", 4.5, 0.4, 62.0, 62.0),
        ReviewSyllable("8", "mor", 5.0, 1.8, 64.0, 64.0),
    )
    private val trace = (0 until 140).map { k ->
        val beat = k * 0.05
        beat to syllables.firstOrNull { beat >= it.startBeat && beat < it.endBeat }?.let { it.startMidi!! + 0.15 * kotlin.math.sin(k * 0.6) }
    }

    @Test
    fun reviewWithASelectedSyllable() {
        rule.setContent {
            MusicReaderTheme {
                Surface {
                    ReviewContent(
                        review = ReviewState(syllables, emptyList(), trace, 0.0, 6.8, selected = 3, method = TakeMethod.SUNG),
                        beatsPerBar = 4,
                        accidentals = AccidentalPreference.SHARPS,
                        onSelect = {}, onNudge = { _, _ -> }, onPlay = {}, onAccept = {}, onAgain = {}, onCancel = {},
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/capture_review.png")
    }

    /** Short syllables, long words and close notes: no word may run over another. */
    @Test
    fun aCrowdedTakeHasNoOverlappingWords() {
        val crowded = listOf(
            ReviewSyllable("1", "Strength", 0.0, 0.2, 60.0, 60.0),
            ReviewSyllable("2", "ens", 0.2, 0.15, 60.3, 60.3),
            ReviewSyllable("3", "your", 0.35, 0.2, 60.0, 60.0),
            ReviewSyllable("4", "heart", 0.6, 0.12, 61.0, 61.0),
            ReviewSyllable("5", "to", 0.75, 0.1, 61.0, 61.0),
            ReviewSyllable("6", "night", 0.9, 1.0, 62.0, 62.0),
            ReviewSyllable("7", "de", 2.0, 0.25, 60.0, 60.0),
            ReviewSyllable("8", "a", 2.25, 0.25, 60.0, 60.0),
            ReviewSyllable("9", "mor", 2.5, 1.0, 60.0, 60.0),
        )
        rule.setContent {
            MusicReaderTheme {
                Surface {
                    ReviewContent(
                        review = ReviewState(crowded, emptyList(), emptyList(), 0.0, 3.5, selected = null, method = TakeMethod.SUNG),
                        beatsPerBar = 4,
                        accidentals = AccidentalPreference.SHARPS,
                        onSelect = {}, onNudge = { _, _ -> }, onPlay = {}, onAccept = {}, onAgain = {}, onCancel = {},
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/capture_review_crowded.png")
    }
}
