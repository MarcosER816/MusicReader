package com.musicreader.app.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicreader.app.AppLanguage
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.app.ui.common.numberColorOn
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.ColorMath
import com.musicreader.core.model.Layer
import com.musicreader.core.model.NoteLetter
import com.musicreader.core.model.UiLanguage

/** What the color picker is open for. */
private sealed interface PickerFor {
    data class Chord(val letter: NoteLetter) : PickerFor
    data class Finger(val finger: Int) : PickerFor
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val activity = LocalActivity.current
    var picker by rememberSaveable(stateSaver = PickerSaver) { mutableStateOf<PickerFor?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(AppIcons.Back, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header(stringResource(R.string.chord_colors), onReset = viewModel::resetChordColors)
            NoteLetter.entries.forEach { letter ->
                val color = Color(s.chordColors.getValue(letter).toInt())
                SettingRow(
                    swatch = color,
                    label = letter.name,
                    onClick = { picker = PickerFor.Chord(letter) },
                ) { ChordPreview(letter, color) }
            }

            HorizontalDivider()
            Header(stringResource(R.string.finger_colors), onReset = viewModel::resetFingerColors)
            (0..4).forEach { finger ->
                val color = Color(s.fingerColors.getValue(finger).toInt())
                SettingRow(
                    swatch = color,
                    label = fingerName(finger),
                    onClick = { picker = PickerFor.Finger(finger) },
                ) { FingerDot(finger, color) }
            }
            state.similarFingers.forEach { (a, b) ->
                Text(
                    stringResource(R.string.similar_finger_colors, fingerName(a), fingerName(b)),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            HorizontalDivider()
            Text(stringResource(R.string.viewer_layers_default), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Layer.LYRICS to R.string.layer_lyrics, Layer.CHORDS to R.string.layer_chords, Layer.TAB to R.string.layer_tabs)
                    .forEach { (layer, label) ->
                        val checked = s.viewerLayers.isVisible(layer)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.toggleViewerLayer(layer) },
                                enabled = !(checked && state.visibleLayerCount == 1),
                            )
                            Text(stringResource(label))
                        }
                    }
            }

            HorizontalDivider()
            Text(stringResource(R.string.chord_names), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.accidentals == AccidentalPreference.SHARPS,
                    onClick = { viewModel.setAccidentals(AccidentalPreference.SHARPS) },
                    label = { Text(stringResource(R.string.sharps_example)) },
                )
                FilterChip(
                    selected = s.accidentals == AccidentalPreference.FLATS,
                    onClick = { viewModel.setAccidentals(AccidentalPreference.FLATS) },
                    label = { Text(stringResource(R.string.flats_example)) },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.left_handed), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.left_handed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.leftHanded, onCheckedChange = viewModel::setLeftHanded)
            }

            HorizontalDivider()
            Text(stringResource(R.string.app_language), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    UiLanguage.SYSTEM to stringResource(R.string.language_system),
                    UiLanguage.EN to stringResource(R.string.language_en),
                    UiLanguage.ES to stringResource(R.string.language_es),
                ).forEach { (language, label) ->
                    FilterChip(
                        selected = s.uiLanguage == language,
                        onClick = {
                            if (s.uiLanguage != language) {
                                viewModel.setLanguage(language)
                                activity?.let { AppLanguage.apply(it, language) }
                            }
                        },
                        label = { Text(label) },
                    )
                }
            }
        }
    }

    when (val p = picker) {
        null -> Unit
        is PickerFor.Chord -> ColorPickerDialog(
            title = stringResource(R.string.color_of_chord, p.letter.name),
            initial = s.chordColors.getValue(p.letter),
            default = AppSettings.DEFAULT_CHORD_COLORS.getValue(p.letter),
            preview = { ChordPreview(p.letter, it) },
            onConfirm = { viewModel.setChordColor(p.letter, it); picker = null },
            onDismiss = { picker = null },
        )
        is PickerFor.Finger -> ColorPickerDialog(
            title = stringResource(R.string.color_of_finger, fingerName(p.finger)),
            initial = s.fingerColors.getValue(p.finger),
            default = AppSettings.DEFAULT_FINGER_COLORS.getValue(p.finger),
            preview = { FingerDot(p.finger, it) },
            onConfirm = { viewModel.setFingerColor(p.finger, it); picker = null },
            onDismiss = { picker = null },
        )
    }
}

@Composable
private fun fingerName(finger: Int): String =
    if (finger == 0) stringResource(R.string.finger_open_name) else stringResource(R.string.finger_number, finger)

@Composable
private fun Header(title: String, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onReset) { Text(stringResource(R.string.reset_all)) }
    }
}

@Composable
private fun SettingRow(swatch: Color, label: String, onClick: () -> Unit, preview: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).background(swatch, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        preview()
        Text(ColorMath.toHex((swatch.value shr 32).toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How chords with this root look: the plain chord, a minor seventh and a seventh. */
@Composable
private fun ChordPreview(letter: NoteLetter, color: Color) {
    Text(
        "${letter.name}   ${letter.name}m7   ${letter.name}7",
        color = color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
    )
}

/** A finger dot as in the diagrams; the open string is an outline. */
@Composable
private fun FingerDot(finger: Int, color: Color) {
    val shape = CircleShape
    val modifier = if (finger == 0) Modifier.size(28.dp).border(2.dp, color, shape) else Modifier.size(28.dp).background(color, shape)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (finger > 0) {
            Text(
                "$finger",
                fontWeight = FontWeight.Bold,
                color = numberColorOn(color),
            )
        }
    }
}

/** Keeps the open picker across rotation. */
private val PickerSaver = Saver<PickerFor?, String>(
    save = { p ->
        when (p) {
            null -> ""
            is PickerFor.Chord -> "c:${p.letter.name}"
            is PickerFor.Finger -> "f:${p.finger}"
        }
    },
    restore = { text ->
        when {
            text.startsWith("c:") -> PickerFor.Chord(NoteLetter.valueOf(text.drop(2)))
            text.startsWith("f:") -> PickerFor.Finger(text.drop(2).toInt())
            else -> null
        }
    },
)
