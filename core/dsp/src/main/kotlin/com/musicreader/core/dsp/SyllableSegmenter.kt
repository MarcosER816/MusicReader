package com.musicreader.core.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * Consonant evidence from the recording (D-031, round 2 of Stage 10): per 10 ms frame, how much
 * the sound looks like a consonant: hiss ("s", "f", "ch": high-frequency energy dominates) or a
 * burst ("t", "k", "p": high-frequency energy jumps up). 0..1.
 */
object ConsonantCue {
    const val HOP_SECONDS = 0.01

    /**
     * [samples] start at take time [startSeconds]; returns (seconds, cue) per frame. The
     * high-frequency part is the first difference (x[n] − x[n−1]), which weighs energy by frequency.
     */
    fun compute(samples: FloatArray, sampleRate: Int, startSeconds: Double): List<Pair<Double, Double>> {
        val hop = (HOP_SECONDS * sampleRate).toInt()
        if (samples.size < hop * 2) return emptyList()
        val frames = samples.size / hop
        val hf = DoubleArray(frames)
        val ratio = DoubleArray(frames)
        for (f in 0 until frames) {
            var e = 0.0
            var d = 0.0
            for (i in f * hop until (f + 1) * hop) {
                val x = samples[i].toDouble()
                val prev = if (i > 0) samples[i - 1].toDouble() else x
                e += x * x
                d += (x - prev) * (x - prev)
            }
            hf[f] = d / hop
            // For a pure tone the ratio is (2·sin(πf/sr))², tiny for sung notes; for noise it is 2.
            ratio[f] = if (e > 1e-10) d / e else 0.0
        }
        val floor = hf.sorted()[frames / 5].coerceAtLeast(1e-10)
        return (0 until frames).map { f ->
            val loudEnough = hf[f] > floor * 10
            val hiss = if (loudEnough) ((ratio[f] - 0.3) / 0.7).coerceIn(0.0, 1.0) else 0.0
            val before = (max(0, f - 3) until f).maxOfOrNull { hf[it] } ?: hf[f]
            val rise = 10 * log10((hf[f] + 1e-12) / (before + 1e-12))
            val burst = if (loudEnough) ((rise - 6.0) / 10.0).coerceIn(0.0, 1.0) else 0.0
            (startSeconds + f * HOP_SECONDS) to max(hiss, burst)
        }
    }
}

/**
 * Splits a sung take into exactly the N selected syllables (D-031, Stage 10 round 2). Instead of
 * finding notes first and matching syllables to them, it scores every possible boundary between
 * 10 ms frames:
 * - a silence (any length; a long breath almost always is a boundary);
 * - a small level dip (a voiced consonant like "l", "m", "n": from 2.5 dB);
 * - a pitch step (from half a semitone between the 80 ms before and after);
 * - consonant noise in the recording ([ConsonantCue]);
 *
 * then chooses the N − 1 boundaries that agree best with those clues while keeping the syllables'
 * lengths plausible (dynamic programming; the expected lengths come from the syllables' text).
 * Each syllable starts at its first voiced frame and ends after its last, so silences stay gaps.
 */
object SyllableSegmenter {
    private const val HOP = 0.01
    /** Weight of the length match against the boundary clues. */
    const val LENGTH_WEIGHT = 0.6
    /** A silence this long is a breath: nearly always a boundary. */
    const val BREATH_SECONDS = 0.2
    const val MIN_SYLLABLE_SECONDS = 0.04

    private class Candidate(val endIndex: Int, val startIndex: Int, val score: Double)

    fun timings(frames: List<TakeFrame>, weights: List<Double>, cues: List<Pair<Double, Double>> = emptyList()): List<SyllableTiming?> {
        val n = weights.size
        if (n == 0) return emptyList()
        // The sung span: drop isolated blips (under 60 ms of voice) at the edges.
        val voicedIdx = frames.indices.filter { frames[it].midi != null }
        if (voicedIdx.isEmpty()) return List(n) { null }
        val runs = runsOf(voicedIdx)
        val kept = runs.filter { (a, b) -> (b - a + 1) * HOP >= 0.06 }.ifEmpty { runs }
        val first = kept.first().first
        val last = kept.last().second
        val span = frames.subList(first, last + 1)
        val m = span.size

        // Boundary clues between frame k−1 and k (k in 1 until m).
        val cueAt = DoubleArray(m)
        if (cues.isNotEmpty()) {
            var c = 0
            for (k in 0 until m) {
                val t = span[k].seconds
                while (c + 1 < cues.size && cues[c + 1].first <= t + 1e-9) c++
                if (abs(cues[c].first - t) < HOP) cueAt[k] = cues[c].second
            }
        }
        val candidates = mutableListOf<Candidate>()
        // Silences: the previous syllable ends where the gap starts, the next begins after it.
        var k = 1
        while (k < m) {
            if (span[k].midi == null) {
                val a = k
                while (k < m && span[k].midi == null) k++
                val len = (k - a) * HOP
                val score = 1.0 + min(len, BREATH_SECONDS) / BREATH_SECONDS * 2.0
                candidates += Candidate(a, k, score)
            } else {
                k++
            }
        }
        val gapFrames = candidates.flatMap { (it.endIndex until it.startIndex).toList() }.toHashSet()
        // Voiced clues, each placed where the syllable starts: a dip at its first falling frame,
        // a pitch step where the pitch crosses to the new note, consonant noise at its peak.
        // Clues within 20 ms of each other add up (a consonant often brings two).
        val dip = DoubleArray(m)
        val pitch = DoubleArray(m)
        for (j in 1 until m) {
            if (j in gapFrames || (j - 1) in gapFrames) continue
            levelDip(span, j)?.let { (at, s) -> if (at !in gapFrames) dip[at] = max(dip[at], s) }
        }
        var j = 1
        while (j < m) {
            val st = pitchStep(span, j)
            if (st <= 0.0) {
                j++
                continue
            }
            // One step region: refine to the crossing.
            val a = j
            var peak = st
            while (j < m && pitchStep(span, j) > 0.0) {
                peak = max(peak, pitchStep(span, j))
                j++
            }
            crossing(span, a, j - 1)?.let { at -> if (at !in gapFrames) pitch[at] = max(pitch[at], peak) }
        }
        fun near(a: DoubleArray, k: Int) = (max(0, k - 2)..min(m - 1, k + 2)).maxOf { a[it] }
        val score = DoubleArray(m)
        for (k in 1 until m) {
            if (k in gapFrames || (k - 1) in gapFrames) continue
            val own = max(dip[k], max(pitch[k], cueAt[k]))
            if (own <= 0.0) continue
            score[k] = 0.9 * near(dip, k) + 0.9 * near(cueAt, k) + 0.5 * near(pitch, k)
        }
        for (k in 1 until m) {
            if (score[k] <= 0.05) continue
            val lo = max(1, k - 4)
            val hi = min(m - 1, k + 4)
            if ((lo..hi).all { score[it] <= score[k] } && (lo until k).all { score[it] < score[k] }) candidates += Candidate(k, k, score[k])
        }
        // Weak fallback places every 40 ms, so N syllables always fit (e.g. two in one note).
        val taken = candidates.map { it.endIndex }.toHashSet()
        for (j in 4 until m - 3 step 4) if (j !in gapFrames && j !in taken) candidates += Candidate(j, j, 0.0)
        candidates.sortBy { it.endIndex }

        // Syllable lengths: voiced time between two boundaries.
        val totalVoiced = span.count { it.midi != null } * HOP
        val unit = totalVoiced / weights.sum()
        fun voiced(from: Int, to: Int): Double = (from until to).count { span[it].midi != null } * HOP
        fun lenCost(i: Int, from: Int, to: Int): Double {
            val v = voiced(from, to)
            if (v < MIN_SYLLABLE_SECONDS) return 1e6
            val r = ln(v / (weights[i] * unit))
            return LENGTH_WEIGHT * r * r
        }

        // DP over (syllables placed, last boundary). Boundary −1 = the span start.
        val c = candidates.size
        val inf = Double.POSITIVE_INFINITY
        val best = Array(n) { DoubleArray(c + 1) { inf } }
        val from = Array(n) { IntArray(c + 1) { -2 } }
        // best[i][b]: syllables 0..i placed, syllable i ends at candidate b (index c = span end).
        fun startOf(b: Int) = if (b < 0) 0 else candidates[b].startIndex
        fun endOf(b: Int) = if (b >= c) m else candidates[b].endIndex
        for (b in 0..c) {
            if (n == 1 && b != c) continue
            val cost = lenCost(0, 0, endOf(b)) - (if (b < c) candidates[b].score else 0.0)
            if (cost < best[0][b]) {
                best[0][b] = cost
                from[0][b] = -1
            }
        }
        for (i in 1 until n) {
            for (b in 0..c) {
                if (i == n - 1 && b != c) continue
                val gain = if (b < c) candidates[b].score else 0.0
                for (p in 0 until min(b, c)) {
                    val prev = best[i - 1][p]
                    if (prev == inf) continue
                    if (startOf(p) >= endOf(b)) continue
                    val cost = prev + lenCost(i, startOf(p), endOf(b)) - gain
                    if (cost < best[i][b]) {
                        best[i][b] = cost
                        from[i][b] = p
                    }
                }
            }
        }
        if (best[n - 1][c] == inf) return List(n) { null }
        val ends = IntArray(n)
        var b = c
        for (i in n - 1 downTo 0) {
            ends[i] = b
            b = from[i][b]
        }
        return (0 until n).map { i ->
            val s = startOf(if (i == 0) -1 else ends[i - 1])
            val e = endOf(ends[i])
            // Trim to the voice: the syllable starts at its first voiced frame.
            val firstVoiced = (s until e).firstOrNull { span[it].midi != null } ?: return@map null
            val lastVoiced = (s until e).last { span[it].midi != null }
            TapAlong.spanTiming(span[firstVoiced].seconds, span[lastVoiced].seconds + HOP, span)
        }
    }

    private fun runsOf(idx: List<Int>): List<Pair<Int, Int>> {
        val runs = mutableListOf<Pair<Int, Int>>()
        var a = idx.first()
        var p = a
        for (i in idx.drop(1)) {
            if (i != p + 1) {
                runs += a to p
                a = i
            }
            p = i
        }
        runs += a to p
        return runs
    }

    /**
     * A level dip with its minimum at [j] and a rise on both sides (1.5 dB → 0, 4.5 dB → 1):
     * the frame where it starts falling and the strength; null if [j] is no dip.
     */
    private fun levelDip(span: List<TakeFrame>, j: Int): Pair<Int, Double>? {
        val l = span[j].levelDb
        if (j == 0 || j + 1 >= span.size || l > span[j - 1].levelDb || l > span[j + 1].levelDb) return null
        val before = (max(0, j - 8) until j).maxOf { span[it].levelDb }
        val after = (j + 1..min(span.size - 1, j + 8)).maxOf { span[it].levelDb }
        val depth = min(before, after) - l
        val strength = ((depth - 1.5) / 3.0).coerceIn(0.0, 1.0)
        if (strength <= 0.0) return null
        // Back to where it starts to fall.
        var at = j
        while (at > 1 && span[at - 1].levelDb < before - 1.5) at--
        return at to strength
    }

    /** The first frame in [from]..[to] (and a little after) that is on the new note's side. */
    private fun crossing(span: List<TakeFrame>, from: Int, to: Int): Int? {
        val left = (max(0, from - 5) until from).mapNotNull { span[it].midi }
        val right = (to + 1..min(span.size - 1, to + 5)).mapNotNull { span[it].midi }
        if (left.isEmpty() || right.isEmpty()) return null
        val mL = left.sorted()[left.size / 2]
        val mR = right.sorted()[right.size / 2]
        return (from..min(span.size - 2, to + 5)).firstOrNull { k ->
            val a = span[k].midi
            val b = span[k + 1].midi
            a != null && b != null && abs(a - mR) < abs(a - mL) && abs(b - mR) < abs(b - mL)
        }
    }

    /** 0..1: the pitch step between the 50 ms before [j] and after (0.5 semitone → 0, 1.5 → 1). */
    private fun pitchStep(span: List<TakeFrame>, j: Int): Double {
        val left = (max(0, j - 5) until j).mapNotNull { span[it].midi }
        val right = (j until min(span.size, j + 5)).mapNotNull { span[it].midi }
        if (left.size < 3 || right.size < 3) return 0.0
        val step = abs(right.sorted()[right.size / 2] - left.sorted()[left.size / 2])
        return (step - 0.5).coerceIn(0.0, 1.0)
    }
}
