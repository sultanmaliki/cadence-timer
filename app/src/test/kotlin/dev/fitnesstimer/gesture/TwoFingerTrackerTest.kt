package dev.fitnesstimer.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class TwoFingerTrackerTest {
    private val swipeMin = 150f // px, stands in for 56 dp

    private fun tracker() = TwoFingerTracker()

    @Test fun cleanTwoFingerTapIsATap() {
        val t = tracker()
        t.update(1, 300f, 800f, true); t.update(2, 700f, 800f, true)
        t.update(1, 302f, 801f, true); t.update(2, 699f, 800f, true)
        t.update(1, 302f, 801f, false); t.update(2, 699f, 800f, false)
        assertEquals(TwoFingerResult.TAP, t.result(swipeMin))
    }

    @Test fun oneFingerLiftingFirstDoesNotLookLikeASwipe() {
        // The reported bug: fingers 400 px apart, left one lifts first. The old logic followed
        // "the first pointer still down", which then jumped ~400 px to the other finger.
        val t = tracker()
        t.update(1, 300f, 800f, true); t.update(2, 700f, 800f, true)
        t.update(1, 301f, 800f, false)          // left finger up
        t.update(2, 701f, 800f, true)           // right finger still down, barely moved
        t.update(2, 701f, 800f, false)
        assertEquals(TwoFingerResult.TAP, t.result(swipeMin))
    }

    @Test fun rightFingerLiftingFirstIsAlsoATap() {
        val t = tracker()
        t.update(1, 300f, 800f, true); t.update(2, 700f, 800f, true)
        t.update(2, 700f, 800f, false)
        t.update(1, 300f, 800f, true)
        t.update(1, 300f, 800f, false)
        assertEquals(TwoFingerResult.TAP, t.result(swipeMin))
    }

    @Test fun tinyJitterIsATap() {
        val t = tracker()
        t.update(1, 300f, 800f, true); t.update(2, 700f, 800f, true)
        repeat(10) { i -> t.update(1, 300f + i, 800f, true); t.update(2, 700f + i, 800f, true) }
        assertEquals(TwoFingerResult.TAP, t.result(swipeMin))
    }

    @Test fun deliberateSwipeLeftIsNext() {
        val t = tracker()
        t.update(1, 500f, 800f, true); t.update(2, 800f, 800f, true)
        for (i in 1..10) { t.update(1, 500f - i * 30, 800f, true); t.update(2, 800f - i * 30, 800f, true) }
        assertEquals(TwoFingerResult.NEXT, t.result(swipeMin))
    }

    @Test fun deliberateSwipeRightIsPrevious() {
        val t = tracker()
        t.update(1, 300f, 800f, true); t.update(2, 600f, 800f, true)
        for (i in 1..10) { t.update(1, 300f + i * 30, 800f, true); t.update(2, 600f + i * 30, 800f, true) }
        assertEquals(TwoFingerResult.PREVIOUS, t.result(swipeMin))
    }

    @Test fun swipeStillCountsWhenOneFingerLiftsAtTheEnd() {
        val t = tracker()
        t.update(1, 500f, 800f, true); t.update(2, 800f, 800f, true)
        for (i in 1..10) { t.update(1, 500f - i * 30, 800f, true); t.update(2, 800f - i * 30, 800f, true) }
        t.update(1, 200f, 800f, false)           // left finger lifts
        t.update(2, 500f, 800f, true)            // right finger finishes
        t.update(2, 500f, 800f, false)
        assertEquals(TwoFingerResult.NEXT, t.result(swipeMin))
    }

    @Test fun mostlyVerticalMovementIsNotASwipe() {
        val t = tracker()
        t.update(1, 300f, 800f, true); t.update(2, 700f, 800f, true)
        for (i in 1..10) { t.update(1, 300f + i * 20, 800f + i * 60, true); t.update(2, 700f + i * 20, 800f + i * 60, true) }
        assertEquals(TwoFingerResult.TAP, t.result(swipeMin))   // dx 200 but dy 600
    }

    @Test fun liftPositionIsIgnored() {
        val t = tracker()
        t.update(1, 300f, 800f, true); t.update(2, 700f, 800f, true)
        t.update(1, 900f, 800f, false)           // a wild final position reported on lift
        t.update(2, 700f, 800f, false)
        assertEquals(TwoFingerResult.TAP, t.result(swipeMin))
    }

    @Test fun noPointersIsSafe() {
        assertEquals(TwoFingerResult.TAP, tracker().result(swipeMin))
    }
}
