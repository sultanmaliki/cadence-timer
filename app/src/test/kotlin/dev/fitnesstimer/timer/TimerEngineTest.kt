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

    @Test fun stopwatchTickBoundaries() {
        val e = engine(); e.start()
        e.advance(0); assertEquals(1000L, e.msUntilDisplayChange())
        e.advance(400); assertEquals(600L, e.msUntilDisplayChange())
        e.advance(599); assertEquals(1L, e.msUntilDisplayChange())
        e.advance(1); assertEquals(1000L, e.msUntilDisplayChange())
    }

    @Test fun countdownTickAlignsToDisplayedSecondAndFinish() {
        val e = engine(); e.switchToCountdown(5_000); e.start()
        assertEquals(1L, e.msUntilDisplayChange())          // shows 5s, drops to 4s next ms
        e.advance(500)                                       // 4500 left, shows 4
        assertEquals(501L, e.msUntilDisplayChange())
        e.advance(4_200)                                     // 300 left: finish before next second
        assertEquals(300L, e.msUntilDisplayChange())
        e.advance(300)
        assertEquals(1L, e.msUntilDisplayChange())
    }

    @Test fun tickDelayAlwaysInRange() {
        val e = engine(); e.start()
        for (i in 0..2500 step 37) { e.advance(37); val d = e.msUntilDisplayChange(); assertTrue(d in 1L..1000L) }
    }

    @Test fun snapshotCapturesLiveElapsed() {
        val e = engine(); e.start(); e.advance(4_000)
        val snap = e.snapshot(wallNowMs = 50_000)
        assertEquals(4_000L, snap.elapsedMs); assertTrue(snap.running)
        assertEquals(50_000L, snap.savedWallMs)
    }

    @Test fun restoreRunningCreditsTimeTheProcessWasDead() {
        val e = engine()
        e.restore(TimerSnapshot(TimerMode.STOPWATCH, 0, 4_000, true, savedWallMs = 100_000), wallNowMs = 130_000)
        assertTrue(e.isRunning); assertEquals(34_000L, e.displayMs)
        e.advance(1_000); assertEquals(35_000L, e.displayMs)
    }

    @Test fun restorePausedDoesNotCreditGap() {
        val e = engine()
        e.restore(TimerSnapshot(TimerMode.STOPWATCH, 0, 4_000, false, 100_000), 999_000)
        assertFalse(e.isRunning); assertEquals(4_000L, e.displayMs)
    }

    @Test fun restoreIgnoresNegativeOrHugeGaps() {
        val back = engine()   // wall clock went backwards
        back.restore(TimerSnapshot(TimerMode.STOPWATCH, 0, 4_000, true, 100_000), 50_000)
        assertFalse(back.isRunning); assertEquals(4_000L, back.displayMs)
        val stale = engine()  // saved > 12h ago
        stale.restore(TimerSnapshot(TimerMode.STOPWATCH, 0, 4_000, true, 0), MAX_RESTORE_GAP_MS + 1)
        assertFalse(stale.isRunning); assertEquals(4_000L, stale.displayMs)
    }

    @Test fun restoreCountdownThatFinishedWhileDeadIsFinishedAndStopped() {
        val e = engine()
        e.restore(TimerSnapshot(TimerMode.COUNTDOWN, 60_000, 50_000, true, 0), wallNowMs = 20_000)
        assertFalse(e.isRunning); assertTrue(e.isCountdownFinished); assertEquals(0L, e.displayMs)
    }

    @Test fun restoreCountdownStillRunning() {
        val e = engine()
        e.restore(TimerSnapshot(TimerMode.COUNTDOWN, 60_000, 10_000, true, 0), wallNowMs = 5_000)
        assertTrue(e.isRunning); assertEquals(45_000L, e.displayMs)
    }

    @Test fun restoreIsSilentButUserActionsNotify() {
        val e = engine(); var n = 0
        e.onStateChanged = { n++ }
        e.restore(TimerSnapshot(TimerMode.STOPWATCH, 0, 1_000, true, 0), 1_000)
        assertEquals(0, n)
        e.pause(); e.start(); e.reset(); e.switchToCountdown(5_000); e.switchToStopwatch()
        assertEquals(5, n)
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
