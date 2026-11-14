package com.musicreader.core.model

/**
 * The song as rows on a beat grid, for the viewer (D-023). Every item has an [x] position as a
 * fraction of its row's width (0.0 = row start, 1.0 = row end), computed from its beat, so
 * chords, tab notes and lyrics that happen at the same beat line up.
 */
data class SheetLayout(val sections: List<SheetSection>)

data class SheetSection(
    val sectionId: String,
    val type: SectionType,
    val label: String?,
    /** "2" in "Verse 2"; null when the type appears once. */
    val number: Int?,
    val rows: List<SheetRow>,
)

data class SheetRow(
    /** First beat of the row, relative to the section start. */
    val startBeat: Double,
    val lengthBeats: Double,
    /** Bar lines inside the row (and at its start when a bar starts there), as x fractions. */
    val barLines: List<Double>,
    val chords: List<PlacedChord>,
    val tab: List<PlacedTabNote>,
    /** Main-voice lyrics: syllables when timed, whole words otherwise. */
    val lyrics: List<PlacedText>,
    /** The second-voice line sung with this row's lyric line (D-022). */
    val secondLyrics: List<PlacedText>,
    /** The main-voice syllables of the row's lyric line, in order (empty without lyrics). */
    val syllables: List<RowSyllable> = emptyList(),
)

/**
 * A syllable of a row's lyric line. [x] is its position as a fraction of the row: its start beat
 * when sung, otherwise its character position in the line.
 */
data class RowSyllable(val id: String, val text: String, val startsWord: Boolean, val x: Double)

/**
 * A chord in a row. [syllableId] is the syllable it is drawn above: the one it is placed on, or,
 * for a grid chord in a row with lyrics, the syllable nearest to its beat (D-025).
 */
data class PlacedChord(
    val id: String,
    val x: Double,
    val symbol: ChordSymbol,
    val voicingId: String,
    val syllableId: String? = null,
)

data class PlacedTabNote(
    val id: String,
    val x: Double,
    /** 1 = high e … 6 = low E. */
    val string: Int,
    val fret: Int,
    val finger: Int,
    val technique: Technique,
)

data class PlacedText(val id: String, val x: Double, val text: String)

object SheetLayoutBuilder {
    /** Bars per row for sections without lyrics. */
    const val BARS_PER_ROW_WITHOUT_LYRICS: Int = 2

    fun build(song: Song): SheetLayout {
        val numbers = song.occurrenceNumbers()
        return SheetLayout(
            song.sections.map { section ->
                SheetSection(
                    sectionId = section.id,
                    type = section.type,
                    label = section.label,
                    number = numbers[section.id],
                    rows = rows(song.contentOf(section), song.timeSignature),
                )
            },
        )
    }

    /**
     * Rows of one section. With lyrics, each main lyric line gets an equal share of the
     * section's beats (line i covers [i·L/n, (i+1)·L/n)); without lyrics, rows are
     * [BARS_PER_ROW_WITHOUT_LYRICS] bars long. Chords and tab notes go to the row that contains
     * their beat (an event exactly at the section end goes to the last row).
     */
    fun rows(content: SectionContent, timeSignature: TimeSignature): List<SheetRow> {
        val total = content.lengthBeats.toDouble()
        val mainLines = content.lyricLines.filter { it.voice == LyricLine.MAIN_VOICE }
        val spans: List<Pair<Double, Double>> = if (mainLines.isNotEmpty()) {
            val n = mainLines.size
            (0 until n).map { i -> total * i / n to total * (i + 1) / n }
        } else {
            val rowBeats = (BARS_PER_ROW_WITHOUT_LYRICS * timeSignature.beatsPerBar).toDouble()
            val count = maxOf(1, kotlin.math.ceil(total / rowBeats).toInt())
            (0 until count).map { i -> i * rowBeats to minOf(total, (i + 1) * rowBeats) }
        }
        val beatsPerBar = timeSignature.beatsPerBar.toDouble()
        val allSyllableIds = mainLines.flatMap { l -> l.words.flatMap { w -> w.syllables.map { it.id } } }.toHashSet()
        return spans.mapIndexed { i, (start, end) ->
            val length = end - start
            val last = i == spans.lastIndex
            fun inRow(beat: Double) = beat >= start && (beat < end || (last && beat <= end))
            fun x(beat: Double) = if (length <= 0.0) 0.0 else ((beat - start) / length).coerceIn(0.0, 1.0)

            val line = mainLines.getOrNull(i)
            val second = line?.let { m -> content.lyricLines.firstOrNull { it.voice == LyricLine.SECOND_VOICE && it.pairedLineId == m.id } }
            val syllables = line?.let { rowSyllables(it, ::x) }.orEmpty()
            val ids = syllables.mapTo(HashSet()) { it.id }
            // A chord on a syllable belongs to that syllable's row; other chords go by beat.
            val rowChords = content.chords.filter { c ->
                if (c.syllableId != null && c.syllableId in allSyllableIds) c.syllableId in ids else inRow(c.beat)
            }
            SheetRow(
                startBeat = start,
                lengthBeats = length,
                barLines = barLines(start, end, beatsPerBar).map { x(it) },
                chords = rowChords.map { c ->
                    val anchor = c.syllableId?.takeIf { it in ids }
                    val shown = anchor ?: syllables.minByOrNull { kotlin.math.abs(it.x - x(c.beat)) }?.id
                    val cx = syllables.firstOrNull { it.id == anchor }?.x ?: x(c.beat)
                    PlacedChord(c.id, cx, c.symbol, c.voicingId, shown)
                }.sortedBy { it.x },
                tab = content.tab.filter { inRow(it.beat) }.sortedBy { it.beat }
                    .map { PlacedTabNote(it.id, x(it.beat), it.string, it.fret, it.finger, it.technique) },
                lyrics = line?.let { placeLine(it, ::x) }.orEmpty(),
                secondLyrics = second?.let { placeLine(it, ::x) }.orEmpty(),
                syllables = syllables,
            )
        }
    }

    /**
     * The syllables of a line with their x: the start beat when every syllable is timed,
     * otherwise the character position of the syllable in the line text.
     */
    private fun rowSyllables(line: LyricLine, x: (Double) -> Double): List<RowSyllable> {
        val all = line.words.flatMap { it.syllables }
        val timed = all.isNotEmpty() && all.all { it.startBeat != null }
        val totalChars = line.words.sumOf { it.text.length } + (line.words.size - 1).coerceAtLeast(0)
        var offset = 0
        val out = mutableListOf<RowSyllable>()
        line.words.forEachIndexed { w, word ->
            if (w > 0) offset += 1
            word.syllables.forEachIndexed { s, syllable ->
                val position = if (timed) x(syllable.startBeat!!)
                else if (totalChars == 0) 0.0 else offset.toDouble() / totalChars
                out += RowSyllable(syllable.id, syllable.text, startsWord = s == 0, x = position)
                offset += syllable.text.length
            }
        }
        return out
    }

    /** Bar starts within [start, end), as beats. */
    private fun barLines(start: Double, end: Double, beatsPerBar: Double): List<Double> {
        val out = mutableListOf<Double>()
        // "+ 0.0" turns -0.0 (ceil of a tiny negative number) into 0.0.
        var bar = kotlin.math.ceil(start / beatsPerBar - 1e-9) * beatsPerBar + 0.0
        while (bar < end - 1e-9) {
            out += bar
            bar += beatsPerBar
        }
        return out
    }

    /**
     * A lyric line in its row. When every syllable has a start beat, syllables are placed at their
     * beats. Otherwise (nothing sung yet) whole words are placed at their character position in
     * the line text, so the line spreads over the row.
     */
    private fun placeLine(line: LyricLine, x: (Double) -> Double): List<PlacedText> {
        val syllables = line.words.flatMap { it.syllables }
        if (syllables.isNotEmpty() && syllables.all { it.startBeat != null }) {
            return syllables.map { PlacedText(it.id, x(it.startBeat!!), it.text) }
        }
        val totalChars = line.words.sumOf { it.text.length } + (line.words.size - 1).coerceAtLeast(0)
        var offset = 0
        return line.words.map { word ->
            val placed = PlacedText(word.id, if (totalChars == 0) 0.0 else offset.toDouble() / totalChars, word.text)
            offset += word.text.length + 1
            placed
        }
    }
}

/** How a tab note is written in the viewer: the fret with a technique mark. */
object TabNotation {
    fun label(fret: Int, technique: Technique): String = when (technique) {
        Technique.NONE -> "$fret"
        Technique.HAMMER_ON -> "${fret}h"
        Technique.PULL_OFF -> "${fret}p"
        Technique.SLIDE_UP -> "$fret/"
        Technique.SLIDE_DOWN -> "$fret\\"
        Technique.BEND -> "${fret}b"
        Technique.BEND_RELEASE -> "${fret}br"
        Technique.VIBRATO -> "$fret~"
        Technique.NATURAL_HARMONIC -> "<$fret>"
        Technique.ARTIFICIAL_HARMONIC -> "[$fret]"
        Technique.MUTE -> "x"
    }
}
