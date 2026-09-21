package dev.fitnesstimer.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleSizeTest {
    @Test fun smallImageIsNotDownsampled() = assertEquals(1, sampleSizeFor(800, 600, 1024))
    @Test fun justUnderLimitIsNotDownsampled() = assertEquals(1, sampleSizeFor(1023, 1023, 1024))
    @Test fun hugeImageIsBounded() {
        val s = sampleSizeFor(5000, 5000, 1024)
        assertTrue(s >= 2)
        assertTrue(5000 / s < 2 * 1024)
        assertTrue("decoded pixels must stay far below 100MB", (5000L / s) * (5000L / s) * 4 < 8_000_000L)
    }
    @Test fun usesLargerSide() = assertEquals(8, sampleSizeFor(9000, 100, 1024))
    @Test fun powerOfTwo() = listOf(1, 2, 4, 8, 16).let { ok ->
        listOf(100, 1500, 3000, 6000, 20000).forEach { assertTrue(sampleSizeFor(it, it, 1024) in ok) }
    }
}
