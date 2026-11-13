package com.musicreader.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.musicreader.app.MainActivity
import com.musicreader.app.MusicReaderApplication
import com.musicreader.core.model.SectionType
import kotlinx.coroutines.runBlocking
import android.os.Looper
import com.musicreader.app.ui.song.SongViewModel
import org.junit.Rule
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Runs on Robolectric's default screen (320×470 dp), scrolling to items below the fold.
 *
 * Drives the real screens on Robolectric (no device): create a song, add sections, leave and
 * come back, and check what was saved. Catches crashes in navigation, ViewModels and layouts.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppFlowRobolectricTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) {
        rule.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTextGone(text: String) {
        rule.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    /** Button labels are merged into their button, so text is looked up in the unmerged tree. */
    private fun text(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).onFirst()

    private fun click(label: String) = text(label).performClick()

    /**
     * Lets the song screen's debounced auto-save run: Robolectric's main-thread clock only moves
     * when the test moves it.
     */
    private fun letAutoSaveRun() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SongViewModel.SAVE_DELAY_MS * 2))
        rule.waitForIdle()
    }

    /** For items inside a scrolling screen that may start below the fold. */
    private fun scrollTo(label: String) = text(label).performScrollTo()

    @Test
    fun createSongAddSectionsAndReopen() {
        text("No songs yet.").assertIsDisplayed()
        click("New song")
        rule.onNode(hasSetTextAction()).performTextInput("Prueba")
        click("Create")

        // A new song opens its settings.
        waitForText("Song settings")
        scrollTo("4/4").assertIsDisplayed()
        scrollTo("3/4").performClick()
        scrollTo("Tap tempo").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        // Sections screen: add a chorus and a verse.
        waitForText("Add section")
        text("No sections yet.").assertIsDisplayed()
        click("Add section")
        click("Chorus")
        text("The emotional, catchy peak. Repeats the same lyrics and main message.").assertIsDisplayed()
        scrollTo("Save").performClick()
        waitForTextGone("Save")
        click("Add section")
        scrollTo("Save").performClick() // default type: Verse, 4 bars
        waitForTextGone("Save")
        text("Chorus").assertIsDisplayed() // section cards, not sheet chips: the sheet is closed
        text("Verse").assertIsDisplayed()
        text("Bar 5 · 4 bars · 0:06").assertIsDisplayed() // Verse after 4 bars of Chorus, 3/4 at 120 BPM

        letAutoSaveRun()

        // Back to the library: the song is listed with its sections.
        rule.onNodeWithContentDescription("Back").performClick()
        letAutoSaveRun()
        waitForText("Prueba")
        waitForText("2 sections")
        text("2 sections").assertIsDisplayed()

        // What is stored on disk.
        val repo = (rule.activity.application as MusicReaderApplication).container.songRepository
        val song = runBlocking { repo.load(repo.summaries.value.single().id) }!!
        assertEquals("Prueba", song.title)
        assertEquals(listOf(SectionType.CHORUS, SectionType.VERSE), song.sections.map { it.type })
        assertEquals(3, song.timeSignature.num)

        // Recreate the activity (like coming back to the app): the song is still there.
        rule.activityRule.scenario.recreate()
        waitForText("Prueba")
        click("Prueba")
        waitForText("Chorus")
        text("Verse").assertIsDisplayed()

        // Lyrics: tapping a section opens its lyrics; text is split into syllables.
        click("Chorus")
        waitForText("Lyrics · Chorus")
        rule.onNode(hasSetTextAction()).performTextInput("Canta conmigo\nesta canción")
        letAutoSaveRun()
        click("Syllables")
        waitForText("mi")
        listOf("Can", "ta", "con", "mi", "go", "es", "can", "ción").forEach { text(it).assertIsDisplayed() }
        // Tap the dot between "Can" and "ta": the word becomes "Canta", a manual split.
        rule.onAllNodesWithContentDescription("Join syllables")[0].performClick()
        waitForText("Canta")
        letAutoSaveRun()
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Canta conmigo") // the section card shows the first lyric line
        letAutoSaveRun()

        val stored = runBlocking { repo.load(song.id) }!!
        val chorus = stored.sectionContents.first { c -> c.id == stored.sections[0].contentId }
        assertEquals(listOf("Canta conmigo", "esta canción"), chorus.lyricLines.map { it.text })
        assertEquals(listOf("Canta"), chorus.lyricLines[0].words[0].syllables.map { it.text })
        assertEquals(true, chorus.lyricLines[0].words[0].syllableOverride)

        // The manual split survives recreating the activity.
        rule.activityRule.scenario.recreate()
        waitForText("Canta conmigo")
        click("Chorus")
        waitForText("Lyrics · Chorus")
        click("Syllables")
        waitForText("Canta")
        text("ción").assertIsDisplayed()
    }
}
