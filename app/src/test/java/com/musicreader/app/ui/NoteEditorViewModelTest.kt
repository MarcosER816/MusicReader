package com.musicreader.app.ui

import androidx.lifecycle.viewModelScope
import com.musicreader.app.ui.notes.DragValue
import com.musicreader.app.ui.notes.NoteEditorViewModel
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.CaptureOps
import com.musicreader.core.model.ChordEvent
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.LyricLine
import com.musicreader.core.model.NoteLetter
import com.musicreader.core.model.Section
import com.musicreader.core.model.SectionContent
import com.musicreader.core.model.SectionType
import com.musicreader.core.model.Song
import com.musicreader.core.model.Syllable
import com.musicreader.core.model.Word
import com.musicreader.core.model.contentOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val content = SectionContent(
        id = "c", lengthBeats = 16,
        lyricLines = listOf(
            LyricLine("l", text = "Hola mi", words = listOf(
                Word("w1", "Hola", listOf(Syllable("s1", "Ho", 1.0, 1.0, 60.0, 60.0), Syllable("s2", "la", 2.0, 1.0, 62.0, 62.0))),
                Word("w2", "mi", listOf(Syllable("s3", "mi"))),
            )),
        ),
        chords = listOf(ChordEvent("c1", 0.0, ChordSymbol(NoteLetter.C), "C|major|0")),
    )
    private val song0 = Song(id = "x", title = "T", tempoBpm = 120.0, sections = listOf(Section("sec", SectionType.VERSE, contentId = "c")),
        sectionContents = listOf(content), createdAt = 0, updatedAt = 0)

    private class Rig(scope: TestScope, song: Song) {
        val song = MutableStateFlow<Song?>(song)
        val audio = FakeAudioService()
        val vm = NoteEditorViewModel("sec", this.song, MutableStateFlow(AppSettings()), { _, change ->
            this.song.value = this.song.value!!.let { s -> s.copy(sectionContents = s.sectionContents.map(change)) }
        }, audio)
        fun syl(id: String) = CaptureOps.findSyllable(song.value!!.contentOf(song.value!!.sections.first()), id)!!
    }

    // Viewport: 100 px per beat from beat 0 after a 50 px strip; 20 px per semitone, 72 at the top.
    // Bar "Ho": x 150..250, y (72−60)·20 = 240 ± 13.
    private fun TestScope.ready(): Rig {
        val r = Rig(this, song0)
        advanceUntilIdle()
        r.vm.setSize(1000.0, 600.0, 50.0, 100.0)
        advanceUntilIdle()
        // The fit starts half a beat before the first note; pan so that beat 0 is at the strip.
        r.vm.pan(r.vm.state.value.viewport!!.firstBeat * 100, 0.0)
        advanceUntilIdle()
        val vp = r.vm.state.value.viewport!!
        r.vm.zoom(1.0, 20.0 / vp.pxPerSemitone, 50.0, 0.0)
        r.vm.pan(0.0, (72.0 - vp.topMidi) * 20.0)
        advanceUntilIdle()
        return r
    }

    @Test
    fun theViewportFitsAndConverts() = runTest(dispatcher) {
        val r = ready()
        val vp = r.vm.state.value.viewport!!
        assertEquals(0.0, vp.firstBeat, 1e-6)
        assertEquals(20.0, vp.pxPerSemitone, 1e-6)
        assertEquals(240.0, vp.midiToY(60.0), 1e-6)
        assertEquals(2, r.vm.state.value.bars.size)
        assertEquals(listOf("s3"), r.vm.state.value.unplaced.map { it.id })
        assertEquals(1, r.vm.state.value.chords.size)
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun draggingABarUpChangesOnlyItsNoteFreely() = runTest(dispatcher) {
        val r = ready()
        assertTrue(r.vm.dragStart(180.0, 240.0, 26.0, 22.0, 8.0))
        r.vm.dragTo(1.0, -13.7)          // mostly up: the note axis
        r.vm.dragTo(4.0, -27.4)
        advanceUntilIdle()
        assertEquals(DragValue.NOTE, r.vm.state.value.dragging)
        r.vm.dragEnd()
        assertEquals(61.37, r.syl("s1").pitchStartMidi!!, 1e-9, "no snapping to a note")
        assertEquals(1.0, r.syl("s1").startBeat, "the start did not move")
        assertEquals(62.0, r.syl("s2").pitchStartMidi, "the other bar did not move")
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun draggingSidewaysChangesTheStartAndTheEdgeTheLength() = runTest(dispatcher) {
        val r = ready()
        r.vm.dragStart(180.0, 240.0, 26.0, 22.0, 8.0)
        r.vm.dragTo(37.3, 2.0)
        r.vm.dragEnd()
        assertEquals(1.373, r.syl("s1").startBeat!!, 1e-9, "no snapping to the beat")
        assertEquals(60.0, r.syl("s1").pitchStartMidi)
        assertEquals(2.0, r.syl("s2").startBeat, "even when they now overlap")
        // The right end (now at x 287.3): the length.
        r.vm.dragStart(283.0, 240.0, 26.0, 22.0, 8.0)
        advanceUntilIdle()
        assertEquals(DragValue.LENGTH, r.vm.state.value.dragging)
        r.vm.dragTo(-25.0, 0.0)
        r.vm.dragEnd()
        assertEquals(0.75, r.syl("s1").durationBeats!!, 1e-9)
        assertEquals(1.373, r.syl("s1").startBeat!!, 1e-9)
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun fineModeMovesAQuarterAndButtonsStepTiny() = runTest(dispatcher) {
        val r = ready()
        r.vm.setFine(true)
        r.vm.dragStart(180.0, 240.0, 26.0, 22.0, 8.0)
        r.vm.dragTo(0.0, -20.0)          // one semitone of finger = a quarter semitone
        r.vm.dragEnd()
        assertEquals(60.25, r.syl("s1").pitchStartMidi!!, 1e-9)
        r.vm.nudgeNote(5.0)
        r.vm.nudgeStart(-0.01)
        r.vm.nudgeLength(0.01)
        r.vm.nudgeEndNote(-5.0)
        assertEquals(60.30, r.syl("s1").pitchStartMidi!!, 1e-9)
        assertEquals(60.25, r.syl("s1").pitchEndMidi!!, 1e-9)
        assertEquals(0.99, r.syl("s1").startBeat!!, 1e-9)
        assertEquals(1.01, r.syl("s1").durationBeats!!, 1e-9)
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun undoRedoAddAndClear() = runTest(dispatcher) {
        val r = ready()
        r.vm.dragStart(180.0, 240.0, 26.0, 22.0, 8.0)
        r.vm.dragTo(0.0, -40.0)
        r.vm.dragEnd()
        r.vm.clearNote()
        assertNull(r.syl("s1").pitchStartMidi)
        assertEquals(1.0, r.syl("s1").startBeat, "clearing keeps the timing")
        r.vm.undo()
        assertEquals(62.0, r.syl("s1").pitchStartMidi!!, 1e-9)
        r.vm.undo()
        assertEquals(60.0, r.syl("s1").pitchStartMidi)
        advanceUntilIdle()
        assertFalse(r.vm.state.value.canUndo)
        assertTrue(r.vm.state.value.canRedo)
        r.vm.redo()
        assertEquals(62.0, r.syl("s1").pitchStartMidi!!, 1e-9)
        // "mi" has no note: tap empty space at beat 5.5 for the cursor, select it, add.
        r.vm.tap(50.0 + 5.5 * 100, 500.0, 26.0, 22.0, 8.0)
        r.vm.select("s3")
        r.vm.addNote()
        assertEquals(5.5, r.syl("s3").startBeat!!, 1e-9)
        assertEquals(62.0, r.syl("s3").pitchStartMidi, "the note of the syllable before it")
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun tapsSelectZoomAndPan() = runTest(dispatcher) {
        val r = ready()
        r.vm.tap(260.0, 200.0, 26.0, 22.0, 8.0)   // the "la" bar: x 250..350, y 200
        advanceUntilIdle()
        assertEquals("s2", r.vm.state.value.selectedId)
        r.vm.tap(700.0, 500.0, 26.0, 22.0, 8.0)
        advanceUntilIdle()
        assertNull(r.vm.state.value.selectedId)
        assertEquals(6.5, r.vm.state.value.cursorBeat, 1e-9)
        val before = r.vm.state.value.viewport!!
        r.vm.zoom(2.0, 1.0, 450.0, 300.0)
        advanceUntilIdle()
        val after = r.vm.state.value.viewport!!
        assertEquals(before.xToBeat(450.0), after.xToBeat(450.0), 1e-9)
        assertEquals(200.0, after.pxPerBeat, 1e-9)
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun previewPlaysFromTheCursorAndStopsAfterTheEnd() = runTest(dispatcher) {
        val r = ready()
        try {
            previewChecks(r)
        } finally {
            r.audio.stopPreview()   // never leave the 100 ms watch running, even on a failure
            r.vm.viewModelScope.coroutineContext.cancelChildren()
        }
    }

    private fun TestScope.previewChecks(r: Rig) {
        r.vm.tap(50.0 + 0.5 * 100, 500.0, 26.0, 22.0, 8.0)   // cursor at beat 0.5
        r.vm.togglePreview()
        runCurrent()
        assertEquals("preview 120.0 4 0.5", r.audio.calls.last())
        assertEquals(2, r.audio.previewNotes.size)
        assertEquals(62.0, r.audio.previewNotes[1].startMidi)
        runCurrent()   // (not advanceUntilIdle: the preview checks every 100 ms while it plays)
        assertTrue(r.vm.state.value.previewing)
        assertEquals(0.5, r.vm.previewBeat())
        r.audio.previewBeatNow = 4.2   // past the last note (ends at 3) + 1 beat
        advanceTimeBy(200)
        runCurrent()
        assertEquals("preview stop", r.audio.calls.last())
        advanceUntilIdle()
        assertFalse(r.vm.state.value.previewing)
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }
}
