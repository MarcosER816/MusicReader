package com.musicreader.app.ui.audio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.core.dsp.NoiseGate
import com.musicreader.core.dsp.PitchTrace
import com.musicreader.core.dsp.TunerReading
import com.musicreader.core.model.AccidentalPreference
import com.musicreader.core.model.Enharmonics
import com.musicreader.core.model.PitchMethod
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(onBack: () -> Unit, viewModel: TunerViewModel = viewModel(factory = TunerViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.start() else viewModel.permissionDenied()
    }
    fun begin() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.start() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    // The microphone is asked for when the tuner opens; detection restarts on every return.
    LifecycleStartEffect(Unit) {
        begin()
        onStopOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tuner_title)) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.permissionDenied) {
                Text(stringResource(R.string.mic_denied_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.tuner_mic_denied_text), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = ::begin, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mic_try_again)) }
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.mic_open_settings)) }
                return@Column
            }
            NoteDisplay(state)
            CentsNeedle(state.reading)
            Details(state)
            PitchTraceView(viewModel.trace, state.traceVersion, state.accidentals)
            Text(stringResource(R.string.tuner_method), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.settings.method == PitchMethod.YIN,
                    onClick = { viewModel.setMethod(PitchMethod.YIN) },
                    label = { Text(stringResource(R.string.method_yin)) },
                )
                FilterChip(
                    selected = state.settings.method == PitchMethod.MPM,
                    onClick = { viewModel.setMethod(PitchMethod.MPM) },
                    label = { Text(stringResource(R.string.method_mpm)) },
                )
            }
            Text(
                stringResource(
                    if (state.settings.gateMeasuredAt == null) R.string.tuner_gate_default else R.string.tuner_gate_measured,
                    formatMs(state.settings.noiseGateDb),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            val progress = state.noiseProgress
            OutlinedButton(onClick = viewModel::measureNoise, enabled = progress == null && state.started) {
                Text(
                    if (progress == null) stringResource(R.string.tuner_measure_noise)
                    else stringResource(R.string.tuner_measuring_noise, progress * 100 / NoiseGate.FRAMES),
                )
            }
            Text(stringResource(R.string.tuner_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "A", "C#" or "Db" (sharps/flats setting) for a MIDI note. */
fun noteName(note: Int, accidentals: AccidentalPreference): String =
    Enharmonics.spell(Math.floorMod(note, 12), accidentals).toString().replace("#", "♯").replace("b", "♭")

@Composable
private fun NoteDisplay(state: TunerUiState) {
    val r = state.reading
    val description = r?.let { stringResource(R.string.tuner_reading, noteName(it.note, state.accidentals) + it.octave, formatCents(it.cents)) }
        ?: stringResource(R.string.tuner_no_pitch)
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            r?.let { noteName(it.note, state.accidentals) } ?: "–",
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            color = if (r != null && abs(r.cents) <= IN_TUNE_CENTS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (r != null) Text(r.octave.toString(), fontSize = 32.sp, modifier = Modifier.padding(bottom = 12.dp))
    }
    Text(
        r?.let { stringResource(R.string.cents_value, formatCents(it.cents)) } ?: stringResource(R.string.tuner_no_pitch),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** −50 … +50 cents on a half circle; green inside ±5 cents. */
@Composable
private fun CentsNeedle(reading: TunerReading?) {
    val scale = MaterialTheme.colorScheme.outline
    val needleColor = if (reading != null && abs(reading.cents) <= IN_TUNE_CENTS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val center = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val cx = size.width / 2
        val cy = size.height - 8.dp.toPx()
        val radius = minOf(size.width / 2, size.height) - 12.dp.toPx()
        for (c in -50..50 step 10) {
            val a = Math.toRadians(90.0 - c * 1.6)  // ±80°
            val inner = if (c == 0) radius - 18.dp.toPx() else radius - 10.dp.toPx()
            drawLine(
                if (c == 0) center else scale,
                Offset(cx + (inner * cos(a)).toFloat(), cy - (inner * sin(a)).toFloat()),
                Offset(cx + (radius * cos(a)).toFloat(), cy - (radius * sin(a)).toFloat()),
                strokeWidth = if (c == 0) 3.dp.toPx() else 1.5.dp.toPx(),
            )
        }
        if (reading != null) {
            val a = Math.toRadians(90.0 - reading.cents.coerceIn(-50.0, 50.0) * 1.6)
            drawLine(
                needleColor,
                Offset(cx, cy),
                Offset(cx + ((radius - 4.dp.toPx()) * cos(a)).toFloat(), cy - ((radius - 4.dp.toPx()) * sin(a)).toFloat()),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(needleColor, 6.dp.toPx(), Offset(cx, cy))
    }
}

@Composable
private fun Details(state: TunerUiState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(state.hz?.let { stringResource(R.string.hz_decimal, formatMs(it)) } ?: "– Hz", style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.tuner_confidence, percent(state.confidence)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.db_value, formatMs(state.levelDb.coerceAtLeast(-99.0))), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The last 5 s of pitch, newest on the right; one line per semitone, the range follows the
 * voice. Drawing reuses one Path and allocates nothing per frame.
 */
@Composable
private fun PitchTraceView(trace: PitchTrace, version: Long, accidentals: AccidentalPreference) {
    val path = remember { Path() }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val cLine = MaterialTheme.colorScheme.outline
    val range = trace.range(TunerViewModel.TRACE_SECONDS)
    // At least an octave around the voice.
    val mid = range?.let { (it.start + it.endInclusive) / 2f } ?: 60f
    val half = maxOf(6f, range?.let { (it.endInclusive - it.start) / 2f + 2f } ?: 6f)
    val lo = kotlin.math.floor(mid - half)
    val hi = kotlin.math.ceil(mid + half)
    Column {
        Text(stringResource(R.string.tuner_trace), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(noteName(lo.toInt(), accidentals) + TunerReading(lo.toInt(), 0.0).octave, style = MaterialTheme.typography.labelSmall)
            Text(noteName(hi.toInt(), accidentals) + TunerReading(hi.toInt(), 0.0).octave, style = MaterialTheme.typography.labelSmall)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") version  // redraw on every new frame
                fun y(m: Float) = size.height * (1f - (m - lo) / (hi - lo))
                var n = lo.toInt()
                while (n <= hi.toInt()) {
                    val yy = y(n.toFloat())
                    drawLine(if (Math.floorMod(n, 12) == 0) cLine else grid, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
                    n++
                }
                path.reset()
                val now = trace.latestTime
                var pen = false
                for (k in 0 until trace.size) {
                    val m = trace.midi(k)
                    val x = (size.width * (1.0 - (now - trace.time(k)) / TunerViewModel.TRACE_SECONDS)).toFloat()
                    if (m.isNaN() || x < 0f) {
                        pen = false
                        continue
                    }
                    val yy = y(m.coerceIn(lo, hi))
                    if (pen) path.lineTo(x, yy) else path.moveTo(x, yy)
                    pen = true
                }
                drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}

/** "+12" / "−7" / "0". */
fun formatCents(cents: Double): String {
    val c = Math.round(cents).toInt()
    return when {
        c > 0 -> "+$c"
        c < 0 -> "−${-c}"
        else -> "0"
    }
}

private const val IN_TUNE_CENTS = 5.0
