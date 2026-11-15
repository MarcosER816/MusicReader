package com.musicreader.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicreader.app.R
import com.musicreader.app.ui.audio.noteName
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.core.dsp.TunerReading
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.LyricLine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(viewModel: CaptureViewModel, accidentals: AccidentalPreference, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.sing() else viewModel.permissionDenied()
    }
    fun sing() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.sing() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capture_title)) },
                navigationIcon = { IconButton(onClick = { viewModel.cancel(); onBack() }) { AppIcon(AppIcons.Back, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (!state.ready) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val phase = state.phase) {
                CapturePhase.Idle -> SelectionPanel(state, viewModel, onSing = ::sing)
                CapturePhase.Preparing -> BigText(stringResource(R.string.capture_preparing))
                is CapturePhase.CountIn -> CountIn(phase, state.tapMode, viewModel)
                is CapturePhase.Singing -> SingingPanel(state, phase, viewModel)
                CapturePhase.Analyzing -> BigText(stringResource(R.string.capture_analyzing))
                CapturePhase.Review -> state.review?.let { ReviewPanel(it, state, accidentals, viewModel) }
                is CapturePhase.Failed -> {
                    Text(
                        stringResource(
                            when (phase.reason) {
                                CaptureError.NO_MICROPHONE -> R.string.capture_no_microphone
                                CaptureError.NO_METRONOME -> R.string.capture_no_metronome
                                CaptureError.NOTHING_SUNG -> R.string.capture_nothing_sung
                                CaptureError.AUDIO -> R.string.capture_audio_error
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
    if (state.askBar) BarDialog(state, viewModel)
}

@Composable
private fun BigText(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth().padding(top = 48.dp), textAlign = TextAlign.Center)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectionPanel(state: CaptureUiState, vm: CaptureViewModel, onSing: () -> Unit) {
    if (state.hasSecondVoice) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.voice == LyricLine.MAIN_VOICE, onClick = { vm.setVoice(LyricLine.MAIN_VOICE) }, label = { Text(stringResource(R.string.main_voice)) })
            FilterChip(state.voice == LyricLine.SECOND_VOICE, onClick = { vm.setVoice(LyricLine.SECOND_VOICE) }, label = { Text(stringResource(R.string.second_voice)) })
        }
    }
    Text(stringResource(R.string.capture_select_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.lines.isEmpty()) Text(stringResource(R.string.no_lyrics), style = MaterialTheme.typography.bodyLarge)

    // Words: tap the first and the last, or long-press and drag over them.
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragFrom by remember { mutableStateOf<String?>(null) }
    fun wordAt(p: Offset): String? = bounds.entries.firstOrNull { it.value.contains(p) }?.key
    var origin by remember { mutableStateOf(Offset.Zero) }
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { p -> dragFrom = wordAt(p + origin); dragFrom?.let { vm.dragSelect(it, it) } },
                    onDrag = { change, _ ->
                        val from = dragFrom ?: return@detectDragGesturesAfterLongPress
                        wordAt(change.position + origin)?.let { vm.dragSelect(from, it) }
                    },
                    onDragEnd = { dragFrom = null },
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.lines.forEach { line ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                line.words.forEach { word ->
                    WordButton(word, onClick = { vm.tapWord(word.id) }, modifier = Modifier.onGloballyPositioned { bounds[word.id] = it.boundsInRoot() })
                }
            }
        }
    }
    MarksLegend()
    HorizontalDivider()
    if (state.selection.isNotEmpty()) {
        Text(stringResource(R.string.capture_selected, state.selectionText), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.startBar?.let { stringResource(R.string.capture_starts_bar, it + 1) } ?: stringResource(R.string.capture_bar_unknown),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::changeBar) { Text(stringResource(R.string.capture_change_bar)) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.capture_tap_mode), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.capture_tap_mode_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = state.tapMode, onCheckedChange = vm::setTapMode)
    }
    if (state.latencyMs == null) {
        Text(stringResource(R.string.capture_latency_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.permissionDenied) Text(stringResource(R.string.tuner_mic_denied_text), color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSing, enabled = state.selection.isNotEmpty()) { Text(stringResource(R.string.capture_sing)) }
        OutlinedButton(onClick = vm::clearSelection, enabled = state.selection.isNotEmpty()) { Text(stringResource(R.string.capture_clear)) }
        if (state.canUndo) TextButton(onClick = vm::undoLastTake) { Text(stringResource(R.string.capture_undo)) }
    }
}

@Composable
private fun WordButton(word: CaptureWord, onClick: () -> Unit, modifier: Modifier) {
    val selected = word.selected
    Row(
        modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        word.syllables.forEachIndexed { i, s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    (if (i > 0) "·" else "") + s.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                )
                SyllableMarkBar(s.timed, s.hasNote)
            }
        }
    }
}

/** The mark under a syllable: a line when it has timing, a thicker one when it also has a note. */
@Composable
fun SyllableMarkBar(timed: Boolean, hasNote: Boolean) {
    val color = when {
        hasNote -> MaterialTheme.colorScheme.primary
        timed -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f)
    }
    Box(Modifier.padding(top = 2.dp).width(18.dp).height(if (hasNote) 4.dp else 2.dp).background(color, RoundedCornerShape(2.dp)))
}

@Composable
private fun MarksLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SyllableMarkBar(timed = true, hasNote = true)
        Text(stringResource(R.string.mark_note), style = MaterialTheme.typography.labelSmall)
        SyllableMarkBar(timed = true, hasNote = false)
        Text(stringResource(R.string.mark_timed), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BarDialog(state: CaptureUiState, vm: CaptureViewModel) {
    var bar by rememberSaveable { mutableIntStateOf(state.startBar ?: state.suggestedBar) }
    AlertDialog(
        onDismissRequest = vm::dismissBar,
        title = { Text(stringResource(R.string.capture_which_bar)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.capture_which_bar_hint))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { bar = (bar - 1).coerceAtLeast(0) }) { Text("−") }
                    Text(stringResource(R.string.capture_bar_n, bar + 1, state.bars), style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { bar = (bar + 1).coerceAtMost(state.bars - 1) }) { Text("+") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.chooseBar(bar) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = vm::dismissBar) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CountIn(phase: CapturePhase.CountIn, tapMode: Boolean, vm: CaptureViewModel) {
    Text(stringResource(R.string.capture_count_in), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(phase.beatsPerBar) { i ->
            val on = i + 1 == phase.beat
            Box(
                Modifier.padding(6.dp).size(if (on) 56.dp else 44.dp)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("${i + 1}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    StopAndTap(tapMode, vm, taps = null)
}

@Composable
private fun SingingPanel(state: CaptureUiState, phase: CapturePhase.Singing, vm: CaptureViewModel) {
    Text(stringResource(R.string.capture_sing_now), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Text(stringResource(R.string.capture_bar_beat, phase.bar, phase.beat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Text(state.selectionText, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    StopAndTap(state.tapMode, vm, taps = phase.taps to state.selection.size)
}

@Composable
private fun StopAndTap(tapMode: Boolean, vm: CaptureViewModel, taps: Pair<Int, Int>?) {
    if (tapMode) {
        val label = stringResource(R.string.capture_tap)
        Box(
            Modifier.fillMaxWidth().height(160.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(24.dp))
                .semantics { contentDescription = label }
                .pointerInput(Unit) { detectTapGesturesOnPress { vm.tap() } },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (taps == null) label else stringResource(R.string.capture_taps, taps.first, taps.second),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
    Button(onClick = vm::stop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.capture_stop)) }
    Text(stringResource(R.string.capture_auto_stop), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Taps fire on finger down (not on release), so they are on time. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapGesturesOnPress(onPress: () -> Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            if (e.changes.any { it.pressed && !it.previousPressed }) onPress()
        }
    }
}

@Composable
private fun ReviewPanel(r: ReviewState, state: CaptureUiState, accidentals: AccidentalPreference, vm: CaptureViewModel) {
    Text(stringResource(R.string.capture_review), style = MaterialTheme.typography.titleLarge)
    if (r.missing.isNotEmpty()) {
        Text(pluralStringResource(R.plurals.capture_missing, r.missing.size, r.missing.size), color = MaterialTheme.colorScheme.error)
    }
    Timeline(r, accidentals, onSelect = vm::selectReviewed)
    val sel = r.selected?.let { r.syllables.getOrNull(it) }
    if (sel == null) {
        Text(stringResource(R.string.capture_review_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(
            stringResource(R.string.capture_selected_syllable, sel.text, formatBeat(sel.startBeat, state), formatBeat(sel.endBeat, state)),
            style = MaterialTheme.typography.bodyMedium,
        )
        NudgeRow(stringResource(R.string.capture_start), onMinus = { vm.nudge(true, -CaptureViewModel.NUDGE_BEATS) }, onPlus = { vm.nudge(true, CaptureViewModel.NUDGE_BEATS) })
        NudgeRow(stringResource(R.string.capture_end), onMinus = { vm.nudge(false, -CaptureViewModel.NUDGE_BEATS) }, onPlus = { vm.nudge(false, CaptureViewModel.NUDGE_BEATS) })
    }
    FilledTonalButton(onClick = { if (r.playing) vm.stopPlaying() else vm.playTake() }) {
        Text(stringResource(if (r.playing) R.string.capture_stop_playing else R.string.capture_play))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::accept) { Text(stringResource(R.string.capture_accept)) }
        OutlinedButton(onClick = vm::singAgain) { Text(stringResource(R.string.capture_again)) }
        TextButton(onClick = vm::cancel) { Text(stringResource(R.string.cancel)) }
    }
}

/** "bar 3, beat 2.25" for a section beat (decimals: nothing is snapped). */
@Composable
private fun formatBeat(beat: Double, state: CaptureUiState): String {
    val bpb = state.beatsPerBar
    val bar = (beat / bpb).toInt() + 1
    val inBar = beat - (bar - 1) * bpb + 1
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return stringResource(R.string.capture_bar_beat_decimal, bar, String.format(locale, "%.2f", inBar))
}

@Composable
private fun NudgeRow(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(72.dp))
        OutlinedButton(onClick = onMinus) { Text(stringResource(R.string.capture_earlier)) }
        OutlinedButton(onClick = onPlus) { Text(stringResource(R.string.capture_later)) }
    }
}

/**
 * The take on a beat time line: the pitch trace, one block per syllable at its note (height =
 * pitch), with the syllable and note names; tap a block to select it.
 */
@Composable
private fun Timeline(r: ReviewState, accidentals: AccidentalPreference, onSelect: (Int?) -> Unit) {
    val from = r.fromBeat - 0.25
    val to = r.toBeat + 0.25
    val midis = r.syllables.mapNotNull { it.startMidi } + r.trace.mapNotNull { it.second }
    val lo = (midis.minOrNull() ?: 55.0) - 2
    val hi = (midis.maxOrNull() ?: 67.0) + 2
    val traceColor = MaterialTheme.colorScheme.outline
    val block = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val beatLine = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .pointerInput(r) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        val c = e.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                        val beat = from + (to - from) * c.position.x / size.width
                        onSelect(r.syllables.indexOfFirst { beat >= it.startBeat && beat <= it.endBeat }.takeIf { it >= 0 })
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun x(b: Double) = ((b - from) / (to - from) * size.width).toFloat()
            fun y(m: Double) = ((1 - (m - lo) / (hi - lo)) * size.height).toFloat()
            var b = kotlin.math.ceil(from)
            while (b <= to) {
                drawLine(beatLine, Offset(x(b), 0f), Offset(x(b), size.height), 1f)
                b += 1.0
            }
            var prev: Offset? = null
            for ((beat, m) in r.trace) {
                if (m == null) {
                    prev = null
                    continue
                }
                val p = Offset(x(beat), y(m))
                prev?.let { drawLine(traceColor, it, p, 2f) }
                prev = p
            }
            r.syllables.forEachIndexed { i, s ->
                val m0 = s.startMidi ?: return@forEachIndexed
                val m1 = s.endMidi ?: m0
                drawLine(
                    if (i == r.selected) selectedColor else block,
                    Offset(x(s.startBeat), y(m0)),
                    Offset(x(s.endBeat), y(m1)),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Butt,
                )
            }
        }
    }
    // Names under the time line, in order.
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        r.syllables.forEachIndexed { i, s ->
            val note = s.startMidi?.let { m -> TunerReading.of(m).let { noteName(it.note, accidentals) + it.octave } } ?: "–"
            Text(
                "${s.text} $note",
                style = MaterialTheme.typography.labelLarge,
                color = if (i == r.selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { onSelect(i) }.padding(4.dp),
            )
        }
    }
}
