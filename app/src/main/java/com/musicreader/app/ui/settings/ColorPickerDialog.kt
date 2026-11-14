package com.musicreader.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.musicreader.app.R
import com.musicreader.core.model.AppSettings
import com.musicreader.core.model.ColorMath
import com.musicreader.core.model.Hsv

/** Quick choices: the default chord and finger colors plus a few more. */
private val PALETTE: List<Long> = (AppSettings.DEFAULT_CHORD_COLORS.values + AppSettings.DEFAULT_FINGER_COLORS.values + listOf(
    0xFFFF80AB, 0xFF26A69A, 0xFF8D6E63, 0xFF9E9E9E, 0xFF5C6BC0, 0xFFFFEB3B,
)).distinct()

/**
 * Picks a color: hue, saturation and brightness sliders, a palette, a hex field, a live
 * [preview] and "Reset to default".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Long,
    default: Long,
    preview: @Composable (Color) -> Unit,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = ColorMath.toHsv(initial)
    var hue by rememberSaveable { mutableFloatStateOf(start.hue) }
    var saturation by rememberSaveable { mutableFloatStateOf(start.saturation) }
    var value by rememberSaveable { mutableFloatStateOf(start.value) }
    var hex by rememberSaveable { mutableStateOf(ColorMath.toHex(initial)) }
    // The exact color picked from the palette, the hex field or "Reset to default"; the sliders
    // clear it. Keeps a reset exactly equal to the default (no HSV rounding).
    var exact by rememberSaveable { mutableStateOf<Long?>(initial) }
    val argb = exact ?: ColorMath.toArgb(Hsv(hue, saturation, value))
    val color = Color(argb.toInt())

    fun set(c: Long) {
        val h = ColorMath.toHsv(c)
        hue = h.hue
        saturation = h.saturation
        value = h.value
        hex = ColorMath.toHex(c)
        exact = c
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    preview(color)
                }
                Text(stringResource(R.string.hue), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = hue,
                    onValueChange = { exact = null; hue = it; hex = ColorMath.toHex(ColorMath.toArgb(Hsv(it, saturation, value))) },
                    valueRange = 0f..359f,
                    colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
                )
                Text(stringResource(R.string.saturation), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = saturation,
                    onValueChange = { exact = null; saturation = it; hex = ColorMath.toHex(ColorMath.toArgb(Hsv(hue, it, value))) },
                    colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
                )
                Text(stringResource(R.string.brightness), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = value,
                    onValueChange = { exact = null; value = it; hex = ColorMath.toHex(ColorMath.toArgb(Hsv(hue, saturation, it))) },
                    colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.forEach { c ->
                        val swatch = stringResource(R.string.color_swatch, ColorMath.toHex(c))
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(c.toInt()), CircleShape)
                                .border(2.dp, if (c == argb) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .semantics { contentDescription = swatch }
                                .clickable { set(c) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { text ->
                            hex = text
                            ColorMath.parseHex(text)?.let { c ->
                                exact = c
                                val h = ColorMath.toHsv(c)
                                hue = h.hue
                                saturation = h.saturation
                                value = h.value
                            }
                        },
                        label = { Text(stringResource(R.string.hex_color)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { set(default) }) { Text(stringResource(R.string.reset_to_default)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(argb) }) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
