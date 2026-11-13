package com.musicreader.app.ui.song

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.app.ui.common.descriptionRes
import com.musicreader.app.ui.common.nameRes
import com.musicreader.core.model.SectionType
import com.musicreader.core.model.SongOps

/** The values being edited in the section sheet before Save. */
data class SectionDraft(val type: SectionType, val bars: Int, val label: String) {
    fun withBars(newBars: Int): SectionDraft =
        copy(bars = newBars.coerceIn(SongOps.MIN_SECTION_BARS, SongOps.MAX_SECTION_BARS))

    companion object {
        fun new(): SectionDraft = SectionDraft(SectionType.VERSE, SongOps.DEFAULT_SECTION_BARS, "")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SectionEditorSheet(
    title: String,
    initial: SectionDraft,
    linkedCount: Int,
    onSave: (SectionDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(initial.type) }
    var bars by rememberSaveable { mutableIntStateOf(initial.bars) }
    var label by rememberSaveable { mutableStateOf(initial.label) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.section_type), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionType.entries.forEach { t ->
                    FilterChip(
                        selected = t == type,
                        onClick = { type = t },
                        label = { Text(stringResource(t.nameRes())) },
                    )
                }
            }
            Text(
                stringResource(type.descriptionRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(stringResource(R.string.length_bars), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { bars = SectionDraft(type, bars, label).withBars(bars - 1).bars },
                    enabled = bars > SongOps.MIN_SECTION_BARS,
                ) { AppIcon(AppIcons.Remove, stringResource(R.string.fewer_bars)) }
                Text(
                    bars.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 64.dp),
                )
                FilledTonalIconButton(
                    onClick = { bars = SectionDraft(type, bars, label).withBars(bars + 1).bars },
                    enabled = bars < SongOps.MAX_SECTION_BARS,
                ) { AppIcon(AppIcons.Add, stringResource(R.string.more_bars)) }
            }
            if (linkedCount > 1) {
                Text(
                    pluralStringResource(R.plurals.linked_hint, linkedCount, linkedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.section_label)) },
                placeholder = { Text(stringResource(R.string.section_label_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(SectionDraft(type, bars, label)) }) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
