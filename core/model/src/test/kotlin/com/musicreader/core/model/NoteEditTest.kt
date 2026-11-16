package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteEditTest {
    private val sung = Syllable("s1", "Ho", 1.0, 1.0, 60.0, 62.0, listOf(CurvePoint(0.0, 60.0), CurvePoint(0.5, 61.2), CurvePoint(1.0, 62.0)))
    private val content = SectionContent(
        id = "c", lengthBeats = 16,
        lyricLines = listOf(
            LyricLine("l", text = "Hola mi", words = listOf(
                Word("w1", "Hola", listOf(sung, Syllable("s2", "la", 2.0, 0.5, 64.0, 64.0))),
                Word("w2", "mi", listOf(Syllable("s3", "mi"))),
            )),
        ),
    )

    private fun s(c: SectionContent, id: String) = CaptureOps.findSyllable(c, id)!!

    @Test
    fun movingTheNoteMovesBothEndsAndTheCurveAndNothingElse() {
        val c = NoteEditOps.moveNote(content, "s1", 1.37)
        assertEquals(61.37, s(c, "s1").pitchStartMidi!!, 1e-9)
        assertEquals(63.37, s(c, "s1").pitchEndMidi!!, 1e-9)
        assertEquals(listOf(61.37, 62.57, 63.37), s(c, "s1").pitchCurve.map { it.midi }.map { Math.round(it * 100) / 100.0 })
        assertEquals(s(content, "s2"), s(c, "s2"))
        assertEquals(1.0, s(c, "s1").startBeat)
    }

    @Test
    fun initialAndFinalNotesReshapeTheCurve() {
        // D-010: every point moves by (1 − t)·Δstart + t·Δend.
        val c = NoteEditOps.shiftNotes(content, "s1", 2.0, -1.0)
        assertEquals(62.0, s(c, "s1").pitchStartMidi)
        assertEquals(61.0, s(c, "s1").pitchEndMidi)
        assertEquals(listOf(62.0, 61.7, 61.0), s(c, "s1").pitchCurve.map { Math.round(it.midi * 1000) / 1000.0 })
    }

    @Test
    fun startAndLengthAreContinuousAndKeptInTheSection() {
        val c = NoteEditOps.setStart(content, "s1", 3.141)
        assertEquals(3.141, s(c, "s1").startBeat)
        assertEquals(1.0, s(c, "s1").durationBeats, "the length stays")
        assertEquals(2.0, s(c, "s2").startBeat, "no other bar moves, even if they overlap now")
        assertEquals(15.0, s(NoteEditOps.setStart(content, "s1", 40.0), "s1").startBeat)
        assertEquals(0.0, s(NoteEditOps.setStart(content, "s1", -2.0), "s1").startBeat)
        assertEquals(1.37, s(NoteEditOps.setDuration(content, "s1", 1.37), "s1").durationBeats!!, 1e-12)
        assertEquals(NoteEditOps.MIN_DURATION, s(NoteEditOps.setDuration(content, "s1", -1.0), "s1").durationBeats)
        assertEquals(15.0, s(NoteEditOps.setDuration(content, "s1", 99.0), "s1").durationBeats)
    }

    @Test
    fun addAndClearANote() {
        val suggested = NoteEditOps.suggestedMidi(content, LyricLine.MAIN_VOICE, "s3")
        assertEquals(64.0, suggested, "the note before it")
        val added = NoteEditOps.addNote(content, "s3", suggested, 4.25, 1.0)
        assertEquals(4.25, s(added, "s3").startBeat)
        assertEquals(64.0, s(added, "s3").pitchStartMidi)
        // A timed syllable keeps its timing.
        val cleared = NoteEditOps.clearNote(content, "s1")
        assertNull(s(cleared, "s1").pitchStartMidi)
        assertTrue(s(cleared, "s1").pitchCurve.isEmpty())
        assertEquals(1.0, s(cleared, "s1").startBeat)
        val again = NoteEditOps.addNote(cleared, "s1", 57.5, 9.0, 3.0)
        assertEquals(1.0, s(again, "s1").startBeat)
        assertEquals(1.0, s(again, "s1").durationBeats)
        assertEquals(57.5, s(again, "s1").pitchStartMidi)
    }

    @Test
    fun undoRedoOfNoteEdits() {
        val history = EditHistory<List<Syllable>>()
        var c = content
        fun edit(f: (SectionContent) -> SectionContent) {
            history.record(NoteEditOps.snapshot(c))
            c = f(c)
        }
        edit { NoteEditOps.moveNote(it, "s1", 0.5) }
        edit { NoteEditOps.setDuration(it, "s2", 0.75) }
        c = CaptureOps.restore(c, history.undo(NoteEditOps.snapshot(c))!!)
        assertEquals(0.5, s(c, "s2").durationBeats)
        assertEquals(60.5, s(c, "s1").pitchStartMidi)
        c = CaptureOps.restore(c, history.undo(NoteEditOps.snapshot(c))!!)
        assertEquals(content, c)
        c = CaptureOps.restore(c, history.redo(NoteEditOps.snapshot(c))!!)
        assertEquals(60.5, s(c, "s1").pitchStartMidi)
        assertTrue(history.canRedo)
    }

    @Test
    fun noteNamesWithCents() {
        assertEquals(Triple("A", 3, 12), NoteEditOps.noteParts(57.12, AccidentalPreference.SHARPS))
        assertEquals(Triple("C#", 4, -30), NoteEditOps.noteParts(60.7, AccidentalPreference.SHARPS))
        assertEquals(Triple("Db", 4, -30), NoteEditOps.noteParts(60.7, AccidentalPreference.FLATS))
    }
}

class NoteViewportTest {
    private val vp = NoteViewport(firstBeat = 2.0, pxPerBeat = 100.0, topMidi = 72.0, pxPerSemitone = 20.0, widthPx = 1000.0, heightPx = 600.0, stripPx = 50.0)

    @Test
    fun conversionsAreExactInverses() {
        assertEquals(50.0, vp.beatToX(2.0))
        assertEquals(287.5, vp.beatToX(4.375))
        assertEquals(4.375, vp.xToBeat(287.5))
        assertEquals(0.0, vp.midiToY(72.0))
        assertEquals(245.4, vp.midiToY(59.73), 1e-9)
        assertEquals(59.73, vp.yToMidi(245.4), 1e-9)
        assertEquals(11.5, vp.lastBeat)
        assertEquals(42.0, vp.bottomMidi)
    }

    @Test
    fun zoomKeepsThePointUnderTheFingers() {
        val z = vp.zoom(2.0, 1.5, 450.0, 300.0)
        assertEquals(vp.xToBeat(450.0), z.xToBeat(450.0), 1e-9)
        assertEquals(vp.yToMidi(300.0), z.yToMidi(300.0), 1e-9)
        assertEquals(200.0, z.pxPerBeat)
        assertEquals(30.0, z.pxPerSemitone)
        assertEquals(NoteViewport.MAX_PX_PER_BEAT, vp.zoom(1000.0, 1.0, 0.0, 0.0).pxPerBeat)
    }

    @Test
    fun panFollowsTheFinger() {
        val p = vp.pan(100.0, -40.0)  // finger right and up
        assertEquals(1.0, p.firstBeat, 1e-9)
        assertEquals(70.0, p.topMidi, 1e-9)
        val c = NoteViewport(-50.0, 100.0, 200.0, 20.0, 1000.0, 600.0, 50.0).clamp(16)
        assertEquals(-1.0, c.firstBeat)
        assertEquals(NoteEditOps.MAX_MIDI, c.topMidi)
    }

    private val bars = listOf(
        NoteBar("a", "Ho", 3.0, 1.0, 60.0, 60.0),   // x 150..250, y 230..250 (height 20)
        NoteBar("b", "la", 3.8, 0.5, 60.2, 60.2),   // overlaps "a"
        NoteBar("c", "mi", 6.0, 0.1, 65.0, 65.0),   // x 450..460: short
    )

    @Test
    fun hitTestingBodyEdgeOverlapAndMisses() {
        fun hit(x: Double, y: Double, selected: String? = null) = vp.hitTest(bars, x, y, 20.0, 16.0, 6.0, selected)
        assertEquals("a" to BarPart.BODY, hit(170.0, 240.0))
        assertEquals("a" to BarPart.END, hit(245.0, 240.0, selected = "a"))
        assertEquals("b" to BarPart.BODY, hit(235.0, 240.0), "overlap: the later bar is on top")
        assertEquals("a" to BarPart.END, hit(240.0, 240.0, selected = "a"), "unless the other one is selected")
        assertEquals("c" to BarPart.BODY, hit(452.0, 140.0), "a short bar still has a body")
        assertEquals("c" to BarPart.END, hit(462.0, 140.0))
        assertNull(hit(170.0, 300.0))
        assertNull(hit(30.0, 240.0), "the note-name strip")
    }

    @Test
    fun dragMathIsContinuousAndSlowInFineMode() {
        val d = NoteDrag("a", BarPart.BODY, 3.0, 1.0, 60.0, fine = false)
        assertEquals(3.137, d.newStart(13.7, vp), 1e-12)
        assertEquals(0.685, d.noteDelta(-13.7, vp), 1e-12)   // up 13.7 px = 0.685 semitone
        val f = d.copy(fine = true)
        assertEquals(3.03425, f.newStart(13.7, vp), 1e-12)
        assertEquals(0.17125, f.noteDelta(-13.7, vp), 1e-12)
        val e = NoteDrag("a", BarPart.END, 3.0, 1.0, 60.0, fine = false)
        assertEquals(1.0 + 0.0731, e.newDuration(7.31, vp), 1e-12)
    }
}
