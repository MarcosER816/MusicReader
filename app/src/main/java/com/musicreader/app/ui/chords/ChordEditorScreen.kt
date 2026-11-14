package com.musicreader.app.ui.chords

import androidx.compose.foundation.Canvas
import com.musicreader.app.ui.common.SyllableBox
import com.musicreader.app.ui.common.SheetRowLayout
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.app.ui.common.LyricLane
import com.musicreader.app.ui.common.sectionDisplayName
import com.musicreader.app.ui.song.ChordEditorUiState
import com.musicreader.app.ui.song.SongEvent
import com.musicreader.app.ui.song.SongViewModel
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.ChordEvent
import com.musicreader.core.model.ChordOps
import com.musicreader.core.model.SheetRow
import com.musicreader.core.model.chordColor
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the chord picker is open for. */
private sealed interface PickerTarget {
    data class New(val beat: Double) : PickerTarget
    data class NewOnSyllable(val syllableId: String) : PickerTarget
    data class Existing(val chord: ChordEvent) : PickerTarget
}

/**
 * Chord placement for one section. Each row has a half-beat grid with the chords on it and the
 * row's lyrics below it. Tap the grid to add a chord (it snaps to the nearest half beat), drag a
 * chord to move it, tap it to change it, long-press it to delete it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordEditorScreen(viewModel: SongViewModel, sectionId: String, onBack: () -> Unit) {
    val flow = remember(viewModel, sectionId) { viewModel.chordEditorState(sectionId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dictionary by viewModel.chordDictionary.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            if (event is SongEvent.ChordDeleted) scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = resources.getString(R.string.chord_deleted, event.name),
                    actionLabel = resources.getString(R.string.undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteChord()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val s = state
                    Text(
                        if (s == null) stringResource(R.string.chords)
                        else stringResource(R.string.chords_of, sectionDisplayName(s.type, s.label, s.number)),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { AppIcon(AppIcons.Back, stringResource(R.string.back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Text(
                    stringResource(R.string.chord_editor_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (s.linkedCount > 1) {
                    Text(
                        pluralStringResource(R.plurals.lyrics_linked_hint, s.linkedCount, s.linkedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            itemsIndexed(s.rows) { index, row ->
                ChordRow(
                    row = row,
                    rowIndex = index,
                    rowCount = s.rows.size,
                    state = s,
                    settings = settings,
                    onTapBeat = { beat -> picker = PickerTarget.New(ChordOps.snap(beat, s.lengthBeats)) },
                    onTapSyllable = { syllableId -> picker = PickerTarget.NewOnSyllable(syllableId) },
                    onMoveChordToSyllable = { chord, syllableId -> viewModel.moveChordToSyllable(s.sectionId, chord.id, syllableId) },
                    onTapChord = { picker = PickerTarget.Existing(it) },
                    onDeleteChord = { viewModel.deleteChord(s.sectionId, it.id) },
                    onMoveChord = { chord, beat -> viewModel.moveChord(s.sectionId, chord.id, beat) },
                )
            }
        }
    }

    val dict = dictionary
    val target = picker
    val current = state
    if (target != null && dict != null && current != null) {
        val sectionId2 = current.sectionId
        ChordPickerSheet(
            dictionary = dict,
            settings = settings,
            initial = (target as? PickerTarget.Existing)?.chord?.symbol,
            initialVoicingId = (target as? PickerTarget.Existing)?.chord?.voicingId,
            onPreferenceChange = viewModel::setAccidentalPreference,
            onChoose = { symbol, voicingId ->
                picker = null
                when (target) {
                    is PickerTarget.New -> viewModel.addChord(sectionId2, target.beat, symbol, voicingId)
                    is PickerTarget.NewOnSyllable -> viewModel.addChordOnSyllable(sectionId2, target.syllableId, symbol, voicingId)
                    is PickerTarget.Existing -> viewModel.changeChord(sectionId2, target.chord.id, symbol, voicingId)
                }
            },
            onDismiss = { picker = null },
        )
    }
}

/** One row: bar number, half-beat grid with chords, then the lyrics under it. */
@Composable
private fun ChordRow(
    row: SheetRow,
    rowIndex: Int,
    rowCount: Int,
    state: ChordEditorUiState,
    settings: AppSettings,
    onTapBeat: (Double) -> Unit,
    onTapSyllable: (String) -> Unit,
    onTapChord: (ChordEvent) -> Unit,
    onDeleteChord: (ChordEvent) -> Unit,
    onMoveChord: (ChordEvent, Double) -> Unit,
    onMoveChordToSyllable: (ChordEvent, String) -> Unit,
) {
    if (row.syllables.isNotEmpty()) {
        SyllableChordRow(row, state, settings, onTapSyllable, onTapChord, onDeleteChord, onMoveChordToSyllable)
        return
    }
    val end = row.startBeat + row.lengthBeats
    val last = rowIndex == rowCount - 1
    val chords = state.chords.filter { it.beat >= row.startBeat && (it.beat < end || (last && it.beat <= end)) }
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant
    val barColor = MaterialTheme.colorScheme.outline
    val currentTap by rememberUpdatedState(onTapBeat)
    val firstBar = (row.startBeat / state.beatsPerBar).toInt() + 1
    val gridLabel = stringResource(R.string.chord_grid_description, firstBar)

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            stringResource(R.string.bar_number, firstBar),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { contentDescription = gridLabel }
                .pointerInput(row.startBeat, row.lengthBeats) {
                    detectTapGestures { pos -> currentTap(row.startBeat + pos.x / size.width * row.lengthBeats) }
                },
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            // Grid: a short tick every half beat, a longer one every beat, a full line at each bar.
            Canvas(Modifier.fillMaxSize()) {
                val steps = (row.lengthBeats / ChordOps.GRID).roundToInt()
                for (i in 0..steps) {
                    val beat = row.startBeat + i * ChordOps.GRID
                    val x = (i * ChordOps.GRID / row.lengthBeats * size.width).toFloat().coerceAtMost(size.width - 1f)
                    val isBar = Math.floorMod(beat.roundToInt(), state.beatsPerBar) == 0 && beat % 1.0 == 0.0
                    val isBeat = beat % 1.0 == 0.0
                    val h = when {
                        isBar -> size.height
                        isBeat -> size.height * 0.35f
                        else -> size.height * 0.18f
                    }
                    drawLine(
                        if (isBar) barColor else gridColor.copy(alpha = 0.6f),
                        Offset(x, size.height - h),
                        Offset(x, size.height),
                        strokeWidth = if (isBar) 2.dp.toPx() else 1.dp.toPx(),
                    )
                }
            }
            chords.forEach { chord ->
                DraggableChord(
                    chord = chord,
                    color = Color(settings.chordColor(chord.symbol).toInt()),
                    x = ((chord.beat - row.startBeat) / row.lengthBeats * widthPx).toFloat(),
                    widthPx = widthPx,
                    onTap = { onTapChord(chord) },
                    onLongPress = { onDeleteChord(chord) },
                    onDrop = { dxPx -> onMoveChord(chord, chord.beat + dxPx * row.lengthBeats / widthPx) },
                )
            }
        }
        LyricLane(
            row.lyrics,
            MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
            MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A chord on the grid: tap to change, long-press to delete, drag sideways to move (snaps on release). */
@Composable
private fun DraggableChord(
    chord: ChordEvent,
    color: Color,
    x: Float,
    widthPx: Float,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrop: (dxPx: Float) -> Unit,
) {
    var drag by remember(chord.id, chord.beat) { mutableFloatStateOf(0f) }
    var labelWidth by remember { mutableFloatStateOf(0f) }
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val drop by rememberUpdatedState(onDrop)
    val name = chord.symbol.toString()
    val description = stringResource(R.string.chord_label_description, name)
    // Keep the label inside the row; its left edge marks the beat.
    val left = (x + drag).coerceIn(0f, maxOf(0f, widthPx - labelWidth))
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), 0) }
            .onSizeChanged { labelWidth = it.width.toFloat() }
            .semantics { contentDescription = description }
            .pointerInput(chord.id) { detectTapGestures(onTap = { tap() }, onLongPress = { longPress() }) }
            .pointerInput(chord.id, chord.beat) {
                detectHorizontalDragGestures(
                    onDragEnd = { drop(drag) },
                    onDragCancel = { drag = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        drag += amount
                    },
                )
            },
    ) {
        Text(
            name,
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/** Where the syllables of a row were laid out; read when a dragged chord is dropped. */
private class BoxHolder {
    var boxes: List<SyllableBox> = emptyList()
}

/**
 * A row with lyrics (D-025): the line as text with each chord right above its syllable.
 * Tap a syllable to put a chord on it (or change the chord above it), drag a chord onto another
 * syllable to move it, tap it to change it, long-press it to delete it.
 */
@Composable
private fun SyllableChordRow(
    row: SheetRow,
    state: ChordEditorUiState,
    settings: AppSettings,
    onTapSyllable: (String) -> Unit,
    onTapChord: (ChordEvent) -> Unit,
    onDeleteChord: (ChordEvent) -> Unit,
    onMoveChordToSyllable: (ChordEvent, String) -> Unit,
) {
    val holder = remember { BoxHolder() }
    val chordsById = state.chords.associateBy { it.id }
    val chordOnSyllable = row.chords.associateBy { it.syllableId }
    val firstBar = (row.startBeat / state.beatsPerBar).toInt() + 1
    val underline = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.bar_number, firstBar),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SheetRowLayout(
            row = row,
            showChords = true,
            showTab = false,
            showLyrics = true,
            staff = {},
            wordGap = 10.dp,
            lineGap = 10.dp,
            onSyllableBoxes = { holder.boxes = it },
            syllableContent = { syllable ->
                val existing = chordOnSyllable[syllable.id]?.let { chordsById[it.id] }
                val description = stringResource(R.string.syllable_description, syllable.text)
                Text(
                    syllable.text,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .semantics { contentDescription = description }
                        .pointerInput(syllable.id, existing) {
                            detectTapGestures {
                                if (existing != null) onTapChord(existing) else onTapSyllable(syllable.id)
                            }
                        }
                        .drawBehind {
                            // A dotted underline marks every syllable as a place for a chord.
                            val y = size.height - 1.dp.toPx()
                            drawLine(
                                underline,
                                Offset(0f, y),
                                Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                            )
                        }
                        .padding(bottom = 3.dp),
                )
            },
            chordContent = { placed ->
                val chord = chordsById[placed.id] ?: return@SheetRowLayout
                SyllableChordLabel(
                    chord = chord,
                    color = Color(settings.chordColor(chord.symbol).toInt()),
                    onTap = { onTapChord(chord) },
                    onLongPress = { onDeleteChord(chord) },
                    onDrop = { x, y ->
                        val target = holder.boxes.minByOrNull { b ->
                            val cx = (b.left + b.right) / 2f
                            val cy = (b.top + b.bottom) / 2f
                            kotlin.math.abs(cx - x) + 2 * kotlin.math.abs(cy - y)
                        }
                        if (target != null) onMoveChordToSyllable(chord, target.syllableId)
                    },
                )
            },
        )
    }
}

/** A chord over a syllable: tap to change, long-press to delete, drag to another syllable. */
@Composable
private fun SyllableChordLabel(
    chord: ChordEvent,
    color: Color,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrop: (x: Float, y: Float) -> Unit,
) {
    var offset by remember(chord.id, chord.syllableId) { mutableStateOf(Offset.Zero) }
    var placed by remember { mutableStateOf(Offset.Zero) }
    var labelSize by remember { mutableStateOf(IntSize.Zero) }
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val drop by rememberUpdatedState(onDrop)
    val description = stringResource(R.string.chord_label_description, chord.symbol.toString())
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .onPlaced { placed = it.positionInParent() }
            .onSizeChanged { labelSize = it }
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .zIndex(if (offset != Offset.Zero) 1f else 0f)
            .semantics { contentDescription = description }
            .pointerInput(chord.id) { detectTapGestures(onTap = { tap() }, onLongPress = { longPress() }) }
            .pointerInput(chord.id, chord.syllableId) {
                detectDragGestures(
                    onDragEnd = {
                        // Drop point: the label's left edge, just below it (where its syllable is).
                        drop(placed.x + offset.x + 4f, placed.y + offset.y + labelSize.height * 1.5f)
                        offset = Offset.Zero
                    },
                    onDragCancel = { offset = Offset.Zero },
                    onDrag = { change, amount ->
                        change.consume()
                        offset += amount
                    },
                )
            },
    ) {
        Text(
            chord.symbol.toString(),
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
