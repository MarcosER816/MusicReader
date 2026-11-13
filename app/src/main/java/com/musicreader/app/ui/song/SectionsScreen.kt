package com.musicreader.app.ui.song

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.app.ui.common.nameRes
import com.musicreader.app.ui.common.sectionDisplayName
import kotlinx.coroutines.launch

/** What the section editor sheet is open for. */
private sealed interface SheetTarget {
    data object New : SheetTarget
    data class Existing(val sectionId: String) : SheetTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionsScreen(
    viewModel: SongViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var sheetTarget by remember { mutableStateOf<SheetTarget?>(null) }

    LaunchedEffect(viewModel) {
        if (viewModel.consumeOpenSettingsRequest()) onOpenSettings()
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SongEvent.SectionDeleted -> scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val name = event.label
                        ?: (resources.getString(event.type.nameRes()) + (event.number?.let { " $it" } ?: ""))
                    val result = snackbarHostState.showSnackbar(
                        message = resources.getString(R.string.section_deleted, name),
                        actionLabel = resources.getString(R.string.undo),
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteSection()
                }
            }
        }
    }

    val editor = (state as? SongUiState.Ready)?.editor
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        editor?.song?.title?.ifBlank { null } ?: stringResource(R.string.untitled),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { AppIcon(AppIcons.Back, stringResource(R.string.back)) }
                },
                actions = {
                    if (editor != null) TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            if (editor != null) {
                ExtendedFloatingActionButton(
                    onClick = { sheetTarget = SheetTarget.New },
                    icon = { AppIcon(AppIcons.Add, null) },
                    text = { Text(stringResource(R.string.add_section)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                SongUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SongUiState.NotFound -> Text(
                    stringResource(R.string.song_not_found),
                    Modifier.align(Alignment.Center).padding(24.dp),
                )
                is SongUiState.Ready -> SectionList(
                    editor = s.editor,
                    onEdit = { sheetTarget = SheetTarget.Existing(it) },
                    onMove = viewModel::moveSection,
                    onDuplicate = viewModel::duplicateSection,
                    onUnlink = viewModel::unlinkSection,
                    onDelete = viewModel::deleteSection,
                )
            }
        }
    }

    if (editor != null) {
        when (val target = sheetTarget) {
            null -> Unit
            SheetTarget.New -> SectionEditorSheet(
                title = stringResource(R.string.add_section),
                initial = SectionDraft.new(),
                linkedCount = 1,
                onSave = { draft ->
                    sheetTarget = null
                    viewModel.addSection(draft.type, draft.bars, draft.label)
                },
                onDismiss = { sheetTarget = null },
            )
            is SheetTarget.Existing -> {
                val row = editor.sections.firstOrNull { it.id == target.sectionId }
                if (row != null) {
                    SectionEditorSheet(
                        title = stringResource(R.string.edit_section),
                        initial = SectionDraft(row.type, row.bars, row.label.orEmpty()),
                        linkedCount = row.linkedCount,
                        onSave = { draft ->
                            sheetTarget = null
                            viewModel.updateSection(row.id, draft.type, draft.bars, draft.label)
                        },
                        onDismiss = { sheetTarget = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionList(
    editor: SongEditorState,
    onEdit: (String) -> Unit,
    onMove: (String, String) -> Unit,
    onDuplicate: (String, Boolean) -> Unit,
    onUnlink: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val dragState = rememberDragReorderState(listState, onMove)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = HEADER_KEY) {
            Text(
                stringResource(
                    R.string.song_summary,
                    editor.song.timeSignature.toString(),
                    editor.tempoText,
                    pluralStringResource(R.plurals.bars, editor.totalBars, editor.totalBars),
                    editor.duration,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (editor.sections.isEmpty()) {
            item(key = EMPTY_KEY) { EmptySections() }
        }
        items(editor.sections, key = { it.id }) { row ->
            val isDragging = dragState.draggingKey == row.id
            SectionCard(
                row = row,
                dragState = dragState,
                onEdit = { onEdit(row.id) },
                onDuplicate = { linked -> onDuplicate(row.id, linked) },
                onUnlink = { onUnlink(row.id) },
                onDelete = { onDelete(row.id) },
                modifier = if (isDragging) Modifier.draggedItem(dragState, row.id) else Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun EmptySections() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.sections_empty), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.sections_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionCard(
    row: SectionRow,
    dragState: DragReorderState,
    onEdit: () -> Unit,
    onDuplicate: (linked: Boolean) -> Unit,
    onUnlink: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    ElevatedCard(modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.dragHandle(dragState, row.id).padding(horizontal = 12.dp, vertical = 12.dp)) {
                Icon(
                    painterResource(AppIcons.DragHandle),
                    contentDescription = stringResource(R.string.drag_to_reorder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    sectionDisplayName(row.type, row.label, row.number),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        R.string.section_details,
                        row.startBar,
                        pluralStringResource(R.plurals.bars, row.bars, row.bars),
                        row.duration,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.label != null) {
                    Text(
                        stringResource(row.type.nameRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (row.linkedCount > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(AppIcons.Link),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.linked_count, row.linkedCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { AppIcon(AppIcons.More, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate_linked)) },
                        onClick = { menuOpen = false; onDuplicate(true) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate_independent)) },
                        onClick = { menuOpen = false; onDuplicate(false) },
                    )
                    if (row.linkedCount > 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.unlink)) },
                            onClick = { menuOpen = false; onUnlink() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

private const val HEADER_KEY = 1
private const val EMPTY_KEY = 2
