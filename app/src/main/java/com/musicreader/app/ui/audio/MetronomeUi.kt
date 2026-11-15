package com.musicreader.app.ui.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.musicreader.app.R
import com.musicreader.app.ui.common.AppIcon
import com.musicreader.app.ui.common.AppIcons
import com.musicreader.app.ui.song.SongViewModel

/** The top bar's metronome toggle; highlighted while it plays. */
@Composable
fun MetronomeButton(state: MetronomeUiState, onToggle: () -> Unit) {
    val label = stringResource(if (state.running) R.string.metronome_stop else R.string.metronome_start)
    IconButton(
        onClick = onToggle,
        colors = if (state.running) {
            IconButtonDefaults.filledIconButtonColors()
        } else {
            IconButtonDefaults.iconButtonColors()
        },
    ) { AppIcon(AppIcons.Metronome, label) }
}

/**
 * Tempo, time signature and one light per beat of the bar; the lit one is the beat being heard,
 * read from the audio clock on every frame (the clock decides, the frame only redraws).
 */
@Composable
fun MetronomeStrip(state: MetronomeUiState, beatNow: () -> com.musicreader.audio.MetronomeBeat?) {
    var lit by remember { mutableIntStateOf(-1) }
    LaunchedEffect(state.running) {
        while (state.running) {
            withFrameNanos { }
            val b = beatNow()
            lit = if (b == null || b.beat < 0) -1 else b.beatInBar
        }
    }
    val beats = state.timeSignature.num
    val description = stringResource(
        R.string.metronome_playing,
        SongViewModel.formatTempo(state.bpm),
        state.timeSignature.num,
        state.timeSignature.den,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.metronome_tempo, SongViewModel.formatTempo(state.bpm), state.timeSignature.num, state.timeSignature.den),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        repeat(beats) { i ->
            val on = i == lit
            val color = when {
                on && i == 0 -> MaterialTheme.colorScheme.primary
                on -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(Modifier.size(if (i == 0) 14.dp else 11.dp).background(color, CircleShape))
        }
    }
}
