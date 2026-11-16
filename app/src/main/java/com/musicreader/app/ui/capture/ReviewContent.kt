package com.musicreader.app.ui.capture

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicreader.app.R
import com.musicreader.app.ui.audio.noteName
import com.musicreader.core.dsp.TunerReading
import com.musicreader.core.model.AccidentalPreference
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** "C4" for a continuous MIDI note; "–" when there is none. */
private fun noteText(midi: Double?, accidentals: AccidentalPreference): String =
    midi?.let { m -> TunerReading.of(m).let { noteName(it.note, accidentals) + it.octave } } ?: "–"

/** "bar 2, beat 3.25" for a section beat (decimals: nothing is snapped). */
@Composable
private fun barBeat(beat: Double, beatsPerBar: Int): String {
    val bar = floor(beat / beatsPerBar + 1e-9).toInt()
    val inBar = beat - bar * beatsPerBar + 1
    val locale = LocalConfiguration.current.locales[0]
    return stringResource(R.string.capture_bar_beat_decimal, bar + 1, String.format(locale, "%.2f", inBar))
}

/**
 * The review after a take (D-031): actions on top, then a time line that scrolls sideways
 * (80 dp per beat) with each syllable's text inside its block at the height of its note, and
 * a list of the syllables where the selected one can be moved.
 */
@Composable
fun ReviewContent(
    review: ReviewState,
    beatsPerBar: Int,
    accidentals: AccidentalPreference,
    onSelect: (Int?) -> Unit,
    onNudge: (start: Boolean, deltaBeats: Double) -> Unit,
    onPlay: () -> Unit,
    onAccept: () -> Unit,
    onAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.capture_review), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAccept) { Text(stringResource(R.string.capture_accept)) }
            OutlinedButton(onClick = onAgain) { Text(stringResource(R.string.capture_again)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
        FilledTonalButton(onClick = onPlay) {
            Text(stringResource(if (review.playing) R.string.capture_stop_playing else R.string.capture_play))
        }
        if (review.missing.isNotEmpty()) {
            Text(pluralStringResource(R.plurals.capture_missing, review.missing.size, review.missing.size), color = MaterialTheme.colorScheme.error)
        }
        ReviewTimeline(review, beatsPerBar, accidentals, onSelect)
        Text(stringResource(R.string.capture_review_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        review.syllables.forEachIndexed { i, s ->
            val selected = i == review.selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(if (selected) null else i) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Word and note on one line, where it is on the next: nothing is squeezed or cut.
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(s.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, softWrap = false)
                    Text(
                        noteText(s.startMidi, accidentals) + if (s.endMidi != null && TunerReading.of(s.endMidi).note != s.startMidi?.let { TunerReading.of(it).note }) " → " + noteText(s.endMidi, accidentals) else "",
                        style = MaterialTheme.typography.titleMedium,
                        softWrap = false,
                    )
                }
                Text(
                    barBeat(s.startBeat, beatsPerBar) + "  " + stringResource(R.string.capture_until, barBeat(s.endBeat, beatsPerBar)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selected) {
                    NudgeRow(stringResource(R.string.capture_start), { onNudge(true, -CaptureViewModel.NUDGE_BEATS) }, { onNudge(true, CaptureViewModel.NUDGE_BEATS) })
                    NudgeRow(stringResource(R.string.capture_end), { onNudge(false, -CaptureViewModel.NUDGE_BEATS) }, { onNudge(false, CaptureViewModel.NUDGE_BEATS) })
                }
            }
        }
    }
}

@Composable
private fun NudgeRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(64.dp))
        OutlinedButton(onClick = onMinus) { Text(stringResource(R.string.capture_earlier)) }
        OutlinedButton(onClick = onPlus) { Text(stringResource(R.string.capture_later)) }
    }
}

@Composable
private fun ReviewTimeline(review: ReviewState, beatsPerBar: Int, accidentals: AccidentalPreference, onSelect: (Int?) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val from = floor(review.fromBeat) - 0.25
    val to = ceil(review.toBeat) + 0.25
    val midis = review.syllables.flatMap { listOfNotNull(it.startMidi, it.endMidi) }
    val mid = if (midis.isEmpty()) 60.0 else (midis.min() + midis.max()) / 2
    val half = max(5.0, if (midis.isEmpty()) 5.0 else (midis.max() - midis.min()) / 2 + 2.5)
    val lo = mid - half
    val hi = mid + half
    val colors = MaterialTheme.colorScheme
    val textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val noteStyle = TextStyle(fontSize = 12.sp)
    val rulerStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val barLabel = stringResource(R.string.capture_bar_short)
    val scroll = rememberScrollState()
    // Text is measured once per take/selection, not on every draw.
    val layouts = remember(review.syllables, review.selected, accidentals, colors) {
        review.syllables.mapIndexed { i, it ->
            val selected = i == review.selected
            measurer.measure(it.text, textStyle.copy(color = if (selected) colors.onTertiaryContainer else colors.onPrimaryContainer)) to
                measurer.measure(noteText(it.startMidi, accidentals), noteStyle.copy(color = colors.onSurfaceVariant))
        }
    }
    val outsideWords = remember(review.syllables, colors) {
        review.syllables.map { measurer.measure(it.text, textStyle.copy(color = colors.onSurface)) }
    }
    val barLabels = remember(from, to, beatsPerBar, colors) {
        (ceil(from).toInt()..floor(to).toInt()).filter { it % beatsPerBar == 0 }
            .associateWith { measurer.measure("$barLabel ${it / beatsPerBar + 1}", rulerStyle.copy(color = colors.onSurface)) }
    }
    // Wide enough that the words fit in their blocks (80–240 dp per beat).
    val padPx = with(density) { 8.dp.toPx() }
    val beatPx = remember(layouts) {
        ReviewLayout.beatWidth(
            review.syllables.map { it.durationBeats },
            layouts.map { it.first.size.width.toFloat() },
            padPx,
            with(density) { 80.dp.toPx() },
            with(density) { 240.dp.toPx() },
        )
    }
    val widthDp = with(density) { (beatPx * (to - from)).toFloat().toDp() }
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(12.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
            .horizontalScroll(scroll),
    ) {
        Canvas(
            Modifier
                .width(widthDp)
                .height(320.dp)
                .pointerInput(review) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                            val beat = from + (to - from) * c.position.x / size.width
                            onSelect(review.syllables.indexOfFirst { beat >= it.startBeat - 0.05 && beat <= it.endBeat + 0.05 }.takeIf { it >= 0 })
                        }
                    }
                },
        ) {
            val ruler = 28.dp.toPx()
            // Room above and below the notes for labels pushed out of the way.
            val top = ruler + 64.dp.toPx()
            val bottom = size.height - 56.dp.toPx()
            fun x(b: Double) = ((b - from) / (to - from) * size.width).toFloat()
            fun y(m: Double) = (top + (1 - (m - lo) / (hi - lo)) * (bottom - top)).toFloat()
            // Ruler: bar numbers at bar lines, a line at every beat.
            var b = ceil(from)
            while (b <= to) {
                val isBar = (b.toInt() % beatsPerBar) == 0
                drawLine(if (isBar) colors.outline else colors.outlineVariant, Offset(x(b), if (isBar) 0f else ruler * 0.6f), Offset(x(b), size.height), if (isBar) 2f else 1f)
                if (isBar) barLabels[b.toInt()]?.let { drawText(it, topLeft = Offset(x(b) + 4.dp.toPx(), 4.dp.toPx())) }
                b += 1.0
            }
            // The sung pitch, behind the blocks.
            var prev: Offset? = null
            for ((beat, m) in review.trace) {
                if (m == null || m < lo || m > hi) {
                    prev = null
                    continue
                }
                val p = Offset(x(beat), y(m))
                prev?.let { drawLine(colors.secondary, it, p, 2.dp.toPx()) }
                prev = p
            }
            // Blocks at their notes; words and note names placed so none overlap.
            val blockH = 28.dp.toPx()
            val blocks = review.syllables.map { s ->
                val left = x(s.startBeat)
                val w = max(x(s.endBeat) - left - 2.dp.toPx(), 6.dp.toPx())
                LabelBox(left, y(s.startMidi ?: mid) - blockH / 2, w, blockH)
            }
            val (wordBoxes, noteBoxes) = ReviewLayout.place(
                blocks,
                layouts.map { it.first.size.width.toFloat() to it.first.size.height.toFloat() },
                layouts.map { it.second.size.width.toFloat() to it.second.size.height.toFloat() },
                padding = 4.dp.toPx(),
                gap = 2.dp.toPx(),
            )
            blocks.forEachIndexed { i, bx ->
                val selected = i == review.selected
                drawRoundRect(
                    if (selected) colors.tertiaryContainer else colors.primaryContainer,
                    Offset(bx.left, bx.top), Size(bx.width, bx.height), CornerRadius(6.dp.toPx()),
                )
                drawRoundRect(
                    if (selected) colors.tertiary else colors.primary,
                    Offset(bx.left, bx.top), Size(bx.width, bx.height), CornerRadius(6.dp.toPx()),
                    style = Stroke(if (selected) 3.dp.toPx() else 1.5.dp.toPx()),
                )
            }
            blocks.indices.forEach { i ->
                val wb = wordBoxes[i]
                val inside = wb.top >= blocks[i].top && wb.bottom <= blocks[i].bottom
                if (!inside) {
                    // A word outside its block gets a background so it reads over lines and pitch.
                    drawRoundRect(colors.surfaceContainerHighest, Offset(wb.left - 2.dp.toPx(), wb.top), Size(wb.width + 4.dp.toPx(), wb.height), CornerRadius(4.dp.toPx()))
                    drawText(outsideWords[i], topLeft = Offset(wb.left, wb.top))
                } else {
                    drawText(layouts[i].first, topLeft = Offset(wb.left, wb.top))
                }
                drawText(layouts[i].second, topLeft = Offset(noteBoxes[i].left, noteBoxes[i].top))
            }
        }
    }
}
