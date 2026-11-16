package com.musicreader.core.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Synthetic sung sequences with known boundaries. A [Sung] note becomes 10 ms frames; between
 * notes there is either silence, a consonant (a level dip, voice continues) or nothing (legato).
 */
class CaptureTest {
    private enum class Join { SILENCE, CONSONANT, LEGATO }

    private data class Sung(val midi: Double, val seconds: Double, val join: Join = Join.SILENCE, val vibrato: Double = 0.0)

    private fun frames(notes: List<Sung>, lead: Double = 0.3, seed: Int = 1): Pair<List<TakeFrame>, List<Pair<Double, Double>>> {
        val rnd = Random(seed)
        val out = mutableListOf<TakeFrame>()
        val truth = mutableListOf<Pair<Double, Double>>()
        var t = 0.0
        fun add(midi: Double?, level: Double) {
            out += TakeFrame(t, midi, level)
            t += 0.01
        }
        repeat((lead * 100).toInt()) { add(null, -70.0) }
        notes.forEachIndexed { i, n ->
            when (n.join) {
                Join.SILENCE -> if (i > 0) repeat(12) { add(null, -70.0) }
                Join.CONSONANT -> repeat(4) { k -> add(notes[i - 1].midi + 0.05, -20.0 - listOf(6.0, 12.0, 12.0, 6.0)[k]) }
                Join.LEGATO -> Unit
            }
            val start = t
            val frames = (n.seconds * 100).toInt()
            repeat(frames) { k ->
                val vib = n.vibrato * sin(2 * PI * 5.5 * k * 0.01)
                add(n.midi + vib + (rnd.nextDouble() - 0.5) * 0.06, -20.0 + (rnd.nextDouble() - 0.5) * 2)
            }
            truth += start to t
        }
        repeat(30) { add(null, -70.0) }
        return out to truth
    }

    @Test
    fun separateNotesAreFoundWithTheirBoundariesAndPitch() {
        val notes = listOf(Sung(60.0, 0.4), Sung(62.0, 0.3), Sung(64.0, 0.5), Sung(60.0, 0.6))
        val (f, truth) = frames(notes)
        val found = NoteSegmenter.segment(f)
        assertEquals(4, found.size)
        found.forEachIndexed { i, n ->
            assertEquals(truth[i].first, n.start, 0.015, "start $i")
            assertEquals(truth[i].second, n.end, 0.015, "end $i")
            assertEquals(notes[i].midi, n.midi, 0.05, "pitch $i")
        }
    }

    @Test
    fun legatoStepsAndConsonantsSplitButVibratoDoesNot() {
        val notes = listOf(
            Sung(57.0, 0.5, vibrato = 0.4),             // ±40 cents vibrato: one note
            Sung(59.0, 0.4, Join.LEGATO),               // a whole tone up, no break
            Sung(59.3, 0.4, Join.CONSONANT),            // same pitch area, a consonant between
            Sung(59.8, 0.4, Join.LEGATO),               // 0.5 semitone: not a new note
        )
        val (f, truth) = frames(notes)
        val found = NoteSegmenter.segment(f)
        assertEquals(3, found.size, found.joinToString { "%.2f-%.2f %.2f".format(it.start, it.end, it.midi) })
        assertEquals(truth[1].first, found[1].start, 0.05)   // the legato step
        assertEquals(truth[2].first, found[2].start, 0.05)   // after the consonant
        assertEquals(57.0, found[0].midi, 0.1)
        assertEquals(59.0, found[1].midi, 0.1)
    }

    @Test
    fun blipsAreDroppedAndShortGapsBridged() {
        val f = mutableListOf<TakeFrame>()
        var t = 0.0
        fun add(m: Double?, level: Double = if (m == null) -70.0 else -20.0) { f += TakeFrame(t, m, level); t += 0.01 }
        repeat(3) { add(70.0) }          // a 30 ms blip
        repeat(10) { add(null) }
        repeat(20) { add(60.0) }
        repeat(2) { add(null, -21.0) }   // the pitch drops out for 20 ms, the voice goes on
        repeat(20) { add(60.0) }
        val found = NoteSegmenter.segment(f)
        assertEquals(1, found.size)
        assertEquals(0.13, found[0].start, 1e-9)
        assertEquals(0.55, found[0].end, 1e-9)
    }

    private fun note(start: Double, end: Double, midi: Double) =
        SungNote(start, end, midi, generateSequence(start) { it + 0.01 }.takeWhile { it < end - 1e-9 }.map { it to midi }.toList())

    @Test
    fun alignmentOneToOne() {
        val notes = listOf(note(0.0, 0.4, 60.0), note(0.5, 0.8, 62.0), note(0.9, 1.5, 64.0))
        val t = SyllableAligner.timings(listOf(1.0, 1.0, 1.0), notes)
        assertEquals(listOf(0.0, 0.5, 0.9), t.map { it!!.start })
        assertEquals(listOf(60.0, 62.0, 64.0), t.map { it!!.startMidi })
    }

    @Test
    fun moreSyllablesThanNotesShareANote() {
        // "de amor" sung as 2 notes: "de" and "a" share the first note.
        val notes = listOf(note(0.0, 0.5, 60.0), note(0.6, 1.1, 64.0))
        val moves = SyllableAligner.align(listOf(1.0, 1.0, 1.0), notes)
        assertEquals(2, moves.size)
        val t = SyllableAligner.timings(listOf(1.0, 1.0, 1.0), notes)
        assertTrue(t.all { it != null })
        // Every syllable has its own time, in order, without overlaps.
        for (k in 1 until t.size) assertTrue(t[k]!!.start >= t[k - 1]!!.end - 1e-9)
        assertEquals(0.0, t[0]!!.start)
        assertEquals(1.1, t[2]!!.end, 1e-9)
    }

    @Test
    fun fewerSyllablesThanNotesSpanSeveral() {
        // One long syllable sung as a melisma over three notes, then one more syllable.
        val notes = listOf(note(0.0, 0.3, 60.0), note(0.3, 0.6, 62.0), note(0.6, 0.9, 64.0), note(1.0, 1.9, 60.0))
        val t = SyllableAligner.timings(listOf(1.0, 1.0), notes)
        assertEquals(0.0, t[0]!!.start)
        assertEquals(0.9, t[0]!!.end, 1e-9)
        assertEquals(60.0, t[0]!!.startMidi)
        assertEquals(64.0, t[0]!!.endMidi, "the melisma ends on its last note")
        assertEquals(1.0, t[1]!!.start)
    }

    @Test
    fun aStrayShortNoteIsSkipped() {
        val notes = listOf(note(0.0, 0.5, 60.0), note(0.55, 0.62, 75.0), note(0.7, 1.2, 62.0))
        val t = SyllableAligner.timings(listOf(1.0, 1.0), notes)
        assertEquals(0.0, t[0]!!.start)
        assertEquals(0.7, t[1]!!.start, 1e-9)
    }

    @Test
    fun alignmentIsAlwaysCompleteAndOrdered() {
        val rnd = Random(4)
        repeat(200) {
            val n = 1 + rnd.nextInt(12)
            val m = 1 + rnd.nextInt(12)
            var t = 0.0
            val notes = List(m) { note(t, (t + 0.1 + rnd.nextDouble() * 0.6).also { t = it + 0.05 }, 55.0 + rnd.nextInt(12)) }
            val weights = List(n) { 1.0 + rnd.nextDouble() * 0.5 }
            val timings = SyllableAligner.timings(weights, notes)
            assertTrue(timings.all { it != null }, "N=$n M=$m")
            for (k in 1 until n) assertTrue(timings[k]!!.start >= timings[k - 1]!!.end - 1e-9, "ordered N=$n M=$m")
        }
        assertEquals(emptyList(), SyllableAligner.align(listOf(1.0), emptyList()))
    }

    @Test
    fun aSungLineEndToEnd() {
        // Seven syllables; "de a" sung on one note, one melisma over two notes.
        val notes = listOf(
            Sung(60.0, 0.35), Sung(62.0, 0.35), Sung(64.0, 0.6),   // 3 syllables
            Sung(62.0, 0.3), Sung(60.0, 0.3, Join.LEGATO),          // a melisma: 1 syllable
            Sung(59.0, 0.5), Sung(60.0, 0.8),                        // 2 syllables... 
        )
        val (f, truth) = frames(notes)
        val found = NoteSegmenter.segment(f)
        assertEquals(7, found.size)
        val weights = List(6) { 1.0 }
        val t = SyllableAligner.timings(weights, found)
        assertEquals(truth[0].first, t[0]!!.start, 0.02)
        assertEquals(truth[3].first, t[3]!!.start, 0.02)
        assertEquals(truth[4].second, t[3]!!.end, 0.02)    // the melisma ends after the 5th note
        assertEquals(62.0, t[3]!!.startMidi!!, 0.1)
        assertEquals(60.0, t[3]!!.endMidi!!, 0.1)
        assertEquals(truth[6].first, t[5]!!.start, 0.02)
    }

    @Test
    fun tapAlongUsesTapsForTimeAndThePitchForNotes() {
        val notes = listOf(Sung(60.0, 0.4), Sung(65.0, 0.4, Join.LEGATO), Sung(67.0, 0.4))
        val (f, truth) = frames(notes)
        val taps = listOf(truth[0].first + 0.02, truth[1].first - 0.01, truth[2].first + 0.03)
        val t = TapAlong.timings(taps, f, count = 4)
        assertEquals(taps[0], t[0]!!.start)
        assertEquals(taps[1], t[0]!!.end, 1e-9, "legato: ends at the next tap")
        assertEquals(60.0, t[0]!!.startMidi!!, 0.1)
        assertEquals(65.0, t[1]!!.startMidi!!, 0.1)
        assertEquals(truth[1].second, t[1]!!.end, 0.02, "ends where the voice stops before the gap")
        assertEquals(67.0, t[2]!!.endMidi!!, 0.1)
        assertEquals(truth[2].second, t[2]!!.end, 0.02)
        assertNull(t[3], "the fourth syllable got no tap")
    }

    @Test
    fun weightsGrowWithLength() {
        assertEquals(1.0, SyllableAligner.weight("de"))
        assertTrue(SyllableAligner.weight("strength") > SyllableAligner.weight("a"))
        assertTrue(abs(SyllableAligner.weight("a") - 1.0) < 1e-9)
    }
}
