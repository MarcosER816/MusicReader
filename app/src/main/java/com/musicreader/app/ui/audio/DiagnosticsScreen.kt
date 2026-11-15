package com.musicreader.app.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.audio.AudioApiName
import com.musicreader.audio.PerformanceMode
import com.musicreader.audio.SharingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, viewModel: DiagnosticsViewModel = viewModel(factory = DiagnosticsViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(AppIcons.Back, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val d = state.engine
            val notAvailable = stringResource(R.string.not_available)
            Item(stringResource(R.string.diag_status), stringResource(if (state.running) R.string.diag_running else R.string.diag_stopped))
            state.error?.let { Text(stringResource(R.string.audio_start_failed, it), color = MaterialTheme.colorScheme.error) }
            if (!state.running) Button(onClick = viewModel::startAgain) { Text(stringResource(R.string.diag_start)) }
            Item(stringResource(R.string.diag_route), routeName(state.route))
            Item(
                stringResource(R.string.diag_saved_latency),
                state.savedLatency?.let { stringResource(R.string.ms_value, formatMs(it.roundTripMs)) + " (" + methodName(it.method) + ")" }
                    ?: stringResource(R.string.calibration_not_measured),
            )
            if (state.bluetooth) BluetoothWarning()
            HorizontalDivider()
            Item(stringResource(R.string.diag_sample_rate), stringResource(R.string.hz_value, d.sampleRate))
            Item(stringResource(R.string.diag_burst), stringResource(R.string.frames_ms, d.framesPerBurst, formatMs(d.framesToMs(d.framesPerBurst))))
            Item(stringResource(R.string.diag_buffer), stringResource(R.string.frames_ms, d.bufferSize, formatMs(d.framesToMs(d.bufferSize))))
            Item(stringResource(R.string.diag_capacity), stringResource(R.string.frames_ms, d.bufferCapacity, formatMs(d.framesToMs(d.bufferCapacity))))
            Item(stringResource(R.string.diag_sharing), sharingName(d.sharingMode))
            Item(stringResource(R.string.diag_performance), performanceName(d.performanceMode))
            Item(stringResource(R.string.diag_api), apiName(d.audioApi))
            Item(stringResource(R.string.diag_underruns), d.xRunCount?.toString() ?: notAvailable)
            Item(
                stringResource(R.string.diag_output_latency),
                d.outputLatencyMs?.let { stringResource(R.string.ms_value, formatMs(it)) } ?: notAvailable,
            )
            Item(stringResource(R.string.diag_channels), d.channelCount.toString())
            Item(stringResource(R.string.diag_device_id), d.deviceId.toString())
            Item(stringResource(R.string.diag_clock), stringResource(R.string.seconds_value, formatSeconds(state.clockSeconds)))
            Item(stringResource(R.string.diag_callbacks), d.callbacks.toString())
            Item(stringResource(R.string.diag_restarts), d.restarts.toString())
            HorizontalDivider()
            Item(stringResource(R.string.diag_input), stringResource(if (d.inputOpen) R.string.diag_input_open else R.string.diag_input_closed))
            if (d.inputOpen) {
                Item(stringResource(R.string.diag_sharing), sharingName(d.inputSharingMode))
                Item(stringResource(R.string.diag_burst), d.inputFramesPerBurst.toString())
                Item(stringResource(R.string.diag_input_short), d.inputShortReads.toString())
                Item(stringResource(R.string.diag_input_overflows), d.inputOverflows.toString())
            }
        }
    }
}

@Composable
private fun Item(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun sharingName(mode: SharingMode) = stringResource(
    when (mode) {
        SharingMode.EXCLUSIVE -> R.string.sharing_exclusive
        SharingMode.SHARED -> R.string.sharing_shared
        SharingMode.UNKNOWN -> R.string.not_available
    },
)

@Composable
private fun performanceName(mode: PerformanceMode) = stringResource(
    when (mode) {
        PerformanceMode.LOW_LATENCY -> R.string.performance_low_latency
        PerformanceMode.POWER_SAVING -> R.string.performance_power_saving
        PerformanceMode.NONE -> R.string.performance_none
        PerformanceMode.UNKNOWN -> R.string.not_available
    },
)

@Composable
private fun apiName(api: AudioApiName) = when (api) {
    AudioApiName.AAUDIO -> "AAudio"
    AudioApiName.OPENSL_ES -> "OpenSL ES"
    AudioApiName.UNSPECIFIED -> stringResource(R.string.not_available)
}
