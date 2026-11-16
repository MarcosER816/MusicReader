package com.musicreader.core.model

import kotlinx.serialization.Serializable

/** How a take's timing was found (D-031). */
@Serializable
enum class TakeMethod { SUNG, TAPPED }

/**
 * One accepted take (D-031): which syllables it replaced and their values before, so it can be
 * undone, and the recording. Stored in `songs/<id>/takes/takes.json`; the last 10 per song are kept.
 */
@Serializable
data class TakeInfo(
    val id: String,
    val createdAt: Long,
    val contentId: String,
    val voice: Int,
    /** The replaced syllables in order. */
    val syllableIds: List<String>,
    /** Those syllables as they were before the take (for undo). */
    val previous: List<Syllable>,
    val method: TakeMethod,
    val tempoBpm: Double,
    /** Round-trip latency removed from the pitch frames. */
    val latencyMs: Double,
    /** File name of the recording in the takes folder. */
    val audioFile: String,
    val undone: Boolean = false,
)

/** What a take found for one syllable, in section beats. */
data class SyllableResult(
    val syllableId: String,
    val startBeat: Double,
    val durationBeats: Double,
    val pitchStartMidi: Double?,
    val pitchEndMidi: Double?,
    val curve: List<CurvePoint>,
)

/**
 * Converts a take's seconds (heard timeline) to section beats: section beat
 * [beatAtZero] + seconds × beats per second.
 */
data class BeatClock(val beatAtZero: Double, val beatsPerSecond: Double) {
    fun beat(seconds: Double): Double = beatAtZero + seconds * beatsPerSecond
    fun seconds(beat: Double): Double = (beat - beatAtZero) / beatsPerSecond

    companion object {
        /**
         * The metronome grid of a take: its beat 0 is at engine frame [anchorFrame], one bar of
         * count-in, then section bar [startBar] (0-based). Seconds are engine frame / [sampleRate].
         */
        fun forTake(anchorFrame: Long, framesPerBeat: Double, beatsPerBar: Int, startBar: Int, sampleRate: Int): BeatClock {
            val beatsPerSecond = sampleRate / framesPerBeat
            val sectionStartFrame = anchorFrame + beatsPerBar * framesPerBeat  // after the count-in bar
            val sectionStartSeconds = sectionStartFrame / sampleRate
            val beatOfSectionStart = (startBar * beatsPerBar).toDouble()
            return BeatClock(beatOfSectionStart - sectionStartSeconds * beatsPerSecond, beatsPerSecond)
        }
    }
}

/** Pure operations for lyric capture (D-031). */
object CaptureOps {
    /** The syllables of [voice] in reading order, with their word. */
    fun syllables(content: SectionContent, voice: Int): List<Pair<Word, Syllable>> =
        content.lyricLines.filter { it.voice == voice }.flatMap { line -> line.words.flatMap { w -> w.syllables.map { w to it } } }

    /** The words of [voice] in reading order. */
    fun words(content: SectionContent, voice: Int): List<Word> =
        content.lyricLines.filter { it.voice == voice }.flatMap { it.words }

    /** Syllable ids of the words from [firstWordId] to [lastWordId] (either order), in reading order. */
    fun selection(content: SectionContent, voice: Int, firstWordId: String, lastWordId: String): List<String> {
        val words = words(content, voice)
        val a = words.indexOfFirst { it.id == firstWordId }
        val b = words.indexOfFirst { it.id == lastWordId }
        if (a < 0 || b < 0) return emptyList()
        return words.subList(minOf(a, b), maxOf(a, b) + 1).flatMap { w -> w.syllables.map { it.id } }
    }

    /** The 0-based bar where the selection starts, if its first syllable is timed. */
    fun startBar(content: SectionContent, syllableIds: List<String>, beatsPerBar: Int): Int? {
        val first = syllableIds.firstOrNull() ?: return null
        val s = findSyllable(content, first) ?: return null
        return s.startBeat?.let { (it / beatsPerBar).toInt().coerceAtLeast(0) }
    }

    /**
     * A suggestion for an untimed selection: the bar after the last timed syllable before it
     * (or bar 0).
     */
    fun suggestedBar(content: SectionContent, voice: Int, syllableIds: List<String>, beatsPerBar: Int): Int {
        val all = syllables(content, voice).map { it.second }
        val firstIndex = all.indexOfFirst { it.id == syllableIds.firstOrNull() }
        val before = if (firstIndex <= 0) emptyList() else all.subList(0, firstIndex)
        val last = before.lastOrNull { it.startBeat != null } ?: return 0
        val end = last.startBeat!! + (last.durationBeats ?: 0.0)
        val bars = maxOf(1, content.lengthBeats / beatsPerBar)
        return kotlin.math.ceil(end / beatsPerBar - 1e-9).toInt().coerceIn(0, bars - 1)
    }

    fun findSyllable(content: SectionContent, id: String): Syllable? =
        content.lyricLines.asSequence().flatMap { it.words }.flatMap { it.syllables }.firstOrNull { it.id == id }

    /** The syllables [ids] as they are now (for undo). */
    fun snapshot(content: SectionContent, ids: List<String>): List<Syllable> = ids.mapNotNull { findSyllable(content, it) }

    /** Last take wins: replaces the timing and pitch of exactly the [results]' syllables. */
    fun apply(content: SectionContent, results: List<SyllableResult>, takeId: String): SectionContent {
        val byId = results.associateBy { it.syllableId }
        return mapSyllables(content) { s ->
            val r = byId[s.id] ?: return@mapSyllables s
            s.copy(
                startBeat = r.startBeat,
                durationBeats = r.durationBeats,
                pitchStartMidi = r.pitchStartMidi,
                pitchEndMidi = r.pitchEndMidi,
                pitchCurve = r.curve,
                takeId = takeId,
            )
        }
    }

    /** Undo a take: puts back the timing and pitch the syllables had before (their text stays). */
    fun restore(content: SectionContent, previous: List<Syllable>): SectionContent {
        val byId = previous.associateBy { it.id }
        return mapSyllables(content) { s ->
            val p = byId[s.id] ?: return@mapSyllables s
            s.copy(
                startBeat = p.startBeat,
                durationBeats = p.durationBeats,
                pitchStartMidi = p.pitchStartMidi,
                pitchEndMidi = p.pitchEndMidi,
                pitchCurve = p.pitchCurve,
                takeId = p.takeId,
            )
        }
    }

    /**
     * Converts a timing in seconds to a result in section beats. The start is kept inside the
     * section; the curve gets at most one point per frame, `t` relative to the syllable (D-010).
     */
    fun result(syllableId: String, start: Double, end: Double, startMidi: Double?, endMidi: Double?,
               curve: List<Pair<Double, Double>>, clock: BeatClock, lengthBeats: Int): SyllableResult {
        val startBeat = clock.beat(start).coerceIn(0.0, lengthBeats.toDouble() - 1e-3)
        val endBeat = maxOf(clock.beat(end), startBeat + 1e-3)
        val span = end - start
        val points = if (span <= 0) emptyList() else curve.map { (t, m) -> CurvePoint(((t - start) / span).coerceIn(0.0, 1.0), m) }
        return SyllableResult(syllableId, startBeat, endBeat - startBeat, startMidi, endMidi, points)
    }

    private fun mapSyllables(content: SectionContent, f: (Syllable) -> Syllable): SectionContent =
        content.copy(
            lyricLines = content.lyricLines.map { line ->
                line.copy(words = line.words.map { w -> w.copy(syllables = w.syllables.map(f)) })
            },
        )

    /** True when the syllable has timing (a mark under it in the editors). */
    fun isTimed(s: Syllable): Boolean = s.startBeat != null && s.durationBeats != null

    /** True when the syllable also has a note. */
    fun hasNote(s: Syllable): Boolean = s.pitchStartMidi != null
}
