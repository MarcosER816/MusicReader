package com.musicreader.app.ui

import androidx.lifecycle.viewModelScope
import com.musicreader.app.ui.capture.CapturePhase
import com.musicreader.app.ui.capture.CaptureViewModel
import com.musicreader.app.ui.capture.TakePlayer
import com.musicreader.audio.BeatGridInfo
import com.musicreader.audio.MetronomeBeat
import com.musicreader.audio.PitchFrame
import com.musicreader.core.data.TakeRepository
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.AudioRoute
import com.musicreader.core.model.CaptureOps
import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.LatencyMethod
import com.musicreader.core.model.LatencyProfile
import com.musicreader.core.model.LyricLine
import com.musicreader.core.model.Section
import com.musicreader.core.model.SectionContent
import com.musicreader.core.model.SectionType
import com.musicreader.core.model.Song
import com.musicreader.core.model.Syllable
import com.musicreader.core.model.TakeInfo
import com.musicreader.core.model.Word
import com.musicreader.core.model.contentOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTakes : TakeRepository {
    val takes = mutableListOf<TakeInfo>()
    override suspend fun list(songId: String) = takes.toList()
    override suspend fun add(songId: String, take: TakeInfo, samples: FloatArray, sampleRate: Int) {
        takes.add(0, take)
    }
    override suspend fun markUndone(songId: String, takeId: String) {
        takes.replaceAll { if (it.id == takeId) it.copy(undone = true) else it }
    }
    override suspend fun audio(songId: String, takeId: String): FloatArray? = null
}

private class FakePlayer : TakePlayer {
    var played = 0
    override fun play(samples: FloatArray, sampleRate: Int) { played++ }
    override fun stop() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun syl(id: String, text: String) = Syllable(id, text)

    private val content = SectionContent(
        id = "c1", lengthBeats = 16,
        lyricLines = listOf(
            LyricLine("l1", text = "Hola mi amor", words = listOf(
                Word("w1", "Hola", listOf(syl("s1", "Ho"), syl("s2", "la"))),
                Word("w2", "mi", listOf(syl("s3", "mi"))),
                Word("w3", "amor", listOf(syl("s4", "a"), syl("s5", "mor"))),
            )),
            LyricLine("l2", text = "ven ya", words = listOf(Word("w4", "ven", listOf(syl("s6", "ven"))), Word("w5", "ya", listOf(syl("s7", "ya"))))),
        ),
    )
    private val song0 = Song(
        id = "song", title = "T", tempoBpm = 120.0, sections = listOf(Section("sec", SectionType.VERSE, contentId = "c1")),
        sectionContents = listOf(content), createdAt = 0, updatedAt = 0,
    )

    private class Rig(scope: TestScope, song: Song, settings: AppSettings) {
        val audio = FakeAudioService()
        val song = MutableStateFlow<Song?>(song)
        val repo = FakeSettingsRepository(settings)
        val takes = FakeTakes()
        val player = FakePlayer()
        var n = 0
        val vm = CaptureViewModel(
            songId = "song", sectionId = "sec", song = this.song,
            applyTake = { _, results, takeId ->
                this.song.value = this.song.value!!.let { s ->
                    s.copy(sectionContents = s.sectionContents.map { CaptureOps.apply(it, results, takeId) })
                }
            },
            restoreTake = { _, previous ->
                this.song.value = this.song.value!!.let { s -> s.copy(sectionContents = s.sectionContents.map { CaptureOps.restore(it, previous) }) }
            },
            audio = audio, settings = repo, takes = takes, player = player, appScope = scope,
            analysis = StandardTestDispatcher(scope.testScheduler), ids = IdGenerator { "id${n++}" }, clockMillis = { 42L },
        )
    }

    private fun content(r: Rig) = r.song.value!!.contentOf(r.song.value!!.sections.first())

    /**
     * Sings [notes] (midi, seconds) starting on the section's first beat, then silence. At 120
     * BPM a beat is 0.5 s = 24000 frames. Pitch frames are late by the route latency.
     */
    private suspend fun TestScope.singTake(r: Rig, notes: List<Pair<Double, Double>>, latencyMs: Double, startBar: Int = 0) {
        val bpb = 4
        val anchor = 96000L  // metronome beat 0
        r.audio.grid = BeatGridInfo(anchor, 0, 24000.0, bpb, 0)
        r.audio.pitch.emit(PitchFrame(0, 0.0, null, null, 0.0, -70.0))  // the microphone is open
        runCurrent()
        advanceTimeBy(100)
        // Count-in.
        r.audio.beat = MetronomeBeat(1, 1, 0.0)
        advanceTimeBy(50)
        assertIs<CapturePhase.CountIn>(r.vm.state.value.phase)
        r.audio.beat = MetronomeBeat(4, 0, 0.0)
        advanceTimeBy(50)
        assertIs<CapturePhase.Singing>(r.vm.state.value.phase)
        // The sung frames: section bar [startBar] starts at anchor + 1 bar.
        val sectionStart = anchor + bpb * 24000L
        val lat = (latencyMs * 48).toLong()
        var frame = sectionStart
        for ((midi, seconds) in notes) {
            repeat((seconds * 100).toInt()) {
                r.audio.pitch.emit(PitchFrame(frame + lat, (frame + lat) / 48000.0, 440.0, midi, 0.95, -20.0))
                frame += 480
            }
            repeat(10) {
                r.audio.pitch.emit(PitchFrame(frame + lat, (frame + lat) / 48000.0, null, null, 0.2, -70.0))
                frame += 480
            }
        }
        r.audio.pitch.emit(PitchFrame(frame + lat, (frame + lat) / 48000.0, null, null, 0.2, -70.0))
        runCurrent()
    }

    @Test
    fun selectionByTapsAndTheAskedBar() = runTest(dispatcher) {
        val r = Rig(this, song0, AppSettings())
        advanceUntilIdle()
        r.vm.tapWord("w2")
        r.vm.tapWord("w3")
        advanceUntilIdle()
        assertEquals(listOf("s3", "s4", "s5"), r.vm.state.value.selection)
        assertEquals("mi amor", r.vm.state.value.selectionText)
        assertNull(r.vm.state.value.startBar)
        r.vm.sing()
        advanceUntilIdle()
        assertTrue(r.vm.state.value.askBar, "no timing yet: asks for the bar")
        assertEquals(emptyList(), r.audio.calls)
        r.vm.chooseBar(1)
        advanceUntilIdle()
        assertEquals(1, r.vm.state.value.startBar)
        // A new selection forgets the chosen bar.
        r.vm.tapWord("w4")
        advanceUntilIdle()
        assertEquals(listOf("s6"), r.vm.state.value.selection)
        assertNull(r.vm.state.value.startBar)
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun aSungTakeIsReviewedAcceptedAndOnlyTheSelectionChanges() = runTest(dispatcher) {
        val settings = AppSettings(latencyProfiles = mapOf(AudioRoute.SPEAKER to LatencyProfile(80.0, LatencyMethod.AUTOMATIC, 0.9, 1)))
        val r = Rig(this, song0, settings)
        advanceUntilIdle()
        r.vm.tapWord("w1")
        r.vm.tapWord("w2")  // "Ho la mi"
        r.vm.chooseBar(0)
        r.vm.sing()
        runCurrent()
        assertEquals(listOf("pitch on YIN"), r.audio.calls.take(1))
        singTake(r, listOf(60.0 to 0.4, 62.0 to 0.4, 64.0 to 0.8), latencyMs = 80.0)
        // Silence for longer than the auto-stop.
        r.audio.pitch.emit(PitchFrame(10_000_000, 10_000_000 / 48000.0, null, null, 0.1, -70.0))
        advanceUntilIdle()
        assertEquals(CapturePhase.Review, r.vm.state.value.phase)
        assertTrue("record" in r.audio.calls && "start 120.0 4" in r.audio.calls && "pitch off" in r.audio.calls)
        val review = assertNotNull(r.vm.state.value.review)
        assertEquals(listOf("s1", "s2", "s3"), review.syllables.map { it.id })
        // 0.4 s note + 0.1 s silence at 2 beats per second: starts at beats 0, 1, 2 (latency removed).
        assertEquals(0.0, review.syllables[0].startBeat, 0.03)
        assertEquals(1.0, review.syllables[1].startBeat, 0.03)
        assertEquals(2.0, review.syllables[2].startBeat, 0.03)
        assertEquals(0.8, review.syllables[0].durationBeats, 0.03)
        assertEquals(62.0, review.syllables[1].startMidi!!, 0.01)
        // Nudge the end of "Ho" later; play; accept.
        r.vm.selectReviewed(0)
        r.vm.nudge(start = false, deltaBeats = 0.1)
        runCurrent()
        assertEquals(0.9, r.vm.state.value.review!!.syllables[0].durationBeats, 0.03)
        r.vm.playTake()
        assertEquals(1, r.player.played)
        r.vm.accept()
        advanceUntilIdle()
        val c = content(r)
        val s1 = CaptureOps.findSyllable(c, "s1")!!
        assertEquals(0.0, s1.startBeat!!, 0.03)
        assertEquals(0.9, s1.durationBeats!!, 0.03)
        assertEquals(60.0, s1.pitchStartMidi!!, 0.01)
        assertTrue(s1.pitchCurve.isNotEmpty())
        assertEquals(64.0, CaptureOps.findSyllable(c, "s3")!!.pitchEndMidi!!, 0.01)
        for (id in listOf("s4", "s5", "s6", "s7")) assertNull(CaptureOps.findSyllable(c, id)!!.startBeat, id)
        // The take is stored with the values before it, so it can be undone.
        val stored = r.takes.takes.single()
        assertEquals(listOf("s1", "s2", "s3"), stored.syllableIds)
        assertEquals(80.0, stored.latencyMs)
        assertTrue(r.vm.state.value.canUndo)
        assertEquals(CapturePhase.Idle, r.vm.state.value.phase)
        r.vm.undoLastTake()
        advanceUntilIdle()
        assertNull(CaptureOps.findSyllable(content(r), "s1")!!.startBeat)
        assertEquals(false, r.vm.state.value.canUndo)
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun tapAlongTakesItsTimingFromTheTaps() = runTest(dispatcher) {
        val r = Rig(this, song0, AppSettings())
        advanceUntilIdle()
        r.vm.tapWord("w4")
        r.vm.tapWord("w5")  // "ven ya"
        r.vm.chooseBar(2)
        r.vm.setTapMode(true)
        r.vm.sing()
        runCurrent()
        val anchor = 96000L
        r.audio.grid = BeatGridInfo(anchor, 0, 24000.0, 4, 0)
        r.audio.pitch.emit(PitchFrame(0, 0.0, null, null, 0.0, -70.0))
        runCurrent()
        advanceTimeBy(100)
        r.audio.beat = MetronomeBeat(4, 0, 0.0)
        advanceTimeBy(50)
        val sectionStart = anchor + 4 * 24000L  // bar 2 of the section (beat 8) is here
        // Taps at beat 8.0 and 9.5 of the section (no latency: taps are on the heard timeline).
        r.audio.frameNow = sectionStart.toDouble()
        r.vm.tap()
        r.audio.frameNow = sectionStart + 1.5 * 24000
        r.vm.tap()
        var frame = sectionStart
        repeat(200) {
            r.audio.pitch.emit(PitchFrame(frame, frame / 48000.0, 440.0, if (frame < sectionStart + 36000) 57.0 else 59.0, 0.9, -20.0))
            frame += 480
        }
        runCurrent()
        r.vm.stop()
        advanceUntilIdle()
        val review = assertNotNull(r.vm.state.value.review)
        assertEquals(8.0, review.syllables[0].startBeat, 0.02)
        assertEquals(9.5, review.syllables[1].startBeat, 0.02)
        assertEquals(57.0, review.syllables[0].startMidi!!, 0.01)
        assertEquals(59.0, review.syllables[1].startMidi!!, 0.01)
        assertEquals(emptyList(), review.missing)
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun noMicrophoneFailsAndCancelStopsAudio() = runTest(dispatcher) {
        val r = Rig(this, song0, AppSettings())
        advanceUntilIdle()
        r.vm.tapWord("w1")
        r.vm.chooseBar(0)
        r.vm.sing()
        advanceTimeBy(CaptureViewModel.MIC_TIMEOUT_MS + 200)
        runCurrent()
        assertEquals(CapturePhase.Failed(com.musicreader.app.ui.capture.CaptureError.NO_MICROPHONE), r.vm.state.value.phase)
        assertTrue("pitch off" in r.audio.calls)
        r.vm.dismissError()
        runCurrent()
        assertEquals(CapturePhase.Idle, r.vm.state.value.phase)
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun theSecondVoiceIsSelectedSeparately() = runTest(dispatcher) {
        val withSecond = song0.copy(sectionContents = listOf(content.copy(lyricLines = content.lyricLines + LyricLine(
            "v2", voice = LyricLine.SECOND_VOICE, text = "uh", pairedLineId = "l1", words = listOf(Word("w9", "uh", listOf(syl("s9", "uh")))),
        ))))
        val r = Rig(this, withSecond, AppSettings())
        advanceUntilIdle()
        assertTrue(r.vm.state.value.hasSecondVoice)
        r.vm.setVoice(LyricLine.SECOND_VOICE)
        advanceUntilIdle()
        assertEquals(listOf("w9"), r.vm.state.value.lines.flatMap { l -> l.words.map { it.id } })
        r.vm.tapWord("w9")
        advanceUntilIdle()
        assertEquals(listOf("s9"), r.vm.state.value.selection)
        r.vm.viewModelScope.coroutineContext.cancelChildren()
    }
}
