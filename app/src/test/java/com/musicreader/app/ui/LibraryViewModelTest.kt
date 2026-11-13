package com.musicreader.app.ui

import com.musicreader.app.ui.library.LibraryEvent
import com.musicreader.app.ui.library.LibraryViewModel
import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.Language
import com.musicreader.core.model.SongOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var idCounter = 0
    private var now = 100L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(repo: FakeSongRepository) =
        LibraryViewModel(repo, backgroundScope, IdGenerator { "new${idCounter++}" }, clock = { now++ })

    private fun twoSongs() = FakeSongRepository(
        listOf(
            SongOps.newSong("a", "Alpha", Language.EN, 1),
            SongOps.newSong("b", "Beta", Language.ES, 2),
        ),
    )

    @Test
    fun createSongSavesAndOpensItsSettings() = runTest(dispatcher) {
        val repo = FakeSongRepository()
        val vm = viewModel(repo)
        vm.createSong("  Mi canción ")
        val event = vm.eventFlow.first()
        assertEquals(LibraryEvent.OpenSong("new0", openSettings = true), event)
        assertEquals("Mi canción", repo.songs.getValue("new0").title)
        vm.createSong("   ")
        advanceUntilIdle()
        assertEquals(1, repo.songs.size, "blank titles are refused")
    }

    @Test
    fun renameAndDuplicate() = runTest(dispatcher) {
        val repo = twoSongs()
        val vm = viewModel(repo)
        vm.renameSong("a", "Alfa")
        vm.duplicateSong("b", "Beta (copy)")
        advanceUntilIdle()
        assertEquals("Alfa", repo.songs.getValue("a").title)
        assertEquals("Beta (copy)", repo.songs.getValue("new0").title)
        assertEquals(listOf("new0", "a", "b"), vm.state.value.songs.map { it.id })
    }

    @Test
    fun deleteCanBeUndone() = runTest(dispatcher) {
        val repo = twoSongs()
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.deleteSong("a")
        advanceUntilIdle()
        assertEquals(listOf("b"), vm.state.value.songs.map { it.id })
        assertTrue(repo.songs.containsKey("a"), "nothing is deleted before the snackbar closes")
        vm.undoDelete("a")
        advanceUntilIdle()
        assertEquals(listOf("b", "a"), vm.state.value.songs.map { it.id })
        assertTrue(repo.deleted.isEmpty())
    }

    @Test
    fun confirmedDeleteRemovesTheSong() = runTest(dispatcher) {
        val repo = twoSongs()
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.deleteSong("a")
        vm.confirmDelete("a")
        advanceUntilIdle()
        assertEquals(listOf("a"), repo.deleted)
        assertEquals(listOf("b"), vm.state.value.songs.map { it.id })
    }

    @Test
    fun secondDeleteConfirmsTheFirstAndStaleResultsAreIgnored() = runTest(dispatcher) {
        val repo = twoSongs()
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.deleteSong("a")
        vm.deleteSong("b")
        vm.confirmDelete("a") // late result of the first snackbar: already confirmed, ignored
        vm.undoDelete("b")
        advanceUntilIdle()
        assertEquals(listOf("a"), repo.deleted)
        assertEquals(listOf("b"), vm.state.value.songs.map { it.id })
    }
}
