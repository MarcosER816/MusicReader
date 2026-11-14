package com.musicreader.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicreader.core.model.LineAxes
import com.musicreader.core.model.LineAxis
import com.musicreader.core.model.PlacedChord
import com.musicreader.core.model.PlacedTabNote
import com.musicreader.core.model.RowSyllable
import com.musicreader.core.model.SheetRow

/** Where a syllable ended up on screen (for dropping a dragged chord onto it). */
data class SyllableBox(val syllableId: String, val left: Int, val right: Int, val top: Int, val bottom: Int)

/** A tab note on a visual line, with its x and the x where its duration ends. */
data class StaffNote(val note: PlacedTabNote, val x: Float, val endX: Float)

/** What a visual line's tab staff draws: its notes, its bar lines and its beat axis (for taps). */
data class StaffLine(
    val index: Int,
    val axis: LineAxis,
    val last: Boolean,
    val notes: List<StaffNote>,
    val barXs: List<Float>,
    val width: Int,
)

/**
 * One sheet row (D-025, D-027). With lyrics, the line is written as text (syllables of a word
 * together, a gap between words, wrapping at word boundaries); without lyrics it is a single line.
 * Each visual line is: chords, then the tab staff, then the lyrics.
 *
 * - A chord on a syllable is drawn right above it; other chords are placed by beat.
 * - Tab notes are placed by beat on the line's [LineAxis], which passes through every syllable
 *   at its beat, so chords, tab and lyrics share one beat grid.
 * - Overlapping chord labels move right just enough.
 *
 * [staff] draws a line's tab staff; it is used on lines that have notes, or on every line with
 * [alwaysShowStaff] (the tab editor). With [showLyrics] false the syllables are measured but not
 * drawn, so chords and notes keep their places.
 */
@Composable
fun SheetRowLayout(
    row: SheetRow,
    showChords: Boolean,
    showTab: Boolean,
    showLyrics: Boolean,
    syllableContent: @Composable (RowSyllable) -> Unit,
    chordContent: @Composable (PlacedChord) -> Unit,
    staff: @Composable (StaffLine) -> Unit,
    modifier: Modifier = Modifier,
    alwaysShowStaff: Boolean = false,
    wordGap: Dp = 8.dp,
    chordGap: Dp = 6.dp,
    lineGap: Dp = 6.dp,
    onSyllableBoxes: ((List<SyllableBox>) -> Unit)? = null,
) {
    val syllables = row.syllables
    val chords = if (showChords) row.chords else emptyList()
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val loose = Constraints()
        val syllablePlaceables = subcompose("syllables") { syllables.forEach { syllableContent(it) } }.map { it.measure(loose) }
        val chordPlaceables = subcompose("chords") { chords.forEach { chordContent(it) } }.map { it.measure(loose) }
        val gap = wordGap.roundToPx()

        // 1. Syllables as text, wrapping before a word that does not fit.
        val lineOf = IntArray(syllables.size)
        val xOf = IntArray(syllables.size)
        var line = 0
        var cursor = 0
        var i = 0
        while (i < syllables.size) {
            var end = i + 1
            while (end < syllables.size && !syllables[end].startsWord) end++
            val wordWidth = (i until end).sumOf { syllablePlaceables[it].width }
            if (cursor > 0 && cursor + gap + wordWidth > width) {
                line++
                cursor = 0
            }
            var x = if (cursor == 0) 0 else cursor + gap
            for (k in i until end) {
                lineOf[k] = line
                xOf[k] = x
                x += syllablePlaceables[k].width
            }
            cursor = x
            i = end
        }
        val lineCount = maxOf(1, if (syllables.isEmpty()) 1 else line + 1)

        // 2. Beat axes of the lines.
        val rowEnd = row.startBeat + row.lengthBeats
        val axes = LineAxes.build(
            row.startBeat,
            rowEnd,
            syllables.map { row.startBeat + it.x * row.lengthBeats },
            xOf.map { it.toFloat() },
            lineOf.toList(),
            width.toFloat(),
        )
        fun lineOfBeat(beat: Double): Int =
            axes.indexOfFirst { it.contains(beat, last = it === axes.last()) }.takeIf { it >= 0 } ?: (axes.size - 1)

        // 3. Chords: above their syllable, otherwise by beat; pushed right on overlap.
        val chordLine = IntArray(chords.size)
        val chordX = IntArray(chords.size)
        chords.forEachIndexed { c, chord ->
            val s = syllables.indexOfFirst { it.id == chord.syllableId }
            if (s >= 0) {
                chordLine[c] = lineOf[s]
                chordX[c] = xOf[s]
            } else {
                val beat = row.startBeat + chord.x * row.lengthBeats
                val l = lineOfBeat(beat)
                chordLine[c] = l
                chordX[c] = axes[l].axis.x(beat).toInt().coerceIn(0, maxOf(0, width - chordPlaceables[c].width))
            }
        }
        val order = chords.indices.sortedWith(compareBy({ chordLine[it] }, { chordX[it] }))
        var lastLine = -1
        var right = 0
        for (c in order) {
            if (chordLine[c] != lastLine) {
                lastLine = chordLine[c]
                right = -chordGap.roundToPx()
            }
            chordX[c] = maxOf(chordX[c], right + chordGap.roundToPx())
            right = chordX[c] + chordPlaceables[c].width
        }

        // 4. Tab staff per visual line.
        val barBeats = row.barLines.map { row.startBeat + it * row.lengthBeats }
        val staffPlaceables = (0 until lineCount).map { l ->
            val axis = axes[l]
            val last = l == lineCount - 1
            val notes = row.tab.filter { axis.contains(it.beat, last) }.map { n ->
                StaffNote(n, axis.axis.x(n.beat), axis.axis.x(n.beat + n.durationBeats))
            }
            val show = showTab && (alwaysShowStaff || notes.isNotEmpty())
            if (!show) {
                null
            } else {
                val bars = barBeats.filter { axis.contains(it, last) }.map { axis.axis.x(it) }
                val staffLine = StaffLine(l, axis, last, notes, bars, width)
                subcompose("staff$l") { staff(staffLine) }.firstOrNull()?.measure(Constraints.fixedWidth(width))
            }
        }

        // 5. Vertical positions.
        val chordHeight = chordPlaceables.maxOfOrNull { it.height } ?: 0
        val lyricHeight = if (showLyrics) syllablePlaceables.maxOfOrNull { it.height } ?: 0 else 0
        val spacing = lineGap.roundToPx()
        val chordTop = IntArray(lineCount)
        val staffTop = IntArray(lineCount)
        val lyricTop = IntArray(lineCount)
        var y = 0
        for (l in 0 until lineCount) {
            chordTop[l] = y
            if (order.any { chordLine[it] == l }) y += chordHeight
            staffTop[l] = y
            y += staffPlaceables[l]?.height ?: 0
            lyricTop[l] = y
            if (syllables.isNotEmpty()) y += lyricHeight
            if (l < lineCount - 1) y += spacing
        }

        onSyllableBoxes?.invoke(
            syllables.indices.map { k ->
                val top = lyricTop[lineOf[k]]
                SyllableBox(syllables[k].id, xOf[k], xOf[k] + syllablePlaceables[k].width, top, top + syllablePlaceables[k].height)
            },
        )

        layout(width, y) {
            chords.indices.forEach { c -> chordPlaceables[c].place(chordX[c], chordTop[chordLine[c]]) }
            staffPlaceables.forEachIndexed { l, p -> p?.place(0, staffTop[l]) }
            if (showLyrics) syllablePlaceables.forEachIndexed { k, p -> p.place(xOf[k], lyricTop[lineOf[k]]) }
        }
    }
}
