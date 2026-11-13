package com.musicreader.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
@Config(sdk = [35])
class AppFlowRobolectricTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun createSongAddSectionsAndReopen() {
        rule.onNodeWithText("No songs yet.").assertIsDisplayed()
        rule.onNodeWithText("New song").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Prueba")
        rule.onNodeWithText("Create").performClick()

        // A new song opens its settings.
        waitForText("Song settings")
        rule.onNodeWithText("4/4").assertIsDisplayed()
        rule.onNodeWithText("3/4").performClick()
        rule.onNodeWithText("Tap tempo").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        // Sections screen: add a chorus and a verse.
        waitForText("Add section")
        rule.onNodeWithText("No sections yet.").assertIsDisplayed()
        rule.onNodeWithText("Add section").performClick()
        rule.onNodeWithText("Chorus").performClick()
        rule.onNodeWithText("The emotional, catchy peak. Repeats the same lyrics and main message.").assertIsDisplayed()
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Add section").performClick()
        rule.onNodeWithText("Save").performClick() // default type: Verse, 4 bars
        rule.waitForIdle()
        rule.onNodeWithText("Chorus").assertIsDisplayed()
        rule.onNodeWithText("Verse").assertIsDisplayed()

        // Back to the library: the song is listed with its sections.
        rule.onNodeWithContentDescription("Back").performClick()
        waitForText("Prueba")
        rule.onNodeWithText("2 sections").assertIsDisplayed()

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
        rule.onNodeWithText("Prueba").performClick()
        waitForText("Chorus")
        rule.onNodeWithText("Verse").assertIsDisplayed()
    }
}
