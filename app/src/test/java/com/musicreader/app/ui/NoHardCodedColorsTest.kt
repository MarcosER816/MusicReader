package com.musicreader.app.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Chord and finger colors come from the settings and everything else from the theme (D-028).
 * This scans the app's screens for fixed colors, so a new one cannot slip in unnoticed.
 */
class NoHardCodedColorsTest {
    private val fixedColor = Regex("""Color\(0x[0-9A-Fa-f]+|Color\.(Black|White|Gray|DarkGray|LightGray|Red|Green|Blue|Yellow|Cyan|Magenta|Transparent)\b""")

    /** The only places where a fixed color is right, and why. */
    private val allowed = mapOf(
        // The theme itself defines the palette.
        "ui/Theme.kt" to "theme",
        // Black or white number inside a finger dot, chosen for contrast with the user's color.
        "ui/common/Contrast.kt" to "contrast",
        // The glisten is a white shine over the chord's own color, fading to transparent.
        "ui/viewer/ViewerScreen.kt" to "glisten",
    )

    private val sources: List<File> = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun noScreenHasAFixedColor() {
        assertTrue(sources.size > 20, "sources found from ${File(".").absolutePath}")
        val offenders = sources.flatMap { file ->
            val path = file.invariantSeparatorsPath
            if (allowed.keys.any { path.contains(it) }) return@flatMap emptyList()
            file.readLines().mapIndexedNotNull { i, line ->
                if (fixedColor.containsMatchIn(line)) "$path:${i + 1}: ${line.trim()}" else null
            }
        }
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun theAllowedFilesOnlyUseTheirOneException() {
        val viewer = sources.single { it.invariantSeparatorsPath.endsWith("ui/viewer/ViewerScreen.kt") }
        val lines = viewer.readLines().filter { fixedColor.containsMatchIn(it) }
        assertEquals(1, lines.size, "only the glisten gradient: $lines")
        assertTrue(lines.single().contains("Color.Transparent, Color.White"))
    }

    @Test
    fun colorScreensReadTheSettings() {
        fun text(name: String) = sources.single { it.name == name }.readText()
        // Chord labels use the root color from the settings.
        listOf("ViewerScreen.kt", "ChordPicker.kt", "ChordEditorScreen.kt", "TabEditorScreen.kt").forEach {
            assertTrue(text(it).contains("chordColor("), "$it reads chord colors from settings")
        }
        // Finger dots and tab numbers use the finger colors from the settings.
        listOf("FretboardDiagram.kt", "TabStaff.kt").forEach {
            assertTrue(text(it).contains("fingerColors"), "$it reads finger colors from settings")
        }
    }
}
