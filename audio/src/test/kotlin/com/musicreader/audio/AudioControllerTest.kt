package com.musicreader.audio

import com.musicreader.core.model.AudioRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AudioControllerTest {

    private class Rig(scope: TestScope) {
        val engine = FakeAudioEngine({ scope.testScheduler.currentTime })
        val routes = FakeRouteSource()
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val controller = AudioController(engine, routes, scope.backgroundScope, { scope.testScheduler.currentTime }, dispatcher)
    }

    @Test
    fun metronomeStartsTheOutputOnlyAndStopsItAgain() = runTest {
        val rig = Rig(this)
        rig.controller.startMetronome(MetronomeSettings(90.0, 3))
        runCurrent()
        assertTrue(rig.engine.isRunning)
        assertFalse(rig.engine.withInput, "the metronome never opens the microphone")
        assertEquals(MetronomeSettings(90.0, 3), rig.controller.metronome.value)
        assertTrue(rig.controller.state.value.running)
        rig.controller.stopMetronome()
        runCurrent()
        assertFalse(rig.engine.isRunning)
        assertNull(rig.controller.metronome.value)
        assertEquals(listOf("start 90.0 3", "stop"), rig.engine.metronomeCalls)
    }

    @Test
    fun theBeatComesFromTheAudioClock() = runTest {
        val rig = Rig(this)
        rig.controller.startMetronome(MetronomeSettings(120.0, 4))
        runCurrent()
        assertNull(rig.controller.metronomeBeat()?.takeIf { it.beat >= 0 }, "lead-in before beat 0")
        // 120 BPM = 500 ms per beat; the lead is 120 ms and the fake presents 20 ms behind.
        advanceTimeBy(120 + 20 + 1250)
        val beat = rig.controller.metronomeBeat()!!
        assertEquals(2, beat.beat)
        assertEquals(2, beat.beatInBar)
        assertEquals(0.5, beat.phase, 0.01)
        advanceTimeBy(1000)
        assertEquals(0, rig.controller.metronomeBeat()!!.beatInBar, "beat 1 of bar 2")
    }

    @Test
    fun tempoChangesGoToTheEngineOnlyWhenRunningAndChanged() = runTest {
        val rig = Rig(this)
        rig.controller.updateMetronome(MetronomeSettings(100.0, 4))  // off: ignored
        rig.controller.startMetronome(MetronomeSettings(100.0, 4))
        rig.controller.updateMetronome(MetronomeSettings(100.0, 4))  // same: ignored
        rig.controller.updateMetronome(MetronomeSettings(140.0, 4))
        runCurrent()
        assertEquals(listOf("start 100.0 4", "update 140.0 4"), rig.engine.metronomeCalls)
    }

    @Test
    fun startErrorIsReportedAndNothingRuns() = runTest {
        val rig = Rig(this)
        rig.engine.startError = "ErrorInternal"
        rig.controller.startMetronome(MetronomeSettings(100.0, 4))
        runCurrent()
        assertEquals("ErrorInternal", rig.controller.state.value.error)
        assertNull(rig.controller.metronome.value)
        assertFalse(rig.controller.state.value.running)
    }

    @Test
    fun aDisconnectedStreamIsReopenedAndTheMetronomeKeepsGoing() = runTest {
        val rig = Rig(this)
        rig.controller.startMetronome(MetronomeSettings(100.0, 4))
        runCurrent()
        rig.engine.disconnected = true
        rig.routes.route = AudioRoute.WIRED
        advanceTimeBy(AudioController.WATCHDOG_MS + 10)
        assertEquals(1, rig.engine.restarted)
        assertEquals(AudioRoute.WIRED, rig.controller.state.value.route)
        assertEquals(MetronomeSettings(100.0, 4), rig.controller.metronome.value)
        assertEquals(1, rig.controller.state.value.restarts)
    }

    @Test
    fun pluggingHeadphonesReopensRunningStreamsOnly() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.routes.route = AudioRoute.WIRED
        rig.routes.changeEvents.emit(Unit)
        advanceTimeBy(AudioController.ROUTE_SETTLE_MS + 10)
        assertEquals(0, rig.engine.restarted, "nothing running: just the route is updated")
        assertEquals(AudioRoute.WIRED, rig.controller.state.value.route)
        assertEquals(0, rig.engine.started, "the native engine is not touched while unused")

        rig.controller.startMetronome(MetronomeSettings(100.0, 4))
        runCurrent()
        rig.routes.route = AudioRoute.SPEAKER
        rig.routes.changeEvents.emit(Unit)
        advanceTimeBy(AudioController.ROUTE_SETTLE_MS + 10)
        assertEquals(1, rig.engine.restarted)
        assertEquals(AudioRoute.SPEAKER, rig.controller.state.value.route)
    }

    @Test
    fun goingToTheBackgroundStopsEverything() = runTest {
        val rig = Rig(this)
        rig.controller.startMetronome(MetronomeSettings(100.0, 4))
        rig.controller.startDiagnostics()
        runCurrent()
        rig.controller.stopAll()
        runCurrent()
        assertFalse(rig.engine.isRunning)
        assertNull(rig.controller.metronome.value)
        assertFalse(rig.controller.state.value.running)
    }

    @Test
    fun diagnosticsKeepStreamsRunningUntilClosed() = runTest {
        val rig = Rig(this)
        rig.controller.startDiagnostics()
        runCurrent()
        assertTrue(rig.engine.isRunning)
        assertEquals(48000, rig.controller.diagnostics().sampleRate)
        rig.controller.startMetronome(MetronomeSettings(100.0, 4))
        rig.controller.stopMetronome()
        runCurrent()
        assertTrue(rig.engine.isRunning, "diagnostics still open")
        rig.controller.stopDiagnostics()
        runCurrent()
        assertFalse(rig.engine.isRunning)
    }

    @Test
    fun automaticCalibrationMeasuresTheRoundTrip() = runTest {
        val rig = Rig(this)
        rig.engine.latencyFrames = 2160  // 45 ms
        rig.controller.startMetronome(MetronomeSettings(100.0, 4))
        runCurrent()
        val progress = mutableListOf<CalibrationProgress>()
        val outcome = async { rig.controller.calibrate(CalibrationMode.AUTOMATIC) { progress += it } }
        advanceUntilIdle()
        val measured = assertIs<CalibrationOutcome.Measured>(outcome.await())
        assertEquals(45.0, measured.measurement.latencyMs!!, 0.01)
        assertEquals(5, measured.measurement.found)
        assertEquals(AudioRoute.SPEAKER, measured.route)
        assertNull(rig.controller.metronome.value, "calibration stops the metronome")
        assertTrue(rig.engine.withInput || !rig.engine.isRunning)
        assertFalse(rig.engine.isRunning, "streams stop after calibrating")
        assertEquals(5, progress.last().played)
        assertEquals(5, progress.last().total)
    }

    @Test
    fun manualCalibrationUsesSixteenClicks() = runTest {
        val rig = Rig(this)
        rig.engine.latencyFrames = 4800  // 100 ms
        val outcome = async { rig.controller.calibrate(CalibrationMode.MANUAL) {} }
        advanceUntilIdle()
        val measured = assertIs<CalibrationOutcome.Measured>(outcome.await())
        assertEquals(100.0, measured.measurement.latencyMs!!, 0.01)
        assertEquals(16, measured.measurement.expected)
    }

    @Test
    fun aSilentMicrophoneFails() = runTest {
        val rig = Rig(this)
        rig.engine.micWorks = false
        val outcome = async { rig.controller.calibrate(CalibrationMode.AUTOMATIC) {} }
        advanceUntilIdle()
        assertEquals(CalibrationOutcome.Failed(AudioController.NO_INPUT), outcome.await())
        assertFalse(rig.engine.isRunning)
    }

    @Test
    fun soundsThatAreNeverHeardGiveNotHeard() = runTest {
        val rig = Rig(this)
        rig.engine.latencyFrames = 48000 * 10  // never arrives within the recording
        val outcome = async { rig.controller.calibrate(CalibrationMode.AUTOMATIC) {} }
        advanceUntilIdle()
        assertIs<CalibrationOutcome.NotHeard>(outcome.await())
    }

    @Test
    fun backgroundDuringCalibrationInterrupts() = runTest {
        val rig = Rig(this)
        val outcome = async { rig.controller.calibrate(CalibrationMode.AUTOMATIC) {} }
        advanceTimeBy(1500)
        rig.controller.stopAll()
        advanceUntilIdle()
        assertEquals(CalibrationOutcome.Interrupted, outcome.await())
    }

    @Test
    fun cancellingACalibrationStopsTheStreams() = runTest {
        val rig = Rig(this)
        val job = launch { rig.controller.calibrate(CalibrationMode.MANUAL) {} }
        advanceTimeBy(2000)
        assertTrue(rig.engine.isRunning)
        job.cancel()
        advanceUntilIdle()
        assertFalse(rig.engine.isRunning)
        assertFalse(rig.controller.state.value.running)
    }
}
