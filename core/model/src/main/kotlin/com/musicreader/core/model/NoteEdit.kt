package com.musicreader.core.model

import kotlin.math.max
import kotlin.math.min

/**
 * Hand edits of a syllable's note and length (D-032). Nothing snaps: beats and pitch are
 * continuous decimals. Every operation changes exactly one syllable.
 */
object NoteEditOps {
    /** The shortest a syllable can be, in beats. */
    const val MIN_DURATION = 0.02
    const val MIN_MIDI = 24.0
    const val MAX_MIDI = 108.0

    private fun change(content: SectionContent, id: String, f: (Syllable) -> Syllable): SectionContent =
        content.copy(
            lyricLines = content.lyricLines.map { line ->
                line.copy(words = line.words.map { w -> w.copy(syllables = w.syllables.map { if (it.id == id) f(it) else it }) })
            },
        )

    /**
     * Moves the initial note by [deltaStart] and the final note by [deltaEnd] semitones; every
     * curve point moves by (1 − t)·Δstart + t·Δend, so the curve still runs from the initial to
     * the final note (D-010).
     */
    fun shiftNotes(content: SectionContent, id: String, deltaStart: Double, deltaEnd: Double): SectionContent = change(content, id) { s ->
        val start = s.pitchStartMidi ?: return@change s
        val end = s.pitchEndMidi ?: start
        val ds = (start + deltaStart).coerceIn(MIN_MIDI, MAX_MIDI) - start
        val de = (end + deltaEnd).coerceIn(MIN_MIDI, MAX_MIDI) - end
        s.copy(
            pitchStartMidi = start + ds,
            pitchEndMidi = end + de,
            pitchCurve = s.pitchCurve.map { p -> p.copy(midi = p.midi + (1 - p.t) * ds + p.t * de) },
        )
    }

    /** The whole note up or down (initial note, final note and curve together). */
    fun moveNote(content: SectionContent, id: String, delta: Double): SectionContent = shiftNotes(content, id, delta, delta)

    /** A new start; the length stays. Kept inside the section. */
    fun setStart(content: SectionContent, id: String, startBeat: Double): SectionContent = change(content, id) { s ->
        val dur = s.durationBeats ?: 1.0
        s.copy(startBeat = startBeat.coerceIn(0.0, max(0.0, content.lengthBeats - min(dur, content.lengthBeats.toDouble()))))
    }

    /** A new length (the start stays); the curve stretches with it, because its t is relative. */
    fun setDuration(content: SectionContent, id: String, durationBeats: Double): SectionContent = change(content, id) { s ->
        val start = s.startBeat ?: 0.0
        s.copy(durationBeats = durationBeats.coerceIn(MIN_DURATION, max(MIN_DURATION, content.lengthBeats - start)))
    }

    /**
     * Gives a syllable without a note one: [midi] for both ends. A syllable without timing also
     * gets [startBeat] and [durationBeats].
     */
    fun addNote(content: SectionContent, id: String, midi: Double, startBeat: Double, durationBeats: Double): SectionContent = change(content, id) { s ->
        val m = midi.coerceIn(MIN_MIDI, MAX_MIDI)
        s.copy(
            startBeat = s.startBeat ?: startBeat.coerceIn(0.0, content.lengthBeats.toDouble() - MIN_DURATION),
            durationBeats = s.durationBeats ?: durationBeats,
            pitchStartMidi = m,
            pitchEndMidi = m,
            pitchCurve = emptyList(),
        )
    }

    /** Removes the note; the timing stays. */
    fun clearNote(content: SectionContent, id: String): SectionContent =
        change(content, id) { it.copy(pitchStartMidi = null, pitchEndMidi = null, pitchCurve = emptyList()) }

    /**
     * A note for a syllable that has none: the note of the nearest syllable with one (the one
     * before wins a tie), else middle C.
     */
    fun suggestedMidi(content: SectionContent, voice: Int, id: String): Double {
        val all = CaptureOps.syllables(content, voice).map { it.second }
        val i = all.indexOfFirst { it.id == id }
        if (i < 0) return 60.0
        for (d in 1 until all.size) {
            all.getOrNull(i - d)?.let { s -> (s.pitchEndMidi ?: s.pitchStartMidi)?.let { return it } }
            all.getOrNull(i + d)?.pitchStartMidi?.let { return it }
        }
        return 60.0
    }

    /** All syllables' timing and notes, for undo/redo (the text is never part of it). */
    fun snapshot(content: SectionContent): List<Syllable> =
        content.lyricLines.flatMap { l -> l.words.flatMap { it.syllables } }

    /** Note name with octave and cents, e.g. "A3 +12" for 57.12 (sharps or flats as chosen). */
    fun noteParts(midi: Double, preference: AccidentalPreference): Triple<String, Int, Int> {
        val n = kotlin.math.round(midi).toInt()
        val cents = kotlin.math.round((midi - n) * 100).toInt()
        val name = Enharmonics.spell(Math.floorMod(n, 12), preference).toString()
        return Triple(name, Math.floorDiv(n, 12) - 1, cents)
    }
}

/** A bar in the editor: one syllable with a note, in beats and continuous MIDI. */
data class NoteBar(
    val id: String,
    val text: String,
    val startBeat: Double,
    val durationBeats: Double,
    val startMidi: Double,
    val endMidi: Double,
) {
    val endBeat: Double get() = startBeat + durationBeats
}

/** Which part of a bar a finger is on. */
enum class BarPart { BODY, END }

/**
 * The editor's visible window (D-032): beats left to right, pitch bottom to top, after a fixed
 * note-name strip of [stripPx] on the left. Pinch zooms around the fingers, drag pans. Pure.
 */
data class NoteViewport(
    val firstBeat: Double,
    val pxPerBeat: Double,
    /** Pitch at the top edge. */
    val topMidi: Double,
    val pxPerSemitone: Double,
    val widthPx: Double,
    val heightPx: Double,
    val stripPx: Double,
) {
    fun beatToX(beat: Double): Double = stripPx + (beat - firstBeat) * pxPerBeat
    fun xToBeat(x: Double): Double = firstBeat + (x - stripPx) / pxPerBeat
    fun midiToY(midi: Double): Double = (topMidi - midi) * pxPerSemitone
    fun yToMidi(y: Double): Double = topMidi - y / pxPerSemitone

    val lastBeat: Double get() = xToBeat(widthPx)
    val bottomMidi: Double get() = yToMidi(heightPx)

    /** Zoom by [zoomX]/[zoomY] keeping the point under ([focusX], [focusY]) in place. */
    fun zoom(zoomX: Double, zoomY: Double, focusX: Double, focusY: Double): NoteViewport {
        val beat = xToBeat(focusX)
        val midi = yToMidi(focusY)
        val ppb = (pxPerBeat * zoomX).coerceIn(MIN_PX_PER_BEAT, MAX_PX_PER_BEAT)
        val pps = (pxPerSemitone * zoomY).coerceIn(MIN_PX_PER_SEMITONE, MAX_PX_PER_SEMITONE)
        return copy(
            pxPerBeat = ppb,
            pxPerSemitone = pps,
            firstBeat = beat - (focusX - stripPx) / ppb,
            topMidi = midi + focusY / pps,
        )
    }

    /** Moves the content with the finger by ([dx], [dy]) pixels. */
    fun pan(dx: Double, dy: Double): NoteViewport = copy(firstBeat = firstBeat - dx / pxPerBeat, topMidi = topMidi + dy / pxPerSemitone)

    /** Keeps the window over the section (a little margin) and the useful pitch range. */
    fun clamp(lengthBeats: Int): NoteViewport {
        val visibleBeats = (widthPx - stripPx) / pxPerBeat
        val first = firstBeat.coerceIn(-1.0, max(-1.0, lengthBeats + 1.0 - visibleBeats))
        val visibleSemis = heightPx / pxPerSemitone
        val top = topMidi.coerceIn(NoteEditOps.MIN_MIDI + visibleSemis, NoteEditOps.MAX_MIDI)
        return copy(firstBeat = first, topMidi = top)
    }

    /** The bar's rectangle (left, top, right, bottom) in pixels; its height is [barHeightPx]. */
    fun rect(bar: NoteBar, barHeightPx: Double): DoubleArray {
        val mid = midiToY((bar.startMidi + bar.endMidi) / 2)
        return doubleArrayOf(beatToX(bar.startBeat), mid - barHeightPx / 2, beatToX(bar.endBeat), mid + barHeightPx / 2)
    }

    /**
     * The bar under ([x], [y]) and the part touched: within [edgePx] of the right end is the
     * END (length), elsewhere the BODY. [touchSlopPx] makes thin bars easier to hit. Overlapping
     * bars: the [selected] one wins, then the one drawn last (later start).
     */
    fun hitTest(bars: List<NoteBar>, x: Double, y: Double, barHeightPx: Double, edgePx: Double, touchSlopPx: Double, selected: String?): Pair<String, BarPart>? {
        if (x < stripPx) return null
        val hits = bars.filter { b ->
            val r = rect(b, barHeightPx)
            x >= r[0] - touchSlopPx && x <= r[2] + edgePx && y >= r[1] - touchSlopPx && y <= r[3] + touchSlopPx
        }
        val hit = hits.firstOrNull { it.id == selected } ?: hits.maxByOrNull { it.startBeat } ?: return null
        val right = beatToX(hit.endBeat)
        // A short bar keeps at least half of itself for moving.
        val edge = min(edgePx, (right - beatToX(hit.startBeat)) / 2)
        val part = if (x >= right - edge) BarPart.END else BarPart.BODY
        return hit.id to part
    }

    companion object {
        const val MIN_PX_PER_BEAT = 20.0
        const val MAX_PX_PER_BEAT = 2000.0
        const val MIN_PX_PER_SEMITONE = 6.0
        const val MAX_PX_PER_SEMITONE = 200.0
    }
}

/**
 * One drag of a bar (D-032): the values when the finger went down plus how far it moved. With
 * [fine], the bar moves a quarter of the finger's way, for small changes. Nothing snaps.
 */
data class NoteDrag(
    val id: String,
    val part: BarPart,
    val startBeat: Double,
    val durationBeats: Double,
    val startMidi: Double,
    val fine: Boolean,
) {
    val factor: Double get() = if (fine) FINE_FACTOR else 1.0

    /** Body: the new start beat after the finger moved [dxPx] (sideways). */
    fun newStart(dxPx: Double, vp: NoteViewport): Double = startBeat + dxPx * factor / vp.pxPerBeat

    /** Body: how many semitones the note moves after [dyPx] (up is negative y = higher). */
    fun noteDelta(dyPx: Double, vp: NoteViewport): Double = -dyPx * factor / vp.pxPerSemitone

    /** End: the new length after [dxPx]. */
    fun newDuration(dxPx: Double, vp: NoteViewport): Double = durationBeats + dxPx * factor / vp.pxPerBeat

    companion object {
        const val FINE_FACTOR = 0.25
    }
}
