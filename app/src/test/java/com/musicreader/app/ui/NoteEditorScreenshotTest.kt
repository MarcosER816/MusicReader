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

    /** Mark, Stage 10 round 1: "all the lyrics on the notes get one over the other". */
    @Test
    fun aCrowdedSectionShowsOneLineWithoutOverlaps() {
        fun quick(id: String, text: String, start: Double, m: Double) = Syllable(id, text, start, 0.22, m, m)
        val content = SectionContent(
            id = "c", lengthBeats = 16,
            lyricLines = listOf(
                LyricLine("l1", text = "Strengthen your heart tonight my love", words = listOf(
                    Word("a", "Strengthen", listOf(quick("1", "Streng", 0.0, 62.0), quick("2", "then", 0.25, 62.3))),
                    Word("b", "your", listOf(quick("3", "your", 0.5, 62.0))),
                    Word("c", "heart", listOf(quick("4", "heart", 0.75, 63.0))),
                    Word("d", "tonight", listOf(quick("5", "to", 1.0, 64.0), quick("6", "night", 1.25, 64.2))),
                    Word("e", "my", listOf(quick("7", "my", 1.5, 62.0))),
                    Word("f", "love", listOf(Syllable("8", "love", 1.75, 1.5, 60.0, 60.0))),
                )),
                LyricLine("l2", text = "and again", words = listOf(
                    Word("g", "and", listOf(quick("9", "and", 4.0, 60.0))),
                    Word("h", "again", listOf(quick("10", "a", 4.25, 62.0), Syllable("11", "gain", 4.5, 1.0, 64.0, 64.0))),
                )),
            ),
        )
        val song = MutableStateFlow<Song?>(Song(id = "x", title = "T", sections = listOf(Section("s", SectionType.VERSE, contentId = "c")),
            sectionContents = listOf(content), createdAt = 0, updatedAt = 0))
        val vm = NoteEditorViewModel("s", song, MutableStateFlow(AppSettings()), { _, _ -> }, FakeAudioService())
        rule.setContent { MusicReaderTheme { NoteEditorScreen(vm, onBack = {}) } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("src/test/screenshots/note_editor_crowded.png")
    }
}
