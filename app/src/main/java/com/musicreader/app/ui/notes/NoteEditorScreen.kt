package com.musicreader.app.ui.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.app.ui.capture.LabelBox
import com.musicreader.app.ui.capture.ReviewLayout
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.LyricLine
import com.musicreader.core.model.NoteBar
import com.musicreader.core.model.NoteEditOps
import com.musicreader.core.model.chordColor
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** "A3 +12 cents" (sharps or flats as chosen). */
@Composable
fun noteLabel(midi: Double, accidentals: AccidentalPreference): String {
    val (name, octave, cents) = NoteEditOps.noteParts(midi, accidentals)
    val pretty = name.replace("#", "♯").replace("b", "♭")
    return stringResource(R.string.notes_note_value, pretty + octave, if (cents > 0) "+$cents" else if (cents < 0) "−${-cents}" else "0")
}

/** "1.37 beats". */
@Composable
fun beatsLabel(beats: Double): String =
    stringResource(R.string.notes_beats_value, String.format(LocalConfiguration.current.locales[0], "%.2f", beats))

/** "bar 2, beat 1.25". */
@Composable
fun positionLabel(beat: Double, beatsPerBar: Int): String {
    val bar = floor(beat / beatsPerBar + 1e-9).toInt()
    val inBar = beat - bar * beatsPerBar + 1
    return stringResource(R.string.capture_bar_beat_decimal, bar + 1, String.format(LocalConfiguration.current.locales[0], "%.2f", inBar))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(viewModel: NoteEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notes_title)) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(AppIcons.Back, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) { AppIcon(AppIcons.Undo, stringResource(R.string.undo)) }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo) { AppIcon(AppIcons.Redo, stringResource(R.string.redo)) }
                    IconButton(onClick = viewModel::togglePreview) {
                        AppIcon(if (state.previewing) AppIcons.Pause else AppIcons.Play, stringResource(if (state.previewing) R.string.notes_stop else R.string.notes_play))
                    }
                },
            )
        },
    ) { padding ->
        if (!state.ready) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.hasSecondVoice) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.voice == LyricLine.MAIN_VOICE, onClick = { viewModel.setVoice(LyricLine.MAIN_VOICE) }, label = { Text(stringResource(R.string.main_voice)) })
                    FilterChip(state.voice == LyricLine.SECOND_VOICE, onClick = { viewModel.setVoice(LyricLine.SECOND_VOICE) }, label = { Text(stringResource(R.string.second_voice)) })
                }
            }
            if (state.lines.size > 1) {
                // One lyric line at a time, so the words on the notes have room.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.lines.forEachIndexed { i, line ->
                        FilterChip(
                            selected = i == state.line,
                            onClick = { viewModel.setLine(i) },
                            label = { Text("${i + 1} · " + line.text.take(18) + if (line.text.length > 18) "…" else "", maxLines = 1) },
                        )
                    }
                }
            }
            NoteCanvas(state, viewModel, Modifier.fillMaxWidth().height(380.dp))
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SelectedPanel(state, viewModel)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.notes_fine), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.notes_fine_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.fine, onCheckedChange = viewModel::setFine)
                }
                Text(stringResource(R.string.notes_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.unplaced.isNotEmpty()) {
                    Text(stringResource(R.string.notes_without), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.unplaced.forEach { u ->
                            FilterChip(selected = u.id == state.selectedId, onClick = { viewModel.select(u.id) }, label = { Text(u.text) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedPanel(state: NoteEditorUiState, vm: NoteEditorViewModel) {
    val bar = state.selectedBar
    val unplaced = state.selectedUnplaced
    when {
        bar != null -> {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(bar.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(noteLabel(bar.startMidi, state.accidentals), style = MaterialTheme.typography.titleMedium)
                Text(beatsLabel(bar.durationBeats), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.notes_starts, positionLabel(bar.startBeat, state.beatsPerBar)) +
                    if (abs(bar.endMidi - bar.startMidi) >= 0.005) " · " + stringResource(R.string.notes_ends_on, noteLabel(bar.endMidi, state.accidentals)) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StepRow(stringResource(R.string.notes_note), "−5¢", "+5¢", { vm.nudgeNote(-NoteEditorViewModel.CENTS_STEP) }, { vm.nudgeNote(NoteEditorViewModel.CENTS_STEP) })
            StepRow(stringResource(R.string.notes_first_note), "−5¢", "+5¢", { vm.nudgeStartNote(-NoteEditorViewModel.CENTS_STEP) }, { vm.nudgeStartNote(NoteEditorViewModel.CENTS_STEP) })
            StepRow(stringResource(R.string.notes_last_note), "−5¢", "+5¢", { vm.nudgeEndNote(-NoteEditorViewModel.CENTS_STEP) }, { vm.nudgeEndNote(NoteEditorViewModel.CENTS_STEP) })
            StepRow(stringResource(R.string.capture_start), "−0.01", "+0.01", { vm.nudgeStart(-NoteEditorViewModel.BEAT_STEP) }, { vm.nudgeStart(NoteEditorViewModel.BEAT_STEP) })
            StepRow(stringResource(R.string.notes_length), "−0.01", "+0.01", { vm.nudgeLength(-NoteEditorViewModel.BEAT_STEP) }, { vm.nudgeLength(NoteEditorViewModel.BEAT_STEP) })
            TextButton(onClick = vm::clearNote) { Text(stringResource(R.string.notes_clear)) }
        }
        unplaced != null -> {
            Text(stringResource(R.string.notes_no_note, unplaced.text), style = MaterialTheme.typography.titleMedium)
            FilledTonalButton(onClick = vm::addNote) {
                Text(stringResource(R.string.notes_add, positionLabel(state.cursorBeat, state.beatsPerBar)))
            }
        }
        else -> Text(stringResource(R.string.notes_select_hint), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StepRow(label: String, minus: String, plus: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        OutlinedButton(onClick = onMinus) { Text(minus) }
        OutlinedButton(onClick = onPlus) { Text(plus) }
    }
}

/**
 * The editor canvas (D-032). Gestures: a finger on a bar drags it (body: note or start, right
 * edge: length); on empty space one finger pans and two fingers zoom (horizontally and
 * vertically on their own); a tap selects a bar or sets the cursor.
 */
@Composable
private fun NoteCanvas(state: NoteEditorUiState, vm: NoteEditorViewModel, modifier: Modifier) {
    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val barH = with(density) { 26.dp.toPx() }.toDouble()
    val edge = with(density) { 22.dp.toPx() }.toDouble()
    val hitSlop = with(density) { 8.dp.toPx() }.toDouble()
    val strip = with(density) { 44.dp.toPx() }.toDouble()
    val beatPx = with(density) { 90.dp.toPx() }.toDouble()
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val accidentals = state.accidentals
    // Text measured once (note names for the strip, the bar texts), so drawing allocates nothing.
    val noteNames = remember(accidentals, colors) {
        (NoteEditOps.MIN_MIDI.toInt()..NoteEditOps.MAX_MIDI.toInt()).associateWith { n ->
            val (name, octave, _) = NoteEditOps.noteParts(n.toDouble(), accidentals)
            measurer.measure(name.replace("#", "♯").replace("b", "♭") + octave, TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant))
        }
    }
    val barTexts = remember(state.bars.map { it.text }, colors) {
        state.bars.associate { it.id to measurer.measure(it.text, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onPrimaryContainer)) }
    }
    val outsideTexts = remember(state.bars.map { it.text }, colors) {
        state.bars.associate { it.id to measurer.measure(it.text, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)) }
    }
    // Where each word goes: inside its bar when it fits, else above, never over another word
    // or bar (the same placement as the take review, D-031).
    val wordBoxes = remember(state.bars, state.viewport, barTexts) {
        val vp = state.viewport ?: return@remember emptyList<LabelBox>()
        val blocks = state.bars.map { b ->
            val x0 = vp.beatToX(b.startBeat)
            val x1 = max(vp.beatToX(b.endBeat), x0 + 3.0)
            val top = vp.midiToY(max(b.startMidi, b.endMidi)) - barH / 2
            val bottom = vp.midiToY(minOf(b.startMidi, b.endMidi)) + barH / 2
            LabelBox(x0.toFloat(), top.toFloat(), (x1 - x0).toFloat(), (bottom - top).toFloat())
        }
        val sizes = state.bars.map { b -> barTexts[b.id]!!.let { it.size.width.toFloat() to it.size.height.toFloat() } }
        ReviewLayout.place(blocks, sizes, sizes.map { 0f to 0f }, padding = 6f, gap = 3f).first
    }
    val chordTexts = remember(state.chords, state.settings, colors) {
        state.chords.map { c -> measurer.measure(c.symbol.toString(), TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(state.settings.chordColor(c.symbol).toInt()).copy(alpha = 0.55f))) }
    }
    val sel = state.selectedBar
    val selectedLabel = sel?.let { noteLabel(it.startMidi, accidentals) + " · " + beatsLabel(it.durationBeats) }
    val selectedText = remember(selectedLabel, colors) {
        selectedLabel?.let { measurer.measure(it, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)) }
    }
    val barLabel = stringResource(R.string.capture_bar_short)
    val barNumbers = remember(state.lengthBeats, state.beatsPerBar, colors) {
        (0..state.lengthBeats / state.beatsPerBar).associateWith { measurer.measure("$barLabel ${it + 1}", TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant)) }
    }
    val bubble = when (state.dragging) {
        DragValue.NOTE -> sel?.let { noteLabel(it.startMidi, accidentals) }
        DragValue.START -> sel?.let { positionLabel(it.startBeat, state.beatsPerBar) }
        DragValue.LENGTH -> sel?.let { beatsLabel(it.durationBeats) }
        null -> null
    }
    var playhead by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(state.previewing) {
        playhead = null
        while (state.previewing) {
            withFrameNanos { }
            playhead = vm.previewBeat()
        }
    }
    val shape = remember { Path() }
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .background(colors.surfaceContainerLowest)
                .onSizeChanged { vm.setSize(it.width.toDouble(), it.height.toDouble(), strip, beatPx) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        if (vm.dragStart(start.x.toDouble(), start.y.toDouble(), barH, edge, hitSlop)) {
                            // Dragging a bar until the finger lifts.
                            while (true) {
                                val e = awaitPointerEvent()
                                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed) break
                                vm.dragTo((c.position.x - start.x).toDouble(), (c.position.y - start.y).toDouble())
                                c.consume()
                            }
                            vm.dragEnd()
                        } else {
                            // Panning (one finger) or zooming (two); a short touch is a tap.
                            var moved = false
                            while (true) {
                                val e = awaitPointerEvent()
                                val pressed = e.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    val a = pressed[0]
                                    val b = pressed[1]
                                    val spanX = abs(a.position.x - b.position.x).toDouble()
                                    val spanY = abs(a.position.y - b.position.y).toDouble()
                                    val prevX = abs(a.previousPosition.x - b.previousPosition.x).toDouble()
                                    val prevY = abs(a.previousPosition.y - b.previousPosition.y).toDouble()
                                    val zx = if (prevX > 48 && spanX > 48) spanX / prevX else 1.0
                                    val zy = if (prevY > 48 && spanY > 48) spanY / prevY else 1.0
                                    vm.zoom(zx, zy, ((a.position.x + b.position.x) / 2).toDouble(), ((a.position.y + b.position.y) / 2).toDouble())
                                    moved = true
                                } else {
                                    val c = pressed[0]
                                    val d = c.positionChange()
                                    if (moved || (c.position - start).getDistance() > slop) {
                                        moved = true
                                        vm.pan(d.x.toDouble(), d.y.toDouble())
                                    }
                                }
                                e.changes.forEach { it.consume() }
                            }
                            if (!moved) vm.tap(start.x.toDouble(), start.y.toDouble(), barH, edge, hitSlop)
                        }
                    }
                },
        ) {
            val vp = state.viewport ?: return@Canvas
            fun x(b: Double) = vp.beatToX(b).toFloat()
            fun y(m: Double) = vp.midiToY(m).toFloat()
            val stripW = vp.stripPx.toFloat()
            // Pitch rows: black keys shaded, C lines stronger, names in the strip.
            val lowNote = floor(vp.bottomMidi).toInt()
            val highNote = ceil(vp.topMidi).toInt()
            for (n in lowNote..highNote) {
                val pc = Math.floorMod(n, 12)
                val rowTop = y(n + 0.5)
                if (pc in intArrayOf(1, 3, 6, 8, 10)) {
                    drawRect(colors.surfaceContainer, Offset(stripW, rowTop), Size(size.width - stripW, vp.pxPerSemitone.toFloat()))
                }
                if (pc == 0) drawLine(colors.outlineVariant, Offset(stripW, y(n - 0.5)), Offset(size.width, y(n - 0.5)), 1.5f)
            }
            // Beats and bars.
            var b = ceil(vp.firstBeat)
            while (b <= vp.lastBeat) {
                val isBar = Math.floorMod(b.toInt(), state.beatsPerBar) == 0
                drawLine(if (isBar) colors.outline else colors.outlineVariant.copy(alpha = 0.6f), Offset(x(b), 0f), Offset(x(b), size.height), if (isBar) 2f else 1f)
                if (isBar) barNumbers[b.toInt() / state.beatsPerBar]?.let { drawText(it, topLeft = Offset(x(b) + 4f, size.height - it.size.height - 2f)) }
                b += 1.0
            }
            drawLine(colors.error.copy(alpha = 0.5f), Offset(x(state.lengthBeats.toDouble()), 0f), Offset(x(state.lengthBeats.toDouble()), size.height), 2f)
            // Chords, faint, along the top.
            state.chords.forEachIndexed { i, c -> if (x(c.beat) >= stripW) drawText(chordTexts[i], topLeft = Offset(x(c.beat) + 2f, 2f)) }
            // Sung curves behind the bars.
            for (curve in state.curves.values) {
                for (k in 1 until curve.size) {
                    drawLine(colors.secondary.copy(alpha = 0.8f), Offset(x(curve[k - 1].first), y(curve[k - 1].second)), Offset(x(curve[k].first), y(curve[k].second)), 2.dp.toPx())
                }
            }
            // The other lines' bars, faint and without words, for context.
            val h = (barH / 2).toFloat()
            for (bar in state.otherBars) {
                val x0 = x(bar.startBeat)
                val x1 = max(x(bar.endBeat), x0 + 3f)
                shape.reset()
                shape.moveTo(x0, y(bar.startMidi) - h * 0.6f)
                shape.lineTo(x1, y(bar.endMidi) - h * 0.6f)
                shape.lineTo(x1, y(bar.endMidi) + h * 0.6f)
                shape.lineTo(x0, y(bar.startMidi) + h * 0.6f)
                shape.close()
                drawPath(shape, colors.outline.copy(alpha = 0.35f))
            }
            // Bars of the chosen line: from the initial to the final note.
            for (bar in state.bars) {
                val selected = bar.id == state.selectedId
                val x0 = x(bar.startBeat)
                val x1 = max(x(bar.endBeat), x0 + 3f)
                val y0 = y(bar.startMidi)
                val y1 = y(bar.endMidi)
                shape.reset()
                shape.moveTo(x0, y0 - h)
                shape.lineTo(x1, y1 - h)
                shape.lineTo(x1, y1 + h)
                shape.lineTo(x0, y0 + h)
                shape.close()
                drawPath(shape, if (selected) colors.tertiaryContainer else colors.primaryContainer)
                drawPath(shape, if (selected) colors.tertiary else colors.primary, style = Stroke(if (selected) 3.dp.toPx() else 1.5.dp.toPx()))

                if (selected) {
                    // The length grip at the right end, and the values beside the bar.
                    drawRoundRect(colors.tertiary, Offset(x1 - 5.dp.toPx(), y1 - h + 3f), Size(4.dp.toPx(), 2 * h - 6f), CornerRadius(2.dp.toPx()))
                }
            }
            // Words: inside their bar, or above it on a small background, never overlapping.
            state.bars.forEachIndexed { i, bar ->
                val box = wordBoxes.getOrNull(i) ?: return@forEachIndexed
                val x0 = x(bar.startBeat)
                val x1 = max(x(bar.endBeat), x0 + 3f)
                val inside = box.left >= x0 && box.right <= x1 + 1f && box.top >= minOf(y(bar.startMidi), y(bar.endMidi)) - h - 1f &&
                    box.bottom <= maxOf(y(bar.startMidi), y(bar.endMidi)) + h + 1f
                if (inside) {
                    barTexts[bar.id]?.let { t -> drawText(t, topLeft = Offset(box.left, box.top)) }
                } else {
                    // A thin leader from the word down to its bar, so it is clear whose word it is.
                    val barTop = minOf(y(bar.startMidi), y(bar.endMidi)) - h
                    val ax = minOf(box.left + 6f, x1)
                    drawLine(colors.onSurfaceVariant.copy(alpha = 0.7f), Offset(ax, box.bottom), Offset((x0 + x1) / 2, barTop), 1.5f)
                    drawRoundRect(colors.surfaceContainerHighest, Offset(box.left - 3f, box.top - 1f), Size(box.width + 6f, box.height + 2f), CornerRadius(4.dp.toPx()))
                    outsideTexts[bar.id]?.let { t -> drawText(t, topLeft = Offset(box.left, box.top)) }
                }
            }
            // The selected bar's values, last so nothing covers them: right of the bar if there
            // is room, else under it (words go above bars).
            sel?.let { bar ->
                val x0 = x(bar.startBeat)
                val x1 = max(x(bar.endBeat), x0 + 3f)
                val y0 = y(bar.startMidi)
                val y1 = y(bar.endMidi)
                selectedText?.let { t ->
                    val gap = 8.dp.toPx()
                    val w = t.size.width
                    val at = if (x1 + gap + w <= size.width - gap) Offset(x1 + gap, (y0 + y1) / 2 - t.size.height / 2)
                    else Offset(max(stripW + 4f, minOf(x0, size.width - w - 4f)), maxOf(y0, y1) + h + 6f)
                    drawRoundRect(colors.surfaceContainerHighest, Offset(at.x - 4f, at.y - 2f), Size(w + 8f, t.size.height + 4f), CornerRadius(4.dp.toPx()))
                    drawText(t, topLeft = at)
                }
            }
            // Cursor and playhead.
            drawLine(colors.primary, Offset(x(state.cursorBeat), 0f), Offset(x(state.cursorBeat), size.height), 2f, pathEffect = dash)
            playhead?.let { p -> drawLine(colors.error, Offset(x(p), 0f), Offset(x(p), size.height), 3f) }
            // The note-name strip over everything on the left.
            drawRect(colors.surfaceContainerHigh, Offset.Zero, Size(stripW, size.height))
            for (n in lowNote..highNote) {
                val pc = Math.floorMod(n, 12)
                val show = vp.pxPerSemitone >= 13 || pc == 0
                val t = noteNames[n] ?: continue
                if (show) drawText(t, topLeft = Offset(4f, y(n.toDouble()) - t.size.height / 2))
            }
        }
        // The magnifier: the exact value while dragging, large.
        bubble?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onTertiaryContainer,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(colors.tertiaryContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
