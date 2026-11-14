package com.musicreader.app.ui

import com.musicreader.app.ui.settings.SettingsViewModel
import com.musicreader.app.ui.viewer.ViewerViewModel
import com.musicreader.core.lyrics.DemoSong
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.Layer
import com.musicreader.core.model.LayerVisibility
import com.musicreader.core.model.NoteLetter
import com.musicreader.core.model.UiLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Saves run in the test's own scope: each one finishes, and advanceUntilIdle waits for it. */
    private fun TestScope.viewModel(repo: FakeSettingsRepository) = SettingsViewModel(repo, this)

    @Test
    fun startsWithTheDefaults() = runTest(dispatcher) {
        val vm = viewModel(FakeSettingsRepository())
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(AppSettings(), state.settings)
        assertEquals(emptyList(), state.similarFingers)
        assertEquals(3, state.visibleLayerCount)
    }

    @Test
    fun chordColorChangesAndResets() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.setChordColor(NoteLetter.C, 0xFF00FF00)
        vm.setChordColor(NoteLetter.A, 0xFF0000FF)
        advanceUntilIdle()
        assertEquals(0xFF00FF00, repo.settings.value.chordColors[NoteLetter.C])
        assertEquals(0xFF00FF00, vm.state.value.settings.chordColors[NoteLetter.C])
        vm.resetChordColor(NoteLetter.C)
        advanceUntilIdle()
        assertEquals(AppSettings.DEFAULT_CHORD_COLORS[NoteLetter.C], repo.settings.value.chordColors[NoteLetter.C])
        assertEquals(0xFF0000FF, repo.settings.value.chordColors[NoteLetter.A])
        vm.resetChordColors()
        advanceUntilIdle()
        assertEquals(AppSettings.DEFAULT_CHORD_COLORS, repo.settings.value.chordColors)
    }

    @Test
    fun fingerColorsWarnWhenTwoLookAlike() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        // Finger 3 gets almost the color of finger 1.
        val nearFinger1 = AppSettings.DEFAULT_FINGER_COLORS.getValue(1) + 0x020202
        vm.setFingerColor(3, nearFinger1)
        advanceUntilIdle()
        assertEquals(listOf(1 to 3), vm.state.value.similarFingers)
        vm.resetFingerColor(3)
        advanceUntilIdle()
        assertEquals(emptyList(), vm.state.value.similarFingers)
        vm.setFingerColor(5, 0xFF000000) // no thumb (D-012): ignored
        vm.setFingerColor(2, 0xFF101010)
        advanceUntilIdle()
        assertEquals(AppSettings.DEFAULT_FINGER_COLORS.keys, repo.settings.value.fingerColors.keys)
        vm.resetFingerColors()
        advanceUntilIdle()
        assertEquals(AppSettings.DEFAULT_FINGER_COLORS, repo.settings.value.fingerColors)
    }

    @Test
    fun defaultLayersKeepOneVisible() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.toggleViewerLayer(Layer.TAB)
        vm.toggleViewerLayer(Layer.LYRICS)
        vm.toggleViewerLayer(Layer.CHORDS) // the last one stays on
        advanceUntilIdle()
        assertEquals(LayerVisibility(lyrics = false, chords = true, tab = false), repo.settings.value.viewerLayers)
        assertEquals(1, vm.state.value.visibleLayerCount)
    }

    @Test
    fun otherChoicesAreSaved() = runTest(dispatcher) {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.setAccidentals(AccidentalPreference.FLATS)
        vm.setLeftHanded(true)
        vm.setLanguage(UiLanguage.ES)
        advanceUntilIdle()
        val s = repo.settings.value
        assertEquals(AccidentalPreference.FLATS, s.accidentals)
        assertTrue(s.leftHanded)
        assertEquals(UiLanguage.ES, s.uiLanguage)
    }

    /** The viewer reads the same settings flow, so a change shows there at once. */
    @Test
    fun aColorChangeReachesTheOpenViewer() = runTest(dispatcher) {
        var n = 0
        val demo = DemoSong.create("demo", IdGenerator { "d${n++}" }, 0)
        val repo = FakeSettingsRepository()
        val viewer = ViewerViewModel(MutableStateFlow(demo), MutableStateFlow(TestDictionary.dictionary), repo, backgroundScope)
        val settings = viewModel(repo)
        advanceUntilIdle()
        assertEquals(AppSettings.DEFAULT_CHORD_COLORS[NoteLetter.C], viewer.state.value!!.settings.chordColors[NoteLetter.C])
        settings.setChordColor(NoteLetter.C, 0xFF00FF00)
        settings.setFingerColor(1, 0xFFFFFFFF)
        advanceUntilIdle()
        assertEquals(0xFF00FF00, viewer.state.value!!.settings.chordColors[NoteLetter.C])
        assertEquals(0xFFFFFFFF, viewer.state.value!!.settings.fingerColors[1])
    }
}
