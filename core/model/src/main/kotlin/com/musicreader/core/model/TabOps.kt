package com.musicreader.core.model

import kotlin.math.abs

/** Editing tab notes (D-027). Tab notes snap to a quarter-beat grid (16th notes in 4/4). */
object TabOps {
    const val GRID: Double = 0.25
    const val MAX_FRET: Int = 24

    /** Bend amounts offered in the editor, in quarter tones: ¼, ½ and full (a whole tone). */
    val BEND_AMOUNTS: List<Int> = listOf(1, 2, 4)

    /** Durations offered in the editor, in beats. */
    val DURATIONS: List<Double> = listOf(0.25, 0.5, 1.0, 2.0, 4.0)

    fun snap(beat: Double, lengthBeats: Int): Double = ChordOps.snap(beat, lengthBeats, GRID)

    /**
     * The fret after typing [digit] on the number pad. Right after the pad opens ([fresh]) the
     * digit replaces the fret; after that it is appended while the fret stays ≤ 24 (1 then 2 = 12),
     * otherwise it starts a new number (2 then 5 = 5, because 25 is too high).
     */
    fun typeFretDigit(current: Int, digit: Int, fresh: Boolean): Int {
        if (fresh) return digit
        val appended = current * 10 + digit
        return if (current in 1..9 && appended <= MAX_FRET) appended else digit
    }

    /** The fret after the delete key: the last digit is removed (12 → 1, 7 → 0). */
    fun deleteFretDigit(current: Int): Int = current / 10

    /**
     * Adds [note] at its beat (snapped) and string; a note already on that beat and string is
     * replaced. Fret, finger and duration are kept in range.
     */
    fun add(content: SectionContent, note: TabEvent): SectionContent {
        val clean = clean(note, content.lengthBeats)
        val others = content.tab.filterNot { it.beat == clean.beat && it.string == clean.string }
        return content.copy(tab = sorted(others + clean))
    }

    /** Replaces a note with [changed] (same id), keeping values in range. */
    fun update(content: SectionContent, changed: TabEvent): SectionContent {
        if (content.tab.none { it.id == changed.id }) return content
        val clean = clean(changed, content.lengthBeats)
        val others = content.tab.filterNot { it.id == changed.id || (it.beat == clean.beat && it.string == clean.string) }
        return content.copy(tab = sorted(others + clean))
    }

    /** Moves a note to another beat (snapped) and string, replacing a note already there. */
    fun move(content: SectionContent, noteId: String, beat: Double, string: Int): SectionContent {
        val note = content.tab.firstOrNull { it.id == noteId } ?: return content
        return update(content, note.copy(beat = beat, string = string))
    }

    fun delete(content: SectionContent, noteId: String): SectionContent =
        content.copy(tab = content.tab.filterNot { it.id == noteId })

    /**
     * The note on the same string that a hammer-on, pull-off or slide on [note] leads to: the next
     * note on that string, if it starts no later than one beat after [note] ends.
     */
    fun legatoTarget(tab: List<TabEvent>, note: TabEvent): TabEvent? =
        tab.filter { it.string == note.string && it.beat > note.beat }
            .minByOrNull { it.beat }
            ?.takeIf { it.beat <= note.beat + note.durationBeats + 1.0 + 1e-9 }

    private fun clean(note: TabEvent, lengthBeats: Int): TabEvent {
        val beat = snap(note.beat, lengthBeats)
        val maxDuration = (lengthBeats - beat).coerceAtLeast(GRID)
        return note.copy(
            beat = beat,
            durationBeats = note.durationBeats.coerceIn(GRID, maxDuration),
            string = note.string.coerceIn(TabEvent.HIGHEST_STRING, TabEvent.LOWEST_STRING),
            fret = note.fret.coerceIn(0, MAX_FRET),
            finger = note.finger.coerceIn(TabEvent.OPEN, TabEvent.MAX_FINGER),
            bendQuarterTones = note.bendQuarterTones?.takeIf {
                note.technique == Technique.BEND || note.technique == Technique.BEND_RELEASE
            },
            harmonicFret = if (note.technique == Technique.NATURAL_HARMONIC || note.technique == Technique.ARTIFICIAL_HARMONIC) {
                note.harmonicFret ?: note.fret
            } else {
                null
            },
        )
    }

    private fun sorted(tab: List<TabEvent>) = tab.sortedWith(compareBy({ it.beat }, { it.string }))
}

/**
 * Maps beats to x positions with straight lines between known points (knots), for a visual line
 * where lyrics are laid out as text (D-027). Outside the first and last knot, the nearest
 * segment is continued; with one knot, [defaultPxPerBeat] is used.
 */
class BeatAxis(knots: List<Pair<Double, Float>>, private val defaultPxPerBeat: Float = 40f) {
    /** Knots with strictly increasing beats and non-decreasing x. */
    val knots: List<Pair<Double, Float>> = knots.sortedBy { it.first }.fold(mutableListOf<Pair<Double, Float>>()) { acc, k ->
        val last = acc.lastOrNull()
        when {
            last == null -> acc += k
            k.first - last.first > 1e-9 -> acc += (k.first to maxOf(k.second, last.second))
        }
        acc
    }

    fun x(beat: Double): Float {
        if (knots.isEmpty()) return (beat * defaultPxPerBeat).toFloat()
        if (knots.size == 1) return knots[0].second + ((beat - knots[0].first) * defaultPxPerBeat).toFloat()
        val i = segment(beat) { it.first }
        val (b0, x0) = knots[i]
        val (b1, x1) = knots[i + 1]
        return (x0 + (x1 - x0) * ((beat - b0) / (b1 - b0))).toFloat()
    }

    fun beat(x: Float): Double {
        if (knots.isEmpty()) return (x / defaultPxPerBeat).toDouble()
        if (knots.size == 1) return knots[0].first + (x - knots[0].second) / defaultPxPerBeat
        val i = segment(x.toDouble()) { it.second.toDouble() }
        val (b0, x0) = knots[i]
        val (b1, x1) = knots[i + 1]
        if (abs(x1 - x0) < 1e-6) return b0
        return b0 + (b1 - b0) * ((x - x0) / (x1 - x0))
    }

    /** Index of the segment [i, i+1] used for [value]. */
    private fun segment(value: Double, key: (Pair<Double, Float>) -> Double): Int {
        for (i in 0 until knots.size - 1) if (value <= key(knots[i + 1])) return i
        return knots.size - 2
    }
}

/** Undo and redo for a value (the song while editing tab). */
class EditHistory<T>(private val limit: Int = 100) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** Call with the state before an edit. A new edit clears the redo list. */
    fun record(before: T) {
        undo.addLast(before)
        if (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    /** The state to go back to, given the [current] one; null when there is nothing to undo. */
    fun undo(current: T): T? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        return previous
    }

    fun redo(current: T): T? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        return next
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }
}

/** The beats a visual line covers and how beats map to x on it (D-027). */
data class LineAxis(val startBeat: Double, val endBeat: Double, val axis: BeatAxis) {
    /** True when [beat] belongs to this line ([last]: the row's last line also takes its end beat). */
    fun contains(beat: Double, last: Boolean): Boolean =
        beat >= startBeat - 1e-9 && (beat < endBeat - 1e-9 || (last && beat <= endBeat + 1e-9))
}

object LineAxes {
    /**
     * Beat axes for the visual lines of a sheet row ([rowStart], [rowEnd]) whose lyrics were laid
     * out as text: syllable k (at [syllableBeats]`[k]`) was placed at [syllableX]`[k]` on visual line
     * [syllableLine]`[k]`. Line l covers from its first syllable's beat (the row start for the first
     * line) to the next line's first syllable (the row end for the last line). Its axis goes through
     * every syllable on it and reaches [width] at its end beat, so tab notes and chords on the grid
     * line up with the syllables sung at the same beat. Without syllables there is one line from
     * the row start (x 0) to the row end (x [width]).
     */
    fun build(
        rowStart: Double,
        rowEnd: Double,
        syllableBeats: List<Double>,
        syllableX: List<Float>,
        syllableLine: List<Int>,
        width: Float,
    ): List<LineAxis> {
        if (syllableBeats.isEmpty()) {
            return listOf(LineAxis(rowStart, rowEnd, BeatAxis(listOf(rowStart to 0f, rowEnd to width))))
        }
        val lines = syllableLine.max() + 1
        val firstOf = IntArray(lines) { l -> syllableLine.indexOfFirst { it == l } }
        return (0 until lines).map { l ->
            val start = if (l == 0) rowStart else syllableBeats[firstOf[l]]
            val end = if (l == lines - 1) rowEnd else syllableBeats[firstOf[l + 1]]
            val knots = mutableListOf<Pair<Double, Float>>()
            if (l == 0) knots += rowStart to 0f
            syllableBeats.indices.filter { syllableLine[it] == l }.forEach { knots += syllableBeats[it] to syllableX[it] }
            knots += end to width
            LineAxis(start, maxOf(end, start), BeatAxis(knots))
        }
    }
}
