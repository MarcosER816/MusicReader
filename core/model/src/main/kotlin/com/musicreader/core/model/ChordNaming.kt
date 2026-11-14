package com.musicreader.core.model

import kotlinx.serialization.Serializable

/** How roots with an accidental are spelled in the chord picker (C# or Db). */
@Serializable
enum class AccidentalPreference { SHARPS, FLATS }

/** Pitch classes (0 = C … 11 = B) and enharmonic spelling. */
object Enharmonics {
    private val LETTER_PC = mapOf(
        NoteLetter.C to 0, NoteLetter.D to 2, NoteLetter.E to 4, NoteLetter.F to 5,
        NoteLetter.G to 7, NoteLetter.A to 9, NoteLetter.B to 11,
    )

    private val SHARP_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    fun pitchClass(letter: NoteLetter, accidental: Accidental = Accidental.NONE): Int {
        val base = LETTER_PC.getValue(letter)
        val shift = when (accidental) {
            Accidental.NONE -> 0
            Accidental.SHARP -> 1
            Accidental.FLAT -> -1
        }
        return Math.floorMod(base + shift, 12)
    }

    fun pitchClass(note: NoteName): Int = pitchClass(note.letter, note.accidental)

    /** The spelling of [pitchClass] for the preference (C#/Db, D#/Eb, F#/Gb, G#/Ab, A#/Bb). */
    fun spell(pitchClass: Int, preference: AccidentalPreference): NoteName {
        val names = if (preference == AccidentalPreference.SHARPS) SHARP_NAMES else FLAT_NAMES
        return parse(names[Math.floorMod(pitchClass, 12)])!!
    }

    /** Parses "C", "C#", "Db", "Bb"…; null for anything else. */
    fun parse(name: String): NoteName? {
        if (name.isEmpty() || name.length > 2) return null
        val letter = NoteLetter.entries.firstOrNull { it.name == name.substring(0, 1) } ?: return null
        val accidental = when (name.getOrNull(1)) {
            null -> Accidental.NONE
            '#' -> Accidental.SHARP
            'b' -> Accidental.FLAT
            else -> return null
        }
        return NoteName(letter, accidental)
    }

    /** True for two spellings of the same pitch class (D# and Eb). */
    fun sameNote(a: NoteName, b: NoteName): Boolean = pitchClass(a) == pitchClass(b)
}

/**
 * Names used by the chord dictionary (tombatossals/chords-db) and how they map to [ChordSymbol]:
 * its suffix "major" is our "", "minor" is "m", and slash suffixes ("/E", "m/B", "m9/Bb",
 * "7/G") are a suffix plus a bass note.
 */
object ChordNaming {
    /** The dictionary key of each pitch class, as spelled in chords-db. */
    val DICTIONARY_KEYS: List<String> = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    fun dictionaryKey(root: NoteName): String = DICTIONARY_KEYS[Enharmonics.pitchClass(root)]

    /** Our suffix and bass note for a dictionary suffix. */
    fun fromDictionarySuffix(suffix: String): Pair<String, NoteName?> {
        val slash = suffix.indexOf('/')
        val (body, bass) = if (slash >= 0) suffix.substring(0, slash) to Enharmonics.parse(suffix.substring(slash + 1))
        else suffix to null
        val ours = when (body) {
            "major" -> ""
            "minor" -> "m"
            else -> body
        }
        return ours to bass
    }

    /** The chord symbol for a dictionary chord, with the root spelled as given. */
    fun symbol(root: NoteName, dictionarySuffix: String): ChordSymbol {
        val (suffix, bass) = fromDictionarySuffix(dictionarySuffix)
        return ChordSymbol(root.letter, root.accidental, suffix, bass)
    }

    /**
     * True when [dictionarySuffix] names the same chord type and bass note (by pitch) as [symbol];
     * the root is compared separately by pitch class.
     */
    fun matches(symbol: ChordSymbol, dictionarySuffix: String): Boolean {
        val (suffix, bass) = fromDictionarySuffix(dictionarySuffix)
        if (suffix != symbol.suffix) return false
        val ourBass = symbol.bass
        return when {
            bass == null && ourBass == null -> true
            bass == null || ourBass == null -> false
            else -> Enharmonics.sameNote(bass, ourBass)
        }
    }

    /** voicingId of a dictionary shape: "key|suffix|index", e.g. "Eb|9|0". */
    fun voicingId(dictionaryKey: String, dictionarySuffix: String, index: Int): String =
        "$dictionaryKey|$dictionarySuffix|$index"

    /** The (key, suffix, index) of a voicingId; null when it is not in that form. */
    fun parseVoicingId(id: String): Triple<String, String, Int>? {
        val parts = id.split('|')
        if (parts.size != 3) return null
        val index = parts[2].toIntOrNull() ?: return null
        return Triple(parts[0], parts[1], index)
    }

    /** The voicingId of the first shape of [symbol] (as the dictionary names it). */
    fun firstVoicingId(symbol: ChordSymbol): String {
        val root = NoteName(symbol.root, symbol.accidental)
        val base = when (symbol.suffix) {
            "" -> "major"
            "m" -> "minor"
            else -> symbol.suffix
        }
        val suffix = symbol.bass?.let { bass ->
            val bassName = DICTIONARY_KEYS[Enharmonics.pitchClass(bass)]
            if (symbol.suffix.isEmpty()) "/$bassName" else "${symbol.suffix}/$bassName"
        } ?: base
        return voicingId(dictionaryKey(root), suffix, 0)
    }
}

/** Chord placement on the beat grid (D-009: half beats). */
object ChordOps {
    const val GRID: Double = 0.5

    /** The nearest grid beat, kept inside the section (0 until its length). */
    fun snap(beat: Double, lengthBeats: Int, grid: Double = GRID): Double {
        val snapped = Math.round(beat / grid) * grid
        val last = lengthBeats - grid
        return snapped.coerceIn(0.0, maxOf(0.0, last)) + 0.0
    }

    /** Adds a grid chord at [beat] (snapped); a grid chord already on that beat is replaced. */
    fun add(content: SectionContent, beat: Double, symbol: ChordSymbol, voicingId: String, id: String): SectionContent {
        val at = snap(beat, content.lengthBeats)
        val others = content.chords.filter { it.beat != at || it.syllableId != null }
        return content.copy(chords = (others + ChordEvent(id, at, symbol, voicingId)).sortedBy { it.beat })
    }

    /** Moves a chord to grid beat [beat] (snapped), replacing a grid chord already there. */
    fun move(content: SectionContent, chordId: String, beat: Double): SectionContent {
        val chord = content.chords.firstOrNull { it.id == chordId } ?: return content
        val at = snap(beat, content.lengthBeats)
        if (at == chord.beat && chord.syllableId == null) return content
        val others = content.chords.filter { it.id != chordId && (it.beat != at || it.syllableId != null) }
        return content.copy(chords = (others + chord.copy(beat = at, syllableId = null)).sortedBy { it.beat })
    }

    /** Changes the chord name and shape, keeping its beat. */
    fun change(content: SectionContent, chordId: String, symbol: ChordSymbol, voicingId: String): SectionContent =
        content.copy(chords = content.chords.map { if (it.id == chordId) it.copy(symbol = symbol, voicingId = voicingId) else it })

    fun delete(content: SectionContent, chordId: String): SectionContent =
        content.copy(chords = content.chords.filterNot { it.id == chordId })

    /**
     * The beat of a main-voice syllable: its start beat when sung, otherwise estimated from its
     * place in the line (the line's share of the section, D-023), snapped to the grid. Null when
     * the syllable is not in the section.
     */
    fun syllableBeat(content: SectionContent, timeSignature: TimeSignature, syllableId: String): Double? {
        val rows = SheetLayoutBuilder.rows(content, timeSignature)
        for (row in rows) {
            val syllable = row.syllables.firstOrNull { it.id == syllableId } ?: continue
            return snap(row.startBeat + syllable.x * row.lengthBeats, content.lengthBeats)
        }
        return null
    }

    /** Places a chord on a syllable (D-025); a chord already on that syllable is replaced. */
    fun addOnSyllable(
        content: SectionContent,
        timeSignature: TimeSignature,
        syllableId: String,
        symbol: ChordSymbol,
        voicingId: String,
        id: String,
    ): SectionContent {
        val beat = syllableBeat(content, timeSignature, syllableId) ?: return content
        val others = content.chords.filter { it.syllableId != syllableId }
        val chord = ChordEvent(id, beat, symbol, voicingId, syllableId)
        return content.copy(chords = (others + chord).sortedBy { it.beat })
    }

    /** Moves a chord onto another syllable, replacing a chord already there. */
    fun moveToSyllable(content: SectionContent, timeSignature: TimeSignature, chordId: String, syllableId: String): SectionContent {
        val chord = content.chords.firstOrNull { it.id == chordId } ?: return content
        if (chord.syllableId == syllableId) return content
        val beat = syllableBeat(content, timeSignature, syllableId) ?: return content
        val others = content.chords.filter { it.id != chordId && it.syllableId != syllableId }
        return content.copy(chords = (others + chord.copy(beat = beat, syllableId = syllableId)).sortedBy { it.beat })
    }

    /**
     * Brings the beats of chords on syllables up to date (after lyrics changed or were sung).
     * A chord whose syllable no longer exists keeps its beat and becomes a grid chord.
     */
    fun realign(content: SectionContent, timeSignature: TimeSignature): SectionContent {
        if (content.chords.none { it.syllableId != null }) return content
        val chords = content.chords.map { c ->
            val id = c.syllableId ?: return@map c
            val beat = syllableBeat(content, timeSignature, id)
            if (beat == null) c.copy(syllableId = null) else c.copy(beat = beat)
        }.sortedBy { it.beat }
        return if (chords == content.chords) content else content.copy(chords = chords)
    }
}
