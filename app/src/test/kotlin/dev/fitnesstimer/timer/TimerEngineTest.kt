package dev.fitnesstimer.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TimerEngineTest {
    private var now = 1_000L
    private fun engine() = TimerEngine { now }

    private fun TimerEngine.advance(ms: Long) {
        now += ms
        nowElapsedRealtime = now
    }

    @Test fun stopwatchCountsUpWhileRunning() {
        val e = engine(); e.start(); e.advance(5_000)
        assertEquals(5_000L, e.displayMs)
    }

    @Test fun pauseFreezesAndResumeContinues() {
        val e = engine(); e.start(); e.advance(3_000); e.pause()
        now += 10_000; e.nowElapsedRealtime = now
        assertEquals(3_000L, e.displayMs)
        e.start(); e.advance(2_000)
        assertEquals(5_000L, e.displayMs)
    }

    @Test fun resetClearsAndStops() {
        val e = engine(); e.start(); e.advance(3_000); e.pause(); e.reset()
        assertEquals(0L, e.displayMs); assertFalse(e.isRunning)
    }

    @Test fun doubleStartDoesNotRestartBasis() {
        val e = engine(); e.start(); e.advance(2_000); e.start(); e.advance(1_000)
        assertEquals(3_000L, e.displayMs)
    }

    @Test fun countdownCountsDownAndClampsAtZero() {
        val e = engine(); e.switchToCountdown(10_000); e.start(); e.advance(4_000)
        assertEquals(6_000L, e.displayMs)
        e.advance(20_000)
        assertEquals(0L, e.displayMs); assertTrue(e.isCountdownFinished)
    }

    @Test fun finishedCountdownCannotBeRestartedUntilReset() {
        val e = engine(); e.switchToCountdown(1_000); e.start(); e.advance(2_000); e.pause()
        e.start()
        assertFalse(e.isRunning)
        e.reset(); e.start()
        assertTrue(e.isRunning); assertEquals(1_000L, e.displayMs)
    }

    @Test fun zeroTargetCountdownIsImmediatelyFinishedAndNotStartable() {
        val e = engine(); e.switchToCountdown(0); e.start()
        assertTrue(e.isCountdownFinished); assertFalse(e.isRunning)
    }

    @Test fun negativeTargetIsTreatedAsZero() {
        val e = engine(); e.switchToCountdown(-5_000)
        assertEquals(0L, e.countdownTargetMs)
        assertEquals(0L, e.displayMs)
    }

    @Test fun switchingModesResets() {
        val e = engine(); e.start(); e.advance(4_000)
        e.switchToCountdown(9_000)
        assertFalse(e.isRunning); assertEquals(9_000L, e.displayMs)
        e.switchToStopwatch()
        assertEquals(0L, e.displayMs)
    }

    @Test fun runningChangeCallbackFiresOnlyOnRealTransitions() {
        val e = engine(); val seen = mutableListOf<Boolean>()
        e.onRunningChanged = { seen += it }
        e.start(); e.start(); e.pause(); e.pause(); e.reset()
        e.start(); e.switchToStopwatch()
        assertEquals(listOf(true, false, true, false), seen)
    }

    @Test fun formatBasics() {
        assertEquals("00:00:00", formatElapsed(0))
        assertEquals("00:00:59", formatElapsed(59_999))
        assertEquals("00:01:00", formatElapsed(60_000))
        assertEquals("01:01:01", formatElapsed(3_661_000))
        assertEquals("100:00:00", formatElapsed(360_000_000))
    }

    @Test fun formatNeverGoesNegative() {
        assertEquals("00:00:00", formatElapsed(-1_500))
    }

    @Test fun formatIsLocaleIndependent() {
        val old = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA-u-nu-arab"))
            assertEquals("00:01:05", formatElapsed(65_000))
        } finally { Locale.setDefault(old) }
    }
}
