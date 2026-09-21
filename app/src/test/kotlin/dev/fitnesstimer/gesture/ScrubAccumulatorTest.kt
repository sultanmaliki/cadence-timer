package dev.fitnesstimer.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrubAccumulatorTest {
    private val dur = 100_000L

    @Test fun firstMoveEmitsImmediately() {
        val s = ScrubAccumulator(100)
        assertEquals(15_000L, s.move({ 10_000 }, 5_000, dur, 1_000))
    }

    @Test fun movesInsideIntervalAreThrottledButAccumulate() {
        val s = ScrubAccumulator(100)
        s.move({ 10_000 }, 1_000, dur, 1_000)
        assertNull(s.move({ 999_999 }, 1_000, dur, 1_020)) // start position must not be re-read
        assertNull(s.move({ 999_999 }, 1_000, dur, 1_040))
        assertEquals(14_000L, s.move({ 999_999 }, 1_000, dur, 1_100))
    }

    @Test fun endFlushesThrottledTailOnce() {
        val s = ScrubAccumulator(100)
        s.move({ 10_000 }, 1_000, dur, 1_000)
        s.move({ 10_000 }, 2_000, dur, 1_010)
        assertEquals(13_000L, s.end())
        assertNull(s.end())
    }

    @Test fun endWithNothingPendingReturnsNull() {
        val s = ScrubAccumulator(100)
        s.move({ 10_000 }, 1_000, dur, 1_000)
        assertNull(s.end())
    }

    @Test fun clampsToBounds() {
        val s = ScrubAccumulator(100)
        assertEquals(0L, s.move({ 1_000 }, -50_000, dur, 1_000))
        assertEquals(dur, s.move({ 1_000 }, 500_000, dur, 2_000))
    }

    @Test fun newDragRestartsFromCurrentPosition() {
        val s = ScrubAccumulator(100)
        s.move({ 10_000 }, 1_000, dur, 1_000); s.end()
        assertEquals(51_000L, s.move({ 50_000 }, 1_000, dur, 5_000))
    }

    @Test fun unknownDurationIgnored() = assertNull(ScrubAccumulator().move({ 0 }, 1_000, -1, 0))
}
