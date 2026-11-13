package com.musicreader.app.ui.song

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.core.model.Language
import com.musicreader.core.model.Song
import com.musicreader.core.model.TimeSignature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongSettingsScreen(viewModel: SongViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.song_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { AppIcon(AppIcons.Back, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                SongUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SongUiState.NotFound -> Text(stringResource(R.string.song_not_found), Modifier.align(Alignment.Center))
                is SongUiState.Ready -> SettingsForm(s.editor, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsForm(editor: SongEditorState, viewModel: SongViewModel) {
    val song = editor.song
    var customDialog by rememberSaveable { mutableStateOf(false) }
    // Text fields keep their own text so typing never waits for the state round trip.
    var title by rememberSaveable(song.id) { mutableStateOf(song.title) }
    var artist by rememberSaveable(song.id) { mutableStateOf(song.artist.orEmpty()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; viewModel.setTitle(it) },
            label = { Text(stringResource(R.string.song_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = artist,
            onValueChange = { artist = it; viewModel.setArtist(it) },
            label = { Text(stringResource(R.string.artist)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle(stringResource(R.string.language))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = song.language == Language.ES,
                onClick = { viewModel.setLanguage(Language.ES) },
                label = { Text(stringResource(R.string.language_es)) },
            )
            FilterChip(
                selected = song.language == Language.EN,
                onClick = { viewModel.setLanguage(Language.EN) },
                label = { Text(stringResource(R.string.language_en)) },
            )
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.tempo))
        Text(
            stringResource(R.string.tempo_value, editor.tempoText),
            style = MaterialTheme.typography.headlineMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { viewModel.nudgeTempo(-1) },
                enabled = song.tempoBpm > Song.MIN_TEMPO_BPM,
            ) { AppIcon(AppIcons.Remove, stringResource(R.string.tempo_decrease)) }
            Slider(
                value = song.tempoBpm.toFloat(),
                onValueChange = viewModel::setTempoFromSlider,
                valueRange = Song.MIN_TEMPO_BPM.toFloat()..Song.MAX_TEMPO_BPM.toFloat(),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            FilledTonalIconButton(
                onClick = { viewModel.nudgeTempo(+1) },
                enabled = song.tempoBpm < Song.MAX_TEMPO_BPM,
            ) { AppIcon(AppIcons.Add, stringResource(R.string.tempo_increase)) }
        }
        FilledTonalButton(
            onClick = { viewModel.tap(SystemClock.uptimeMillis()) },
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) {
            Text(stringResource(R.string.tap_tempo), style = MaterialTheme.typography.titleLarge)
        }
        Text(
            if (editor.tapCount > 0) stringResource(R.string.tap_count, editor.tapCount)
            else stringResource(R.string.tap_tempo_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()
        SectionTitle(stringResource(R.string.time_signature))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeSignature.PRESETS.forEach { ts ->
                FilterChip(
                    selected = song.timeSignature == ts,
                    onClick = { viewModel.setTimeSignature(ts.num, ts.den) },
                    label = { Text(ts.toString()) },
                )
            }
            FilterChip(
                selected = !editor.isPresetTimeSignature,
                onClick = { customDialog = true },
                label = {
                    Text(
                        if (editor.isPresetTimeSignature) stringResource(R.string.time_signature_custom)
                        else stringResource(R.string.time_signature_custom_value, song.timeSignature.toString()),
                    )
                },
            )
        }
        Text(
            stringResource(
                R.string.song_length,
                pluralStringResource(R.plurals.bars, editor.totalBars, editor.totalBars),
                editor.duration,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (customDialog) {
        CustomTimeSignatureDialog(
            initial = song.timeSignature,
            onConfirm = { num, den ->
                customDialog = false
                viewModel.setTimeSignature(num, den)
            },
            onDismiss = { customDialog = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomTimeSignatureDialog(
    initial: TimeSignature,
    onConfirm: (num: Int, den: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var num by rememberSaveable { mutableIntStateOf(initial.num) }
    var den by rememberSaveable { mutableIntStateOf(initial.den) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_time_signature)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.beats_per_bar), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { num -= 1 }, enabled = num > TimeSignature.MIN_NUM) {
                        AppIcon(AppIcons.Remove, null)
                    }
                    Text(
                        num.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 56.dp),
                    )
                    FilledTonalIconButton(onClick = { num += 1 }, enabled = num < TimeSignature.MAX_NUM) {
                        AppIcon(AppIcons.Add, null)
                    }
                }
                Text(stringResource(R.string.beat_unit), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CUSTOM_DENOMINATORS.forEach { d ->
                        FilterChip(selected = den == d, onClick = { den = d }, label = { Text(d.toString()) })
                    }
                }
                Text("$num/$den", style = MaterialTheme.typography.headlineMedium)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(num, den) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val CUSTOM_DENOMINATORS = listOf(2, 4, 8, 16)
