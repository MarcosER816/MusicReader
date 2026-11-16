package com.musicreader.core.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mark, Stage 10 round 2: "it confused the syllables they belonged to". Sung phrases with the
 * consonants real singing has (a soft "l"/"m", a short "t" closure, an "s" hiss), as audio plus
 * the pitch frames the tracker would give, with known syllable starts.
 */
class SyllableSegmenterTest {
    private val rate = 48000

    private sealed interface Part {
        val seconds: Double
        data class Vowel(val midi: Double, override val seconds: Double, val gain: Double = 0.3) : Part
        /** A voiced consonant ("l", "m", "n"): same pitch, a little softer. */
        data class Soft(val midi: Double, override val seconds: Double, val dropDb: Double) : Part
        /** A stop ("t", "k", "p"): a short silence. */
        data class Stop(override val seconds: Double) : Part
        /** A fricative ("s", "f"): noise, no pitch. */
        data class Hiss(override val seconds: Double, val gain: Double = 0.08) : Part
    }

    private class Phrase(val audio: FloatArray, val frames: List<TakeFrame>, val starts: List<Double>)

    /** [syllables]: each syllable is a list of parts; it "starts" at its first Vowel or Soft part. */
    private fun phrase(syllables: List<List<Part>>, seed: Int = 1): Phrase {
        val rnd = Random(seed)
        val lead = 0.3
        val total = lead + syllables.sumOf { s -> s.sumOf { it.seconds } } + 0.4
        val audio = FloatArray((total * rate).toInt())
        val voiced = DoubleArray(audio.size / 480) { Double.NaN }
        var t = lead
        var phase = 0.0
        val starts = mutableListOf<Double>()
        for (syl in syllables) {
            var started = false
            for (p in syl) {
                val from = (t * rate).toInt()
                val to = ((t + p.seconds) * rate).toInt()
                when (p) {
                    is Part.Vowel, is Part.Soft -> {
                        if (!started) {
                            starts += t
                            started = true
                        }
                        val midi = if (p is Part.Vowel) p.midi else (p as Part.Soft).midi
                        val gain = if (p is Part.Vowel) p.gain else 0.3 * 10.0.pow(-(p as Part.Soft).dropDb / 20)
                        val hz = 440.0 * 2.0.pow((midi - 69) / 12)
                        for (i in from until minOf(to, audio.size)) {
                            phase += 2 * PI * hz / rate
                            audio[i] += (gain * (sin(phase) + 0.5 * sin(2 * phase) + 0.25 * sin(3 * phase))).toFloat()
                        }
                        for (f in from / 480 until minOf(to / 480, voiced.size)) voiced[f] = midi + (rnd.nextDouble() - 0.5) * 0.06
                    }
                    is Part.Stop -> Unit
                    is Part.Hiss -> for (i in from until minOf(to, audio.size)) audio[i] += ((rnd.nextDouble() * 2 - 1) * p.gain).toFloat()
                }
                t += p.seconds
            }
        }
        for (i in audio.indices) audio[i] += ((rnd.nextDouble() * 2 - 1) * 0.0005).toFloat()
        // Frames as the tracker reports them: level of the newest 20 ms, pitch where voiced.
        val frames = voiced.indices.map { f ->
            val end = minOf(audio.size, (f + 1) * 480)
            val begin = maxOf(0, end - 960)
            var e = 0.0
            for (i in begin until end) e += audio[i].toDouble() * audio[i]
            val rms = sqrt(e / (end - begin))
            TakeFrame(f * 0.01, voiced[f].takeIf { !it.isNaN() }, if (rms > 1e-6) 20 * log10(rms) else -120.0)
        }
        return Phrase(audio, frames, starts)
    }

    private fun check(p: Phrase, weights: List<Double>, toleranceSeconds: Double = 0.03, useAudio: Boolean = true) {
        val cues = if (useAudio) ConsonantCue.compute(p.audio, rate, 0.0) else emptyList()
        val t = SyllableSegmenter.timings(p.frames, weights, cues)
        assertEquals(p.starts.size, t.size)
        t.forEachIndexed { i, s ->
            assertTrue(s != null, "syllable $i")
            assertTrue(abs(s.start - p.starts[i]) <= toleranceSeconds, "syllable $i starts at ${s.start}, sung at ${p.starts[i]}")
        }
        for (i in 1 until t.size) assertTrue(t[i]!!.start >= t[i - 1]!!.end - 1e-9, "in order, no overlap")
    }

    private fun v(midi: Double, s: Double) = Part.Vowel(midi, s)

    @Test
    fun legatoLaLaLaWithSoftLs() {
        // "la la la la" on one pitch: the only clue is a 3 dB softer "l" (old method: one note).
        val syl = List(4) { i -> listOf(Part.Soft(62.0, 0.05, 3.0), v(62.0, 0.3 + i * 0.05)) }
        check(phrase(syl), List(4) { 1.0 })
    }

    @Test
    fun shortStopsBetweenSyllables() {
        // "te to ta": 25 ms closures (old method bridged them as dropouts).
        val syl = listOf(listOf(v(60.0, 0.35)), listOf(Part.Stop(0.025), v(60.0, 0.35)), listOf(Part.Stop(0.025), v(60.2, 0.4)))
        check(phrase(syl), List(3) { 1.0 })
    }

    @Test
    fun fricativesBetweenVowels() {
        // "a-sa-se-so": hiss between the vowels, the pitch steps a little.
        val syl = listOf(listOf(v(64.0, 0.3)), listOf(Part.Hiss(0.07), v(64.0, 0.3)), listOf(Part.Hiss(0.06), v(65.0, 0.3)), listOf(Part.Hiss(0.08), v(64.0, 0.45)))
        check(phrase(syl), List(4) { 1.0 })
    }

    @Test
    fun aMixedLineWithAMelismaAndABreath() {
        // "Cie-li-to (breath) lin-do": "to" sung as a melisma over two notes, "m"-like softness.
        val syl = listOf(
            listOf(Part.Hiss(0.05), v(64.0, 0.3)),
            listOf(Part.Soft(65.0, 0.04, 3.5), v(65.0, 0.3)),
            listOf(Part.Stop(0.03), v(67.0, 0.3), v(65.0, 0.3)),          // melisma: one syllable
            listOf(Part.Stop(0.3), Part.Soft(64.0, 0.05, 4.0), v(64.0, 0.35)),
            listOf(Part.Stop(0.03), v(62.0, 0.6)),
        )
        check(phrase(syl), List(5) { 1.0 })
    }

    @Test
    fun twoSyllablesSungAsOneNoteShareIt() {
        // "de a-mor": "de" and "a" on one vowel with no clue between them.
        val syl = listOf(listOf(v(60.0, 0.6)), listOf(Part.Soft(62.0, 0.05, 3.0), v(62.0, 0.5)))
        val p = phrase(syl)
        val t = SyllableSegmenter.timings(p.frames, listOf(1.0, 1.0, 1.0), ConsonantCue.compute(p.audio, rate, 0.0))
        assertTrue(t.all { it != null })
        assertEquals(p.starts[0], t[0]!!.start, 0.03)
        assertTrue(t[1]!!.start > t[0]!!.start + 0.15 && t[1]!!.start < p.starts[1], "the shared note is split")
        assertEquals(p.starts[1], t[2]!!.start, 0.03)
    }

    @Test
    fun theOldNoteFirstMethodMissedTheseBoundaries() {
        // Documents the improvement: on "la la la la" the old path found one note, not four.
        val syl = List(4) { i -> listOf(Part.Soft(62.0, 0.05, 3.0), v(62.0, 0.3 + i * 0.05)) }
        val p = phrase(syl)
        assertEquals(1, NoteSegmenter.segment(p.frames).size)
    }

    @Test
    fun consonantCueSeesHissAndBursts() {
        val p = phrase(listOf(listOf(v(64.0, 0.3)), listOf(Part.Hiss(0.07), v(64.0, 0.3))))
        val cues = ConsonantCue.compute(p.audio, rate, 0.0)
        val vowel = cues.filter { it.first in 0.35..0.55 }.maxOf { it.second }
        val hiss = cues.filter { it.first in 0.61..0.66 }.maxOf { it.second }
        assertTrue(vowel < 0.2, "a sung vowel is not a consonant ($vowel)")
        assertTrue(hiss > 0.6, "hiss is ($hiss)")
    }

    @Test
    fun edgeCases() {
        assertEquals(emptyList(), SyllableSegmenter.timings(emptyList(), emptyList()))
        val silent = List(50) { TakeFrame(it * 0.01, null, -80.0) }
        assertEquals(listOf(null, null), SyllableSegmenter.timings(silent, listOf(1.0, 1.0)))
        val one = phrase(listOf(listOf(v(60.0, 0.5))))
        val t = SyllableSegmenter.timings(one.frames, listOf(1.0))
        assertEquals(one.starts[0], t.single()!!.start, 0.02)
        assertEquals(60.0, t.single()!!.startMidi!!, 0.1)
    }

    @Test
    fun withoutTheRecordingThePitchAndLevelStillSplit() {
        // The consonant cue only adds evidence: the same phrases split from the frames alone.
        check(phrase(List(4) { i -> listOf(Part.Soft(62.0, 0.05, 3.0), v(62.0, 0.3 + i * 0.05)) }), List(4) { 1.0 }, useAudio = false)
        check(phrase(listOf(listOf(v(60.0, 0.35)), listOf(Part.Stop(0.025), v(60.0, 0.35)), listOf(Part.Stop(0.025), v(60.2, 0.4)))), List(3) { 1.0 }, useAudio = false)
    }

    @Test
    fun vibratoDoesNotSplitASyllable() {
        // Two long syllables with ±40 cents vibrato, a soft "m" between them.
        val rnd = Random(4)
        val p = phrase(listOf(listOf(v(60.0, 0.8)), listOf(Part.Soft(60.0, 0.05, 3.5), v(60.0, 0.8))))
        val vib = p.frames.mapIndexed { i, f -> if (f.midi == null) f else f.copy(midi = f.midi + 0.4 * sin(2 * PI * 5.5 * i * 0.01) + rnd.nextDouble() * 0.02) }
        val t = SyllableSegmenter.timings(vib, listOf(1.0, 1.0), ConsonantCue.compute(p.audio, rate, 0.0))
        assertEquals(p.starts[1], t[1]!!.start, 0.03)
    }
}
