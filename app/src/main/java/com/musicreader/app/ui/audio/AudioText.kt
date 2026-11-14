package com.musicreader.app.ui.audio

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.musicreader.app.R
import com.musicreader.core.model.AudioRoute
import com.musicreader.core.model.LatencyMethod
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun routeName(route: AudioRoute): String = stringResource(
    when (route) {
        AudioRoute.SPEAKER -> R.string.route_speaker
        AudioRoute.WIRED -> R.string.route_wired
        AudioRoute.USB -> R.string.route_usb
        AudioRoute.BLUETOOTH -> R.string.route_bluetooth
    },
)

@Composable
fun methodName(method: LatencyMethod): String = stringResource(
    if (method == LatencyMethod.AUTOMATIC) R.string.calibration_method_automatic else R.string.calibration_method_manual,
)

/** The app's current locale, read so that a language change recomposes. */
@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

/** "23.4" — one decimal, in the app's language. */
@Composable
fun formatMs(ms: Double): String = String.format(locale(), "%.1f", ms)

/** "12.345" — seconds with three decimals. */
@Composable
fun formatSeconds(seconds: Double): String = String.format(locale(), "%.3f", seconds)

@Composable
fun formatDate(epochMillis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, locale()).format(Date(epochMillis))

/** Confidence as a whole percentage. */
fun percent(fraction: Double): Int = Math.round(fraction.coerceIn(0.0, 1.0) * 100).toInt()
