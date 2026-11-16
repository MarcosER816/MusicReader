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
import kotlin.test.assertNotNull
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

    @Test
    fun twoAutomaticCalibrationsInARowBothMeasure() = runTest {
        val rig = Rig(this)
        rig.engine.latencyFrames = 4110  // 85.6 ms, Mark's speaker
        repeat(2) { round ->
            val outcome = async { rig.controller.calibrate(CalibrationMode.AUTOMATIC) {} }
            advanceUntilIdle()
            val measured = assertIs<CalibrationOutcome.Measured>(outcome.await(), "round ${round + 1}")
            assertEquals(85.625, measured.measurement.latencyMs!!, 0.01)
            assertFalse(rig.engine.isRunning)
        }
        assertEquals(2, rig.engine.started)
    }

    @Test
    fun pitchOpensTheMicrophoneAndStreamsFrames() = runTest {
        val rig = Rig(this)
        val received = mutableListOf<PitchFrame>()
        val collector = launch { rig.controller.pitch.collect { received += it } }
        rig.controller.startPitch(com.musicreader.core.model.PitchSettings(com.musicreader.core.model.PitchMethod.MPM, -50.0))
        runCurrent()
        assertTrue(rig.engine.isRunning)
        assertTrue(rig.engine.withInput, "pitch needs the microphone")
        assertEquals(listOf("configure MPM -50.0", "reset"), rig.engine.pitchLog)
        rig.engine.pitchQueue += PitchFrame(480, 0.01, 440.0, 69.0, 0.97, -20.0)
        rig.engine.pitchQueue += PitchFrame(960, 0.02, null, null, 0.3, -60.0)
        advanceTimeBy(AudioController.PITCH_POLL_MS + 1)
        assertEquals(listOf(69.0, null), received.map { it.midi })
        rig.controller.updatePitch(com.musicreader.core.model.PitchSettings(com.musicreader.core.model.PitchMethod.YIN, -45.0))
        runCurrent()
        assertEquals("configure YIN -45.0", rig.engine.pitchLog.last())
        rig.controller.stopPitch()
        runCurrent()
        assertFalse(rig.engine.isRunning)
        rig.engine.pitchQueue += PitchFrame(1440, 0.03, 440.0, 69.0, 0.97, -20.0)
        advanceTimeBy(200)
        assertEquals(2, received.size, "no polling after stop")
        collector.cancel()
    }

    @Test
    fun calibrationStopsPitchDetection() = runTest {
        val rig = Rig(this)
        rig.controller.startPitch(com.musicreader.core.model.PitchSettings())
        runCurrent()
        val outcome = async { rig.controller.calibrate(CalibrationMode.AUTOMATIC) {} }
        advanceUntilIdle()
        assertIs<CalibrationOutcome.Measured>(outcome.await())
        assertFalse(rig.engine.isRunning, "pitch was stopped, calibration released the engine")
    }

    @Test
    fun previewMathAlignsTheMetronomeWithTheSectionBeats() {
        // From beat 5.5 at 120 BPM in 4/4: the metronome starts on beat 6 (250 ms later) and its
        // beat 2 (section beat 8) is the accented beat 1 of bar 3.
        assertEquals(6, PreviewMath.firstWholeBeat(5.5))
        assertEquals(250.0, PreviewMath.extraLeadMs(5.5, 120.0), 1e-9)
        assertEquals(2, PreviewMath.barOrigin(5.5, 4))
        assertEquals(0, PreviewMath.barOrigin(8.0, 4))
        assertEquals(96000.0 - 12000.0, PreviewMath.frameAtFromBeat(96000, 5.5, 24000.0), 1e-9)
        val clock = PreviewClock(5.5, 84000.0, 24000.0, 10.0)
        val frames = PreviewMath.frames(
            listOf(
                PreviewNote(7.0, 8.0, 69.0, 69.0),
                PreviewNote(4.0, 6.0, 57.0, 59.0),     // the cursor is inside it: starts there
                PreviewNote(1.0, 2.0, 60.0, 60.0),     // already past: not played
            ),
            clock,
        )
        assertEquals(2, frames.size)
        assertEquals(84000L, frames[0].startFrame)
        assertEquals(84000L + 12000L, frames[0].endFrame)
        assertEquals(PreviewMath.midiToHz(58.5), frames[0].startHz, 1e-9)   // 3/4 through a 57→59 glide
        assertEquals(84000L + 36000L, frames[1].startFrame)
        assertEquals(440.0, frames[1].endHz, 1e-9)
        assertEquals(6.0, clock.beatAt(84000.0 + 12000.0), 1e-12)
    }

    @Test
    fun previewPlaysTheMelodyWithTheMetronomeAndStops() = runTest {
        val rig = Rig(this)
        rig.controller.startMetronome(MetronomeSettings(100.0, 4))
        runCurrent()
        rig.controller.startPreview(120.0, 4, 2.0, listOf(PreviewNote(2.0, 3.0, 60.0, 60.0), PreviewNote(3.0, 4.0, 62.0, 62.0)))
        runCurrent()
        assertNull(rig.controller.metronome.value, "the song metronome stops")
        assertEquals("start 120.0 4 origin 2", rig.engine.metronomeCalls.last())
        assertEquals(2, rig.engine.melodyNotes.size)
        val clock = assertNotNull(rig.controller.preview.value)
        assertEquals(2.0, clock.fromBeat)
        advanceTimeBy(1000)
        val beat = rig.controller.previewBeat()!!
        assertTrue(beat > 2.0 && beat < 4.5, "playhead from the audio clock: $beat")
        assertFalse(rig.engine.withInput, "no microphone for the preview")
        rig.controller.stopPreview()
        runCurrent()
        assertNull(rig.controller.preview.value)
        assertTrue(rig.engine.melodyNotes.isEmpty())
        assertFalse(rig.engine.isRunning)
        assertNull(rig.controller.previewBeat())
    }
}
