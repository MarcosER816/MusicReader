package com.musicreader.core.model

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Hue (0–360), saturation and value (0–1) of a color. */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/** Color helpers for the settings screen (D-028). Colors are ARGB longs, as stored in settings. */
object ColorMath {
    /** ΔE (CIE76) below which two finger colors count as nearly the same. */
    const val SIMILAR_DELTA_E: Double = 20.0

    fun toArgb(hsv: Hsv): Long {
        val h = ((hsv.hue % 360f) + 360f) % 360f / 60f
        val s = hsv.saturation.coerceIn(0f, 1f)
        val v = hsv.value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1 - abs(h % 2 - 1))
        val (r, g, b) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun channel(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    fun toHsv(argb: Long): Hsv {
        val r = red(argb) / 255f
        val g = green(argb) / 255f
        val b = blue(argb) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val hue = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0) it + 360f else it }
        return Hsv(hue, if (max == 0f) 0f else d / max, max)
    }

    /** "#RRGGBB" (opaque). */
    fun toHex(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)

    /** Parses "#RRGGBB" or "RRGGBB" (any case) to an opaque ARGB color; null when invalid. */
    fun parseHex(text: String): Long? {
        val hex = text.trim().removePrefix("#")
        if (hex.length != 6 || hex.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
        return 0xFF000000L or hex.toLong(16)
    }

    /** Perceptual distance (CIE76 ΔE in Lab, D65). */
    fun deltaE(a: Long, b: Long): Double {
        val (l1, a1, b1) = lab(a)
        val (l2, a2, b2) = lab(b)
        return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    /** Pairs of fingers (lower finger first) whose colors are nearly the same. */
    fun similarFingerPairs(colors: Map<Int, Long>): List<Pair<Int, Int>> {
        val fingers = colors.keys.sorted()
        return fingers.flatMapIndexed { i, f -> fingers.drop(i + 1).map { f to it } }
            .filter { (f, g) -> deltaE(colors.getValue(f), colors.getValue(g)) < SIMILAR_DELTA_E }
    }

    private fun red(c: Long) = ((c shr 16) and 0xFF).toInt()
    private fun green(c: Long) = ((c shr 8) and 0xFF).toInt()
    private fun blue(c: Long) = (c and 0xFF).toInt()

    private fun lab(c: Long): Triple<Double, Double, Double> {
        fun linear(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(red(c))
        val g = linear(green(c))
        val b = linear(blue(c))
        val x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047
        val y = r * 0.2126 + g * 0.7152 + b * 0.0722
        val z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
        return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }
}

/** Settings edits used by the settings screen (pure). */
object SettingsOps {
    fun setChordColor(s: AppSettings, letter: NoteLetter, argb: Long) = s.copy(chordColors = s.chordColors + (letter to argb))

    fun resetChordColor(s: AppSettings, letter: NoteLetter) =
        s.copy(chordColors = s.chordColors + (letter to AppSettings.DEFAULT_CHORD_COLORS.getValue(letter)))

    fun resetChordColors(s: AppSettings) = s.copy(chordColors = AppSettings.DEFAULT_CHORD_COLORS)

    fun setFingerColor(s: AppSettings, finger: Int, argb: Long) =
        if (finger !in AppSettings.DEFAULT_FINGER_COLORS) s else s.copy(fingerColors = s.fingerColors + (finger to argb))

    fun resetFingerColor(s: AppSettings, finger: Int) =
        AppSettings.DEFAULT_FINGER_COLORS[finger]?.let { s.copy(fingerColors = s.fingerColors + (finger to it)) } ?: s

    fun resetFingerColors(s: AppSettings) = s.copy(fingerColors = AppSettings.DEFAULT_FINGER_COLORS)

    /** The viewer's layers; the last visible layer cannot be switched off. */
    fun toggleViewerLayer(s: AppSettings, layer: Layer) =
        s.copy(viewerLayers = s.viewerLayers.toggled(layer, keepOneVisible = true))
}
