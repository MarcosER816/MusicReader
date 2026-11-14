package com.musicreader.app.ui.tab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicreader.app.ui.common.StaffLine
import com.musicreader.app.ui.common.StaffNote
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.TabEvent
import com.musicreader.core.model.TabExport
import com.musicreader.core.model.Technique
import kotlin.math.abs
import kotlin.math.roundToInt

/** Sizes of a tab staff, shared by drawing and by the editor's hit tests. */
data class StaffMetrics(val topPx: Float, val gapPx: Float, val heightPx: Float) {
    /** y of string 1 (high e) … 6 (low E). */
    fun y(string: Int): Float = topPx + gapPx * (string - 0.5f)

    /** The string nearest to [y]. */
    fun stringAt(y: Float): Int = ((y - topPx) / gapPx + 0.5f).roundToInt().coerceIn(TabEvent.HIGHEST_STRING, TabEvent.LOWEST_STRING)

    companion object {
        fun of(density: Density, scale: Float): StaffMetrics = with(density) {
            val top = 16.dp.toPx() * scale // room for bend arrows and amounts
            val gap = 12.dp.toPx() * scale
            StaffMetrics(top, gap, top + gap * 6 + 4.dp.toPx())
        }
    }
}

/** The note under a tap at ([x], [y]) on [line], within about one string gap; null when none. */
fun StaffLine.noteAt(x: Float, y: Float, metrics: StaffMetrics): StaffNote? =
    notes.filter { abs(metrics.y(it.note.string) - y) < metrics.gapPx * 0.7f && x >= it.x - metrics.gapPx * 0.6f && x <= it.x + metrics.gapPx * 2f }
        .minByOrNull { abs(it.x - x) }

/** The label written for a note: the fret, x for a muted note, brackets for harmonics. */
fun staffLabel(fret: Int, technique: Technique, harmonicFret: Int?): String = when (technique) {
    Technique.MUTE -> "x"
    Technique.NATURAL_HARMONIC -> "<${harmonicFret ?: fret}>"
    Technique.ARTIFICIAL_HARMONIC -> "[${harmonicFret ?: fret}]"
    else -> "$fret"
}

/**
 * A six-line tab staff for one visual line (D-027). Fret numbers are drawn in their finger's color
 * (open strings with an outline). Hammer-ons and pull-offs: an arc to the next note on the same
 * string with h/p; slides: a diagonal line; bends: an arrow up with the amount (and back down
 * for a release); vibrato: a wavy line over the note's length; harmonics: brackets; muted: x.
 */
@Composable
fun TabStaff(
    line: StaffLine,
    fingerColors: Map<Int, Long>,
    scale: Float,
    modifier: Modifier = Modifier,
    selectedNoteId: String? = null,
) {
    val density = LocalDensity.current
    val metrics = StaffMetrics.of(density, scale)
    val measurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val barColor = MaterialTheme.colorScheme.outline
    val background = MaterialTheme.colorScheme.background
    val markColor = MaterialTheme.colorScheme.onSurface
    val selectedColor = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp * scale, fontWeight = FontWeight.Bold)
    val smallStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp * scale, color = markColor)
    Canvas(modifier.fillMaxWidth().height(with(density) { metrics.heightPx.toDp() })) {
        for (string in TabEvent.HIGHEST_STRING..TabEvent.LOWEST_STRING) {
            drawLine(lineColor, Offset(0f, metrics.y(string)), Offset(size.width, metrics.y(string)), strokeWidth = 1.dp.toPx())
        }
        line.barXs.forEach { x ->
            val bx = x.coerceIn(0f, size.width - 1f)
            drawLine(barColor, Offset(bx, metrics.y(1)), Offset(bx, metrics.y(6)), strokeWidth = 1.5.dp.toPx())
        }
        fun color(finger: Int) = Color((fingerColors[finger] ?: AppSettings.DEFAULT_FINGER_COLORS.getValue(finger.coerceIn(0, 4))).toInt())

        // Labels first measured, so marks can start at the label's right edge.
        val laid = line.notes.map { staffNote ->
            val n = staffNote.note
            val text = measurer.measure(staffLabel(n.fret, n.technique, n.harmonicFret), labelStyle.copy(color = color(n.finger)))
            val left = staffNote.x.coerceIn(0f, size.width - text.size.width)
            Laid(staffNote, text.size.width.toFloat(), text.size.height.toFloat(), left, text)
        }
        for (note in laid) drawMarks(note, laid, metrics, markColor, measurer, smallStyle)
        for (note in laid) {
            val n = note.staffNote.note
            val y = metrics.y(n.string)
            val top = y - note.height / 2f
            val pad = 2.dp.toPx()
            drawRect(background, Offset(note.left - pad, top), Size(note.width + 2 * pad, note.height))
            if (n.finger == 0 && n.technique != Technique.MUTE) {
                val w = maxOf(note.width, note.height)
                drawRoundRect(
                    color(0),
                    topLeft = Offset(note.left + note.width / 2f - w / 2f - pad, top),
                    size = Size(w + 2 * pad, note.height),
                    cornerRadius = CornerRadius(note.height / 2f),
                    style = Stroke(1.dp.toPx()),
                )
            }
            if (n.id == selectedNoteId) {
                drawRoundRect(
                    selectedColor,
                    topLeft = Offset(note.left - 2 * pad, top - pad),
                    size = Size(note.width + 4 * pad, note.height + 2 * pad),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(2.dp.toPx()),
                )
            }
            drawText(note.text, topLeft = Offset(note.left, top))
        }
    }
}

private data class Laid(
    val staffNote: StaffNote,
    val width: Float,
    val height: Float,
    val left: Float,
    val text: androidx.compose.ui.text.TextLayoutResult,
) {
    val right: Float get() = left + width
    val center: Float get() = left + width / 2f
}

/** The marks of one note's technique, drawn under the labels. */
private fun DrawScope.drawMarks(
    note: Laid,
    all: List<Laid>,
    metrics: StaffMetrics,
    color: Color,
    measurer: TextMeasurer,
    smallStyle: TextStyle,
) {
    val n = note.staffNote.note
    val y = metrics.y(n.string)
    val stroke = 1.5.dp.toPx()
    // The next note on the same string on this line (for legato marks).
    val target = all.filter { it.staffNote.note.string == n.string && it.staffNote.note.beat > n.beat }
        .minByOrNull { it.staffNote.note.beat }
        ?.takeIf { it.staffNote.note.beat <= n.beat + n.durationBeats + 1.0 + 1e-9 }
    val stub = 16.dp.toPx()

    fun smallText(text: String, x: Float, yy: Float) {
        val layout = measurer.measure(text, smallStyle)
        drawText(layout, topLeft = Offset(x - layout.size.width / 2f, yy - layout.size.height))
    }

    when (n.technique) {
        Technique.HAMMER_ON, Technique.PULL_OFF -> {
            val x0 = note.center
            val x1 = target?.center ?: (note.right + stub)
            val top = y - note.height / 2f
            val path = Path().apply {
                moveTo(x0, top)
                quadraticTo((x0 + x1) / 2f, top - metrics.gapPx, x1, top)
            }
            drawPath(path, color, style = Stroke(stroke))
            smallText(if (n.technique == Technique.HAMMER_ON) "h" else "p", (x0 + x1) / 2f, top - metrics.gapPx * 0.5f)
        }
        Technique.SLIDE_UP, Technique.SLIDE_DOWN -> {
            val x0 = note.right + 2.dp.toPx()
            val x1 = (target?.left?.minus(2.dp.toPx())) ?: (note.right + stub)
            val dy = metrics.gapPx * 0.35f
            val (y0, y1) = if (n.technique == Technique.SLIDE_UP) (y + dy) to (y - dy) else (y - dy) to (y + dy)
            drawLine(color, Offset(x0, y0), Offset(maxOf(x1, x0 + 6.dp.toPx()), y1), strokeWidth = stroke)
        }
        Technique.BEND, Technique.BEND_RELEASE -> {
            val x0 = note.right + 1.dp.toPx()
            val tipX = x0 + 10.dp.toPx()
            val tipY = metrics.topPx - 2.dp.toPx()
            val path = Path().apply {
                moveTo(x0, y)
                quadraticTo(tipX, y, tipX, tipY + 4.dp.toPx())
            }
            drawPath(path, color, style = Stroke(stroke))
            arrowHead(Offset(tipX, tipY), up = true, color, stroke)
            smallText(TabExport.bendLabel(n.bendQuarterTones ?: 4), tipX, tipY)
            if (n.technique == Technique.BEND_RELEASE) {
                val endX = tipX + 12.dp.toPx()
                val down = Path().apply {
                    moveTo(tipX, tipY + 4.dp.toPx())
                    quadraticTo(endX, tipY + 4.dp.toPx(), endX, y - 3.dp.toPx())
                }
                drawPath(down, color, style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))))
                arrowHead(Offset(endX, y - 2.dp.toPx()), up = false, color, stroke)
            }
        }
        Technique.VIBRATO -> {
            val x0 = note.right + 2.dp.toPx()
            val x1 = maxOf(note.staffNote.endX, x0 + stub)
            val yy = y - metrics.gapPx * 0.55f
            val amplitude = 2.dp.toPx()
            val wave = 6.dp.toPx()
            val path = Path().apply {
                moveTo(x0, yy)
                var x = x0
                var up = true
                while (x < x1) {
                    val next = minOf(x + wave / 2f, x1)
                    quadraticTo((x + next) / 2f, yy + if (up) -amplitude * 2 else amplitude * 2, next, yy)
                    x = next
                    up = !up
                }
            }
            drawPath(path, color, style = Stroke(stroke))
        }
        else -> Unit
    }
}

private fun DrawScope.arrowHead(tip: Offset, up: Boolean, color: Color, stroke: Float) {
    val s = 4.dp.toPx()
    val dir = if (up) 1f else -1f
    drawLine(color, tip, Offset(tip.x - s, tip.y + dir * s), strokeWidth = stroke)
    drawLine(color, tip, Offset(tip.x + s, tip.y + dir * s), strokeWidth = stroke)
}
