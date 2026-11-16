package com.musicreader.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.musicreader.app.ui.notes.NoteEditorScreen
import com.musicreader.app.ui.notes.NoteEditorViewModel
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.ChordEvent
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.CurvePoint
import com.musicreader.core.model.LyricLine
import com.musicreader.core.model.NoteLetter
import com.musicreader.core.model.Section
import com.musicreader.core.model.SectionContent
import com.musicreader.core.model.SectionType
import com.musicreader.core.model.Song
import com.musicreader.core.model.Syllable
import com.musicreader.core.model.Word
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The note editor (D-032) on a phone-sized screen, compared with a reference image. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class NoteEditorScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun syl(id: String, text: String, start: Double, dur: Double, m0: Double, m1: Double = m0) =
        Syllable(id, text, start, dur, m0, m1, listOf(CurvePoint(0.0, m0 - 0.2), CurvePoint(0.3, m0 + 0.1), CurvePoint(0.7, (m0 + m1) / 2), CurvePoint(1.0, m1)))

    @Test
    fun editorWithASelectedNote() {
        val content = SectionContent(
            id = "c", lengthBeats = 8,
            lyricLines = listOf(LyricLine("l", text = "Cielito lindo de", words = listOf(
                Word("w1", "Cielito", listOf(syl("1", "Cie", 0.0, 0.45, 64.1), syl("2", "li", 0.5, 0.45, 65.0), syl("3", "to", 1.0, 0.9, 67.0))),
                Word("w2", "lindo", listOf(syl("4", "lin", 2.0, 0.6, 65.0, 64.0), syl("5", "do", 2.75, 1.2, 62.0))),
                Word("w3", "de", listOf(Syllable("6", "de"))),
            ))),
            chords = listOf(ChordEvent("a", 0.0, ChordSymbol(NoteLetter.C), "C|major|0"), ChordEvent("b", 2.0, ChordSymbol(NoteLetter.G, suffix = "7"), "G|7|0")),
        )
        val song = MutableStateFlow<Song?>(Song(id = "x", title = "T", sections = listOf(Section("s", SectionType.VERSE, contentId = "c")),
            sectionContents = listOf(content), createdAt = 0, updatedAt = 0))
        val vm = NoteEditorViewModel("s", song, MutableStateFlow(AppSettings()), { _, _ -> }, FakeAudioService())
        vm.select("4")
        rule.setContent { MusicReaderTheme { NoteEditorScreen(vm, onBack = {}) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/note_editor.png")
    }
}
