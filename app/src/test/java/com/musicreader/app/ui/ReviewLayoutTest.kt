package com.musicreader.app.ui

import com.musicreader.app.ui.capture.LabelBox
import com.musicreader.app.ui.capture.ReviewLayout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Mark, Stage 9 round 2: "some words get over one another" in the take review. */
class ReviewLayoutTest {
    @Test
    fun theTimeLineWidensSoWordsFitTheirBlocks() {
        // "mor" is 30 px wide and lasts a quarter beat: it needs (30 + 8) / 0.25 = 152 px per beat.
        assertEquals(152f, ReviewLayout.beatWidth(listOf(1.0, 0.25), listOf(20f, 30f), 8f, 80f, 240f))
        assertEquals(80f, ReviewLayout.beatWidth(listOf(1.0), listOf(20f), 8f, 80f, 240f), "never narrower than 80")
        assertEquals(240f, ReviewLayout.beatWidth(listOf(0.05), listOf(40f), 8f, 80f, 240f), "never wider than 240")
    }

    @Test
    fun wordsThatFitStayInsideTheirBlock() {
        val blocks = listOf(LabelBox(0f, 100f, 60f, 28f), LabelBox(60f, 100f, 60f, 28f))
        val (words, _) = ReviewLayout.place(blocks, listOf(30f to 20f, 30f to 20f), listOf(20f to 14f, 20f to 14f), 4f, 2f)
        words.forEachIndexed { i, w -> assertTrue(w.left >= blocks[i].left && w.right <= blocks[i].right && w.top >= blocks[i].top && w.bottom <= blocks[i].bottom) }
    }

    @Test
    fun noLabelEverOverlapsAnotherLabelOrAForeignBlock() {
        val rnd = Random(3)
        repeat(300) {
            // Crowded takes: short syllables, similar pitches, long words.
            var x = 0f
            val n = 2 + rnd.nextInt(12)
            val blocks = List(n) {
                val w = 6f + rnd.nextFloat() * 70f
                LabelBox(x, 120f + rnd.nextInt(4) * 10f, w, 28f).also { x += w + rnd.nextFloat() * 4f }
            }
            val words = List(n) { (12f + rnd.nextFloat() * 60f) to 20f }
            val notes = List(n) { 22f to 14f }
            val (wb, nb) = ReviewLayout.place(blocks, words, notes, 4f, 2f)
            val all = wb + nb
            for (i in all.indices) for (j in i + 1 until all.size) {
                assertTrue(!all[i].overlaps(all[j]), "labels $i and $j overlap: ${all[i]} ${all[j]}")
            }
            // A label outside its block never covers another syllable's block.
            all.forEachIndexed { k, label ->
                val own = k % n
                blocks.forEachIndexed { j, b ->
                    if (j != own) assertTrue(!label.overlaps(b), "label $k covers block $j")
                }
            }
        }
    }
}
