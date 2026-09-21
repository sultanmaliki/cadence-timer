package dev.fitnesstimer.timer

import android.content.Context
import androidx.compose.ui.test.advanceEventTime
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fitnesstimer.ui.MainScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end timer flows on a device: the REAL MainScreen (gestures + engine + persistence + alarm
 * sync), driven by touches. The screen text is drawn on a canvas, so assertions read the shared
 * [AppTimer] engine, which is what the text is rendered from.
 */
@RunWith(AndroidJUnit4::class)
class TimerFlowInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    private val engine get() = AppTimer.engine
    private fun elapsedMs() = engine.snapshot(System.currentTimeMillis()).elapsedMs

    @Before fun setUp() {
        AppTimer.init(ApplicationProvider.getApplicationContext<Context>())
        engine.switchToStopwatch()          // stopped, zero, stopwatch mode
        rule.setContent { MainScreen() }
        rule.waitForIdle()
    }

    private fun tapCenter() {
        rule.onRoot().performTouchInput { down(0, center); advanceEventTime(40); up(0) }
        rule.waitForIdle()
    }

    /** Lets real time and Compose's virtual clock pass TOGETHER, in small steps, like a real finger held down. */
    private fun waitReal(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(50L, left)
            Thread.sleep(step); rule.mainClock.advanceTimeBy(step)
            left -= step
        }
        rule.waitForIdle()
    }

    /** Presses the centre and holds it for [ms] of real time, then releases. */
    private fun holdCenter(ms: Long) {
        rule.onRoot().performTouchInput { down(0, center) }
        waitReal(ms)
        rule.onRoot().performTouchInput { up(0) }
        rule.waitForIdle()
    }

    @Test fun tappingTheCentreStartsPausesAndResumesTheStopwatch() {
        assertFalse(engine.isRunning)
        tapCenter(); assertTrue("started", engine.isRunning)
        waitReal(600)
        tapCenter(); assertFalse("paused", engine.isRunning)
        val paused = elapsedMs()
        assertTrue("counted while running: $paused", paused in 450..2500)
        Thread.sleep(500)
        assertEquals("frozen while paused", paused, elapsedMs())
        tapCenter(); assertTrue("resumed", engine.isRunning)
        Thread.sleep(500)
        tapCenter()
        assertTrue("resume continues from the paused total", elapsedMs() > paused + 350)
    }

    @Test fun holdingWhilePausedResetsToZero() {
        tapCenter(); Thread.sleep(400); tapCenter()          // run a little, then pause
        assertTrue(elapsedMs() > 200)
        holdCenter(1000)
        assertEquals("reset to zero", 0L, elapsedMs())
        assertFalse(engine.isRunning)
    }

    @Test fun holdingWhileRunningDoesNotReset() {
        tapCenter()                                          // running
        Thread.sleep(300)
        rule.onRoot().performTouchInput { down(0, center) }  // hold is only for a paused timer
        waitReal(1000)
        rule.onRoot().performTouchInput { up(0) }
        rule.waitForIdle()
        assertTrue("timer kept its time", elapsedMs() > 900)
    }

    @Test fun countdownRunsToZeroAndStops() {
        engine.switchToCountdown(2_000)
        tapCenter(); assertTrue(engine.isRunning)
        waitReal(2_500)                                       // the UI loop notices on the virtual clock
        assertFalse("stopped at zero", engine.isRunning)
        assertTrue(engine.isCountdownFinished)
        assertEquals(0L, engine.displayMs)
    }

    @Test fun aFinishedCountdownIsRestartedOnlyAfterAReset() {
        engine.switchToCountdown(1_000)
        tapCenter(); waitReal(1_400)
        assertTrue(engine.isCountdownFinished)
        tapCenter()
        assertFalse("cannot restart a finished countdown", engine.isRunning)
        holdCenter(1000)                                     // reset (paused + hold)
        assertFalse(engine.isCountdownFinished)
        tapCenter(); assertTrue("runs again after reset", engine.isRunning)
        engine.switchToStopwatch()
    }

    @Test fun rapidTapsEndInTheRightState() {
        repeat(21) { tapCenter() }                           // odd number of taps -> running
        assertTrue(engine.isRunning)
        tapCenter(); assertFalse(engine.isRunning)
    }

    @Test fun switchingModeWhileRunningStopsAndZeroesIt() {
        tapCenter(); Thread.sleep(300)
        engine.switchToCountdown(60_000)
        assertFalse(engine.isRunning); assertEquals(60_000L, engine.displayMs)
        engine.switchToStopwatch()
        assertEquals(0L, engine.displayMs)
    }

    @Test fun stateIsPersistedOnEveryTransition() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("timer_state", Context.MODE_PRIVATE)
        tapCenter()
        assertTrue("running is saved", prefs.getBoolean("running", false))
        tapCenter()
        assertFalse("paused is saved", prefs.getBoolean("running", true))
        assertTrue("elapsed is saved", prefs.getLong("elapsedMs", -1) >= 0)
    }
}
