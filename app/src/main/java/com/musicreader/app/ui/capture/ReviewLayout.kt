package com.musicreader.app.ui.capture

import kotlin.math.max

/** A rectangle in pixels. */
data class LabelBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    fun overlaps(o: LabelBox): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/**
 * Where the review time line puts its text, so no two labels overlap (Mark, Stage 9 round 2:
 * "some words get over one another"). Pure, so it is tested without drawing.
 */
object ReviewLayout {
    /**
     * Pixels per beat so that each syllable's word fits inside its block: the widest need
     * (word + padding) / duration, kept between [minPx] and [maxPx].
     */
    fun beatWidth(durations: List<Double>, wordWidths: List<Float>, padding: Float, minPx: Float, maxPx: Float): Float {
        var need = minPx
        durations.forEachIndexed { i, d -> if (d > 0) need = max(need, ((wordWidths[i] + padding) / d).toFloat()) }
        return need.coerceAtMost(maxPx)
    }

    /**
     * Places the words and the note names. A word goes inside its block when it fits, otherwise
     * above it; a note name goes under its block. A label that would touch an earlier label or
     * another block moves one more row away (up for words, down for names).
     */
    fun place(blocks: List<LabelBox>, words: List<Pair<Float, Float>>, notes: List<Pair<Float, Float>>, padding: Float, gap: Float, maxRows: Int = 6): Pair<List<LabelBox>, List<LabelBox>> {
        val placed = mutableListOf<LabelBox>()
        fun free(b: LabelBox, own: Int) = placed.none { it.overlaps(b) } && blocks.withIndex().none { (j, k) -> j != own && k.overlaps(b) }
        val wordBoxes = blocks.mapIndexed { i, block ->
            val (w, h) = words[i]
            val inside = LabelBox(block.left + padding, block.top + (block.height - h) / 2, w, h)
            val box = if (w + 2 * padding <= block.width && placed.none { it.overlaps(inside) }) {
                inside
            } else {
                (0 until maxRows).map { k -> LabelBox(block.left, block.top - gap - h - k * (h + gap), w, h) }
                    .firstOrNull { free(it, i) } ?: LabelBox(block.left, block.top - gap - h - maxRows * (h + gap), w, h)
            }
            placed += box
            box
        }
        val noteBoxes = blocks.mapIndexed { i, block ->
            val (w, h) = notes[i]
            val box = (0 until maxRows).map { k -> LabelBox(block.left, block.bottom + gap + k * (h + gap), w, h) }
                .firstOrNull { free(it, i) } ?: LabelBox(block.left, block.bottom + gap + maxRows * (h + gap), w, h)
            placed += box
            box
        }
        return wordBoxes to noteBoxes
    }
}
