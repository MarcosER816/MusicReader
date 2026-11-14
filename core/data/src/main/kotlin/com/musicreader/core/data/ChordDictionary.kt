package com.musicreader.core.data

import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.ChordNaming
import com.musicreader.core.model.ChordSymbol
import com.musicreader.core.model.Enharmonics
import com.musicreader.core.model.NoteName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

/** A barre: [finger] presses strings [fromString] to [toString] (6 = low E … 1 = high e) at [fret]. */
data class Barre(val fret: Int, val fromString: Int, val toString: Int, val finger: Int)

/**
 * One way to play a chord. Lists are per string from low E (index 0, string 6) to high e
 * (index 5, string 1). [frets] are absolute: -1 muted, 0 open, otherwise the fret number.
 */
data class ChordShape(
    val voicingId: String,
    val frets: List<Int>,
    /** 0 = no finger (open or muted), 1–4 = index … pinky. */
    val fingers: List<Int>,
    /** First fret shown in the diagram (1 = the nut is shown). */
    val baseFret: Int,
    val barres: List<Barre>,
    /** Sounding notes, low to high. */
    val midi: List<Int>,
) {
    companion object {
        /** Absolute fret of a chords-db fret given relative to [baseFret] (-1 muted, 0 open). */
        fun absoluteFret(relative: Int, baseFret: Int): Int = if (relative <= 0) relative else baseFret + relative - 1
    }
}

/** A chord type of one root, e.g. "Eb" + "9", with all its shapes. */
data class DictionaryChord(
    val dictionaryKey: String,
    val dictionarySuffix: String,
    val shapes: List<ChordShape>,
) {
    /** The chord name with the root spelled as [root] (e.g. D#9 for the Eb 9 entry). */
    fun symbol(root: NoteName): ChordSymbol = ChordNaming.symbol(root, dictionarySuffix)
}

/** The guitar chord dictionary (tombatossals/chords-db, MIT), from assets/chords/guitar.json. */
class ChordDictionary(val chords: List<DictionaryChord>, val fretsOnChord: Int) {

    private val byKey: Map<String, List<DictionaryChord>> = chords.groupBy { it.dictionaryKey }
    private val byVoicing: Map<String, ChordShape> = chords.flatMap { it.shapes }.associateBy { it.voicingId }

    val shapeCount: Int get() = byVoicing.size

    /** Every chord type of the root (any spelling: D# and Eb give the same list), dictionary order. */
    fun chordsFor(root: NoteName): List<DictionaryChord> = byKey[ChordNaming.dictionaryKey(root)].orEmpty()

    /**
     * The chord types of a root for the picker: the common ones first (major, m, 7, maj7, m7, 9,
     * sus4, sus2, add9, 6, m6, dim, aug, 5, …), then every other type in dictionary order, slash
     * chords last.
     */
    fun typesFor(root: NoteName): List<DictionaryChord> {
        val all = chordsFor(root)
        val common = COMMON_SUFFIXES.mapNotNull { suffix -> all.firstOrNull { it.dictionarySuffix == suffix } }
        val rest = all.filter { it !in common }
        return common + rest.filter { '/' !in it.dictionarySuffix } + rest.filter { '/' in it.dictionarySuffix }
    }

    /** The dictionary chord matching [symbol] by pitch (D#9 finds Eb 9; C/E finds C /E), or null. */
    fun find(symbol: ChordSymbol): DictionaryChord? =
        chordsFor(NoteName(symbol.root, symbol.accidental)).firstOrNull { ChordNaming.matches(symbol, it.dictionarySuffix) }

    /** The shape with [voicingId], or null. */
    fun shape(voicingId: String): ChordShape? = byVoicing[voicingId]

    /** The chosen shape of a chord, or the first shape of its chord type when the id is unknown. */
    fun shapeFor(symbol: ChordSymbol, voicingId: String): ChordShape? = shape(voicingId) ?: find(symbol)?.shapes?.firstOrNull()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Types shown first in the picker, in this order. */
        val COMMON_SUFFIXES: List<String> = listOf(
            "major", "minor", "7", "maj7", "m7", "9", "sus4", "sus2", "add9", "6", "m6", "dim", "dim7",
            "aug", "5", "7sus4", "m9", "maj9", "m7b5", "11", "13",
        )

        /** Parses the converted asset (tools/convert_chords_db.py). */
        fun parse(text: String): ChordDictionary {
            val file = json.decodeFromString(AssetFile.serializer(), text)
            val chords = file.chords.map { chord ->
                DictionaryChord(
                    dictionaryKey = chord.key,
                    dictionarySuffix = chord.suffix,
                    shapes = chord.positions.mapIndexed { index, p ->
                        val frets = p.frets.map { ChordShape.absoluteFret(it, p.baseFret) }
                        ChordShape(
                            voicingId = ChordNaming.voicingId(chord.key, chord.suffix, index),
                            frets = frets,
                            fingers = p.fingers,
                            baseFret = p.baseFret,
                            barres = p.barres.map { relative -> barre(p, relative) },
                            midi = p.midi,
                        )
                    },
                )
            }
            return ChordDictionary(chords, file.fretsOnChord)
        }

        /**
         * A barre at relative fret [relative]: it spans from the lowest to the highest string
         * pressed at that fret, with the finger used there.
         */
        private fun barre(p: AssetPosition, relative: Int): Barre {
            val strings = p.frets.indices.filter { p.frets[it] == relative }
            val low = strings.minOrNull() ?: 0
            val high = strings.maxOrNull() ?: 5
            val finger = strings.map { p.fingers[it] }.firstOrNull { it > 0 } ?: 1
            return Barre(
                fret = ChordShape.absoluteFret(relative, p.baseFret),
                fromString = 6 - low,
                toString = 6 - high,
                finger = finger,
            )
        }

        /** Root spellings offered by the picker: the 12 pitch classes in the preferred spelling. */
        fun roots(preference: AccidentalPreference): List<NoteName> = (0 until 12).map { Enharmonics.spell(it, preference) }
    }

    @Serializable
    private data class AssetFile(val fretsOnChord: Int, val chords: List<AssetChord>)

    @Serializable
    private data class AssetChord(val key: String, val suffix: String, val positions: List<AssetPosition>)

    @Serializable
    private data class AssetPosition(
        val frets: List<Int>,
        val fingers: List<Int>,
        val baseFret: Int,
        val barres: List<Int>,
        val capo: Boolean = false,
        val midi: List<Int>,
    )
}

/** Loads the dictionary once, off the main thread. */
class ChordDictionaryRepository(
    private val open: () -> InputStream,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var loaded: ChordDictionary? = null

    suspend fun dictionary(): ChordDictionary = mutex.withLock {
        loaded ?: withContext(io) { ChordDictionary.parse(open().use { it.readBytes().toString(Charsets.UTF_8) }) }
            .also { loaded = it }
    }

    companion object {
        const val ASSET_PATH: String = "chords/guitar.json"
    }
}
