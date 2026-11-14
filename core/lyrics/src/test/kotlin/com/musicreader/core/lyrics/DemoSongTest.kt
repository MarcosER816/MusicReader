package com.musicreader.core.lyrics

import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.LyricLine
import com.musicreader.core.model.NoteLetter
import com.musicreader.core.model.SheetLayoutBuilder
import com.musicreader.core.model.SongJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoSongTest {
    private var n = 0
    private val song = DemoSong.create("demo", IdGenerator { "d${n++}" }, now = 5)

    @Test
    fun hasEveryRootLetterAndTheRequiredChordNames() {
        val chords = song.sectionContents.flatMap { it.chords }
        assertEquals(NoteLetter.entries.toSet(), chords.map { it.symbol.root }.toSet())
        val names = chords.map { it.symbol.toString() }
        assertTrue(names.containsAll(listOf("D#", "Bb9", "Cmaj7")), "$names")
    }

    @Test
    fun hasLyricsInTwoVoicesAndTabWithEveryFinger() {
        val lines = song.sectionContents.flatMap { it.lyricLines }
        assertEquals(4, lines.count { it.voice == LyricLine.MAIN_VOICE })
        assertEquals(1, lines.count { it.voice == LyricLine.SECOND_VOICE && it.pairedLineId != null })
        assertEquals(setOf(0, 1, 2, 3, 4), song.sectionContents.flatMap { it.tab }.map { it.finger }.toSet())
    }

    @Test
    fun chordsInLyricSectionsSitOnTheIntendedSyllables() {
        val texts = song.sectionContents.flatMap { c ->
            val syllables = c.lyricLines.flatMap { l -> l.words.flatMap { it.syllables } }.associateBy { it.id }
            c.chords.mapNotNull { ch -> ch.syllableId?.let { "${ch.symbol}@${syllables.getValue(it).text}" } }
        }
        assertEquals(
            listOf("C@road", "Em@neath", "F@We", "G7@til", "Cmaj7@Shine", "D#@on,", "Bb9@And", "Am@sing", "D/F#@me"),
            texts,
        )
    }

    @Test
    fun isAValidSongThatLaysOut() {
        assertEquals(song, SongJson.decode(SongJson.encode(song)))
        val layout = SheetLayoutBuilder.build(song)
        assertEquals(4, layout.sections.size)
        val placedChords = layout.sections.sumOf { s -> s.rows.sumOf { it.chords.size } }
        assertEquals(song.sectionContents.sumOf { it.chords.size }, placedChords)
        val placedNotes = layout.sections.sumOf { s -> s.rows.sumOf { it.tab.size } }
        assertEquals(song.sectionContents.sumOf { it.tab.size }, placedNotes)
    }
}
