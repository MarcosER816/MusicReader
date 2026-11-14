package com.musicreader.app.ui

import com.musicreader.app.ui.audio.CalibrationStep
import com.musicreader.app.ui.audio.CalibrationViewModel
import com.musicreader.app.ui.audio.DiagnosticsViewModel
import com.musicreader.app.ui.audio.FailReason
import com.musicreader.app.ui.audio.MetronomeViewModel
import com.musicreader.audio.AudioController
import com.musicreader.audio.CalibrationMode
import com.musicreader.audio.CalibrationOutcome
import com.musicreader.audio.CalibrationProgress
import com.musicreader.audio.LatencyMeasurement
import com.musicreader.audio.MetronomeBeat
import com.musicreader.core.model.AudioRoute
import com.musicreader.core.model.LatencyMethod
import com.musicreader.core.model.Song
import com.musicreader.core.model.TimeSignature
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AudioViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun song(bpm: Double, ts: TimeSignature) = Song(id = "s", title = "Song", tempoBpm = bpm, timeSignature = ts, createdAt = 0, updatedAt = 0)

    @Test
    fun metronomePlaysTheSongsTempoAndMeterAndFollowsEdits() = runTest(dispatcher) {
        val audio = FakeAudioService()
        val current = MutableStateFlow<Song?>(song(96.0, TimeSignature.THREE_FOUR))
        val vm = MetronomeViewModel(current, audio)
        advanceUntilIdle()
        assertFalse(vm.state.value.running)
        vm.toggle()
        advanceUntilIdle()
        assertTrue(vm.state.value.running)
        assertEquals(96.0, vm.state.value.bpm)
        // 6/8: six beats per bar, each an eighth note (D-018).
        current.value = song(150.0, TimeSignature(6, 8))
        advanceUntilIdle()
        current.value = current.value!!.copy(title = "Renamed")  // no tempo change: nothing sent
        advanceUntilIdle()
        assertEquals(listOf("start 96.0 3", "update 150.0 6"), audio.calls)
        audio.beat = MetronomeBeat(beat = 7, beatInBar = 1, phase = 0.25)
        assertEquals(1, vm.beatNow()!!.beatInBar)
        vm.toggle()
        advanceUntilIdle()
        assertFalse(vm.state.value.running)
        assertEquals("stop", audio.calls.last())
    }

    @Test
    fun editsWhileStoppedDoNotStartTheMetronome() = runTest(dispatcher) {
        val audio = FakeAudioService()
        val current = MutableStateFlow<Song?>(song(96.0, TimeSignature.FOUR_FOUR))
        MetronomeViewModel(current, audio)
        current.value = song(100.0, TimeSignature.FOUR_FOUR)
        advanceUntilIdle()
        assertEquals(emptyList(), audio.calls)
    }

    @Test
    fun automaticCalibrationShowsProgressAndSavesPerRoute() = runTest(dispatcher) {
        val audio = FakeAudioService()
        audio.state.value = audio.state.value.copy(route = AudioRoute.WIRED)
        val settings = FakeSettingsRepository()
        val vm = CalibrationViewModel(audio, settings, this) { 1234L }
        advanceUntilIdle()
        assertEquals(AudioRoute.WIRED, vm.state.value.route)
        vm.start(CalibrationMode.AUTOMATIC)
        vm.start(CalibrationMode.MANUAL)  // ignored while one runs
        runCurrent()
        assertEquals(CalibrationStep.Running(CalibrationMode.AUTOMATIC, 0, AudioController.CHIRP_COUNT), vm.state.value.step)
        audio.progress!!(CalibrationProgress(CalibrationMode.AUTOMATIC, 3, 5))
        runCurrent()
        assertEquals(CalibrationStep.Running(CalibrationMode.AUTOMATIC, 3, 5), vm.state.value.step)
        audio.outcome.complete(CalibrationOutcome.Measured(LatencyMeasurement(21.7, 0.96, 5, 5, 0.2), AudioRoute.WIRED))
        advanceUntilIdle()
        val done = assertIs<CalibrationStep.Done>(vm.state.value.step)
        assertFalse(done.saved)
        assertFalse(done.lowConfidence)
        vm.save()
        advanceUntilIdle()
        val profile = settings.settings.value.latencyProfiles.getValue(AudioRoute.WIRED)
        assertEquals(21.7, profile.roundTripMs)
        assertEquals(LatencyMethod.AUTOMATIC, profile.method)
        assertEquals(0.96, profile.confidence)
        assertEquals(1234L, profile.measuredAt)
        assertTrue((vm.state.value.step as CalibrationStep.Done).saved)
        assertEquals(profile, vm.state.value.currentProfile)
        assertEquals(listOf("calibrate AUTOMATIC"), audio.calls)
        vm.clear(AudioRoute.WIRED)
        advanceUntilIdle()
        assertNull(vm.state.value.currentProfile)
    }

    @Test
    fun manualLowConfidenceAndFailures() = runTest(dispatcher) {
        val audio = FakeAudioService()
        val settings = FakeSettingsRepository()
        val vm = CalibrationViewModel(audio, settings, this) { 0L }
        vm.start(CalibrationMode.MANUAL)
        audio.outcome.complete(CalibrationOutcome.Measured(LatencyMeasurement(150.0, 0.3, 9, 16, 25.0), AudioRoute.SPEAKER))
        advanceUntilIdle()
        assertTrue(assertIs<CalibrationStep.Done>(vm.state.value.step).lowConfidence)
        vm.save()
        advanceUntilIdle()
        assertEquals(LatencyMethod.MANUAL, settings.settings.value.latencyProfiles.getValue(AudioRoute.SPEAKER).method)

        for ((outcome, expected) in listOf(
            CalibrationOutcome.NotHeard(LatencyMeasurement(null, 0.0, 0, 5, 0.0)) to CalibrationStep.NotHeard(CalibrationMode.AUTOMATIC),
            CalibrationOutcome.Failed(AudioController.NO_INPUT) to CalibrationStep.Failed(FailReason.NO_INPUT, AudioController.NO_INPUT),
            CalibrationOutcome.Failed("ErrorBusy") to CalibrationStep.Failed(FailReason.START, "ErrorBusy"),
            CalibrationOutcome.Interrupted to CalibrationStep.Interrupted,
        )) {
            vm.back()
            audio.outcome = kotlinx.coroutines.CompletableDeferred()
            vm.start(CalibrationMode.AUTOMATIC)
            audio.outcome.complete(outcome)
            advanceUntilIdle()
            assertEquals(expected, vm.state.value.step)
        }
    }

    @Test
    fun permissionDeniedAndCancel() = runTest(dispatcher) {
        val audio = FakeAudioService()
        val vm = CalibrationViewModel(audio, FakeSettingsRepository(), this) { 0L }
        vm.permissionDenied()
        advanceUntilIdle()
        assertEquals(CalibrationStep.PermissionDenied, vm.state.value.step)
        vm.back()
        vm.start(CalibrationMode.MANUAL)
        runCurrent()
        vm.cancel()
        advanceUntilIdle()
        assertEquals(CalibrationStep.Ready, vm.state.value.step)
        assertTrue(audio.outcome.isActive, "the fake calibration was cancelled, not completed")
    }

    @Test
    fun bluetoothIsFlagged() = runTest(dispatcher) {
        val audio = FakeAudioService()
        audio.state.value = audio.state.value.copy(route = AudioRoute.BLUETOOTH)
        val vm = CalibrationViewModel(audio, FakeSettingsRepository(), this) { 0L }
        advanceUntilIdle()
        assertTrue(vm.state.value.bluetooth)
    }

    @Test
    fun diagnosticsRunWhileOpenAndRefresh() = runTest(dispatcher) {
        val audio = FakeAudioService()
        val settings = FakeSettingsRepository()
        val vm = DiagnosticsViewModel(audio, settings)
        runCurrent()
        assertEquals(listOf("diagnostics on"), audio.calls)
        assertTrue(vm.state.value.running)
        assertEquals(96, vm.state.value.engine.framesPerBurst)
        assertEquals(12.5, vm.state.value.clockSeconds)
        audio.clock = 13.0
        advanceTimeBy(DiagnosticsViewModel.REFRESH_MS + 1)
        assertEquals(13.0, vm.state.value.clockSeconds)
        assertNull(vm.state.value.savedLatency)
        // The refresh loop runs until the screen closes; end it like closing the screen does.
        vm.viewModelScope.coroutineContext.cancelChildren()
    }
}
