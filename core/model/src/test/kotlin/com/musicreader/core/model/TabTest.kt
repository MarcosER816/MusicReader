package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabOpsTest {
    private val empty = SectionContent(id = "s", lengthBeats = 8)
    private fun note(id: String, beat: Double, string: Int, fret: Int, finger: Int = 1, technique: Technique = Technique.NONE, duration: Double = 0.5) =
        TabEvent(id, beat, duration, string, fret, finger, technique)

    @Test
    fun addSnapsToQuarterBeatsAndReplacesTheSameSpot() {
        var c = TabOps.add(empty, note("a", 1.1, 3, 5))
        assertEquals(1.0, c.tab.single().beat)
        c = TabOps.add(c, note("b", 1.12, 3, 7))
        assertEquals(listOf("b"), c.tab.map { it.id })
        c = TabOps.add(c, note("c", 1.0, 2, 5))
        assertEquals(listOf(2, 3), c.tab.map { it.string }) // same beat, sorted by string
        assertEquals(0.75, TabOps.snap(0.7, 8))
        assertEquals(7.75, TabOps.snap(9.0, 8))
    }

    @Test
    fun valuesAreKeptInRange() {
        val c = TabOps.add(empty, note("a", 7.5, 9, 30, finger = 7, duration = 10.0).copy(bendQuarterTones = 4))
        val n = c.tab.single()
        assertEquals(6, n.string)
        assertEquals(24, n.fret)
        assertEquals(4, n.finger)
        assertEquals(0.5, n.durationBeats) // ends at the section end
        assertNull(n.bendQuarterTones) // only bends keep an amount
        val h = TabOps.add(empty, note("h", 0.0, 1, 12, finger = 0, technique = Technique.NATURAL_HARMONIC)).tab.single()
        assertEquals(12, h.harmonicFret)
    }

    @Test
    fun moveUpdateDelete() {
        var c = TabOps.add(empty, note("a", 0.0, 3, 5))
        c = TabOps.add(c, note("b", 2.0, 4, 7))
        c = TabOps.move(c, "a", 2.1, 4) // onto b: replaces it
        assertEquals(listOf(Triple("a", 2.0, 4)), c.tab.map { Triple(it.id, it.beat, it.string) })
        c = TabOps.update(c, c.tab.single().copy(fret = 9, technique = Technique.VIBRATO))
        assertEquals(9 to Technique.VIBRATO, c.tab.single().let { it.fret to it.technique })
        assertEquals(emptyList(), TabOps.delete(c, "a").tab)
        assertEquals(c, TabOps.update(c, note("zzz", 0.0, 1, 1)))
    }

    @Test
    fun numberPadTyping() {
        assertEquals(7, TabOps.typeFretDigit(3, 7, fresh = true))
        assertEquals(12, TabOps.typeFretDigit(1, 2, fresh = false))
        assertEquals(24, TabOps.typeFretDigit(2, 4, fresh = false))
        assertEquals(5, TabOps.typeFretDigit(2, 5, fresh = false)) // 25 is too high
        assertEquals(3, TabOps.typeFretDigit(12, 3, fresh = false)) // already two digits
        assertEquals(0, TabOps.typeFretDigit(0, 0, fresh = false))
        assertEquals(1, TabOps.deleteFretDigit(12))
        assertEquals(0, TabOps.deleteFretDigit(7))
    }

    @Test
    fun legatoTargetIsTheNextNoteOnTheSameString() {
        val tab = listOf(
            note("h", 0.0, 3, 5, technique = Technique.HAMMER_ON),
            note("other", 0.5, 2, 5),
            note("t", 0.5, 3, 7),
            note("far", 4.0, 3, 9),
        )
        assertEquals("t", TabOps.legatoTarget(tab, tab[0])?.id)
        assertNull(TabOps.legatoTarget(tab, tab[2])) // the next note on string 3 is 3 beats later
    }
}

class BeatAxisTest {
    @Test
    fun interpolatesBetweenKnotsAndInverts() {
        val axis = BeatAxis(listOf(0.0 to 0f, 2.0 to 100f, 4.0 to 140f))
        assertEquals(50f, axis.x(1.0))
        assertEquals(120f, axis.x(3.0))
        assertEquals(1.0, axis.beat(50f), 1e-9)
        assertEquals(3.0, axis.beat(120f), 1e-9)
        // Outside the knots, the nearest segment continues.
        assertEquals(160f, axis.x(5.0))
        assertEquals(-50f, axis.x(-1.0))
        for (b in listOf(0.0, 0.25, 1.5, 2.0, 3.75, 4.0)) assertEquals(b, axis.beat(axis.x(b)), 1e-6)
    }

    @Test
    fun duplicateBeatsAndBackwardsXAreCleaned() {
        val axis = BeatAxis(listOf(0.0 to 0f, 1.0 to 40f, 1.0 to 60f, 2.0 to 30f))
        assertEquals(listOf(0.0 to 0f, 1.0 to 40f, 2.0 to 40f), axis.knots)
        assertEquals(1.0, axis.beat(40f), 1e-9)
    }

    @Test
    fun oneOrNoKnots() {
        assertEquals(80f, BeatAxis(emptyList(), defaultPxPerBeat = 40f).x(2.0))
        assertEquals(140f, BeatAxis(listOf(1.0 to 100f), defaultPxPerBeat = 40f).x(2.0))
        assertEquals(1.5, BeatAxis(listOf(1.0 to 100f), defaultPxPerBeat = 40f).beat(120f), 1e-9)
    }
}

class EditHistoryTest {
    @Test
    fun undoRedo() {
        val h = EditHistory<String>()
        assertFalse(h.canUndo)
        h.record("a") // a -> b
        h.record("b") // b -> c
        assertEquals("b", h.undo("c"))
        assertEquals("a", h.undo("b"))
        assertNull(h.undo("a"))
        assertEquals("b", h.redo("a"))
        assertTrue(h.canRedo)
        h.record("b") // a new edit clears redo
        assertFalse(h.canRedo)
        val small = EditHistory<Int>(limit = 2)
        (1..5).forEach { small.record(it) }
        assertEquals(5, small.undo(6))
        assertEquals(4, small.undo(5))
        assertNull(small.undo(4))
    }
}

class TabExportTest {
    private fun n(beat: Double, string: Int, fret: Int, technique: Technique = Technique.NONE, duration: Double = 1.0, bend: Int? = null) =
        TabEvent("$beat-$string", beat, duration, string, fret, 1, technique, bendQuarterTones = bend)

    private val riff = SectionContent(
        id = "s",
        lengthBeats = 8,
        chords = listOf(ChordEvent("c", 0.0, ChordSymbol(NoteLetter.A, suffix = "m"), "A|minor|0")),
        tab = listOf(
            n(0.0, 3, 5, Technique.HAMMER_ON, duration = 0.5),
            n(0.5, 3, 7, duration = 0.5),
            n(1.0, 2, 8, Technique.BEND, bend = 4),
            n(2.0, 1, 5, Technique.VIBRATO, duration = 2.0),
            n(4.0, 4, 7, Technique.SLIDE_UP, duration = 0.5),
            n(4.5, 4, 9, Technique.PULL_OFF, duration = 0.5),
            n(5.0, 4, 7),
            n(6.0, 1, 12, Technique.NATURAL_HARMONIC),
            n(6.0, 6, 0, Technique.MUTE),
        ),
    )

    @Test
    fun alphaTexOfARiff() {
        val tex = TabExport.alphaTex("Riff", riff, TimeSignature.FOUR_FOUR, 96.0)
        assertEquals(
            """
            \title "Riff"
            \tempo 96
            .
            \ts 4 4
            5.3{h}.8 7.3.8 8.2{b (0 4)}.4 5.1{v}.2 | 7.4{sl}.8 9.4{h}.8 7.4.4 (12.1{nh} x.6).4 r.4 |
            """.trimIndent() + "\n",
            tex,
        )
    }

    @Test
    fun alphaTexRestsAndOtherTimeSignatures() {
        val c = SectionContent(id = "s", lengthBeats = 6, tab = listOf(n(2.0, 1, 3, duration = 1.0)))
        val tex = TabExport.alphaTex("x", c, TimeSignature.SIX_EIGHT, 120.0)
        // 6/8: a beat is an eighth (8); two beats of rest = a quarter (4).
        assertTrue(tex.endsWith("\\ts 6 8\nr.4 3.1.8 r.4 r.8 |\n"), tex)
        val empty = TabExport.alphaTex("e", SectionContent("e", 4), TimeSignature.FOUR_FOUR, 60.0)
        assertTrue(empty.endsWith("r.1 |\n"), empty)
    }

    @Test
    fun asciiOfARiff() {
        val ascii = TabExport.ascii(riff, TimeSignature.FOUR_FOUR)
        val lines = ascii.trimEnd('\n').lines()
        assertEquals(7, lines.size)
        assertTrue(lines[0].startsWith("  Am"), lines[0])
        assertTrue(lines[1].startsWith("e|"))
        assertTrue(lines.drop(1).all { it.endsWith("|") })
        assertTrue("5~" in lines[1] && "<12>" in lines[1])
        assertTrue("8b10" in lines[2])
        assertTrue("5h" in lines[3] && "7" in lines[3])
        assertTrue("7/" in lines[4] && "9p" in lines[4])
        assertTrue("x" in lines[6])
        // All six tab lines have the same length and a bar line in the middle.
        assertEquals(1, lines.drop(1).map { it.length }.toSet().size)
        assertEquals(3, lines[1].count { it == '|' })
    }

    @Test
    fun bendLabels() {
        assertEquals("¼", TabExport.bendLabel(1))
        assertEquals("½", TabExport.bendLabel(2))
        assertEquals("full", TabExport.bendLabel(4))
        assertEquals("1½", TabExport.bendLabel(6))
        assertEquals("2", TabExport.bendLabel(8))
    }
}

class LineAxesTest {
    @Test
    fun rowWithoutLyricsIsOneLinearLine() {
        val lines = LineAxes.build(8.0, 16.0, emptyList(), emptyList(), emptyList(), 400f)
        assertEquals(1, lines.size)
        assertEquals(0f, lines[0].axis.x(8.0))
        assertEquals(200f, lines[0].axis.x(12.0))
        assertEquals(400f, lines[0].axis.x(16.0))
        assertTrue(lines[0].contains(16.0, last = true))
        assertFalse(lines[0].contains(16.0, last = false))
    }

    @Test
    fun syllablesSetTheAxisAndLinesSplitAtWraps() {
        // Row 0–8 beats, 5 syllables: three on line 0 (beats 0, 1, 2.5), two on line 1 (beats 4, 6).
        val lines = LineAxes.build(
            0.0, 8.0,
            syllableBeats = listOf(0.0, 1.0, 2.5, 4.0, 6.0),
            syllableX = listOf(0f, 60f, 150f, 0f, 120f),
            syllableLine = listOf(0, 0, 0, 1, 1),
            width = 300f,
        )
        assertEquals(listOf(0.0 to 4.0, 4.0 to 8.0), lines.map { it.startBeat to it.endBeat })
        // A tab note at a syllable's beat lands under that syllable.
        assertEquals(60f, lines[0].axis.x(1.0))
        assertEquals(150f, lines[0].axis.x(2.5))
        assertEquals(120f, lines[1].axis.x(6.0))
        // Between syllables: straight line; at the line's end beat: the right edge.
        assertEquals(30f, lines[0].axis.x(0.5))
        assertEquals(300f, lines[0].axis.x(4.0))
        assertEquals(210f, lines[1].axis.x(7.0))
        // Taps convert back to beats.
        assertEquals(1.75, lines[0].axis.beat(105f), 1e-9)
        assertTrue(lines[1].contains(8.0, last = true))
        assertFalse(lines[0].contains(4.0, last = false))
    }
}
