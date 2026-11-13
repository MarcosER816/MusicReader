package com.musicreader.core.model

import kotlinx.serialization.Serializable

/** The three layers of a song view. */
@Serializable
enum class Layer { LYRICS, CHORDS, TAB }

/** Which layers are visible. */
@Serializable
data class LayerVisibility(
    val lyrics: Boolean = true,
    val chords: Boolean = true,
    val tab: Boolean = true,
) {
    fun isVisible(layer: Layer): Boolean = when (layer) {
        Layer.LYRICS -> lyrics
        Layer.CHORDS -> chords
        Layer.TAB -> tab
    }

    val anyVisible: Boolean get() = lyrics || chords || tab

    /**
     * Returns a copy with [layer] switched. With [keepOneVisible] (the song viewer: at least one
     * layer must stay on), switching off the last visible layer is refused and `this` is returned.
     */
    fun toggled(layer: Layer, keepOneVisible: Boolean): LayerVisibility {
        val next = when (layer) {
            Layer.LYRICS -> copy(lyrics = !lyrics)
            Layer.CHORDS -> copy(chords = !chords)
            Layer.TAB -> copy(tab = !tab)
        }
        return if (keepOneVisible && !next.anyVisible) this else next
    }
}

/** Timing and pitch windows of the practice judge. */
@Serializable
data class JudgeWindows(
    val goldMs: Int = 50,
    val greenMs: Int = 120,
    val redMs: Int = 250,
    val goldCents: Int = 35,
    val greenCents: Int = 100,
)

/** Audio routes with their own latency profile. */
@Serializable
enum class AudioRoute { SPEAKER, WIRED, BLUETOOTH, USB }

@Serializable
enum class UiLanguage { SYSTEM, EN, ES }

/** User settings, stored with DataStore (docs/DATA_MODEL.md). Colors are ARGB. */
@Serializable
data class AppSettings(
    val chordColors: Map<NoteLetter, Long> = DEFAULT_CHORD_COLORS,
    /** Finger 0 (open) to 4 (pinky). Open strings are drawn as an outline in this color. */
    val fingerColors: Map<Int, Long> = DEFAULT_FINGER_COLORS,
    val leftHanded: Boolean = false,
    val viewerLayers: LayerVisibility = LayerVisibility(),
    /** Layers shown below each practice lane; the practiced layer itself is never shown. */
    val practiceLowerLayers: Map<PracticeType, LayerVisibility> = DEFAULT_PRACTICE_LOWER_LAYERS,
    /** Autoscroll speed multiplier (1.0 = song tempo). */
    val autoscrollSpeed: Double = 1.0,
    val judgeWindows: JudgeWindows = JudgeWindows(),
    /** Measured round-trip latency in milliseconds per audio route. */
    val latencyProfiles: Map<AudioRoute, Double> = emptyMap(),
    val uiLanguage: UiLanguage = UiLanguage.SYSTEM,
) {
    companion object {
        val DEFAULT_CHORD_COLORS: Map<NoteLetter, Long> = mapOf(
            NoteLetter.C to 0xFFE53935,
            NoteLetter.D to 0xFFFB8C00,
            NoteLetter.E to 0xFFFDD835,
            NoteLetter.F to 0xFFC6FF00,
            NoteLetter.G to 0xFF4FC3F7,
            NoteLetter.A to 0xFFE040FB,
            NoteLetter.B to 0xFFB39DDB,
        )

        val DEFAULT_FINGER_COLORS: Map<Int, Long> = mapOf(
            0 to 0xFFFFFFFF,
            1 to 0xFF42A5F5,
            2 to 0xFF66BB6A,
            3 to 0xFFFFA726,
            4 to 0xFFEF5350,
        )

        val DEFAULT_PRACTICE_LOWER_LAYERS: Map<PracticeType, LayerVisibility> = mapOf(
            PracticeType.SING to LayerVisibility(lyrics = false, chords = true, tab = false),
            PracticeType.CHORDS to LayerVisibility(lyrics = true, chords = false, tab = false),
            PracticeType.TAB to LayerVisibility(lyrics = true, chords = true, tab = false),
        )
    }
}
