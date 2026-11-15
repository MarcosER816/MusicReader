package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptureOpsTest {
    private fun syl(id: String, text: String, start: Double? = null) =
        Syllable(id, text, startBeat = start, durationBeats = start?.let { 1.0 }, pitchStartMidi = start?.let { 60.0 }, pitchEndMidi = start?.let { 60.0 })

    private val content = SectionContent(
        id = "c",
        lengthBeats = 16,
        lyricLines = listOf(
            LyricLine("l1", text = "Hola mi amor", words = listOf(
                Word("w1", "Hola", listOf(syl("s1", "Ho", 0.0), syl("s2", "la", 1.0))),
                Word("w2", "mi", listOf(syl("s3", "mi", 2.0))),
                Word("w3", "amor", listOf(syl("s4", "a"), syl("s5", "mor"))),
            )),
            LyricLine("l2", text = "ven", words = listOf(Word("w4", "ven", listOf(syl("s6", "ven"))))),
            LyricLine("v2", voice = LyricLine.SECOND_VOICE, text = "uh", pairedLineId = "l1", words = listOf(Word("w5", "uh", listOf(syl("s7", "uh"))))),
        ),
    )

    @Test
    fun selectionFromFirstToLastWordInEitherOrder() {
        assertEquals(listOf("s3", "s4", "s5", "s6"), CaptureOps.selection(content, 0, "w2", "w4"))
        assertEquals(listOf("s3", "s4", "s5", "s6"), CaptureOps.selection(content, 0, "w4", "w2"))
        assertEquals(listOf("s7"), CaptureOps.selection(content, 1, "w5", "w5"))
        assertEquals(emptyList(), CaptureOps.selection(content, 1, "w1", "w5"), "other voice")
    }

    @Test
    fun startBarFromTheFirstTimedSyllableOrASuggestion() {
        assertEquals(0, CaptureOps.startBar(content, listOf("s2", "s3"), 4))
        assertNull(CaptureOps.startBar(content, listOf("s4"), 4))
        // s3 ends at beat 3: the next bar is bar 1 (beat 4).
        assertEquals(1, CaptureOps.suggestedBar(content, 0, listOf("s4", "s5"), 4))
        assertEquals(0, CaptureOps.suggestedBar(content, 0, listOf("s1"), 4))
    }

    @Test
    fun lastTakeWinsOnlyInTheSelectedRange() {
        val results = listOf(
            SyllableResult("s3", 2.25, 0.5, 62.1, 62.3, listOf(CurvePoint(0.0, 62.1), CurvePoint(1.0, 62.3))),
            SyllableResult("s4", 3.0, 0.75, 64.0, 64.0, emptyList()),
        )
        val before = CaptureOps.snapshot(content, listOf("s3", "s4"))
        val after = CaptureOps.apply(content, results, "take1")
        val s3 = CaptureOps.findSyllable(after, "s3")!!
        assertEquals(2.25, s3.startBeat)
        assertEquals(0.5, s3.durationBeats)
        assertEquals(62.3, s3.pitchEndMidi)
        assertEquals("take1", s3.takeId)
        assertEquals(2, s3.pitchCurve.size)
        // Everything else is untouched.
        for (id in listOf("s1", "s2", "s5", "s6", "s7")) {
            assertEquals(CaptureOps.findSyllable(content, id), CaptureOps.findSyllable(after, id), id)
        }
        // A second take of only s4 replaces only s4.
        val again = CaptureOps.apply(after, listOf(SyllableResult("s4", 3.1, 0.6, 65.0, 65.0, emptyList())), "take2")
        assertEquals(3.1, CaptureOps.findSyllable(again, "s4")!!.startBeat)
        assertEquals(s3, CaptureOps.findSyllable(again, "s3"))
        // Undo the first take: back to before, text untouched.
        val undone = CaptureOps.restore(after, before)
        assertEquals(content, undone)
    }

    @Test
    fun restoreKeepsEditedText() {
        val after = CaptureOps.apply(content, listOf(SyllableResult("s3", 2.5, 0.5, 61.0, 61.0, emptyList())), "t")
        val renamed = after.copy(lyricLines = after.lyricLines.map { l ->
            l.copy(words = l.words.map { w -> w.copy(syllables = w.syllables.map { if (it.id == "s3") it.copy(text = "mí") else it }) })
        })
        val undone = CaptureOps.restore(renamed, CaptureOps.snapshot(content, listOf("s3")))
        assertEquals("mí", CaptureOps.findSyllable(undone, "s3")!!.text)
        assertEquals(2.0, CaptureOps.findSyllable(undone, "s3")!!.startBeat)
    }

    @Test
    fun beatClockFromTheMetronomeGrid() {
        // 120 BPM at 48 kHz: 24000 frames per beat. Beat 0 of the metronome at frame 48000,
        // a 4/4 count-in bar, then section bar 2 (beat 8) starts at frame 48000 + 96000.
        val clock = BeatClock.forTake(48000, 24000.0, 4, startBar = 2, sampleRate = 48000)
        assertEquals(8.0, clock.beat(144000 / 48000.0), 1e-9)
        assertEquals(8.5, clock.beat(156000 / 48000.0), 1e-9)
        assertEquals(4.0, clock.beat(48000 / 48000.0), 1e-9, "the count-in is the bar before")
        assertEquals(3.25, clock.seconds(8.5), 1e-9)
    }

    @Test
    fun resultInBeatsWithARelativeCurve() {
        val clock = BeatClock(beatAtZero = 0.0, beatsPerSecond = 2.0)
        val r = CaptureOps.result("s", 1.0, 1.5, 60.0, 61.0, listOf(1.0 to 60.0, 1.25 to 60.5, 1.49 to 61.0), clock, lengthBeats = 16)
        assertEquals(2.0, r.startBeat)
        assertEquals(1.0, r.durationBeats, 1e-9)
        assertEquals(listOf(0.0, 0.5), r.curve.take(2).map { it.t })
        // Before the section start it is kept inside the section.
        assertEquals(0.0, CaptureOps.result("s", -1.0, 0.2, null, null, emptyList(), clock, 16).startBeat)
    }

    @Test
    fun marks() {
        assertEquals(true, CaptureOps.isTimed(CaptureOps.findSyllable(content, "s1")!!))
        assertEquals(false, CaptureOps.isTimed(CaptureOps.findSyllable(content, "s4")!!))
        assertEquals(true, CaptureOps.hasNote(CaptureOps.findSyllable(content, "s1")!!))
    }
}

class CaptureVerseTest {
    private fun word(id: String, vararg syl: Pair<String, String>) = Word(id, syl.joinToString("") { it.second }, syl.map { Syllable(it.first, it.second) })

    private val verse = SectionContent(
        id = "c", lengthBeats = 32,
        lyricLines = listOf(
            LyricLine("l1", text = "cielito lindo", words = listOf(word("a", "s1" to "cie", "s2" to "li", "s3" to "to"), word("b", "s4" to "lin", "s5" to "do"))),
            LyricLine("l2", text = "de amor", words = listOf(word("c", "s6" to "de"), word("d", "s7" to "a", "s8" to "mor"))),
            LyricLine("l3", text = "ay ay", words = listOf(word("e", "s9" to "ay"), word("f", "s10" to "ay"))),
        ),
    )

    private fun take(ids: List<String>, firstBeat: Double, midi: Double) =
        ids.mapIndexed { i, id -> SyllableResult(id, firstBeat + i, 0.8, midi + i, midi + i, listOf(CurvePoint(0.0, midi + i), CurvePoint(1.0, midi + i))) }

    @Test
    fun aVerseLineByLineThenPartlyResungSurvivesSaving() {
        var c = verse
        c = CaptureOps.apply(c, take(listOf("s1", "s2", "s3", "s4", "s5"), 0.0, 60.0), "t1")
        c = CaptureOps.apply(c, take(listOf("s6", "s7", "s8"), 8.5, 62.0), "t2")
        c = CaptureOps.apply(c, take(listOf("s9", "s10"), 16.0, 64.0), "t3")
        // Sing "lindo" again, a bit later and higher.
        c = CaptureOps.apply(c, take(listOf("s4", "s5"), 3.25, 66.0), "t4")
        val s3 = CaptureOps.findSyllable(c, "s3")!!
        assertEquals("t1", s3.takeId)
        assertEquals(2.0, s3.startBeat)
        assertEquals(3.25, CaptureOps.findSyllable(c, "s4")!!.startBeat)
        assertEquals(67.0, CaptureOps.findSyllable(c, "s5")!!.pitchStartMidi)
        assertEquals("t2", CaptureOps.findSyllable(c, "s8")!!.takeId)
        // Saved and loaded (as after a restart): everything is still there.
        val song = Song(id = "x", title = "Cielito", sections = listOf(Section("sec", SectionType.VERSE, contentId = "c")),
            sectionContents = listOf(c), createdAt = 0, updatedAt = 0)
        assertEquals(song, SongJson.decode(SongJson.encode(song)))
    }

    @Test
    fun timedLinesGetRowsFromTheirBars() {
        var c = verse
        // Line 2 sung from beat 9.5 (bar 3 of 4/4) and line 3 from beat 21.
        c = CaptureOps.apply(c, take(listOf("s6", "s7", "s8"), 9.5, 60.0), "t")
        c = CaptureOps.apply(c, take(listOf("s9", "s10"), 21.0, 60.0), "t")
        val rows = SheetLayoutBuilder.rows(c, TimeSignature.FOUR_FOUR)
        assertEquals(listOf(0.0, 8.0, 20.0), rows.map { it.startBeat })
        assertEquals(listOf(8.0, 12.0, 12.0), rows.map { it.lengthBeats })
        // The first sung syllable of line 2 sits at 1.5 beats into its row.
        assertEquals((9.5 - 8.0) / 12.0, rows[1].syllables.first().x, 1e-9)
        // Untimed: even split as before.
        assertEquals(listOf(0.0, 32.0 / 3, 64.0 / 3), SheetLayoutBuilder.rows(verse, TimeSignature.FOUR_FOUR).map { it.startBeat })
        // Only the middle line timed (beat 12): the others share the rest.
        val middle = CaptureOps.apply(verse, take(listOf("s6", "s7", "s8"), 12.0, 60.0), "t")
        assertEquals(listOf(0.0, 12.0, 22.0), SheetLayoutBuilder.rows(middle, TimeSignature.FOUR_FOUR).map { it.startBeat })
    }
}
