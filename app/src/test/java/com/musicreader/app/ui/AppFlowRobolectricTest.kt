package com.musicreader.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Drives the real screens on Robolectric (no device): create a song, add sections, leave and
 * come back, and check what was saved. Catches crashes in navigation, ViewModels and layouts.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowRobolectricTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Button labels are merged into their button, so text is looked up in the unmerged tree. */
    private fun text(text: String) = rule.onAllNodesWithText(text, useUnmergedTree = true).onFirst()

    private fun click(label: String) = text(label).performClick()

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
        click("Save")
        click("Add section")
        click("Save") // default type: Verse, 4 bars
        rule.waitForIdle()
        text("Chorus").assertIsDisplayed()
        text("Verse").assertIsDisplayed()

        // Back to the library: the song is listed with its sections.
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Prueba")
        text("2 sections").assertIsDisplayed()

        // What is stored on disk.
        val repo = (rule.activity.application as MusicReaderApplication).container.songRepository
        rule.waitUntil(5_000) {
            runBlocking { repo.summaries.value.singleOrNull()?.sectionCount == 2 }
        }
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
    }
}
