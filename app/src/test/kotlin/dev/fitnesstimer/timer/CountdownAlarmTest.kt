package dev.fitnesstimer.timer

import dev.fitnesstimer.render.deriveArtColors
import dev.fitnesstimer.render.hslToColor
import dev.fitnesstimer.render.rgbToHsl
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownAlarmTest {
    private fun snap(mode: TimerMode, target: Long, elapsed: Long, running: Boolean) =
        TimerSnapshot(mode, target, elapsed, running, savedWallMs = 0)

    // ---- alarmDelayMs (pure) ----
    @Test fun runningCountdownGetsAlarmForRemainingTime() =
        assertEquals(40_000L, alarmDelayMs(snap(TimerMode.COUNTDOWN, 60_000, 20_000, true)))

    @Test fun pausedCountdownGetsNoAlarm() =
        assertNull(alarmDelayMs(snap(TimerMode.COUNTDOWN, 60_000, 20_000, false)))

    @Test fun stopwatchNeverGetsAlarm() =
        assertNull(alarmDelayMs(snap(TimerMode.STOPWATCH, 0, 20_000, true)))

    @Test fun finishedOrZeroTargetGetsNoAlarm() {
        assertNull(alarmDelayMs(snap(TimerMode.COUNTDOWN, 60_000, 60_000, true)))
        assertNull(alarmDelayMs(snap(TimerMode.COUNTDOWN, 60_000, 90_000, true)))
        assertNull(alarmDelayMs(snap(TimerMode.COUNTDOWN, 0, 0, true)))
    }

    // ---- engine settle / restore ----
    private var now = 1_000L
    private fun engine() = TimerEngine { now }

    @Test fun settleIfFinishedUsesRealClockNotStaleTickState() {
        val e = engine(); e.switchToCountdown(10_000); e.start()
        now += 12_000                       // time passes; no UI loop updates nowElapsedRealtime
        assertTrue(e.settleIfFinished())
        assertFalse(e.isRunning)
        assertEquals(0L, e.displayMs)
    }

    @Test fun settleIfFinishedLeavesRunningCountdownAlone() {
        val e = engine(); e.switchToCountdown(10_000); e.start()
        now += 4_000
        assertFalse(e.settleIfFinished())
        assertTrue(e.isRunning)
    }

    @Test fun settleIfFinishedIsFalseForStopwatch() {
        val e = engine(); e.start(); now += 50_000
        assertFalse(e.settleIfFinished())
    }

    @Test fun settleFiresStateChangeSoFinalStateIsPersisted() {
        val e = engine(); var n = 0
        e.switchToCountdown(1_000); e.start()
        e.onStateChanged = { n++ }
        now += 2_000; e.settleIfFinished()
        assertEquals(1, n)                  // pause() persisted the finished state
    }

    @Test fun restoreReportsCountdownThatFinishedWhileAway() {
        val e = engine()
        assertTrue(e.restore(TimerSnapshot(TimerMode.COUNTDOWN, 60_000, 50_000, true, 0), wallNowMs = 20_000))
        assertFalse(e.restore(TimerSnapshot(TimerMode.COUNTDOWN, 60_000, 10_000, true, 0), wallNowMs = 5_000))
        assertFalse(e.restore(TimerSnapshot(TimerMode.COUNTDOWN, 60_000, 50_000, false, 0), wallNowMs = 999_000))
        assertFalse(e.restore(TimerSnapshot(TimerMode.STOPWATCH, 0, 50_000, true, 0), wallNowMs = 20_000))
    }

    // ---- ArtColors derivation (pure) ----
    @Test fun backgroundIsDarkerThanBaseAndBottomDarkerThanTop() {
        val base = Color(0xFF8844AA)
        val c = deriveArtColors(base, base)
        assertTrue(c.top.luminance() < base.luminance())
        assertTrue(c.bottom.luminance() < c.top.luminance())
    }

    @Test fun accentIsBrightAndKeepsItsHue() {
        val source = Color(0xFF221133) // very dark purple
        val accent = deriveArtColors(source, source).accent
        assertTrue(accent.luminance() >= 0.3f)
        val (h1, _, _) = rgbToHsl(source); val (h2, s2, l2) = rgbToHsl(accent)
        assertEquals(h1, h2, 3f)
        assertTrue(s2 >= 0.5f); assertEquals(0.7f, l2, 0.02f)
    }

    @Test fun darkBackgroundIsTintedNotBlackWhenArtHasColor() {
        val c = deriveArtColors(Color(0xFF2A4A1A), Color(0xFF2A4A1A)) // dark forest green
        val (_, s, l) = rgbToHsl(c.top)
        assertTrue(s >= 0.25f); assertEquals(0.24f, l, 0.02f)
    }

    @Test fun greysStayNeutral() {
        val grey = Color(0xFF555555)
        val c = deriveArtColors(grey, grey)
        for (col in listOf(c.top, c.bottom, c.accent)) {
            assertEquals(col.red, col.green, 0.01f); assertEquals(col.green, col.blue, 0.01f)
        }
        assertTrue(c.accent.luminance() > 0.5f)
    }

    @Test fun hslRoundTrips() {
        for (argb in listOf(0xFFCC3355, 0xFF33CC88, 0xFF3355CC, 0xFFEEEE22, 0xFF808080)) {
            val c = Color(argb)
            val (h, s, l) = rgbToHsl(c)
            val back = hslToColor(h, s, l)
            assertEquals(c.red, back.red, 0.01f); assertEquals(c.green, back.green, 0.01f); assertEquals(c.blue, back.blue, 0.01f)
        }
    }
}
