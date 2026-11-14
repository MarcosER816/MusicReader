package com.musicreader.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musicreader.core.model.PlacedChord
import com.musicreader.core.model.RowSyllable

/** Where a syllable ended up on screen (for dropping a dragged chord onto it). */
data class SyllableBox(val syllableId: String, val left: Int, val right: Int, val top: Int, val bottom: Int)

/**
 * A lyric line written as normal text (syllables of a word touch, words are separated by a gap,
 * long lines wrap at word boundaries), with each chord drawn right above its syllable (D-025).
 * When two chords would overlap, the later one moves right just enough.
 *
 * Each visual line is: chords, then [middle] (only under the first visual line; the tab lane),
 * then the syllables. With [showLyrics] false the syllables are measured but not drawn, so the
 * chords keep their places.
 */
@Composable
fun ChordLyricsLayout(
    syllables: List<RowSyllable>,
    chords: List<PlacedChord>,
    showChords: Boolean,
    showLyrics: Boolean,
    syllableContent: @Composable (RowSyllable) -> Unit,
    chordContent: @Composable (PlacedChord) -> Unit,
    modifier: Modifier = Modifier,
    middle: (@Composable () -> Unit)? = null,
    wordGap: Dp = 8.dp,
    chordGap: Dp = 6.dp,
    lineGap: Dp = 6.dp,
    onSyllableBoxes: ((List<SyllableBox>) -> Unit)? = null,
) {
    val shownChords = if (showChords) chords.filter { c -> syllables.any { it.id == c.syllableId } } else emptyList()
    Layout(
        contents = listOf(
            { syllables.forEach { syllableContent(it) } },
            { shownChords.forEach { chordContent(it) } },
            { middle?.invoke() },
        ),
        modifier = modifier.fillMaxWidth(),
    ) { (syllableMeasurables, chordMeasurables, middleMeasurables), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints()
        val syllablePlaceables = syllableMeasurables.map { it.measure(loose) }
        val chordPlaceables = chordMeasurables.map { it.measure(loose) }
        val middlePlaceables = middleMeasurables.map { it.measure(Constraints(maxWidth = width)) }
        val gap = wordGap.roundToPx()

        // 1. Syllable positions: line index and x, wrapping before a word that does not fit.
        val lineOf = IntArray(syllables.size)
        val xOf = IntArray(syllables.size)
        var line = 0
        var cursor = 0
        var i = 0
        while (i < syllables.size) {
            var end = i + 1
            while (end < syllables.size && !syllables[end].startsWord) end++
            val wordWidth = (i until end).sumOf { syllablePlaceables[it].width }
            val start = if (cursor == 0) 0 else cursor + gap
            if (cursor > 0 && start + wordWidth > width) {
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
        val lines = if (syllables.isEmpty()) 0 else line + 1

        // 2. Chords above their syllables, pushed right when they would overlap.
        val chordIndex = shownChords.map { c -> syllables.indexOfFirst { it.id == c.syllableId } }
        val chordX = IntArray(shownChords.size)
        val chordLine = IntArray(shownChords.size)
        val order = shownChords.indices.sortedWith(compareBy({ lineOf[chordIndex[it]] }, { xOf[chordIndex[it]] }))
        var lastLine = -1
        var right = 0
        for (c in order) {
            val s = chordIndex[c]
            if (lineOf[s] != lastLine) {
                lastLine = lineOf[s]
                right = -chordGap.roundToPx()
            }
            chordLine[c] = lineOf[s]
            chordX[c] = maxOf(xOf[s], right + chordGap.roundToPx())
            right = chordX[c] + chordPlaceables[c].width
        }

        // 3. Heights of each visual line.
        val chordHeight = chordPlaceables.maxOfOrNull { it.height } ?: 0
        val lyricHeight = if (showLyrics) syllablePlaceables.maxOfOrNull { it.height } ?: 0 else 0
        val middleHeight = middlePlaceables.maxOfOrNull { it.height } ?: 0
        val spacing = lineGap.roundToPx()
        val tops = IntArray(maxOf(lines, 1))
        var y = 0
        for (l in 0 until maxOf(lines, 1)) {
            tops[l] = y
            val hasChords = order.any { chordLine[it] == l }
            y += (if (hasChords) chordHeight else 0) + (if (l == 0) middleHeight else 0) + lyricHeight + spacing
        }
        val total = if (lines == 0) middleHeight + chordHeight else y - spacing

        fun chordTop(l: Int) = tops[l]
        fun lyricTop(l: Int): Int {
            val hasChords = order.any { chordLine[it] == l }
            return tops[l] + (if (hasChords) chordHeight else 0) + (if (l == 0) middleHeight else 0)
        }

        onSyllableBoxes?.invoke(
            syllables.indices.map { k ->
                val top = lyricTop(lineOf[k])
                SyllableBox(syllables[k].id, xOf[k], xOf[k] + syllablePlaceables[k].width, top, top + syllablePlaceables[k].height)
            },
        )

        layout(width, total.coerceAtLeast(0)) {
            middlePlaceables.forEach { it.place(0, if (order.any { c -> chordLine[c] == 0 }) chordHeight else 0) }
            shownChords.indices.forEach { c -> chordPlaceables[c].place(chordX[c], chordTop(chordLine[c])) }
            if (showLyrics) {
                syllablePlaceables.forEachIndexed { k, p: Placeable -> p.place(xOf[k], lyricTop(lineOf[k])) }
            }
        }
    }
}
