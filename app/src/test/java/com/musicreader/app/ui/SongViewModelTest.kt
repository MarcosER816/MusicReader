package com.musicreader.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.musicreader.app.ui.song.SongUiState
import com.musicreader.app.ui.song.SongViewModel
import com.musicreader.core.model.IdGenerator
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import com.musicreader.core.model.Accidental
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.NoteLetter
import com.musicreader.core.model.TabEvent
import com.musicreader.core.model.Technique
import com.musicreader.app.ui.song.WordUi
import com.musicreader.core.model.Language

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
        settingsRepository = FakeSettingsRepository(),
        chordDictionary = MutableStateFlow(null),
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
    fun lyricsAreSplitEditedAndSaved() = runTest(dispatcher) {
        val repo = repoWithSong() // Spanish song
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.addSection(SectionType.CHORUS, 4, null)
        advanceUntilIdle()
        val section = ready(vm).sections.single().id

        vm.setMainLyrics(section, "Canta conmigo\nahora")
        advanceUntilIdle()
        var lyrics = vm.lyricsState(section).first()!!
        val (line1, line2) = lyrics.lines.map { it.mainLineId }
        vm.setSecondLine(section, line2, "eco")
        advanceUntilIdle()
        lyrics = vm.lyricsState(section).first()!!
        assertEquals(listOf(listOf("Can", "ta"), listOf("con", "mi", "go")), lyrics.lines[0].mainWords.map { it.syllables })
        assertEquals(listOf("", "eco"), lyrics.lines.map { it.secondText })
        assertEquals(listOf(listOf("e", "co")), lyrics.lines[1].secondWords.map { it.syllables })
        assertEquals("Canta conmigo", ready(vm).sections.single().lyricsPreview)

        val canta = lyrics.lines[0].mainWords[0]
        vm.mergeSyllables(section, canta.id, 0)
        val conmigo = lyrics.lines[0].mainWords[1]
        vm.splitSyllable(section, conmigo.id, 0, 2) // co-n-mi-go
        advanceUntilIdle()
        lyrics = vm.lyricsState(section).first()!!
        assertEquals(WordUi(canta.id, listOf("Canta"), manual = true), lyrics.lines[0].mainWords[0])
        assertEquals(listOf("co", "n", "mi", "go"), lyrics.lines[0].mainWords[1].syllables)

        // Editing a line keeps the manual splits, its id and its second voice.
        vm.setMainLyrics(section, "Canta conmigo\nahora mismo")
        advanceUntilIdle()
        lyrics = vm.lyricsState(section).first()!!
        assertEquals(listOf(line1, line2), lyrics.lines.map { it.mainLineId })
        assertEquals(listOf("Canta"), lyrics.lines[0].mainWords[0].syllables)
        assertEquals(listOf("mis", "mo"), lyrics.lines[1].mainWords[1].syllables)
        assertEquals("eco", lyrics.lines[1].secondText)

        // Reset brings back the automatic split.
        vm.resetWord(section, canta.id)
        advanceUntilIdle()
        assertEquals(WordUi(canta.id, listOf("Can", "ta"), manual = false), vm.lyricsState(section).first()!!.lines[0].mainWords[0])

        val saved = repo.songs.getValue("song").sectionContents.single()
        assertEquals(listOf("Canta conmigo", "ahora mismo", "eco"), saved.lyricLines.map { it.text })
        assertEquals(line2, saved.lyricLines[2].pairedLineId)
        assertTrue(saved.lyricLines[0].words[1].syllableOverride)
    }

    @Test
    fun languageChangeResplitsOnlyAutomaticWords() = runTest(dispatcher) {
        val repo = repoWithSong()
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.addSection(SectionType.VERSE, 4, null)
        advanceUntilIdle()
        val section = ready(vm).sections.single().id
        vm.setMainLyrics(section, "animal remember")
        advanceUntilIdle()
        val remember = vm.lyricsState(section).first()!!.lines[0].mainWords[1]
        assertEquals(listOf("re", "mem", "ber"), remember.syllables) // Spanish rules agree here
        vm.mergeSyllables(section, remember.id, 1) // re-member, by hand
        vm.setLanguage(Language.EN)
        advanceUntilIdle()
        val words = vm.lyricsState(section).first()!!.lines[0].mainWords
        assertEquals(listOf("a", "ni", "mal"), words[0].syllables)
        assertEquals(listOf("re", "member"), words[1].syllables)
        assertEquals(Language.EN, repo.songs.getValue("song").language)
    }

    @Test
    fun lyricsOfAMissingSectionAreNull() = runTest(dispatcher) {
        val vm = viewModel(repoWithSong())
        advanceUntilIdle()
        assertNull(vm.lyricsState("nope").first())
    }

    @Test
    fun chordsArePlacedMovedChangedAndDeleted() = runTest(dispatcher) {
        val repo = repoWithSong()
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.addSection(SectionType.VERSE, 2, null) // 8 beats in 4/4
        advanceUntilIdle()
        val section = ready(vm).sections.single().id
        val c = ChordSymbol(NoteLetter.C)
        val g = ChordSymbol(NoteLetter.G)

        vm.addChord(section, 0.2, c, "C|major|0")
        vm.addChord(section, 4.3, g, "G|major|1")
        advanceUntilIdle()
        var editor = vm.chordEditorState(section).first()!!
        assertEquals(listOf(0.0 to "C", 4.5 to "G"), editor.chords.map { it.beat to it.symbol.toString() })
        assertEquals("C · G", ready(vm).sections.single().chordsPreview)

        val gId = editor.chords[1].id
        vm.moveChord(section, gId, 6.1)
        vm.changeChord(section, gId, ChordSymbol(NoteLetter.B, Accidental.FLAT, "9"), "Bb|9|1")
        advanceUntilIdle()
        editor = vm.chordEditorState(section).first()!!
        assertEquals(6.0, editor.chords[1].beat)
        assertEquals("Bb9", editor.chords[1].symbol.toString())
        assertEquals("Bb|9|1", editor.chords[1].voicingId)

        vm.deleteChord(section, gId)
        advanceUntilIdle()
        assertEquals(1, repo.songs.getValue("song").sectionContents.single().chords.size)
        vm.undoDeleteChord()
        advanceUntilIdle()
        assertEquals(listOf("C", "Bb9"), repo.songs.getValue("song").sectionContents.single().chords.map { it.symbol.toString() })
        assertEquals(1, editor.rows.size)
    }

    @Test
    fun chordsOnSyllablesFollowLyricEdits() = runTest(dispatcher) {
        val repo = repoWithSong() // Spanish
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.addSection(SectionType.CHORUS, 2, null) // 8 beats
        advanceUntilIdle()
        val section = ready(vm).sections.single().id
        vm.setMainLyrics(section, "canta conmigo")
        advanceUntilIdle()
        val ta = vm.chordEditorState(section).first()!!.rows.single().syllables[1]
        assertEquals("ta", ta.text)
        vm.addChordOnSyllable(section, ta.id, ChordSymbol(NoteLetter.A, suffix = "m"), "A|minor|0")
        advanceUntilIdle()
        var chord = vm.chordEditorState(section).first()!!.chords.single()
        assertEquals(ta.id, chord.syllableId)
        // "canta conmigo" is 13 characters over 8 beats; "ta" starts at character 3 → 1.85 → 2.0.
        assertEquals(2.0, chord.beat)

        // Words are added before "canta": the chord stays on "ta" and its beat moves.
        vm.setMainLyrics(section, "oh oh canta conmigo")
        advanceUntilIdle()
        chord = vm.chordEditorState(section).first()!!.chords.single()
        assertEquals(ta.id, chord.syllableId)
        assertEquals(4.0, chord.beat) // character 9 of 19 → 3.79 → 4.0

        val mi = vm.chordEditorState(section).first()!!.rows.single().syllables.first { it.text == "mi" }
        vm.moveChordToSyllable(section, chord.id, mi.id)
        advanceUntilIdle()
        assertEquals(mi.id, repo.songs.getValue("song").sectionContents.single().chords.single().syllableId)
    }

    @Test
    fun tabNotesWithUndoRedoAndExport() = runTest(dispatcher) {
        val repo = repoWithSong()
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.addSection(SectionType.VERSE, 1, null) // 4 beats
        advanceUntilIdle()
        val section = ready(vm).sections.single().id
        fun note(beat: Double, string: Int, fret: Int, technique: Technique = Technique.NONE) =
            TabEvent("new", beat, 0.5, string, fret, 1, technique)

        vm.addTabNote(section, note(0.1, 3, 5, Technique.HAMMER_ON))
        vm.addTabNote(section, note(0.5, 3, 7))
        advanceUntilIdle()
        var editor = vm.tabEditorState(section).first()!!
        assertEquals(listOf(0.0 to 5, 0.5 to 7), editor.notes.map { it.beat to it.fret })
        assertEquals(true, editor.canUndo)
        assertEquals(false, editor.canRedo)
        val ids = editor.notes.map { it.id }.toSet()
        assertEquals(2, ids.size) // each note gets its own id

        val first = editor.notes[0]
        vm.updateTabNote(section, first.copy(fret = 6, technique = Technique.BEND, bendQuarterTones = 2))
        vm.moveTabNote(section, editor.notes[1].id, 2.2, 2)
        advanceUntilIdle()
        editor = vm.tabEditorState(section).first()!!
        assertEquals(listOf(Triple(0.0, 3, 6), Triple(2.25, 2, 7)), editor.notes.map { Triple(it.beat, it.string, it.fret) })

        vm.deleteTabNote(section, first.id)
        advanceUntilIdle()
        assertEquals(1, vm.tabEditorState(section).first()!!.notes.size)
        vm.undoTab() // delete
        vm.undoTab() // move
        advanceUntilIdle()
        editor = vm.tabEditorState(section).first()!!
        assertEquals(listOf(0.0 to 3, 0.5 to 3), editor.notes.map { it.beat to it.string })
        assertEquals(true, editor.canRedo)
        vm.redoTab()
        advanceUntilIdle()
        assertEquals(2.25, vm.tabEditorState(section).first()!!.notes[1].beat)
        assertEquals(listOf(6, 7), repo.songs.getValue("song").sectionContents.single().tab.map { it.fret })

        val (tex, ascii) = vm.tabExport(section)!!
        assertTrue("6.3{b (0 2)}" in tex, tex)
        assertTrue(ascii.lines().any { it.startsWith("B|") && "7" in it }, ascii)

        vm.clearTabHistory()
        advanceUntilIdle()
        assertEquals(false to false, vm.tabEditorState(section).first()!!.let { it.canUndo to it.canRedo })
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
