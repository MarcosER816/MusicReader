package com.musicreader.core.dsp

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** One 10 ms pitch frame of a take, already moved to the heard timeline (latency removed). */
data class TakeFrame(
    /** Seconds on the take's timeline. */
    val seconds: Double,
    /** Continuous MIDI; null when unvoiced. */
    val midi: Double?,
    val levelDb: Double,
)

/** A sung note found in a take. */
data class SungNote(
    val start: Double,
    val end: Double,
    /** Median of the stable part (the middle 60 % of the note). */
    val midi: Double,
    /** The voiced frames of the note (seconds, midi). */
    val curve: List<Pair<Double, Double>>,
) {
    val duration: Double get() = end - start
}

/** Timing and pitch for one syllable, on the take's timeline (seconds). */
data class SyllableTiming(
    val start: Double,
    val end: Double,
    /** Median of the stable part of the first note; null if nothing voiced was sung there. */
    val startMidi: Double?,
    /** Median of the stable part of the last note. */
    val endMidi: Double?,
    val curve: List<Pair<Double, Double>>,
)

/** Median of the middle 60 % of [values] (the stable part); of all of them if there are few. */
internal fun stableMedian(values: List<Double>): Double? {
    if (values.isEmpty()) return null
    val core = if (values.size >= 5) values.subList(values.size / 5, values.size - values.size / 5) else values
    val s = core.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

private fun median(values: List<Double>): Double {
    val s = values.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

/**
 * Finds the sung notes in a take (D-031): voiced regions, split where the level dips and rises
 * again (a consonant between legato syllables) and where the pitch steps by more than
 * [STEP_SEMITONES] between two stable stretches. Vibrato does not split: the stretches are
 * medians over [STABLE_SECONDS].
 */
object NoteSegmenter {
    const val HOP_SECONDS = 0.01
    /** Unvoiced gaps up to this long inside a note are bridged (if the pitch goes on). */
    const val BRIDGE_SECONDS = 0.03
    const val MIN_NOTE_SECONDS = 0.06
    const val ONSET_DB = 6.0
    const val STEP_SEMITONES = 0.7
    const val STABLE_SECONDS = 0.15

    fun segment(frames: List<TakeFrame>): List<SungNote> {
        val notes = mutableListOf<SungNote>()
        for (run in voicedRuns(frames)) {
            for (piece in split(run)) {
                val duration = piece.last().seconds - piece.first().seconds + HOP_SECONDS
                if (duration < MIN_NOTE_SECONDS) continue
                val midis = piece.mapNotNull { it.midi }
                notes += SungNote(
                    start = piece.first().seconds,
                    end = piece.last().seconds + HOP_SECONDS,
                    midi = stableMedian(midis)!!,
                    curve = piece.filter { it.midi != null }.map { it.seconds to it.midi!! },
                )
            }
        }
        return notes
    }

    /** Runs of voiced frames; short gaps are bridged when the pitch on both sides agrees. */
    private fun voicedRuns(frames: List<TakeFrame>): List<List<TakeFrame>> {
        val runs = mutableListOf<MutableList<TakeFrame>>()
        var current: MutableList<TakeFrame>? = null
        var gap = mutableListOf<TakeFrame>()
        val maxGap = (BRIDGE_SECONDS / HOP_SECONDS).toInt()
        for (f in frames) {
            if (f.midi != null) {
                val run = current
                if (run != null && gap.isNotEmpty()) {
                    val lastMidi = run.last { it.midi != null }.midi!!
                    if (gap.size <= maxGap && abs(f.midi - lastMidi) < 1.0) {
                        run += gap
                    } else {
                        current = null
                    }
                }
                gap = mutableListOf()
                val target = current ?: mutableListOf<TakeFrame>().also { runs += it; current = it }
                target += f
            } else if (current != null) {
                gap += f
                if (gap.size > maxGap) {
                    current = null
                    gap = mutableListOf()
                }
            }
        }
        return runs
    }

    /** Splits one voiced run at level dips and stable pitch steps. */
    private fun split(run: List<TakeFrame>): List<List<TakeFrame>> {
        val n = run.size
        val minFrames = (MIN_NOTE_SECONDS / HOP_SECONDS).toInt()
        val cuts = sortedSetOf<Int>()

        // Level dips: a local minimum with a 6 dB rise on both sides within 80 ms.
        val reach = 8
        for (j in 1 until n - 1) {
            val l = run[j].levelDb
            if (l > run[j - 1].levelDb || l > run[j + 1].levelDb) continue
            val before = (max(0, j - reach) until j).maxOf { run[it].levelDb }
            val after = (j + 1..min(n - 1, j + reach)).maxOf { run[it].levelDb }
            if (before - l >= ONSET_DB && after - l >= ONSET_DB) cuts += j
        }

        // Pitch steps: medians of the stretch before and after each frame.
        val w = (STABLE_SECONDS / HOP_SECONDS).toInt()
        val midi = DoubleArray(n) { run[it].midi ?: Double.NaN }
        val step = DoubleArray(n)
        for (j in 1 until n) {
            val left = (max(0, j - w) until j).map { midi[it] }.filter { !it.isNaN() }
            val right = (j until min(n, j + w)).map { midi[it] }.filter { !it.isNaN() }
            if (left.size < minFrames / 2 || right.size < minFrames / 2) continue
            step[j] = abs(median(right) - median(left))
        }
        // Each stretch of frames where the step exceeds the threshold is one boundary. The cut
        // goes where the pitch crosses to the new note's side (the step measure itself is flat
        // over the window around the boundary).
        var j = 1
        while (j < n) {
            if (step[j] <= STEP_SEMITONES) {
                j++
                continue
            }
            val regionStart = j
            while (j < n && step[j] > STEP_SEMITONES) j++
            val regionEnd = j - 1
            val peak = (regionStart..regionEnd).maxByOrNull { step[it] }!!
            val left = (max(0, peak - w) until peak).map { midi[it] }.filter { !it.isNaN() }
            val right = (peak until min(n, peak + w)).map { midi[it] }.filter { !it.isNaN() }
            val mL = median(left)
            val mR = median(right)
            val cut = (regionStart..regionEnd).firstOrNull { k ->
                val a = midi[k]
                val b = if (k + 1 < n) midi[k + 1] else a
                !a.isNaN() && !b.isNaN() && abs(a - mR) < abs(a - mL) && abs(b - mR) < abs(b - mL)
            } ?: peak
            cuts += cut
        }

        // Drop cuts that would leave too short a piece.
        val kept = mutableListOf<Int>()
        var last = 0
        for (c in cuts) {
            if (c - last >= minFrames && n - c >= minFrames) {
                kept += c
                last = c
            }
        }
        val pieces = mutableListOf<List<TakeFrame>>()
        var from = 0
        for (c in kept) {
            pieces += run.subList(from, c)
            from = c
        }
        pieces += run.subList(from, n)
        return pieces
    }
}

/**
 * Assigns N syllables, in order, to M sung notes by dynamic programming (D-031). Moves:
 * one syllable on one note; one syllable over 2..k notes (a melisma); 2..k syllables in one
 * note (sung together, e.g. "de amor"); and skipping a stray note. Each move costs the squared
 * log ratio between the expected and the sung duration, plus a penalty for splits, merges and
 * skips. Every syllable always gets a place when at least one note was sung.
 */
object SyllableAligner {
    const val SPLIT_PENALTY = 0.8
    const val MERGE_PENALTY = 1.0
    const val SKIP_PENALTY = 1.5

    sealed interface Move {
        /** Syllable [syllable] covers notes [firstNote]..[lastNote]. */
        data class Notes(val syllable: Int, val firstNote: Int, val lastNote: Int) : Move
        /** Syllables [firstSyllable]..[lastSyllable] share note [note]. */
        data class Shared(val firstSyllable: Int, val lastSyllable: Int, val note: Int) : Move
        data class Skip(val note: Int) : Move
    }

    /** The cheapest moves for syllables with relative lengths [weights]; empty if no notes. */
    fun align(weights: List<Double>, notes: List<SungNote>): List<Move> {
        val n = weights.size
        val m = notes.size
        if (n == 0 || m == 0) return emptyList()
        val sung = notes.sumOf { it.duration }
        val unit = sung / weights.sum()
        val maxSplit = max(3, ceil(m.toDouble() / n).toInt() + 1)
        val maxMerge = max(3, ceil(n.toDouble() / m).toInt() + 1)
        val meanNote = sung / m

        fun durCost(expected: Double, actual: Double): Double {
            val r = ln(max(actual, 1e-3) / max(expected, 1e-3))
            return r * r
        }

        val inf = Double.POSITIVE_INFINITY
        val cost = Array(n + 1) { DoubleArray(m + 1) { inf } }
        val back = Array(n + 1) { arrayOfNulls<Move>(m + 1) }
        cost[0][0] = 0.0
        for (i in 0..n) {
            for (j in 0..m) {
                val c = cost[i][j]
                if (c == inf) continue
                // Skip note j (stray sound); a long note is expensive to skip.
                if (j < m) {
                    val s = c + SKIP_PENALTY * (1.0 + notes[j].duration / meanNote)
                    if (s < cost[i][j + 1]) {
                        cost[i][j + 1] = s
                        back[i][j + 1] = Move.Skip(j)
                    }
                }
                if (i == n) continue
                // Syllable i over notes j..j+k-1.
                for (k in 1..maxSplit) {
                    if (j + k > m) break
                    val span = notes[j + k - 1].end - notes[j].start
                    val s = c + durCost(weights[i] * unit, span) + SPLIT_PENALTY * (k - 1)
                    if (s < cost[i + 1][j + k]) {
                        cost[i + 1][j + k] = s
                        back[i + 1][j + k] = Move.Notes(i, j, j + k - 1)
                    }
                }
                // Syllables i..i+k-1 share note j.
                if (j < m) {
                    for (k in 2..maxMerge) {
                        if (i + k > n) break
                        val expected = (i until i + k).sumOf { weights[it] } * unit
                        val s = c + durCost(expected, notes[j].duration) + MERGE_PENALTY * (k - 1)
                        if (s < cost[i + k][j + 1]) {
                            cost[i + k][j + 1] = s
                            back[i + k][j + 1] = Move.Shared(i, i + k - 1, j)
                        }
                    }
                }
            }
        }
        if (cost[n][m] == inf) return emptyList()
        val moves = ArrayDeque<Move>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            val mv = back[i][j] ?: break
            moves.addFirst(mv)
            when (mv) {
                is Move.Skip -> j -= 1
                is Move.Notes -> {
                    i -= 1
                    j = mv.firstNote
                }
                is Move.Shared -> {
                    i = mv.firstSyllable
                    j -= 1
                }
            }
        }
        return moves.toList()
    }

    /** Timing and pitch per syllable from the moves (index = syllable). */
    fun timings(weights: List<Double>, notes: List<SungNote>): List<SyllableTiming?> {
        val out = arrayOfNulls<SyllableTiming>(weights.size)
        for (mv in align(weights, notes)) {
            when (mv) {
                is Move.Skip -> Unit
                is Move.Notes -> {
                    val first = notes[mv.firstNote]
                    val last = notes[mv.lastNote]
                    out[mv.syllable] = SyllableTiming(
                        start = first.start,
                        end = last.end,
                        startMidi = first.midi,
                        endMidi = last.midi,
                        curve = (mv.firstNote..mv.lastNote).flatMap { notes[it].curve },
                    )
                }
                is Move.Shared -> {
                    // The note's time is shared by the syllables in proportion to their lengths.
                    val note = notes[mv.note]
                    val total = (mv.firstSyllable..mv.lastSyllable).sumOf { weights[it] }
                    var t = note.start
                    for (s in mv.firstSyllable..mv.lastSyllable) {
                        val end = if (s == mv.lastSyllable) note.end else t + note.duration * weights[s] / total
                        val slice = note.curve.filter { it.first >= t - 1e-9 && it.first < end - 1e-9 }
                        val m = stableMedian(slice.map { it.second }) ?: note.midi
                        out[s] = SyllableTiming(t, end, m, m, slice)
                        t = end
                    }
                }
            }
        }
        return out.toList()
    }

    /** How long a syllable is expected to be relative to the others: longer text, a bit longer. */
    fun weight(text: String): Double = 1.0 + 0.1 * (text.count { it.isLetter() } - 2).coerceIn(0, 6)
}

/**
 * The "tap along" fallback (D-031): the singer taps once at the start of each syllable. Taps
 * give the starts; a syllable ends at the next tap or where the voice stops (a gap of 60 ms or
 * more); its notes come from the pitch frames in that span.
 */
object TapAlong {
    const val END_GAP_SECONDS = 0.06

    /** One timing per tap, for the first [count] syllables (null where a syllable got no tap). */
    fun timings(taps: List<Double>, frames: List<TakeFrame>, count: Int): List<SyllableTiming?> {
        val sorted = taps.sorted()
        return List(count) { k ->
            if (k >= sorted.size) return@List null
            val start = sorted[k]
            val limit = sorted.getOrNull(k + 1) ?: (frames.lastOrNull()?.seconds ?: start) + NoteSegmenter.HOP_SECONDS
            // End where the voice stops after the tap (or at the next tap).
            var end = limit
            var lastVoiced: Double? = null
            for (f in frames) {
                if (f.seconds < start) continue
                if (f.seconds >= limit) break
                if (f.midi != null) {
                    lastVoiced = f.seconds
                } else if (lastVoiced != null && f.seconds - lastVoiced >= END_GAP_SECONDS) {
                    end = lastVoiced + NoteSegmenter.HOP_SECONDS
                    break
                }
            }
            if (lastVoiced != null && end == limit && sorted.getOrNull(k + 1) == null) end = lastVoiced + NoteSegmenter.HOP_SECONDS
            spanTiming(start, max(end, start + NoteSegmenter.HOP_SECONDS), frames)
        }
    }

    /**
     * Timing and notes of a syllable sung from [start] to [end]: the first and last note found
     * in that span give its two notes (the same rule as a sung take). Also used when a
     * boundary is nudged in the review.
     */
    fun spanTiming(start: Double, end: Double, frames: List<TakeFrame>): SyllableTiming {
        val span = frames.filter { it.seconds >= start && it.seconds < end }
        val curve = span.filter { it.midi != null }.map { it.seconds to it.midi!! }
        val notes = NoteSegmenter.segment(span)
        val all = stableMedian(curve.map { it.second })
        return SyllableTiming(
            start = start,
            end = end,
            startMidi = notes.firstOrNull()?.midi ?: all,
            endMidi = notes.lastOrNull()?.midi ?: all,
            curve = curve,
        )
    }
}
