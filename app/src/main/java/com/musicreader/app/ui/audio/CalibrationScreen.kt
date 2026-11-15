package com.musicreader.app.ui.audio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.audio.CalibrationMode
import com.musicreader.core.model.AudioRoute
import com.musicreader.core.model.LatencyProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(onBack: () -> Unit, viewModel: CalibrationViewModel = viewModel(factory = CalibrationViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The microphone is asked for only here, when a measurement starts.
    var pendingMode by remember { mutableStateOf<CalibrationMode?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val mode = pendingMode
        pendingMode = null
        if (granted && mode != null) viewModel.start(mode) else viewModel.permissionDenied()
    }
    fun begin(mode: CalibrationMode) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.start(mode)
        } else {
            pendingMode = mode
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calibration_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.cancel(); onBack() }) { AppIcon(AppIcons.Back, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val step = state.step) {
                CalibrationStep.PermissionDenied -> PermissionDenied(
                    onTryAgain = { viewModel.back() },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
                is CalibrationStep.Running -> Running(step, onCancel = viewModel::cancel)
                else -> {
                    RouteCard(state)
                    when (step) {
                        is CalibrationStep.Done -> Result(step, state.route, onSave = viewModel::save, onAgain = viewModel::back)
                        is CalibrationStep.NotHeard -> Problem(
                            stringResource(if (step.mode == CalibrationMode.AUTOMATIC) R.string.calibration_not_heard else R.string.calibration_no_taps),
                            onAgain = viewModel::back,
                        )
                        is CalibrationStep.Failed -> Problem(
                            when (step.reason) {
                                FailReason.NO_INPUT -> stringResource(R.string.calibration_no_input)
                                FailReason.GAP -> stringResource(R.string.calibration_gap)
                                FailReason.START -> stringResource(R.string.audio_start_failed, step.detail)
                            },
                            onAgain = viewModel::back,
                        )
                        CalibrationStep.Interrupted -> Problem(stringResource(R.string.calibration_interrupted), onAgain = viewModel::back)
                        else -> Choices(onStart = ::begin)
                    }
                    HorizontalDivider()
                    SavedProfiles(state.profiles, onClear = viewModel::clear)
                }
            }
        }
    }
}

@Composable
private fun RouteCard(state: CalibrationUiState) {
    Text(stringResource(R.string.current_route, routeName(state.route)), style = MaterialTheme.typography.titleMedium)
    val p = state.currentProfile
    Text(
        if (p == null) stringResource(R.string.calibration_none_saved) else savedText(p),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.bluetooth) BluetoothWarning()
}

@Composable
fun BluetoothWarning() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(
            stringResource(R.string.bluetooth_warning),
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun savedText(p: LatencyProfile): String =
    stringResource(R.string.calibration_saved_value, formatMs(p.roundTripMs), methodName(p.method), percent(p.confidence), formatDate(p.measuredAt))

@Composable
private fun Choices(onStart: (CalibrationMode) -> Unit) {
    Text(stringResource(R.string.calibration_automatic), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.calibration_automatic_help), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = { onStart(CalibrationMode.AUTOMATIC) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.calibration_start_automatic))
    }
    Text(stringResource(R.string.calibration_manual), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.calibration_manual_help), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { onStart(CalibrationMode.MANUAL) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.calibration_start_manual))
    }
}

@Composable
private fun Running(step: CalibrationStep.Running, onCancel: () -> Unit) {
    val manual = step.mode == CalibrationMode.MANUAL
    Text(
        stringResource(if (manual) R.string.calibration_manual_running else R.string.calibration_automatic_running),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        if (manual) {
            if (step.played == 0) stringResource(R.string.calibration_get_ready) else stringResource(R.string.calibration_click_of, step.played, step.total)
        } else {
            stringResource(R.string.calibration_chirp_of, step.played, step.total)
        },
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    LinearProgressIndicator(progress = { step.played.toFloat() / step.total }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.calibration_listening), style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
}

@Composable
private fun Result(step: CalibrationStep.Done, route: AudioRoute, onSave: () -> Unit, onAgain: () -> Unit) {
    val m = step.measurement
    Text(stringResource(R.string.calibration_result, formatMs(m.latencyMs ?: 0.0)), style = MaterialTheme.typography.headlineMedium)
    Text(
        stringResource(R.string.calibration_confidence, percent(m.confidence)) + " · " +
            pluralStringResource(R.plurals.calibration_found, m.expected, m.found, m.expected) + " · " +
            stringResource(R.string.calibration_spread, formatMs(m.spreadMs)),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (step.lowConfidence) {
        Text(stringResource(R.string.calibration_low_confidence), color = MaterialTheme.colorScheme.error)
    }
    if (step.route != route) {
        Text(stringResource(R.string.calibration_route_changed, routeName(step.route)), color = MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSave, enabled = !step.saved) {
            Text(
                if (step.saved) stringResource(R.string.calibration_saved_for, routeName(step.route))
                else stringResource(R.string.calibration_save_for, routeName(step.route)),
            )
        }
        OutlinedButton(onClick = onAgain) { Text(stringResource(R.string.calibration_again)) }
    }
}

@Composable
private fun Problem(text: String, onAgain: () -> Unit) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
    OutlinedButton(onClick = onAgain) { Text(stringResource(R.string.calibration_again)) }
}

@Composable
private fun SavedProfiles(profiles: Map<AudioRoute, LatencyProfile>, onClear: (AudioRoute) -> Unit) {
    Text(stringResource(R.string.calibration_saved_title), style = MaterialTheme.typography.titleMedium)
    AudioRoute.entries.forEach { route ->
        val p = profiles[route]
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routeName(route), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (p == null) stringResource(R.string.calibration_not_measured) else savedText(p),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (p != null) TextButton(onClick = { onClear(route) }) { Text(stringResource(R.string.calibration_clear)) }
        }
    }
}

@Composable
private fun PermissionDenied(onTryAgain: () -> Unit, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.mic_denied_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.mic_denied_text), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mic_try_again)) }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mic_open_settings)) }
    }
}
