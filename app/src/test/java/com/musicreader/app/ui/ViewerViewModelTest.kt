package com.musicreader.app.ui

import com.musicreader.app.ui.viewer.ViewerViewModel
import com.musicreader.core.data.SettingsRepository
import com.musicreader.core.lyrics.DemoSong
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.Layer
import com.musicreader.core.model.LayerVisibility
import com.musicreader.core.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    override suspend fun update(change: (AppSettings) -> AppSettings) = settings.update(change)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var n = 0
    private val demo = DemoSong.create("demo", IdGenerator { "d${n++}" }, 0)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(song: Song?, settings: FakeSettingsRepository = FakeSettingsRepository()) =
        ViewerViewModel(MutableStateFlow(song), settings, backgroundScope)

    @Test
    fun buildsTheLayoutFromTheSong() = runTest(dispatcher) {
        val vm = viewModel(demo)
        advanceUntilIdle()
        val state = vm.state.value!!
        assertEquals(DemoSong.TITLE, state.title)
        assertEquals(4, state.layout.sections.size)
        assertEquals(3, state.visibleLayerCount)
        assertNull(viewModel(null).state.value)
    }

    @Test
    fun theLastVisibleLayerCannotBeSwitchedOff() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(demo, settings)
        vm.toggleLayer(Layer.TAB)
        vm.toggleLayer(Layer.CHORDS)
        advanceUntilIdle()
        assertEquals(LayerVisibility(lyrics = true, chords = false, tab = false), vm.state.value!!.layers)
        assertEquals(1, vm.state.value!!.visibleLayerCount)
        vm.toggleLayer(Layer.LYRICS) // refused
        advanceUntilIdle()
        assertEquals(LayerVisibility(lyrics = true, chords = false, tab = false), settings.settings.value.viewerLayers)
    }

    @Test
    fun fontScaleAndSpeedAreClampedAndSaved() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val vm = viewModel(demo, settings)
        vm.setFontScale(5f)
        vm.setSpeedFromSlider(0f)
        advanceUntilIdle()
        assertEquals(AppSettings.MAX_VIEWER_FONT_SCALE, settings.settings.value.viewerFontScale)
        assertEquals(AppSettings.MIN_AUTOSCROLL_DP_PER_SECOND, settings.settings.value.autoscrollSpeed, 1e-9)
        assertEquals(0f, vm.state.value!!.speedSliderPosition)
    }

    @Test
    fun playPause() = runTest(dispatcher) {
        val vm = viewModel(demo)
        vm.togglePlaying()
        advanceUntilIdle()
        assertEquals(true, vm.state.value!!.playing)
        vm.pause()
        advanceUntilIdle()
        assertEquals(false, vm.state.value!!.playing)
    }

    @Test
    fun speedSliderIsLogarithmic() {
        assertEquals(2.0, ViewerViewModel.sliderToSpeed(0f), 1e-9)
        assertEquals(150.0, ViewerViewModel.sliderToSpeed(1f), 1e-9)
        assertEquals(kotlin.math.sqrt(2.0 * 150.0), ViewerViewModel.sliderToSpeed(0.5f), 1e-6)
        for (speed in listOf(2.0, 7.5, 15.0, 80.0, 150.0)) {
            assertEquals(speed, ViewerViewModel.sliderToSpeed(ViewerViewModel.speedToSlider(speed)), 1e-4)
        }
        assertEquals(0f, ViewerViewModel.speedToSlider(0.5)) // clamped
    }
}
