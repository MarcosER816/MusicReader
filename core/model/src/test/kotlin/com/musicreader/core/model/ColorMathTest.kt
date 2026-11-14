package com.musicreader.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorMathTest {
    @Test
    fun hsvRoundTripForEveryDefaultColor() {
        val colors = AppSettings.DEFAULT_CHORD_COLORS.values + AppSettings.DEFAULT_FINGER_COLORS.values +
            listOf(0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF)
        for (c in colors) assertEquals(ColorMath.toHex(c), ColorMath.toHex(ColorMath.toArgb(ColorMath.toHsv(c))))
        assertEquals(0xFFFF0000, ColorMath.toArgb(Hsv(0f, 1f, 1f)))
        assertEquals(0xFF00FF00, ColorMath.toArgb(Hsv(120f, 1f, 1f)))
        assertEquals(0xFF0000FF, ColorMath.toArgb(Hsv(240f, 1f, 1f)))
        assertEquals(0xFFFF0000, ColorMath.toArgb(Hsv(360f, 1f, 1f)))
        assertEquals(Hsv(0f, 0f, 1f), ColorMath.toHsv(0xFFFFFFFF))
    }

    @Test
    fun hex() {
        assertEquals("#E53935", ColorMath.toHex(0xFFE53935))
        assertEquals(0xFFE53935, ColorMath.parseHex("#e53935"))
        assertEquals(0xFFE53935, ColorMath.parseHex(" E53935 "))
        assertNull(ColorMath.parseHex("#E5393"))
        assertNull(ColorMath.parseHex("#GGGGGG"))
    }

    @Test
    fun similarColors() {
        assertTrue(ColorMath.deltaE(0xFF42A5F5, 0xFF42A5F5) < 1e-9)
        assertTrue(ColorMath.deltaE(0xFF42A5F5, 0xFF45A8F2) < ColorMath.SIMILAR_DELTA_E)
        assertTrue(ColorMath.deltaE(0xFF000000, 0xFFFFFFFF) > 99)
        // The default finger colors are all clearly different.
        assertEquals(emptyList(), ColorMath.similarFingerPairs(AppSettings.DEFAULT_FINGER_COLORS))
        val clash = AppSettings.DEFAULT_FINGER_COLORS + (3 to 0xFF44A6F4) // 3 almost like 1
        assertEquals(listOf(1 to 3), ColorMath.similarFingerPairs(clash))
    }

    @Test
    fun settingsOps() {
        var s = AppSettings()
        s = SettingsOps.setChordColor(s, NoteLetter.C, 0xFF00FF00)
        assertEquals(0xFF00FF00, s.chordColor(ChordSymbol(NoteLetter.C, suffix = "m7")))
        assertEquals(0xFFFB8C00, s.chordColor(ChordSymbol(NoteLetter.D)))
        s = SettingsOps.resetChordColor(s, NoteLetter.C)
        assertEquals(AppSettings.DEFAULT_CHORD_COLORS, s.chordColors)
        s = SettingsOps.setFingerColor(s, 2, 0xFF123456)
        s = SettingsOps.setFingerColor(s, 5, 0xFF123456) // no thumb (D-012): ignored
        assertEquals(0xFF123456, s.fingerColor(2))
        assertEquals(setOf(0, 1, 2, 3, 4), s.fingerColors.keys)
        assertEquals(AppSettings.DEFAULT_FINGER_COLORS, SettingsOps.resetFingerColors(s).fingerColors)
        assertEquals(AppSettings.DEFAULT_FINGER_COLORS.getValue(2), SettingsOps.resetFingerColor(s, 2).fingerColor(2))
        s = SettingsOps.toggleViewerLayer(s, Layer.TAB)
        s = SettingsOps.toggleViewerLayer(s, Layer.CHORDS)
        s = SettingsOps.toggleViewerLayer(s, Layer.LYRICS) // last one: refused
        assertEquals(LayerVisibility(lyrics = true, chords = false, tab = false), s.viewerLayers)
    }
}
