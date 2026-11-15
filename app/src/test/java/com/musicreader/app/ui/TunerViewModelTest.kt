package com.musicreader.app.ui

import androidx.lifecycle.viewModelScope
import com.musicreader.app.ui.audio.TunerViewModel
import com.musicreader.app.ui.audio.formatCents
import com.musicreader.app.ui.audio.noteName
import com.musicreader.audio.PitchFrame
import com.musicreader.core.dsp.NoiseGate
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.PitchMethod
import com.musicreader.core.model.PitchSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun frame(i: Int, midi: Double?, level: Double = -20.0) =
        PitchFrame(i * 480L, i * 0.01, midi?.let { 440.0 * Math.pow(2.0, (it - 69) / 12) }, midi, if (midi != null) 0.95 else 0.2, level)

    private fun TestScope.rig(settings: AppSettings = AppSettings()): Triple<TunerViewModel, FakeAudioService, FakeSettingsRepository> {
        val audio = FakeAudioService()
        val repo = FakeSettingsRepository(settings)
        return Triple(TunerViewModel(audio, repo, this) { 777L }, audio, repo)
    }

    @Test
    fun startsPitchWithTheSavedSettingsAndShowsTheNote() = runTest(dispatcher) {
        val (vm, audio, _) = rig(AppSettings(pitch = PitchSettings(PitchMethod.MPM, -48.0)))
        vm.start()
        advanceUntilIdle()
        assertEquals(listOf("pitch on MPM"), audio.calls)
        assertEquals(-48.0, audio.pitchSettings!!.noiseGateDb)
        audio.pitch.emit(frame(1, 69.12))
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(69, s.reading!!.note)
        assertEquals(12.0, s.reading!!.cents, 1e-6)
        assertEquals(0.95, s.confidence)
        audio.pitch.emit(frame(2, null, level = -70.0))
        advanceUntilIdle()
        assertEquals(69, vm.state.value.reading!!.note, "a short gap keeps the last note")
        assertTrue(vm.state.value.held)
        audio.pitch.emit(frame(2 + (TunerViewModel.HOLD_SECONDS * 100).toInt() + 1, null, level = -70.0))
        advanceUntilIdle()
        assertNull(vm.state.value.reading, "after the hold: no note")
        assertEquals(3, vm.trace.size)
        assertTrue(vm.trace.midi(1).isNaN())
        // Back from the background: detection starts again, frames still arrive once.
        vm.start()
        advanceUntilIdle()
        assertEquals(listOf("pitch on MPM", "pitch on MPM"), audio.calls)
        audio.pitch.emit(frame(3, 60.0))
        advanceUntilIdle()
        assertEquals(4, vm.trace.size)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun switchingTheMethodIsSavedAndSent() = runTest(dispatcher) {
        val (vm, audio, repo) = rig()
        vm.start()
        advanceUntilIdle()
        vm.setMethod(PitchMethod.MPM)
        advanceUntilIdle()
        assertEquals("pitch MPM -55.0", audio.calls.last())
        assertEquals(PitchMethod.MPM, repo.settings.value.pitch.method)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun roomNoiseOfTwoSecondsSetsTheGate() = runTest(dispatcher) {
        val (vm, audio, repo) = rig()
        vm.start()
        advanceUntilIdle()
        vm.measureNoise()
        for (i in 0 until NoiseGate.FRAMES - 1) audio.pitch.emit(frame(i, null, level = -63.0))
        advanceUntilIdle()
        assertEquals(NoiseGate.FRAMES - 1, vm.state.value.noiseProgress)
        audio.pitch.emit(frame(NoiseGate.FRAMES, null, level = -63.0))
        advanceUntilIdle()
        assertNull(vm.state.value.noiseProgress)
        assertEquals(-53.0, repo.settings.value.pitch.noiseGateDb)
        assertEquals(777L, repo.settings.value.pitch.gateMeasuredAt)
        assertEquals(-53.0, audio.pitchSettings!!.noiseGateDb, "the running tracker uses the new gate")
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun permissionDeniedDoesNotStart() = runTest(dispatcher) {
        val (vm, audio, _) = rig()
        vm.permissionDenied()
        advanceUntilIdle()
        assertTrue(vm.state.value.permissionDenied)
        assertEquals(emptyList(), audio.calls)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun namesAndCents() {
        assertEquals("A", noteName(69, AccidentalPreference.SHARPS))
        assertEquals("C♯", noteName(61, AccidentalPreference.SHARPS))
        assertEquals("D♭", noteName(61, AccidentalPreference.FLATS))
        assertEquals("E", noteName(40, AccidentalPreference.FLATS))
        assertEquals("+12", formatCents(12.2))
        assertEquals("−7", formatCents(-7.4))
        assertEquals("0", formatCents(0.3))
    }
}
