package com.musicreader.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.musicreader.core.model.PlacedText

/** Lyrics of a sheet row, each word or syllable at its x position (shared by viewer and chord editor). */
@Composable
fun LyricLane(texts: List<PlacedText>, style: TextStyle, color: Color) {
    PlacedLane(texts.map { it.x }) {
        texts.forEach { Text(it.text, style = style, color = color, maxLines = 1) }
    }
}

/**
 * Places its children at x fractions of the width. A child never overlaps the one before it
 * (it is pushed right), and when the row overflows everything is shifted left as far as possible.
 */
@Composable
fun PlacedLane(xs: List<Double>, content: @Composable () -> Unit) {
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints()) }
        val lefts = IntArray(placeables.size)
        var right = -gap
        placeables.forEachIndexed { i, p ->
            val wanted = ((xs.getOrElse(i) { 0.0 }) * width).toInt()
            lefts[i] = maxOf(wanted, right + gap)
            right = lefts[i] + p.width
        }
        val overflow = (right - width).coerceAtMost(lefts.firstOrNull() ?: 0)
        if (overflow > 0) for (i in lefts.indices) lefts[i] -= overflow
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, p -> p.place(lefts[i], 0) }
        }
    }
}

