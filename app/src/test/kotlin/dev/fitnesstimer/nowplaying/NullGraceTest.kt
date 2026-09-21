package dev.fitnesstimer.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NullGraceTest {
    @Test fun passesRealValuesThrough() {
        val g = NullGrace<String>(1500)
        assertEquals("a", g.filter("a", 0)); assertEquals("b", g.filter("b", 100))
    }

    @Test fun holdsTheLastValueDuringATrackChangeGap() {
        val g = NullGrace<String>(1500)
        g.filter("song", 1_000)
        assertEquals("song", g.filter(null, 1_150))     // the ~150 ms gap seen on Mi Music
        assertEquals("next", g.filter("next", 1_200))
    }

    @Test fun aLastingAbsenceStillShowsThrough() {
        val g = NullGrace<String>(1500)
        g.filter("song", 1_000)
        assertEquals("song", g.filter(null, 2_400))
        assertNull(g.filter(null, 2_600))               // grace (1500) expired
        assertNull(g.filter(null, 2_700))               // and stays null
    }

    @Test fun nothingHeldMeansNullImmediately() {
        assertNull(NullGrace<String>(1500).filter(null, 5))
    }

    @Test fun remainingTimeCountsDown() {
        val g = NullGrace<String>(1500)
        assertEquals(0L, g.remainingMs(0))
        g.filter("x", 1_000)
        assertEquals(1_500L, g.remainingMs(1_000)); assertEquals(500L, g.remainingMs(2_000)); assertEquals(0L, g.remainingMs(9_000))
    }

    @Test fun resetDropsTheHeldValueAtOnce() {
        val g = NullGrace<String>(1500)
        g.filter("x", 0); g.reset()
        assertNull(g.filter(null, 10))
    }
}
