package com.musicreader.app.ui.tab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.app.ui.common.SheetRowLayout
import com.musicreader.app.ui.common.StaffLine
import com.musicreader.app.ui.common.sectionDisplayName
import com.musicreader.app.ui.song.SongViewModel
import com.musicreader.app.ui.song.TabEditorUiState
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.SheetRow
import com.musicreader.core.model.TabEvent
import com.musicreader.core.model.TabOps
import com.musicreader.core.model.Technique
import com.musicreader.core.model.chordColor

/** The note being edited: an existing one, or a new one at a beat and string. */
private data class EditTarget(val note: TabEvent, val isNew: Boolean)

/**
 * Typing tab for one section (D-027). Each row shows its chords, a tab staff on the same beat grid
 * and the lyrics. Tap the staff to add a note (string from where you tap, beat from the
 * quarter-beat grid); tap a note to edit it, drag it to move it, long-press it to delete it.
 * Undo and redo are in the top bar; Export shows the tab as alphaTex and ASCII.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabEditorScreen(viewModel: SongViewModel, sectionId: String, onBack: () -> Unit) {
    val flow = remember(viewModel, sectionId) { viewModel.tabEditorState(sectionId) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<EditTarget?>(null) }
    var showExport by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(viewModel) { onDispose { viewModel.clearTabHistory() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val s = state
                    Text(
                        if (s == null) stringResource(R.string.tab_title) else stringResource(R.string.tab_of, sectionDisplayName(s.type, s.label, s.number)),
                        maxLines = 1,
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(AppIcons.Back, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = viewModel::undoTab, enabled = state?.canUndo == true) {
                        AppIcon(AppIcons.Undo, stringResource(R.string.undo))
                    }
                    IconButton(onClick = viewModel::redoTab, enabled = state?.canRedo == true) {
                        AppIcon(AppIcons.Redo, stringResource(R.string.redo))
                    }
                    TextButton(onClick = { showExport = true }) { Text(stringResource(R.string.export)) }
                },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Text(
                    stringResource(R.string.tab_editor_hint),
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
            itemsIndexed(s.rows) { _, row ->
                TabRow(
                    row = row,
                    state = s,
                    settings = settings,
                    selectedNoteId = (target?.takeIf { !it.isNew })?.note?.id,
                    onNew = { beat, string ->
                        target = EditTarget(
                            TabEvent(id = "new", beat = beat, durationBeats = 0.5, string = string, fret = 0, finger = 1),
                            isNew = true,
                        )
                    },
                    onEdit = { note -> target = EditTarget(note, isNew = false) },
                    onDelete = { note -> viewModel.deleteTabNote(s.sectionId, note.id) },
                    onMove = { note, beat, string -> viewModel.moveTabNote(s.sectionId, note.id, beat, string) },
                )
            }
        }
    }

    val current = state
    val editing = target
    if (current != null && editing != null) {
        val (bar, beatText) = beatPosition(editing.note.beat, current.beatsPerBar)
        NoteEditorSheet(
            initial = editing.note,
            isNew = editing.isNew,
            position = stringResource(R.string.note_position, editing.note.string, bar, beatText),
            fingerColors = settings.fingerColors,
            onSave = { note ->
                target = null
                if (editing.isNew) viewModel.addTabNote(current.sectionId, note) else viewModel.updateTabNote(current.sectionId, note)
            },
            onSaveAndNext = { note ->
                if (editing.isNew) viewModel.addTabNote(current.sectionId, note) else viewModel.updateTabNote(current.sectionId, note)
                val nextBeat = TabOps.snap(note.beat + note.durationBeats, current.lengthBeats)
                target = if (nextBeat > note.beat) {
                    EditTarget(note.copy(id = "new", beat = nextBeat, technique = Technique.NONE, bendQuarterTones = null), isNew = true)
                } else {
                    null
                }
            },
            onDelete = {
                target = null
                viewModel.deleteTabNote(current.sectionId, editing.note.id)
            },
            onDismiss = { target = null },
        )
    }

    if (showExport && current != null) {
        val texts = remember(current) { viewModel.tabExport(current.sectionId) }
        if (texts != null) ExportDialog(alphaTex = texts.first, ascii = texts.second, onDismiss = { showExport = false })
    }
}

/** One row: chords, an editable staff per visual line, lyrics. */
@Composable
private fun TabRow(
    row: SheetRow,
    state: TabEditorUiState,
    settings: AppSettings,
    selectedNoteId: String?,
    onNew: (beat: Double, string: Int) -> Unit,
    onEdit: (TabEvent) -> Unit,
    onDelete: (TabEvent) -> Unit,
    onMove: (TabEvent, beat: Double, string: Int) -> Unit,
) {
    val (firstBar, _) = beatPosition(row.startBeat, state.beatsPerBar)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.bar_number, firstBar), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SheetRowLayout(
            row = row,
            showChords = true,
            showTab = true,
            showLyrics = true,
            alwaysShowStaff = true,
            syllableContent = { Text(it.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)) },
            chordContent = { chord ->
                Text(
                    chord.symbol.toString(),
                    color = Color(settings.chordColor(chord.symbol).toInt()),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            staff = { line ->
                EditableStaff(line, state, settings, selectedNoteId, onNew, onEdit, onDelete, onMove)
            },
        )
    }
}

/** A staff that turns taps and drags into note edits. */
@Composable
private fun EditableStaff(
    line: StaffLine,
    state: TabEditorUiState,
    settings: AppSettings,
    selectedNoteId: String?,
    onNew: (beat: Double, string: Int) -> Unit,
    onEdit: (TabEvent) -> Unit,
    onDelete: (TabEvent) -> Unit,
    onMove: (TabEvent, beat: Double, string: Int) -> Unit,
) {
    val metrics = StaffMetrics.of(LocalDensity.current, 1f)
    val notesById = state.notes.associateBy { it.id }
    val currentLine by rememberUpdatedState(line)
    val currentNotes by rememberUpdatedState(notesById)
    val lengthBeats by rememberUpdatedState(state.lengthBeats)
    val newNote by rememberUpdatedState(onNew)
    val edit by rememberUpdatedState(onEdit)
    val delete by rememberUpdatedState(onDelete)
    val move by rememberUpdatedState(onMove)
    val label = stringResource(R.string.tab_staff_description, line.index + 1)
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    TabStaff(
        line = line,
        fingerColors = settings.fingerColors,
        scale = 1f,
        selectedNoteId = dragging ?: selectedNoteId,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos ->
                        val hit = currentLine.noteAt(pos.x, pos.y, metrics)?.let { currentNotes[it.note.id] }
                        if (hit != null) {
                            edit(hit)
                        } else {
                            val beat = TabOps.snap(currentLine.axis.axis.beat(pos.x), lengthBeats)
                            newNote(beat, metrics.stringAt(pos.y))
                        }
                    },
                    onLongPress = { pos ->
                        currentLine.noteAt(pos.x, pos.y, metrics)?.let { currentNotes[it.note.id] }?.let(delete)
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        dragging = currentLine.noteAt(pos.x, pos.y, metrics)?.note?.id
                        dragStart = pos
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, amount ->
                        if (dragging != null) {
                            change.consume()
                            dragOffset += amount
                        }
                    },
                    onDragEnd = {
                        val note = dragging?.let { currentNotes[it] }
                        if (note != null) {
                            val end = dragStart + dragOffset
                            val beat = currentLine.axis.axis.beat(end.x)
                            move(note, beat, metrics.stringAt(end.y))
                        }
                        dragging = null
                    },
                    onDragCancel = { dragging = null },
                )
            },
    )
}

/** The section's tab as alphaTex or ASCII, with Copy and Share. */
@Composable
private fun ExportDialog(alphaTex: String, ascii: String, onDismiss: () -> Unit) {
    var showAscii by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val text = if (showAscii) ascii else alphaTex
    val copiedLabel = stringResource(R.string.export)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_tab)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showAscii, onClick = { showAscii = false }, label = { Text("alphaTex") })
                    FilterChip(selected = showAscii, onClick = { showAscii = true }, label = { Text("ASCII") })
                }
                Text(
                    text,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = false,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, null))
            }) { Text(stringResource(R.string.share)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(copiedLabel, text))
                }) { Text(stringResource(R.string.copy)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
    )
}
