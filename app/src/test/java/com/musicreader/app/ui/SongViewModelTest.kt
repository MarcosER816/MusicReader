package com.musicreader.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.musicreader.app.ui.song.SongUiState
import com.musicreader.app.ui.song.SongViewModel
import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.Language
import com.musicreader.core.model.SectionType
import com.musicreader.core.model.SongOps
import com.musicreader.core.model.TimeSignature
import com.musicreader.core.model.contentOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
class SongViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var idCounter = 0
    private val ids = IdGenerator { "id${idCounter++}" }
    private var now = 1_000L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(repo: FakeSongRepository, openSettings: Boolean = false) = SongViewModel(
        songId = "song",
        savedState = SavedStateHandle(),
        openSettingsRequested = openSettings,
        repository = repo,
        appScope = backgroundScope,
        ids = ids,
        clock = { now++ },
    )

    private fun ready(vm: SongViewModel) = (vm.state.value as SongUiState.Ready).editor

    private fun repoWithSong() = FakeSongRepository(listOf(SongOps.newSong("song", "Song", Language.ES, 0)))

    @Test
    fun editsAreSavedShortlyAfterTheLastChange() = runTest(dispatcher) {
        val repo = repoWithSong()
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertTrue(repo.saved.isEmpty(), "loading does not save")

        vm.addSection(SectionType.INTRO, 2, null)
        vm.addSection(SectionType.VERSE, 8, null)
        vm.addSection(SectionType.CHORUS, 8, "Coro")
        advanceTimeBy(SongViewModel.SAVE_DELAY_MS / 2)
        assertTrue(repo.saved.isEmpty(), "still within the save delay")
        advanceUntilIdle()

        val saved = repo.songs.getValue("song")
        assertEquals(listOf(SectionType.INTRO, SectionType.VERSE, SectionType.CHORUS), saved.sections.map { it.type })
        assertEquals(1, repo.saved.size, "three quick edits are one write")
        assertEquals(listOf(2, 8, 8), ready(vm).sections.map { it.bars })
    }

    @Test
    fun moveSectionByKeyAndUndoDelete() = runTest(dispatcher) {
        val repo = repoWithSong()
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.addSection(SectionType.INTRO, 2, null)
        vm.addSection(SectionType.VERSE, 8, null)
        vm.addSection(SectionType.OUTRO, 4, null)
        advanceUntilIdle()
        val (intro, verse, outro) = ready(vm).sections.map { it.id }

        vm.moveSection(fromId = outro, toId = intro)
        advanceUntilIdle()
        assertEquals(listOf(outro, intro, verse), ready(vm).sections.map { it.id })

        vm.deleteSection(intro)
        advanceUntilIdle()
        assertEquals(listOf(outro, verse), repo.songs.getValue("song").sections.map { it.id })
        vm.undoDeleteSection()
        advanceUntilIdle()
        assertEquals(listOf(outro, intro, verse), repo.songs.getValue("song").sections.map { it.id })
    }

    @Test
    fun linkedCopiesShareLengthUntilUnlinked() = runTest(dispatcher) {
        val repo = repoWithSong()
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.addSection(SectionType.CHORUS, 8, null)
        advanceUntilIdle()
        val chorus = ready(vm).sections.single().id
        vm.duplicateSection(chorus, linked = true)
        advanceUntilIdle()
        val copy = ready(vm).sections[1]
        assertEquals(2, copy.linkedCount)
        assertEquals(listOf(1, 2), ready(vm).sections.map { it.number })

        vm.updateSection(copy.id, SectionType.CHORUS, bars = 4, label = null)
        advanceUntilIdle()
        assertEquals(listOf(4, 4), ready(vm).sections.map { it.bars })

        vm.unlinkSection(copy.id)
        vm.updateSection(copy.id, SectionType.CHORUS, bars = 6, label = "Final")
        advanceUntilIdle()
        assertEquals(listOf(4, 6), ready(vm).sections.map { it.bars })
        assertEquals(listOf(1, 1), ready(vm).sections.map { it.linkedCount })
        val saved = repo.songs.getValue("song")
        assertEquals(listOf(16, 24), saved.sections.map { saved.contentOf(it).lengthBeats })
    }

    @Test
    fun settingsTempoTapAndTimeSignature() = runTest(dispatcher) {
        val repo = repoWithSong()
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.addSection(SectionType.VERSE, 4, null)
        vm.setTitle("Nueva")
        vm.setArtist("  ")
        vm.setTempo(96.54)
        advanceUntilIdle()
        assertEquals("96.5", ready(vm).tempoText)
        vm.nudgeTempo(+1)
        advanceUntilIdle()
        assertEquals(97.0, ready(vm).song.tempoBpm)

        vm.tap(10_000)
        vm.tap(10_500)
        vm.tap(11_000)
        advanceUntilIdle()
        assertEquals(120.0, ready(vm).song.tempoBpm)
        assertEquals(3, ready(vm).tapCount)

        vm.setTimeSignature(3, 4)
        advanceUntilIdle()
        assertEquals(TimeSignature.THREE_FOUR, ready(vm).song.timeSignature)
        assertEquals(4, ready(vm).sections.single().bars)
        vm.setTimeSignature(7, 8)
        advanceUntilIdle()
        assertEquals(false, ready(vm).isPresetTimeSignature)
        vm.setTimeSignature(4, 5) // invalid: ignored
        advanceUntilIdle()

        val saved = repo.songs.getValue("song")
        assertEquals("Nueva", saved.title)
        assertEquals(null, saved.artist)
        assertEquals(TimeSignature(7, 8), saved.timeSignature)
        assertEquals(28, saved.sectionContents.single().lengthBeats)
    }

    @Test
    fun editsMadeJustBeforeClosingAreSaved() = runTest(dispatcher) {
        val repo = repoWithSong()
        val store = ViewModelStore()
        val vm = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel(repo) as T
            },
        )[SongViewModel::class.java]
        advanceUntilIdle()

        vm.addSection(SectionType.INTRO, 2, null)
        advanceTimeBy(SongViewModel.SAVE_DELAY_MS + 1) // the debounced save starts…
        vm.addSection(SectionType.OUTRO, 2, null) // …and one more edit right before closing
        store.clear() // the screen closes: viewModelScope is cancelled
        advanceUntilIdle()

        assertEquals(
            listOf(SectionType.INTRO, SectionType.OUTRO),
            repo.songs.getValue("song").sections.map { it.type },
        )
    }

    @Test
    fun newSongOpensSettingsOnlyOnce() = runTest(dispatcher) {
        val vm = viewModel(repoWithSong(), openSettings = true)
        assertEquals(true, vm.consumeOpenSettingsRequest())
        assertEquals(false, vm.consumeOpenSettingsRequest())
        assertEquals(false, viewModel(repoWithSong(), openSettings = false).consumeOpenSettingsRequest())
    }

    @Test
    fun missingSongIsNotFound() = runTest(dispatcher) {
        val vm = viewModel(FakeSongRepository())
        advanceUntilIdle()
        assertEquals(SongUiState.NotFound, vm.state.value)
    }

    @Test
    fun tempoFormatting() {
        assertEquals("120", SongViewModel.formatTempo(120.0))
        assertEquals("96.5", SongViewModel.formatTempo(96.5))
        assertEquals("30", SongViewModel.formatTempo(30.04))
    }
}
