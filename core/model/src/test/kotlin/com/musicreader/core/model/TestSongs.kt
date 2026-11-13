package com.musicreader.core.model

/** Deterministic ids for tests: "id-1", "id-2", … */
class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 1
    override fun newId(): String = "$prefix-${next++}"
}

/** Song fixtures that use every class of the data model. */
object TestSongs {
    val chorusContent = SectionContent(
        id = "content-chorus",
        lengthBeats = 16,
        strum = StrumPattern(
            subdivision = 2,
            strokes = listOf(
                StrumStroke(atBeat = 0.0, dir = StrumDirection.DOWN, accent = true),
                StrumStroke(atBeat = 1.5, dir = StrumDirection.UP),
                StrumStroke(atBeat = 2.0, dir = StrumDirection.MUTE),
            ),
        ),
        lyricLines = listOf(
            LyricLine(
                id = "line-1",
                voice = LyricLine.MAIN_VOICE,
                text = "Canta conmigo",
                words = listOf(
                    Word(
                        id = "word-1",
                        text = "Canta",
                        syllables = listOf(
                            Syllable(
                                id = "syl-1",
                                text = "Can",
                                startBeat = 0.0,
                                durationBeats = 1.25,
                                pitchStartMidi = 64.27,
                                pitchEndMidi = 65.0,
                                pitchCurve = listOf(CurvePoint(0.0, 64.27), CurvePoint(0.5, 64.6), CurvePoint(1.0, 65.0)),
                                takeId = "take-1",
                            ),
                            Syllable(id = "syl-2", text = "ta", freestyle = true),
                        ),
                    ),
                    Word(id = "word-2", text = "conmigo", syllableOverride = true),
                ),
            ),
            LyricLine(id = "line-2", voice = LyricLine.SECOND_VOICE, text = "conmigo"),
        ),
        chords = listOf(
            ChordEvent(id = "chord-1", beat = 0.0, symbol = ChordSymbol(NoteLetter.C, suffix = "maj7"), voicingId = "Cmaj7-1"),
            ChordEvent(
                id = "chord-2",
                beat = 2.5,
                symbol = ChordSymbol(NoteLetter.D, Accidental.NONE, "", bass = NoteName(NoteLetter.F, Accidental.SHARP)),
                voicingId = "D/F#-1",
            ),
            ChordEvent(id = "chord-3", beat = 8.0, symbol = ChordSymbol(NoteLetter.B, Accidental.FLAT, "9"), voicingId = "Bb9-2"),
        ),
        tab = listOf(
            TabEvent(id = "tab-1", beat = 0.0, durationBeats = 0.5, string = 3, fret = 7, finger = 3, technique = Technique.BEND, bendQuarterTones = 4),
            TabEvent(id = "tab-2", beat = 0.5, durationBeats = 0.5, string = 2, fret = 5, finger = 1, technique = Technique.HAMMER_ON, targetFret = 7),
            TabEvent(id = "tab-3", beat = 1.0, durationBeats = 1.0, string = 1, fret = 12, finger = 0, technique = Technique.NATURAL_HARMONIC, harmonicFret = 12),
            TabEvent(id = "tab-4", beat = 2.0, durationBeats = 2.0, string = 6, fret = 0, finger = 0),
        ),
    )

    val verseContent = SectionContent(id = "content-verse", lengthBeats = 32)

    /** Intro (independent), Verse, Chorus, Verse, Chorus (linked to the first chorus). */
    val fullSong = Song(
        id = "song-1",
        title = "Canción de prueba",
        artist = "Mark",
        language = Language.ES,
        instrument = Instrument(),
        tempoBpm = 96.5,
        timeSignature = TimeSignature.FOUR_FOUR,
        sections = listOf(
            Section(id = "sec-intro", type = SectionType.INTRO, contentId = "content-intro"),
            Section(id = "sec-verse-1", type = SectionType.VERSE, contentId = "content-verse"),
            Section(id = "sec-chorus-1", type = SectionType.CHORUS, label = "Chorus", contentId = "content-chorus"),
            Section(id = "sec-verse-2", type = SectionType.VERSE, contentId = "content-verse-2"),
            Section(id = "sec-chorus-2", type = SectionType.CHORUS, contentId = "content-chorus"),
        ),
        sectionContents = listOf(
            SectionContent(id = "content-intro", lengthBeats = 8),
            verseContent,
            chorusContent,
            SectionContent(id = "content-verse-2", lengthBeats = 32),
        ),
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_100_000,
    )

    val fullRecording = Recording(
        practiceType = PracticeType.SING,
        file = "sing-1.wav",
        startSongBeat = 4.0,
        startFrameOffset = 12_345,
        latencyCompensationMs = 87.5,
        official = true,
        createdAt = 1_700_000_200_000,
    )
}
