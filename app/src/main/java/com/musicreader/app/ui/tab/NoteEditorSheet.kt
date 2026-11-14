package com.musicreader.app.ui.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.TabEvent
import com.musicreader.core.model.TabExport
import com.musicreader.core.model.TabOps
import com.musicreader.core.model.Technique

/** A technique button: the technique and, for bends, the amount in quarter tones. */
private data class TechniqueChoice(val technique: Technique, val bend: Int? = null)

private val TECHNIQUES: List<TechniqueChoice> = listOf(
    TechniqueChoice(Technique.NONE),
    TechniqueChoice(Technique.HAMMER_ON),
    TechniqueChoice(Technique.PULL_OFF),
    TechniqueChoice(Technique.SLIDE_UP),
    TechniqueChoice(Technique.SLIDE_DOWN),
) + TabOps.BEND_AMOUNTS.map { TechniqueChoice(Technique.BEND, it) } +
    TabOps.BEND_AMOUNTS.map { TechniqueChoice(Technique.BEND_RELEASE, it) } +
    listOf(
        TechniqueChoice(Technique.VIBRATO),
        TechniqueChoice(Technique.NATURAL_HARMONIC),
        TechniqueChoice(Technique.ARTIFICIAL_HARMONIC),
        TechniqueChoice(Technique.MUTE),
    )

@Composable
private fun techniqueLabel(choice: TechniqueChoice): String = when (choice.technique) {
    Technique.NONE -> stringResource(R.string.technique_none)
    Technique.HAMMER_ON -> stringResource(R.string.technique_hammer_on)
    Technique.PULL_OFF -> stringResource(R.string.technique_pull_off)
    Technique.SLIDE_UP -> stringResource(R.string.technique_slide_up)
    Technique.SLIDE_DOWN -> stringResource(R.string.technique_slide_down)
    Technique.BEND -> stringResource(R.string.technique_bend, TabExport.bendLabel(choice.bend ?: 4))
    Technique.BEND_RELEASE -> stringResource(R.string.technique_bend_release, TabExport.bendLabel(choice.bend ?: 4))
    Technique.VIBRATO -> stringResource(R.string.technique_vibrato)
    Technique.NATURAL_HARMONIC -> stringResource(R.string.technique_natural_harmonic)
    Technique.ARTIFICIAL_HARMONIC -> stringResource(R.string.technique_artificial_harmonic)
    Technique.MUTE -> stringResource(R.string.technique_mute)
}

/**
 * Edits one tab note: fret on a number pad, finger (0 open … 4 pinky; no thumb, D-012),
 * length and technique. "Save + next" saves and opens a new note right after this one on the
 * same string, for typing a riff quickly.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorSheet(
    initial: TabEvent,
    isNew: Boolean,
    position: String,
    fingerColors: Map<Int, Long>,
    onSave: (TabEvent) -> Unit,
    onSaveAndNext: (TabEvent) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var fret by rememberSaveable(initial.id, initial.beat, initial.string) { mutableIntStateOf(initial.fret) }
    var fresh by rememberSaveable(initial.id, initial.beat, initial.string) { mutableStateOf(true) }
    var finger by rememberSaveable(initial.id, initial.beat, initial.string) { mutableIntStateOf(initial.finger) }
    var duration by rememberSaveable(initial.id, initial.beat, initial.string) { mutableStateOf(initial.durationBeats) }
    var technique by rememberSaveable(initial.id, initial.beat, initial.string) { mutableStateOf(initial.technique) }
    var bend by rememberSaveable(initial.id, initial.beat, initial.string) { mutableIntStateOf(initial.bendQuarterTones ?: 4) }

    fun result() = initial.copy(
        fret = fret,
        finger = finger,
        durationBeats = duration,
        technique = technique,
        bendQuarterTones = bend.takeIf { technique == Technique.BEND || technique == Technique.BEND_RELEASE },
        harmonicFret = null,
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(position, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.fret), style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    staffLabel(fret, technique, null),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color((fingerColors[finger] ?: AppSettings.DEFAULT_FINGER_COLORS.getValue(finger)).toInt()),
                )
            }

            // Number pad: 1–9, then delete, 0.
            val keys = (1..9).toList()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
                keys.forEach { d ->
                    FilledTonalButton(onClick = { fret = TabOps.typeFretDigit(fret, d, fresh); fresh = false }, modifier = Modifier.weight(1f)) {
                        Text("$d", style = MaterialTheme.typography.titleLarge)
                    }
                }
                OutlinedButton(onClick = { fret = TabOps.deleteFretDigit(fret); fresh = false }, modifier = Modifier.weight(1f)) {
                    AppIcon(AppIcons.Backspace, stringResource(R.string.delete_digit))
                }
                FilledTonalButton(onClick = { fret = TabOps.typeFretDigit(fret, 0, fresh); fresh = false }, modifier = Modifier.weight(1f)) {
                    Text("0", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.weight(1f))
            }

            Text(stringResource(R.string.finger), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..TabEvent.MAX_FINGER).forEach { f ->
                    FilterChip(
                        selected = finger == f,
                        onClick = { finger = f },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .background(Color((fingerColors[f] ?: AppSettings.DEFAULT_FINGER_COLORS.getValue(f)).toInt()), CircleShape),
                            )
                        },
                        label = { Text(if (f == 0) stringResource(R.string.finger_open) else "$f") },
                    )
                }
            }

            Text(stringResource(R.string.note_length), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TabOps.DURATIONS.forEach { d ->
                    FilterChip(
                        selected = duration == d,
                        onClick = { duration = d },
                        label = { Text(beatsLabel(d)) },
                    )
                }
            }

            Text(stringResource(R.string.technique), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TECHNIQUES.forEach { choice ->
                    val selected = technique == choice.technique && (choice.bend == null || choice.bend == bend)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            technique = choice.technique
                            choice.bend?.let { bend = it }
                        },
                        label = { Text(techniqueLabel(choice)) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { onSaveAndNext(result()) }) { Text(stringResource(R.string.save_and_next)) }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { onSave(result()) }) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

/** A length in beats as shown on the chips: ¼, ½, 1, 2, 4. */
fun beatsLabel(beats: Double): String = when (beats) {
    0.25 -> "¼"
    0.5 -> "½"
    0.75 -> "¾"
    else -> if (beats % 1.0 == 0.0) beats.toInt().toString() else beats.toString()
}

/** "Bar 2, beat 1½" for a beat inside a section. */
fun beatPosition(beat: Double, beatsPerBar: Int): Pair<Int, String> {
    val bar = (beat / beatsPerBar).toInt() + 1
    val inBar = beat - (bar - 1) * beatsPerBar + 1
    val whole = inBar.toInt()
    val fraction = inBar - whole
    val text = whole.toString() + when (fraction) {
        0.25 -> "¼"
        0.5 -> "½"
        0.75 -> "¾"
        else -> ""
    }
    return bar to text
}
