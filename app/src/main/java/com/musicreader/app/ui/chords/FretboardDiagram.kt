package com.musicreader.app.ui.chords

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.musicreader.app.ui.common.numberColorOn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicreader.core.data.ChordShape
import com.musicreader.core.model.AppSettings

/**
 * A chord diagram: strings are vertical (low E on the left, or on the right when [leftHanded]),
 * frets horizontal. Shows the nut (base fret 1) or "Nfr", × over muted and o over open strings,
 * barres, and a dot per pressed string in the finger's color with the finger number inside.
 * Finger colors are ARGB (settings); finger 0 (open) is drawn as an outline.
 */
@Composable
fun FretboardDiagram(
    shape: ChordShape,
    fingerColors: Map<Int, Long>,
    modifier: Modifier = Modifier,
    leftHanded: Boolean = false,
    fretsShown: Int = 4,
    width: Dp = 180.dp,
) {
    val measurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, color = lineColor)
    val description = shape.frets.joinToString(" ") { if (it < 0) "x" else "$it" }
    Canvas(
        modifier
            .size(width, width * 1.15f)
            .semantics { contentDescription = description },
    ) {
        fun color(finger: Int) = Color((fingerColors[finger] ?: AppSettings.DEFAULT_FINGER_COLORS.getValue(finger.coerceIn(0, 4))).toInt())
        drawDiagram(shape, ::color, measurer, labelStyle, lineColor, leftHanded, maxOf(fretsShown, needed(shape)))
    }
}

/** Frets the shape needs from its base fret (at least the dictionary's 4). */
private fun needed(shape: ChordShape): Int =
    (shape.frets.filter { it > 0 }.maxOrNull()?.let { it - shape.baseFret + 1 } ?: 1).coerceAtLeast(1)

private fun DrawScope.drawDiagram(
    shape: ChordShape,
    color: (Int) -> Color,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
    lineColor: Color,
    leftHanded: Boolean,
    frets: Int,
) {
    val top = size.height * 0.16f
    val left = size.width * 0.2f
    val right = size.width * 0.92f
    val bottom = size.height * 0.97f
    val stringGap = (right - left) / 5f
    val fretGap = (bottom - top) / frets
    val dotRadius = minOf(stringGap, fretGap) * 0.38f
    val thin = 1.5.dp.toPx()

    // String index 0 = low E. Mirror for left-handed players.
    fun x(stringIndex: Int) = left + stringGap * (if (leftHanded) 5 - stringIndex else stringIndex)
    fun y(fret: Int) = top + fretGap * (fret - shape.baseFret + 0.5f)

    for (s in 0..5) drawLine(lineColor, Offset(x(s), top), Offset(x(s), bottom), strokeWidth = thin)
    for (f in 0..frets) {
        val yy = top + fretGap * f
        drawLine(lineColor, Offset(left, yy), Offset(right, yy), strokeWidth = thin)
    }
    if (shape.baseFret == 1) {
        drawRect(lineColor, Offset(left - thin / 2, top - 4.dp.toPx()), Size(right - left + thin, 5.dp.toPx()))
    } else {
        val label = measurer.measure("${shape.baseFret}fr", labelStyle)
        val sideX = if (leftHanded) right + 4.dp.toPx() else left - label.size.width - 6.dp.toPx()
        drawText(label, topLeft = Offset(sideX, top + fretGap / 2 - label.size.height / 2f))
    }

    // × for muted and o for open strings, above the nut.
    val markY = top - fretGap * 0.45f - 4.dp.toPx()
    for (s in 0..5) {
        when (shape.frets[s]) {
            -1 -> {
                val r = dotRadius * 0.6f
                drawLine(lineColor, Offset(x(s) - r, markY - r), Offset(x(s) + r, markY + r), strokeWidth = 2.dp.toPx())
                drawLine(lineColor, Offset(x(s) - r, markY + r), Offset(x(s) + r, markY - r), strokeWidth = 2.dp.toPx())
            }
            0 -> drawCircle(color(0), radius = dotRadius * 0.62f, center = Offset(x(s), markY), style = Stroke(2.dp.toPx()))
        }
    }

    fun drawNumber(finger: Int, center: Offset, fill: Color) {
        val textColor = numberColorOn(fill)
        val layout = measurer.measure("$finger", labelStyle.copy(color = textColor, fontWeight = FontWeight.Bold))
        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
    }

    // Barres: a bar across the strings it covers, in the finger's color.
    val barred = mutableSetOf<Int>()
    for (barre in shape.barres) {
        val from = 6 - barre.fromString
        val to = 6 - barre.toString
        val xa = minOf(x(from), x(to))
        val xb = maxOf(x(from), x(to))
        val fill = color(barre.finger)
        drawRoundRect(
            fill,
            topLeft = Offset(xa - dotRadius, y(barre.fret) - dotRadius),
            size = Size(xb - xa + 2 * dotRadius, 2 * dotRadius),
            cornerRadius = CornerRadius(dotRadius),
        )
        drawNumber(barre.finger, Offset((xa + xb) / 2f, y(barre.fret)), fill)
        for (s in from..to) if (shape.frets[s] == barre.fret) barred += s
    }

    // One dot per pressed string that is not already under its barre.
    for (s in 0..5) {
        val fret = shape.frets[s]
        if (fret <= 0 || s in barred) continue
        val finger = shape.fingers[s]
        val fill = color(finger)
        val center = Offset(x(s), y(fret))
        drawCircle(fill, radius = dotRadius, center = center)
        if (finger > 0) drawNumber(finger, center, fill)
    }
}
