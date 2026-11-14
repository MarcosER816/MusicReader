package com.musicreader.core.lyrics

import com.musicreader.core.model.Accidental
import com.musicreader.core.model.ChordEvent
import com.musicreader.core.model.ChordNaming
import com.musicreader.core.model.ChordOps
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.IdGenerator
import com.musicreader.core.model.Language
import com.musicreader.core.model.LyricLine
import com.musicreader.core.model.NoteLetter
import com.musicreader.core.model.NoteName
import com.musicreader.core.model.SectionContent
import com.musicreader.core.model.SectionType
import com.musicreader.core.model.Song
import com.musicreader.core.model.SongOps
import com.musicreader.core.model.TabEvent
import com.musicreader.core.model.Technique

/**
 * A song for trying the viewer before chords and tabs can be entered (Stage 3, debug menu):
 * lyrics in two voices, chords of every root letter including D#, Bb9 and Cmaj7, and tab notes
 * with every finger and several techniques.
 */
object DemoSong {
    const val TITLE: String = "Demo song"

    fun create(id: String, ids: IdGenerator, now: Long): Song {
        var song = SongOps.newSong(id, TITLE, Language.EN, now).copy(artist = "MusicReader", tempoBpm = 96.0)
        val splitter = WordSyllabifier.forLanguage(Language.EN)
        song = SongOps.addSection(song, SectionType.INTRO, 2, null, ids, now)
        song = SongOps.addSection(song, SectionType.VERSE, 4, null, ids, now)
        song = SongOps.addSection(song, SectionType.CHORUS, 4, null, ids, now)
        song = SongOps.addSection(song, SectionType.OUTRO, 2, null, ids, now)
        val (intro, verse, chorus, outro) = song.sections.map { s -> song.sectionContents.first { it.id == s.contentId } }

        fun chord(beat: Double, root: NoteLetter, accidental: Accidental = Accidental.NONE, suffix: String = "", bass: NoteName? = null): ChordEvent {
            val symbol = ChordSymbol(root, accidental, suffix, bass)
            return ChordEvent(ids.newId(), beat, symbol, voicingId = ChordNaming.firstVoicingId(symbol))
        }

        fun note(beat: Double, string: Int, fret: Int, finger: Int, technique: Technique = Technique.NONE, duration: Double = 0.5) =
            TabEvent(
                id = ids.newId(), beat = beat, durationBeats = duration, string = string, fret = fret, finger = finger,
                technique = technique,
                bendQuarterTones = if (technique == Technique.BEND) 4 else null,
                targetFret = when (technique) {
                    Technique.HAMMER_ON -> fret + 2
                    Technique.SLIDE_UP -> fret + 2
                    else -> null
                },
                harmonicFret = if (technique == Technique.NATURAL_HARMONIC) fret else null,
            )

        val introContent = intro.copy(
            chords = listOf(chord(0.0, NoteLetter.C), chord(4.0, NoteLetter.G)),
            tab = listOf(
                note(0.0, 5, 3, 3),
                note(1.0, 4, 2, 2),
                note(2.0, 3, 0, 0),
                note(3.0, 2, 1, 1),
                note(4.0, 6, 3, 2),
                note(5.0, 1, 3, 4, Technique.VIBRATO, 1.0),
                note(6.0, 1, 12, 0, Technique.NATURAL_HARMONIC, 2.0),
            ),
        )
        var verseContent = LyricsOps.setMainText(
            verse,
            "The road is long beneath the morning sky\nWe sing along until the night is high",
            splitter,
            ids,
        )
        verseContent = verseContent.copy(
            chords = listOf(
                chord(0.0, NoteLetter.C), chord(4.0, NoteLetter.E, suffix = "m"),
                chord(8.0, NoteLetter.F), chord(12.0, NoteLetter.G, suffix = "7"),
            ),
            tab = listOf(
                note(0.0, 3, 5, 1, Technique.HAMMER_ON),
                note(1.0, 3, 7, 3),
                note(2.0, 2, 5, 1, Technique.SLIDE_UP),
                note(3.0, 2, 7, 3, Technique.BEND),
            ),
        )
        var chorusContent = LyricsOps.setMainText(
            chorus,
            "Shine on, shine on, my golden melody\nAnd all the colors sing with me",
            splitter,
            ids,
        )
        val secondLineOf = chorusContent.lyricLines.last { it.voice == LyricLine.MAIN_VOICE }.id
        chorusContent = LyricsOps.setSecondLine(chorusContent, secondLineOf, "(sing with me)", splitter, ids)
        chorusContent = chorusContent.copy(
            chords = listOf(
                chord(0.0, NoteLetter.C, suffix = "maj7"),
                chord(4.0, NoteLetter.D, Accidental.SHARP),
                chord(8.0, NoteLetter.B, Accidental.FLAT, "9"),
                chord(12.0, NoteLetter.A, suffix = "m"),
                chord(14.0, NoteLetter.D, bass = NoteName(NoteLetter.F, Accidental.SHARP)),
            ),
            tab = listOf(
                note(8.0, 4, 8, 2, Technique.PULL_OFF),
                note(9.0, 4, 6, 1),
                note(12.0, 6, 0, 0, Technique.MUTE),
            ),
        )
        val outroContent = outro.copy(
            chords = listOf(
                chord(0.0, NoteLetter.D), chord(2.0, NoteLetter.E),
                chord(4.0, NoteLetter.A), chord(6.0, NoteLetter.B, suffix = "m"),
            ),
            tab = listOf(note(7.0, 1, 0, 0, duration = 1.0)),
        )
        // In the sections with lyrics, chords sit on syllables (D-025), as a user places them.
        fun onSyllables(content: SectionContent, vararg placements: Pair<Pair<Int, Int>, ChordEvent>): SectionContent {
            val lines = content.lyricLines.filter { it.voice == LyricLine.MAIN_VOICE }
            var result = content.copy(chords = emptyList())
            for ((where, chord) in placements) {
                val (lineIndex, syllableIndex) = where
                val syllable = lines[lineIndex].words.flatMap { it.syllables }[syllableIndex]
                result = ChordOps.addOnSyllable(result, song.timeSignature, syllable.id, chord.symbol, chord.voicingId, chord.id)
            }
            return result
        }
        verseContent = onSyllables(
            verseContent,
            (0 to 1) to verseContent.chords[0], // C on "road"
            (0 to 5) to verseContent.chords[1], // Em on "neath"
            (1 to 0) to verseContent.chords[2], // F on "We"
            (1 to 5) to verseContent.chords[3], // G7 on "til"
        )
        chorusContent = onSyllables(
            chorusContent,
            (0 to 0) to chorusContent.chords[0], // Cmaj7 on "Shine"
            (0 to 3) to chorusContent.chords[1], // D# on "on,"
            (1 to 0) to chorusContent.chords[2], // Bb9 on "And"
            (1 to 5) to chorusContent.chords[3], // Am on "sing"
            (1 to 7) to chorusContent.chords[4], // D/F# on "me"
        )
        val replaced: Map<String, SectionContent> = listOf(introContent, verseContent, chorusContent, outroContent)
            .associateBy { it.id }
        return song.copy(sectionContents = song.sectionContents.map { replaced[it.id] ?: it })
    }
}
