package com.musicreader.app.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicreader.app.ui.common.appContainer
import com.musicreader.app.ui.song.SongViewModel
import com.musicreader.audio.AudioService
import com.musicreader.audio.PreviewNote
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.BarPart
import com.musicreader.core.model.CaptureOps
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.EditHistory
import com.musicreader.core.model.LyricLine
import com.musicreader.core.model.NoteBar
import com.musicreader.core.model.NoteDrag
import com.musicreader.core.model.NoteEditOps
import com.musicreader.core.model.NoteViewport
import com.musicreader.core.model.SectionContent
import com.musicreader.core.model.Song
import com.musicreader.core.model.Syllable
import com.musicreader.core.model.contentOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** A syllable without a note (listed under the editor so it can be given one). */
data class UnplacedSyllable(val id: String, val text: String, val timed: Boolean)

/** A lyric line to choose (the editor shows one line at a time). */
data class EditorLine(val id: String, val text: String)

/** A chord of the section, drawn faintly along the top (D-032). */
data class ChordMark(val beat: Double, val symbol: ChordSymbol)

/** What a drag is changing right now (shown large in the magnifier). */
enum class DragValue { NOTE, START, LENGTH }

data class NoteEditorUiState(
    val ready: Boolean = false,
    val voice: Int = LyricLine.MAIN_VOICE,
    val hasSecondVoice: Boolean = false,
    /** The voice's lyric lines; [line] is the one being edited. */
    val lines: List<EditorLine> = emptyList(),
    val line: Int = 0,
    /** The bars of the chosen line (editable, with their text). */
    val bars: List<NoteBar> = emptyList(),
    /** The other lines' bars, drawn faintly for context (not editable here). */
    val otherBars: List<NoteBar> = emptyList(),
    /** The sung curves, in (beat, midi), drawn faintly behind the bars. */
    val curves: Map<String, List<Pair<Double, Double>>> = emptyMap(),
    val unplaced: List<UnplacedSyllable> = emptyList(),
    val chords: List<ChordMark> = emptyList(),
    val lengthBeats: Int = 4,
    val beatsPerBar: Int = 4,
    val tempoBpm: Double = 120.0,
    val settings: AppSettings = AppSettings(),
    val viewport: NoteViewport? = null,
    val selectedId: String? = null,
    val cursorBeat: Double = 0.0,
    val fine: Boolean = false,
    val dragging: DragValue? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val previewing: Boolean = false,
) {
    val selectedBar: NoteBar? get() = bars.firstOrNull { it.id == selectedId }
    val selectedUnplaced: UnplacedSyllable? get() = unplaced.firstOrNull { it.id == selectedId }
    val accidentals: AccidentalPreference get() = settings.accidentals
}

/**
 * The note editor (D-032): every syllable's note and length on a zoomable canvas, moved freely
 * with a finger (no snapping), with fine control, undo/redo and a preview from the cursor.
 */
class NoteEditorViewModel(
    private val sectionId: String,
    private val song: StateFlow<Song?>,
    settings: StateFlow<AppSettings>,
    private val edit: (sectionId: String, change: (SectionContent) -> SectionContent) -> Unit,
    private val audio: AudioService,
) : ViewModel() {

    private data class Local(
        val voice: Int = LyricLine.MAIN_VOICE,
        val line: Int = 0,
        val viewport: NoteViewport? = null,
        val selectedId: String? = null,
        val cursorBeat: Double = 0.0,
        val fine: Boolean = false,
        val dragging: DragValue? = null,
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
    )

    private val local = MutableStateFlow(Local())
    private val history = EditHistory<List<Syllable>>()
    private var drag: NoteDrag? = null
    private var appliedNoteDelta = 0.0
    private var axis: DragValue? = null

    val state: StateFlow<NoteEditorUiState> = combine(song, settings, local, audio.preview) { s, set, l, preview ->
        uiState(s, set, l, preview != null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NoteEditorUiState())

    private fun content(s: Song? = song.value): SectionContent? {
        val section = s?.sections?.firstOrNull { it.id == sectionId } ?: return null
        return s.contentOf(section)
    }

    private fun uiState(s: Song?, settings: AppSettings, l: Local, previewing: Boolean): NoteEditorUiState {
        val content = content(s) ?: return NoteEditorUiState()
        val voiceLines = content.lyricLines.filter { it.voice == l.voice }
        val lineIndex = l.line.coerceIn(0, maxOf(0, voiceLines.size - 1))
        fun barsOf(lines: List<LyricLine>) = lines.flatMap { line -> line.words.flatMap { it.syllables } }
            .filter { it.startBeat != null && it.durationBeats != null && it.pitchStartMidi != null }
        val lineSyllables = voiceLines.getOrNull(lineIndex)?.words?.flatMap { it.syllables }.orEmpty()
        val withNote = barsOf(listOfNotNull(voiceLines.getOrNull(lineIndex)))
        fun bar(it: Syllable) = NoteBar(it.id, it.text, it.startBeat!!, it.durationBeats!!, it.pitchStartMidi!!, it.pitchEndMidi ?: it.pitchStartMidi!!)
        return NoteEditorUiState(
            ready = true,
            voice = l.voice,
            hasSecondVoice = content.lyricLines.any { it.voice == LyricLine.SECOND_VOICE && it.words.isNotEmpty() },
            lines = voiceLines.map { EditorLine(it.id, it.text) },
            line = lineIndex,
            bars = withNote.map(::bar),
            otherBars = barsOf(voiceLines.filterIndexed { i, _ -> i != lineIndex }).map(::bar),
            curves = withNote.associate { sy -> sy.id to sy.pitchCurve.map { p -> sy.startBeat!! + p.t * sy.durationBeats!! to p.midi } },
            unplaced = lineSyllables.filter { it.pitchStartMidi == null }.map { UnplacedSyllable(it.id, it.text, it.startBeat != null) },
            chords = content.chords.map { ChordMark(it.beat, it.symbol) },
            lengthBeats = content.lengthBeats,
            beatsPerBar = s!!.timeSignature.num,
            tempoBpm = s.tempoBpm,
            settings = settings,
            viewport = l.viewport,
            selectedId = l.selectedId,
            cursorBeat = l.cursorBeat,
            fine = l.fine,
            dragging = l.dragging,
            canUndo = l.canUndo,
            canRedo = l.canRedo,
            previewing = previewing,
        )
    }

    private fun update(f: (Local) -> Local) {
        local.value = f(local.value)
    }

    private fun current(): NoteEditorUiState = uiState(song.value, state.value.settings, local.value, audio.preview.value != null)

    // --- View ---

    /**
     * The canvas size in pixels. The first time, the view fits the section's notes: a beat is
     * [pxPerBeat] wide and the notes are in the middle, about two octaves high.
     */
    fun setSize(widthPx: Double, heightPx: Double, stripPx: Double, pxPerBeat: Double) {
        val vp = local.value.viewport
        if (vp != null) {
            if (vp.widthPx != widthPx || vp.heightPx != heightPx) update { it.copy(viewport = vp.copy(widthPx = widthPx, heightPx = heightPx)) }
            return
        }
        val content = content() ?: return
        update { it.copy(viewport = NoteViewport(0.0, pxPerBeat, 72.0, heightPx / 24.0, widthPx, heightPx, stripPx).clamp(content.lengthBeats)) }
        fitLine()
    }

    fun zoom(zoomX: Double, zoomY: Double, focusX: Double, focusY: Double) {
        val len = content()?.lengthBeats ?: return
        update { l -> l.copy(viewport = l.viewport?.zoom(zoomX, zoomY, focusX, focusY)?.clamp(len)) }
    }

    fun pan(dx: Double, dy: Double) {
        val len = content()?.lengthBeats ?: return
        update { l -> l.copy(viewport = l.viewport?.pan(dx, dy)?.clamp(len)) }
    }

    fun setVoice(voice: Int) {
        update { it.copy(voice = voice, line = 0, selectedId = null) }
        fitLine()
    }

    /** Shows lyric line [index]: only its words are labelled and editable, and the view fits it. */
    fun setLine(index: Int) {
        update { it.copy(line = index, selectedId = null) }
        fitLine()
    }

    /** Fits the view to the chosen line's notes (a little margin around them). */
    private fun fitLine() {
        val vp = local.value.viewport ?: return
        val st = current()
        val content = content() ?: return
        val bars = st.bars
        if (bars.isEmpty()) {
            // No notes yet: start where the line's row starts.
            update { it.copy(cursorBeat = rowStart(content, st.line)) }
            return
        }
        val from = bars.minOf { it.startBeat }
        val to = bars.maxOf { it.endBeat }
        val midis = bars.flatMap { listOf(it.startMidi, it.endMidi) }
        val semis = max(12.0, midis.max() - midis.min() + 6.0)
        val usable = vp.widthPx - vp.stripPx
        val pxPerBeat = (usable / (to - from + 1.0)).coerceIn(NoteViewport.MIN_PX_PER_BEAT, NoteViewport.MAX_PX_PER_BEAT)
        val fitted = vp.copy(
            firstBeat = from - 0.5,
            pxPerBeat = pxPerBeat,
            pxPerSemitone = vp.heightPx / semis,
            topMidi = (midis.min() + midis.max()) / 2 + semis / 2,
        ).clamp(content.lengthBeats)
        update { it.copy(viewport = fitted, cursorBeat = from) }
    }

    private fun rowStart(content: SectionContent, line: Int): Double {
        val n = content.lyricLines.count { it.voice == local.value.voice }
        return if (n == 0) 0.0 else content.lengthBeats.toDouble() * line / n
    }

    fun setFine(on: Boolean) = update { it.copy(fine = on) }

    /** A tap: selects the bar under the finger, or sets the cursor where there is none. */
    fun tap(x: Double, y: Double, barHeightPx: Double, edgePx: Double, slopPx: Double) {
        val vp = local.value.viewport ?: return
        val hit = vp.hitTest(current().bars, x, y, barHeightPx, edgePx, slopPx, local.value.selectedId)
        if (hit != null) {
            update { it.copy(selectedId = hit.first) }
        } else if (x >= vp.stripPx) {
            update { it.copy(selectedId = null, cursorBeat = vp.xToBeat(x).coerceIn(0.0, (content()?.lengthBeats ?: 0).toDouble())) }
        }
    }

    fun select(id: String?) = update { it.copy(selectedId = id) }

    // --- Dragging (D-032: free, one bar only) ---

    /** A finger went down on ([x], [y]); returns true when it is on a bar (then it drags it). */
    fun dragStart(x: Double, y: Double, barHeightPx: Double, edgePx: Double, slopPx: Double): Boolean {
        val vp = local.value.viewport ?: return false
        val st = current()
        val (id, part) = vp.hitTest(st.bars, x, y, barHeightPx, edgePx, slopPx, local.value.selectedId) ?: return false
        val bar = st.bars.first { it.id == id }
        drag = NoteDrag(id, part, bar.startBeat, bar.durationBeats, bar.startMidi, local.value.fine)
        appliedNoteDelta = 0.0
        axis = if (part == BarPart.END) DragValue.LENGTH else null
        recordHistory()
        update { it.copy(selectedId = id, dragging = if (part == BarPart.END) DragValue.LENGTH else DragValue.NOTE) }
        return true
    }

    /**
     * The finger is now [dx], [dy] pixels from where it went down. The body moves along one
     * axis, chosen by the first [AXIS_LOCK_PX] of movement, so a note change does not shift the
     * start by accident (and the other way round).
     */
    fun dragTo(dx: Double, dy: Double) {
        val d = drag ?: return
        val vp = local.value.viewport ?: return
        if (axis == null) {
            if (abs(dx) < AXIS_LOCK_PX && abs(dy) < AXIS_LOCK_PX) return
            axis = if (abs(dy) >= abs(dx)) DragValue.NOTE else DragValue.START
            update { it.copy(dragging = axis) }
        }
        when (axis) {
            DragValue.NOTE -> {
                val target = d.noteDelta(dy, vp)
                val step = target - appliedNoteDelta
                appliedNoteDelta = target
                edit(sectionId) { NoteEditOps.moveNote(it, d.id, step) }
            }
            DragValue.START -> edit(sectionId) { NoteEditOps.setStart(it, d.id, d.newStart(dx, vp)) }
            DragValue.LENGTH -> edit(sectionId) { NoteEditOps.setDuration(it, d.id, d.newDuration(dx, vp)) }
            null -> Unit
        }
    }

    fun dragEnd() {
        drag = null
        axis = null
        update { it.copy(dragging = null) }
    }

    // --- Buttons ---

    private fun recordHistory() {
        val c = content() ?: return
        history.record(NoteEditOps.snapshot(c))
        update { it.copy(canUndo = true, canRedo = false) }
    }

    private fun step(change: (SectionContent, String) -> SectionContent) {
        val id = local.value.selectedId ?: return
        recordHistory()
        edit(sectionId) { change(it, id) }
    }

    /** The whole note (both ends) by [cents]. */
    fun nudgeNote(cents: Double) = step { c, id -> NoteEditOps.moveNote(c, id, cents / 100) }

    /** Only the initial (or final) note by [cents] (D-010). */
    fun nudgeStartNote(cents: Double) = step { c, id -> NoteEditOps.shiftNotes(c, id, cents / 100, 0.0) }
    fun nudgeEndNote(cents: Double) = step { c, id -> NoteEditOps.shiftNotes(c, id, 0.0, cents / 100) }

    fun nudgeStart(beats: Double) = step { c, id ->
        val s = CaptureOps.findSyllable(c, id)?.startBeat ?: return@step c
        NoteEditOps.setStart(c, id, s + beats)
    }

    fun nudgeLength(beats: Double) = step { c, id ->
        val d = CaptureOps.findSyllable(c, id)?.durationBeats ?: return@step c
        NoteEditOps.setDuration(c, id, d + beats)
    }

    /** Gives the selected syllable without a note one, at the cursor (D-032). */
    fun addNote() {
        val c = content() ?: return
        val id = local.value.selectedId ?: return
        val midi = NoteEditOps.suggestedMidi(c, local.value.voice, id)
        step { content, sid -> NoteEditOps.addNote(content, sid, midi, local.value.cursorBeat, 1.0) }
    }

    fun clearNote() = step { c, id -> NoteEditOps.clearNote(c, id) }

    fun undo() {
        val c = content() ?: return
        val previous = history.undo(NoteEditOps.snapshot(c)) ?: return
        edit(sectionId) { CaptureOps.restore(it, previous) }
        update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    fun redo() {
        val c = content() ?: return
        val next = history.redo(NoteEditOps.snapshot(c)) ?: return
        edit(sectionId) { CaptureOps.restore(it, next) }
        update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    // --- Preview ---

    /** Plays the melody from the cursor with the metronome, or stops it. */
    fun togglePreview() {
        if (audio.preview.value != null) {
            audio.stopPreview()
            return
        }
        val st = current()
        if (st.bars.isEmpty()) return
        audio.startPreview(
            st.tempoBpm, st.beatsPerBar, st.cursorBeat,
            st.bars.map { PreviewNote(it.startBeat, it.endBeat, it.startMidi, it.endMidi) },
        )
        viewModelScope.launch {
            // Stops one beat after the last note.
            while (isActive) {
                delay(100)
                val clock = audio.preview.value ?: break
                val beat = audio.previewBeat() ?: break
                if (beat > clock.endBeat + 1.0) {
                    audio.stopPreview()
                    break
                }
            }
        }
    }

    /** The beat heard now in the preview (audio clock), for the playhead; null when none plays. */
    fun previewBeat(): Double? = audio.previewBeat()

    override fun onCleared() {
        if (audio.preview.value != null) audio.stopPreview()
    }

    companion object {
        const val AXIS_LOCK_PX = 10.0
        /** Button steps: tiny on purpose. */
        const val CENTS_STEP = 5.0
        const val BEAT_STEP = 0.01

        fun factory(sectionId: String, songVm: SongViewModel) = viewModelFactory {
            initializer {
                NoteEditorViewModel(sectionId, songVm.currentSong, songVm.settings, songVm::editNotes, appContainer.audio)
            }
        }
    }
}
