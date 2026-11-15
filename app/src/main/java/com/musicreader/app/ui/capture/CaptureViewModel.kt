package com.musicreader.app.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicreader.audio.AudioService
import com.musicreader.audio.MetronomeSettings
import com.musicreader.audio.PitchFrame
import com.musicreader.audio.TakeAudio
import com.musicreader.core.data.SettingsRepository
import com.musicreader.core.data.TakeRepository
import com.musicreader.core.dsp.NoteSegmenter
import com.musicreader.core.dsp.SyllableAligner
import com.musicreader.core.dsp.SyllableTiming
import com.musicreader.core.dsp.TakeFrame
import com.musicreader.core.dsp.TapAlong
import com.musicreader.core.model.BeatClock
import com.musicreader.core.model.CaptureOps
import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.LyricLine
import com.musicreader.core.model.SectionContent
import com.musicreader.core.model.Song
import com.musicreader.core.model.Syllable
import com.musicreader.core.model.SyllableResult
import com.musicreader.core.model.TakeInfo
import com.musicreader.core.model.TakeMethod
import com.musicreader.core.model.contentOf
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.musicreader.app.ui.common.appContainer
import com.musicreader.app.ui.song.SongViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/** A syllable on the Sing screen, with its mark (D-031). */
data class CaptureSyllable(val id: String, val text: String, val timed: Boolean, val hasNote: Boolean)

data class CaptureWord(val id: String, val syllables: List<CaptureSyllable>, val selected: Boolean)

data class CaptureLine(val id: String, val words: List<CaptureWord>)

/** Where the take is. */
sealed interface CapturePhase {
    data object Idle : CapturePhase
    /** Waiting for the microphone and the metronome. */
    data object Preparing : CapturePhase
    /** Count-in: [beat] 1..beatsPerBar. */
    data class CountIn(val beat: Int, val beatsPerBar: Int) : CapturePhase
    /** Singing: [bar] and [beat] (1-based) in the section; [taps] so far in tap-along. */
    data class Singing(val bar: Int, val beat: Int, val taps: Int) : CapturePhase
    data object Analyzing : CapturePhase
    data object Review : CapturePhase
    data class Failed(val reason: CaptureError) : CapturePhase
}

enum class CaptureError { NO_MICROPHONE, NO_METRONOME, NOTHING_SUNG, AUDIO }

/** One syllable of the take under review, in section beats. */
data class ReviewSyllable(
    val id: String,
    val text: String,
    val startBeat: Double,
    val durationBeats: Double,
    val startMidi: Double?,
    val endMidi: Double?,
) {
    val endBeat: Double get() = startBeat + durationBeats
}

data class ReviewState(
    val syllables: List<ReviewSyllable>,
    /** Selected syllables that got no timing (tap-along with too few taps). */
    val missing: List<String>,
    /** The take's pitch trace: (beat, midi or null). */
    val trace: List<Pair<Double, Double?>>,
    val fromBeat: Double,
    val toBeat: Double,
    val selected: Int?,
    val method: TakeMethod,
    val playing: Boolean = false,
)

data class CaptureUiState(
    val ready: Boolean = false,
    val title: String = "",
    val voice: Int = LyricLine.MAIN_VOICE,
    val hasSecondVoice: Boolean = false,
    val lines: List<CaptureLine> = emptyList(),
    val selection: List<String> = emptyList(),
    val selectionText: String = "",
    /** 0-based start bar of the selection; null = must be asked. */
    val startBar: Int? = null,
    val suggestedBar: Int = 0,
    val bars: Int = 1,
    val beatsPerBar: Int = 4,
    val askBar: Boolean = false,
    val tapMode: Boolean = false,
    val phase: CapturePhase = CapturePhase.Idle,
    val review: ReviewState? = null,
    /** The latency removed from the take; null = the route is not calibrated (0 is used). */
    val latencyMs: Double? = null,
    val canUndo: Boolean = false,
    val permissionDenied: Boolean = false,
)

/**
 * Lyric capture (D-031): select words, sing them after a one-bar count-in, and the timing and
 * notes of exactly those syllables are set (last take wins). Everything is timed on the audio
 * clock; the take is moved back by the route's measured latency.
 */
class CaptureViewModel(
    private val songId: String,
    private val sectionId: String,
    private val song: StateFlow<Song?>,
    private val applyTake: (sectionId: String, results: List<SyllableResult>, takeId: String) -> Unit,
    private val restoreTake: (sectionId: String, previous: List<Syllable>) -> Unit,
    private val audio: AudioService,
    private val settings: SettingsRepository,
    private val takes: TakeRepository,
    private val player: TakePlayer,
    private val appScope: CoroutineScope,
    private val analysis: CoroutineDispatcher,
    private val ids: IdGenerator = IdGenerator.RANDOM,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val local = MutableStateFlow(Local())
    private var job: Job? = null
    @Volatile private var stopRequested = false
    private val frames = ArrayList<PitchFrame>()
    private val taps = ArrayList<Double>()
    private var take: Take? = null

    /** Screen-only state; combined with the song below. */
    private data class Local(
        val voice: Int = LyricLine.MAIN_VOICE,
        val anchorWord: String? = null,
        val selectionWords: Pair<String, String>? = null,
        val chosenBar: Int? = null,
        val askBar: Boolean = false,
        val tapMode: Boolean = false,
        val phase: CapturePhase = CapturePhase.Idle,
        val review: ReviewState? = null,
        val latencyMs: Double? = null,
        val canUndo: Boolean = false,
        val permissionDenied: Boolean = false,
    )

    /** What one take found; kept for nudging and accepting. */
    private class Take(
        val audio: TakeAudio,
        val frames: List<TakeFrame>,
        val clock: BeatClock,
        val syllableIds: List<String>,
        val previous: List<Syllable>,
        val latencyMs: Double,
        val tempoBpm: Double,
        val contentId: String,
        val voice: Int,
    )

    val state: StateFlow<CaptureUiState> = combine(song, local) { s, l -> uiState(s, l) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CaptureUiState())

    init {
        viewModelScope.launch { refreshUndo() }
    }

    private fun content(s: Song?): SectionContent? {
        val section = s?.sections?.firstOrNull { it.id == sectionId } ?: return null
        return s.contentOf(section)
    }

    private fun uiState(s: Song?, l: Local): CaptureUiState {
        val content = content(s) ?: return CaptureUiState()
        val bpb = s!!.timeSignature.num
        val selection = l.selectionWords?.let { (a, b) -> CaptureOps.selection(content, l.voice, a, b) }.orEmpty()
        val selectedWords = l.selectionWords?.let { (a, b) ->
            val words = CaptureOps.words(content, l.voice)
            val i = words.indexOfFirst { it.id == a }
            val j = words.indexOfFirst { it.id == b }
            if (i < 0 || j < 0) emptySet() else words.subList(min(i, j), max(i, j) + 1).map { it.id }.toSet()
        }.orEmpty()
        val lines = content.lyricLines.filter { it.voice == l.voice }.map { line ->
            CaptureLine(line.id, line.words.map { w ->
                CaptureWord(
                    w.id,
                    w.syllables.map { CaptureSyllable(it.id, it.text, CaptureOps.isTimed(it), CaptureOps.hasNote(it)) },
                    selected = w.id in selectedWords || w.id == l.anchorWord,
                )
            })
        }
        val textOf = CaptureOps.words(content, l.voice).filter { it.id in selectedWords }.joinToString(" ") { it.text }
        val section = s.sections.first { it.id == sectionId }
        return CaptureUiState(
            ready = true,
            title = section.label ?: "",
            voice = l.voice,
            hasSecondVoice = content.lyricLines.any { it.voice == LyricLine.SECOND_VOICE && it.words.isNotEmpty() },
            lines = lines,
            selection = selection,
            selectionText = textOf,
            startBar = l.chosenBar ?: CaptureOps.startBar(content, selection, bpb),
            suggestedBar = CaptureOps.suggestedBar(content, l.voice, selection, bpb),
            bars = max(1, content.lengthBeats / bpb),
            beatsPerBar = bpb,
            askBar = l.askBar,
            tapMode = l.tapMode,
            phase = l.phase,
            review = l.review,
            latencyMs = l.latencyMs,
            canUndo = l.canUndo,
            permissionDenied = l.permissionDenied,
        )
    }

    private fun update(f: (Local) -> Local) {
        local.value = f(local.value)
    }

    // --- Selection ---

    fun setVoice(voice: Int) = update { it.copy(voice = voice, anchorWord = null, selectionWords = null, chosenBar = null) }

    /** Tap the first word, then the last; a third tap starts a new selection. */
    fun tapWord(wordId: String) = update { l ->
        val anchor = l.anchorWord
        if (anchor == null) {
            l.copy(anchorWord = wordId, selectionWords = wordId to wordId, chosenBar = null)
        } else {
            l.copy(anchorWord = null, selectionWords = anchor to wordId, chosenBar = null)
        }
    }

    /** Dragging over the words selects from [fromWordId] to [toWordId]. */
    fun dragSelect(fromWordId: String, toWordId: String) =
        update { it.copy(anchorWord = null, selectionWords = fromWordId to toWordId, chosenBar = null) }

    fun clearSelection() = update { it.copy(anchorWord = null, selectionWords = null, chosenBar = null) }

    fun setTapMode(on: Boolean) = update { it.copy(tapMode = on) }

    /** The bar (0-based) the selection starts in. */
    fun chooseBar(bar: Int) = update { it.copy(chosenBar = bar, askBar = false) }

    fun dismissBar() = update { it.copy(askBar = false) }

    fun changeBar() = update { it.copy(askBar = true) }

    fun permissionDenied() = update { it.copy(permissionDenied = true) }

    // --- The take ---

    /** Call with the microphone permission granted. */
    fun sing() {
        // From the current inputs, not the (one dispatch later) state flow.
        val st = uiState(song.value, local.value)
        if (st.selection.isEmpty() || job?.isActive == true) return
        val bar = st.startBar
        if (bar == null) {
            update { it.copy(askBar = true) }
            return
        }
        val s = song.value ?: return
        val content = content(s) ?: return
        player.stop()
        update { it.copy(phase = CapturePhase.Preparing, review = null, permissionDenied = false) }
        stopRequested = false
        frames.clear()
        taps.clear()
        take = null
        val syllableIds = st.selection
        val voice = st.voice
        job = viewModelScope.launch {
            val app = settings.settings.first()
            val latency = app.latencyProfiles[audio.state.value.route]?.roundTripMs
            update { it.copy(latencyMs = latency) }
            val collector = launch { audio.pitch.collect { synchronized(frames) { frames += it } } }
            try {
                audio.startPitch(app.pitch)
                if (!waitFor(MIC_TIMEOUT_MS) { synchronized(frames) { frames.isNotEmpty() } }) {
                    fail(CaptureError.NO_MICROPHONE)
                    return@launch
                }
                audio.startTakeRecording(MAX_TAKE_SECONDS)
                val bpb = s.timeSignature.num
                audio.startMetronome(MetronomeSettings(s.tempoBpm, bpb))
                if (!waitFor(METRONOME_TIMEOUT_MS) { audio.metronomeGrid() != null }) {
                    fail(CaptureError.NO_METRONOME)
                    return@launch
                }
                val grid = audio.metronomeGrid()!!
                val clock = BeatClock.forTake(grid.anchorFrame, grid.framesPerBeat, bpb, bar, audio.sampleRate)
                // Count-in and singing, read from the audio clock.
                while (!stopRequested) {
                    val b = audio.metronomeBeat()
                    val beat = b?.beat ?: -1
                    if (beat < bpb) {
                        update { it.copy(phase = CapturePhase.CountIn((beat.coerceAtLeast(0) + 1).toInt(), bpb)) }
                    } else {
                        val sectionBeat = (bar * bpb) + (beat - bpb)
                        update {
                            it.copy(phase = CapturePhase.Singing((sectionBeat / bpb + 1).toInt(), (sectionBeat % bpb + 1).toInt(), taps.size))
                        }
                        // Stop by itself after a long silence once something was sung.
                        val last = synchronized(frames) { frames.lastOrNull() }
                        val lastVoiced = synchronized(frames) { frames.lastOrNull { it.voiced } }
                        if (lastVoiced != null && lastVoiced.frame >= grid.anchorFrame + (bpb * grid.framesPerBeat).toLong()) {
                            if (last != null && last.seconds - lastVoiced.seconds > AUTO_STOP_SECONDS) break
                        }
                    }
                    delay(POLL_MS)
                }
                update { it.copy(phase = CapturePhase.Analyzing) }
                audio.stopMetronome()
                val recording = audio.stopTakeRecording()
                audio.stopPitch()
                collector.cancel()
                val latencyFrames = ((latency ?: 0.0) * audio.sampleRate / 1000.0)
                val countInStart = grid.anchorFrame
                val takeFrames = synchronized(frames) { frames.toList() }
                    .filter { it.frame - latencyFrames >= countInStart }
                    .map { TakeFrame((it.frame - latencyFrames) / audio.sampleRate, it.midi, it.levelDb) }
                val tapSeconds = taps.toList()
                val texts = CaptureOps.syllables(content, voice).associate { it.second.id to it.second.text }
                val weights = syllableIds.map { SyllableAligner.weight(texts[it].orEmpty()) }
                val method = if (local.value.tapMode) TakeMethod.TAPPED else TakeMethod.SUNG
                val timings: List<SyllableTiming?> = withContext(analysis) {
                    if (method == TakeMethod.TAPPED) TapAlong.timings(tapSeconds, takeFrames, syllableIds.size)
                    else SyllableAligner.timings(weights, NoteSegmenter.segment(takeFrames))
                }
                if (timings.all { it == null }) {
                    fail(CaptureError.NOTHING_SUNG)
                    return@launch
                }
                take = Take(recording, takeFrames, clock, syllableIds, CaptureOps.snapshot(content, syllableIds),
                    latency ?: 0.0, s.tempoBpm, content.id, voice)
                showReview(timings, texts, method, content.lengthBeats)
            } finally {
                collector.cancel()
                if (local.value.phase !is CapturePhase.Review && local.value.phase !is CapturePhase.Failed) {
                    withContext(NonCancellable) { stopAudio() }
                }
            }
        }
    }

    /** Ends the take (the Stop button). */
    fun stop() {
        stopRequested = true
    }

    /** Tap-along: one tap per syllable, timed on the audio clock (what is heard now). */
    fun tap() {
        val phase = local.value.phase
        if (phase !is CapturePhase.Singing && phase !is CapturePhase.CountIn) return
        taps += audio.clockFrame() / audio.sampleRate
        if (phase is CapturePhase.Singing) update { it.copy(phase = phase.copy(taps = taps.size)) }
    }

    private suspend fun stopAudio() {
        audio.stopMetronome()
        audio.stopTakeRecording()
        audio.stopPitch()
    }

    private suspend fun fail(reason: CaptureError) {
        stopAudio()
        update { it.copy(phase = CapturePhase.Failed(reason)) }
    }

    private suspend fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        var waited = 0L
        while (!condition()) {
            if (waited >= timeoutMs) return false
            delay(POLL_MS)
            waited += POLL_MS
        }
        return true
    }

    private fun showReview(timings: List<SyllableTiming?>, texts: Map<String, String>, method: TakeMethod, lengthBeats: Int) {
        val t = take ?: return
        val reviewed = t.syllableIds.mapIndexedNotNull { i, id ->
            val timing = timings[i] ?: return@mapIndexedNotNull null
            val r = CaptureOps.result(id, timing.start, timing.end, timing.startMidi, timing.endMidi, timing.curve, t.clock, lengthBeats)
            ReviewSyllable(id, texts[id].orEmpty(), r.startBeat, r.durationBeats, r.pitchStartMidi, r.pitchEndMidi)
        }
        val missing = t.syllableIds.filterIndexed { i, _ -> timings[i] == null }
        val from = reviewed.minOf { it.startBeat }
        val to = reviewed.maxOf { it.endBeat }
        update {
            it.copy(
                phase = CapturePhase.Review,
                review = ReviewState(
                    syllables = reviewed,
                    missing = missing,
                    trace = t.frames.map { f -> t.clock.beat(f.seconds) to f.midi }.filter { it.first in (from - 1.0)..(to + 1.0) },
                    fromBeat = from,
                    toBeat = to,
                    selected = null,
                    method = method,
                ),
            )
        }
    }

    // --- Review ---

    fun selectReviewed(index: Int?) = update { it.copy(review = it.review?.copy(selected = index)) }

    /**
     * Moves the start ([start] = true) or end of the selected syllable by [deltaBeats]. A
     * boundary shared with the neighbour moves for both. Notes are measured again from the take.
     */
    fun nudge(start: Boolean, deltaBeats: Double) {
        val t = take ?: return
        val r = local.value.review ?: return
        val i = r.selected ?: return
        val list = r.syllables.toMutableList()
        val cur = list[i]
        val prev = list.getOrNull(i - 1)
        val next = list.getOrNull(i + 1)
        val min = NUDGE_MIN_BEATS
        if (start) {
            val lower = (prev?.startBeat?.plus(min) ?: 0.0)
            val newStart = (cur.startBeat + deltaBeats).coerceIn(lower, cur.endBeat - min)
            list[i] = remeasure(cur, newStart, cur.endBeat, t)
            if (prev != null && kotlin.math.abs(prev.endBeat - cur.startBeat) < 1e-6 || prev != null && prev.endBeat > newStart) {
                list[i - 1] = remeasure(prev!!, prev.startBeat, newStart, t)
            }
        } else {
            val upper = next?.endBeat?.minus(min) ?: (cur.endBeat + 8.0)
            val newEnd = (cur.endBeat + deltaBeats).coerceIn(cur.startBeat + min, upper)
            list[i] = remeasure(cur, cur.startBeat, newEnd, t)
            if (next != null && (kotlin.math.abs(next.startBeat - cur.endBeat) < 1e-6 || next.startBeat < newEnd)) {
                list[i + 1] = remeasure(next, newEnd, next.endBeat, t)
            }
        }
        update { it.copy(review = r.copy(syllables = list)) }
    }

    private fun remeasure(s: ReviewSyllable, startBeat: Double, endBeat: Double, t: Take): ReviewSyllable {
        val timing = TapAlong.spanTiming(t.clock.seconds(startBeat), t.clock.seconds(endBeat), t.frames)
        return s.copy(startBeat = startBeat, durationBeats = endBeat - startBeat, startMidi = timing.startMidi, endMidi = timing.endMidi)
    }

    fun playTake() {
        val t = take ?: return
        player.play(t.audio.samples, t.audio.sampleRate)
        update { it.copy(review = it.review?.copy(playing = true)) }
    }

    fun stopPlaying() {
        player.stop()
        update { it.copy(review = it.review?.copy(playing = false)) }
    }

    /** Keeps the take: sets exactly the selected syllables, saves the recording and undo values. */
    fun accept() {
        val t = take ?: return
        val r = local.value.review ?: return
        val s = song.value ?: return
        val content = content(s) ?: return
        player.stop()
        val takeId = "take-" + ids.newId()
        val results = r.syllables.map { rs ->
            val timing = TapAlong.spanTiming(t.clock.seconds(rs.startBeat), t.clock.seconds(rs.endBeat), t.frames)
            CaptureOps.result(rs.id, timing.start, timing.end, rs.startMidi, rs.endMidi, timing.curve, t.clock, content.lengthBeats)
                .copy(startBeat = rs.startBeat, durationBeats = rs.durationBeats)
        }
        applyTake(sectionId, results, takeId)
        val info = TakeInfo(
            id = takeId,
            createdAt = clockMillis(),
            contentId = t.contentId,
            voice = t.voice,
            syllableIds = results.map { it.syllableId },
            previous = t.previous.filter { p -> results.any { it.syllableId == p.id } },
            method = r.method,
            tempoBpm = t.tempoBpm,
            latencyMs = t.latencyMs,
            audioFile = "$takeId.wav",
        )
        take = null
        update { it.copy(phase = CapturePhase.Idle, review = null, canUndo = true) }
        appScope.launch { takes.add(songId, info, t.audio.samples, t.audio.sampleRate) }
    }

    /** Throws the take away and sings the same words again. */
    fun singAgain() {
        player.stop()
        take = null
        update { it.copy(phase = CapturePhase.Idle, review = null) }
        sing()
    }

    /** Throws the take away. */
    fun cancel() {
        player.stop()
        stopRequested = true
        job?.cancel()
        take = null
        update { it.copy(phase = CapturePhase.Idle, review = null) }
        appScope.launch { stopAudio() }
    }

    fun dismissError() = update { it.copy(phase = CapturePhase.Idle) }

    /** Undo the last accepted take of this section: its syllables get their old values back. */
    fun undoLastTake() {
        viewModelScope.launch {
            val content = content(song.value) ?: return@launch
            val last = takes.list(songId).firstOrNull { it.contentId == content.id && !it.undone } ?: return@launch
            restoreTake(sectionId, last.previous)
            takes.markUndone(songId, last.id)
            refreshUndo()
        }
    }

    private suspend fun refreshUndo() {
        val content = content(song.filterReady()) ?: return
        val can = takes.list(songId).any { it.contentId == content.id && !it.undone }
        update { it.copy(canUndo = can) }
    }

    private suspend fun StateFlow<Song?>.filterReady(): Song? = value ?: run {
        var s: Song? = null
        repeat(50) {
            s = value
            if (s != null) return@run s
            delay(20)
        }
        s
    }

    override fun onCleared() {
        player.stop()
        if (job?.isActive == true) {
            job?.cancel()
            appScope.launch { stopAudio() }
        }
    }

    companion object {
        fun factory(songId: String, sectionId: String, songVm: SongViewModel) = viewModelFactory {
            initializer {
                val c = appContainer
                CaptureViewModel(
                    songId = songId,
                    sectionId = sectionId,
                    song = songVm.currentSong,
                    applyTake = songVm::applyTake,
                    restoreTake = songVm::restoreTake,
                    audio = c.audio,
                    settings = c.settingsRepository,
                    takes = c.takeRepository,
                    player = AudioTrackTakePlayer(),
                    appScope = c.appScope,
                    analysis = Dispatchers.Default,
                )
            }
        }

        const val POLL_MS = 20L
        const val MIC_TIMEOUT_MS = 3000L
        const val METRONOME_TIMEOUT_MS = 2000L
        /** A take stops by itself after this long without voice. */
        const val AUTO_STOP_SECONDS = 2.5
        const val MAX_TAKE_SECONDS = 120.0
        /** The step of the nudge buttons and the shortest syllable, in beats. */
        const val NUDGE_BEATS = 0.05
        const val NUDGE_MIN_BEATS = 0.05
    }
}
