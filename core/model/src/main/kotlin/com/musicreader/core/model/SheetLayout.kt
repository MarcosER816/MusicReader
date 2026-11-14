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
)

data class PlacedChord(val id: String, val x: Double, val symbol: ChordSymbol, val voicingId: String)

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
        return spans.mapIndexed { i, (start, end) ->
            val length = end - start
            val last = i == spans.lastIndex
            fun inRow(beat: Double) = beat >= start && (beat < end || (last && beat <= end))
            fun x(beat: Double) = if (length <= 0.0) 0.0 else ((beat - start) / length).coerceIn(0.0, 1.0)

            val line = mainLines.getOrNull(i)
            val second = line?.let { m -> content.lyricLines.firstOrNull { it.voice == LyricLine.SECOND_VOICE && it.pairedLineId == m.id } }
            SheetRow(
                startBeat = start,
                lengthBeats = length,
                barLines = barLines(start, end, beatsPerBar).map { x(it) },
                chords = content.chords.filter { inRow(it.beat) }.sortedBy { it.beat }
                    .map { PlacedChord(it.id, x(it.beat), it.symbol, it.voicingId) },
                tab = content.tab.filter { inRow(it.beat) }.sortedBy { it.beat }
                    .map { PlacedTabNote(it.id, x(it.beat), it.string, it.fret, it.finger, it.technique) },
                lyrics = line?.let { placeLine(it, ::x) }.orEmpty(),
                secondLyrics = second?.let { placeLine(it, ::x) }.orEmpty(),
            )
        }
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
