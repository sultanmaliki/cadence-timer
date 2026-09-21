package dev.fitnesstimer.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ArtTrimTest {
    private val brown = 0xFF4A2E17.toInt()

    /** A W x H image of [bg] with a noisy "cover" rectangle at [cx0, cx1) x [cy0, cy1). */
    private fun image(w: Int, h: Int, bg: Int, cx0: Int, cx1: Int, cy0: Int = 0, cy1: Int = h, seed: Int = 1): IntArray {
        val rnd = Random(seed)
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            if (x in cx0 until cx1 && y in cy0 until cy1) {
                val r = rnd.nextInt(30, 230); val g = rnd.nextInt(30, 230); val b = rnd.nextInt(30, 230)
                0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
            } else bg
        }
    }

    @Test fun findsPillarboxBarsOnBothSides() {
        val px = image(256, 144, brown, 74, 182)            // 74 px bars left, 74 right
        val ins = findUniformInsets(px, 256, 144)
        assertEquals(75, ins.left); assertEquals(75, ins.right)   // +1 shaved for edge ringing
        assertEquals(0, ins.top); assertEquals(0, ins.bottom)
    }

    @Test fun findsLetterboxBarsTopAndBottom() {
        val px = image(200, 200, 0xFF000000.toInt(), 0, 200, 40, 160)
        val ins = findUniformInsets(px, 200, 200)
        assertEquals(41, ins.top); assertEquals(41, ins.bottom)
        assertEquals(0, ins.left); assertEquals(0, ins.right)
    }

    @Test fun toleratesCompressionNoiseInTheBars() {
        val rnd = Random(7)
        val px = image(256, 144, brown, 74, 182)
        for (i in px.indices) {
            val x = i % 256
            if (x < 74 || x >= 182) {
                val n = rnd.nextInt(-6, 7)
                val r = (((brown shr 16) and 0xFF) + n).coerceIn(0, 255)
                px[i] = 0xFF000000.toInt() or (r shl 16) or (((brown shr 8) and 0xFF) shl 8) or (brown and 0xFF)
            }
        }
        assertEquals(75, findUniformInsets(px, 256, 144).left)
    }

    @Test fun fullBleedArtworkIsNeverCropped() {
        assertTrue(findUniformInsets(image(256, 144, brown, 0, 256), 256, 144).isEmpty)
    }

    @Test fun barOnOnlyOneSideIsNotCropped() {
        assertTrue(findUniformInsets(image(256, 144, brown, 74, 256), 256, 144).isEmpty)
    }

    @Test fun barsOfDifferentColoursAreNotCropped() {
        val px = image(256, 144, brown, 74, 182)
        for (y in 0 until 144) for (x in 182 until 256) px[y * 256 + x] = 0xFFEEEEEE.toInt()
        assertTrue(findUniformInsets(px, 256, 144).isEmpty)
    }

    @Test fun darkArtworkWithSmallEdgeIsNotDestroyed() {
        // Solid dark image: everything is "flat", but no content would remain, so nothing is cropped.
        assertTrue(findUniformInsets(IntArray(256 * 144) { brown }, 256, 144).isEmpty)
    }

    @Test fun thinBordersBelowTheMinimumAreIgnored() {
        assertTrue(findUniformInsets(image(256, 144, brown, 2, 254), 256, 144).isEmpty)
    }

    @Test fun tinyOrInconsistentInputIsSafe() {
        assertTrue(findUniformInsets(IntArray(4), 2, 2).isEmpty)
        assertTrue(findUniformInsets(IntArray(10), 256, 144).isEmpty)
    }
}
