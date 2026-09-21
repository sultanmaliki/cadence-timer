package dev.fitnesstimer.gesture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.advanceEventTime
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the REAL gesture state machine on a device/emulator. Compose's test framework dispatches
 * pointer events straight into the view hierarchy, so unlike `adb shell input` it works on phones
 * (e.g. MIUI) that deny INJECT_EVENTS to the shell.
 */
@RunWith(AndroidJUnit4::class)
class TimerGesturesInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    private class Counts {
        var timerToggle = 0; var mediaToggle = 0; var next = 0; var prev = 0
        var seekBack = 0; var seekForward = 0; var scrubs = 0; var topLeft = 0; var topRight = 0
        var resets = 0; var paused = false; var maxRing = 0f; var lastRing = -1f
    }

    private fun show(c: Counts) {
        val actions = TimerGestureActions(
            isPaused = { c.paused }, // false by default: keeps most tests about taps/swipes, not the reset ring
            onTogglePause = { c.timerToggle++ },
            onReset = { c.resets++ },
            onHoldProgress = { c.maxRing = maxOf(c.maxRing, it); c.lastRing = it },
            onSeekBack = { c.seekBack++ },
            onSeekForward = { c.seekForward++ },
            onScrub = { c.scrubs++ },
            onToggleMediaPlayPause = { c.mediaToggle++ },
            onSkipNext = { c.next++ },
            onSkipPrevious = { c.prev++ },
            onTimerModePicker = { c.topLeft++ },
            onMediaSourcePicker = { c.topRight++ },
        )
        rule.setContent { Box(Modifier.fillMaxSize().timerGestures(actions)) }
    }

    /** Real time and Compose's virtual clock pass together in small steps, like a finger held down. */
    private fun waitReal(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(50L, left)
            Thread.sleep(step); rule.mainClock.advanceTimeBy(step)
            left -= step
        }
        rule.waitForIdle()
    }

    private val a = Offset(300f, 900f)
    private val b = Offset(700f, 900f)

    @Test fun twoFingerTapTogglesMedia() {
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput { down(0, a); down(1, b); advanceEventTime(40); up(0); up(1) }
        rule.waitForIdle()
        assertEquals(1, c.mediaToggle); assertEquals(0, c.next + c.prev)
    }

    @Test fun liftingOneFingerFirstIsStillATap() {
        // The reported bug: a tiny lift-order difference used to read as a swipe and change the song.
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput {
            down(0, a); down(1, b)
            advanceEventTime(40); up(0)          // left finger up first
            advanceEventTime(60); up(1)          // right finger up later
        }
        rule.waitForIdle()
        assertEquals("media toggles", 1, c.mediaToggle); assertEquals("skips", 0, c.next + c.prev)
    }

    @Test fun rightFingerLiftingFirstIsStillATap() {
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput { down(0, a); down(1, b); advanceEventTime(40); up(1); advanceEventTime(60); up(0) }
        rule.waitForIdle()
        assertEquals(1, c.mediaToggle); assertEquals(0, c.next + c.prev)
    }

    @Test fun tinyJitterDuringATwoFingerTapIsStillATap() {
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput {
            down(0, a); down(1, b)
            repeat(4) { moveBy(0, Offset(1f, 1f)); moveBy(1, Offset(1f, 0f)) }
            advanceEventTime(30); up(0); up(1)
        }
        rule.waitForIdle()
        assertEquals(1, c.mediaToggle); assertEquals(0, c.next + c.prev)
    }

    @Test fun deliberateTwoFingerSwipeLeftSkipsToNext() {
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput {
            down(0, Offset(700f, 900f)); down(1, Offset(900f, 900f))
            repeat(12) { moveBy(0, Offset(-40f, 0f)); moveBy(1, Offset(-40f, 0f)) }
            up(0); up(1)
        }
        rule.waitForIdle()
        assertEquals("next", 1, c.next); assertEquals("prev", 0, c.prev); assertEquals("toggle", 0, c.mediaToggle)
    }

    @Test fun deliberateTwoFingerSwipeRightGoesToPrevious() {
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput {
            down(0, Offset(300f, 900f)); down(1, Offset(500f, 900f))
            repeat(12) { moveBy(0, Offset(40f, 0f)); moveBy(1, Offset(40f, 0f)) }
            up(0); up(1)
        }
        rule.waitForIdle()
        assertEquals("prev", 1, c.prev); assertEquals("next", 0, c.next); assertEquals("toggle", 0, c.mediaToggle)
    }

    @Test fun centerSingleTapTogglesTheTimerNotTheMedia() {
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput { down(0, Offset(500f, 1200f)); advanceEventTime(50); up(0) }
        rule.waitForIdle()
        assertEquals(1, c.timerToggle); assertEquals(0, c.mediaToggle + c.next + c.prev)
    }

    @Test fun topLeftAndTopRightCornersOpenTheirMenus() {
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput { down(0, Offset(30f, 30f)); advanceEventTime(50); up(0) }
        rule.waitForIdle()
        rule.onRoot().performTouchInput { down(0, Offset(width - 30f, 30f)); advanceEventTime(50); up(0) }
        rule.waitForIdle()
        assertEquals(1, c.topLeft); assertEquals(1, c.topRight); assertEquals(0, c.timerToggle)
    }

    @Test fun horizontalDragScrubsAndDoesNotSkip() {
        val c = Counts(); show(c)
        // Separate blocks: Compose's test framework delivers a block's events together when it ends, so a
        // real pause between finger-down and movement (a real drag outlasts the engine's 100 ms grace
        // window) needs the pause OUTSIDE the block.
        rule.onRoot().performTouchInput { down(0, Offset(400f, 1200f)) }
        Thread.sleep(200)
        rule.onRoot().performTouchInput { repeat(10) { moveBy(0, Offset(30f, 0f)) } }
        rule.onRoot().performTouchInput { up(0) }
        rule.waitForIdle()
        assert(c.scrubs > 0) { "expected scrub callbacks, got ${c.scrubs}" }
        assertEquals(0, c.next + c.prev + c.mediaToggle + c.timerToggle)
    }

    @Test fun aQuickFlickInTheCenterDoesNotToggleTheTimer() {
        // A fast swipe (finished inside the 100 ms grace) used to fall through as a "tap" and
        // start/pause the timer even though the finger had clearly travelled.
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput {
            down(0, Offset(550f, 1200f))          // centre third (x 406..813 on this screen), where a tap toggles the timer
            repeat(6) { moveBy(0, Offset(30f, 0f)) }
            up(0)
        }
        rule.waitForIdle()
        assertEquals("timer toggles", 0, c.timerToggle); assertEquals(0, c.mediaToggle + c.next + c.prev)
    }

    @Test fun doubleTapLeftSeeksBack() {
        val c = Counts(); show(c)
        repeat(2) { rule.onRoot().performTouchInput { down(0, Offset(150f, 1200f)); advanceEventTime(30); up(0) }; Thread.sleep(60) }
        rule.waitForIdle()
        assertEquals("seekBack", 1, c.seekBack); assertEquals(0, c.seekForward + c.timerToggle)
    }

    @Test fun doubleTapRightSeeksForward() {
        val c = Counts(); show(c)
        repeat(2) { rule.onRoot().performTouchInput { down(0, Offset(1100f, 1200f)); advanceEventTime(30); up(0) }; Thread.sleep(60) }
        rule.waitForIdle()
        assertEquals("seekForward", 1, c.seekForward); assertEquals(0, c.seekBack + c.timerToggle)
    }

    @Test fun singleTapInASeekZoneDoesNothing() {
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput { down(0, Offset(150f, 1200f)); advanceEventTime(30); up(0) }
        rule.waitForIdle()
        assertEquals(0, c.seekBack + c.seekForward + c.timerToggle + c.mediaToggle)
    }

    @Test fun slowTwoTapsInASeekZoneAreNotADoubleTap() {
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput { down(0, Offset(150f, 1200f)); advanceEventTime(30); up(0) }
        Thread.sleep(450)                        // longer than the 300 ms double-tap window
        rule.onRoot().performTouchInput { down(0, Offset(150f, 1200f)); advanceEventTime(30); up(0) }
        rule.waitForIdle()
        assertEquals(0, c.seekBack)
    }

    @Test fun holdingTheTimerWhilePausedResetsIt() {
        val c = Counts(); c.paused = true; show(c)
        rule.onRoot().performTouchInput { down(0, Offset(550f, 1200f)) }
        // The engine measures the hold in REAL time (SystemClock) but wakes on coroutine timeouts, which run on
        // Compose's virtual test clock: pass real time AND tick the virtual clock.
        waitReal(1000)
        rule.onRoot().performTouchInput { up(0) }
        rule.waitForIdle()
        assertEquals("resets", 1, c.resets); assertEquals("timer toggles", 0, c.timerToggle)
    }

    @Test fun releasingTheHoldEarlyDoesNotReset() {
        val c = Counts(); c.paused = true; show(c)
        rule.onRoot().performTouchInput { down(0, Offset(550f, 1200f)) }
        waitReal(300)
        rule.onRoot().performTouchInput { up(0) }
        rule.waitForIdle()
        assertEquals("resets", 0, c.resets); assertEquals("timer toggles", 1, c.timerToggle)   // a normal tap
    }

    @Test fun aSecondFingerDuringTheHoldCancelsTheReset() {
        val c = Counts(); c.paused = true; show(c)
        rule.onRoot().performTouchInput { down(0, Offset(550f, 1200f)) }
        waitReal(300)
        rule.onRoot().performTouchInput { down(1, Offset(750f, 1200f)) }
        waitReal(900)                            // well past the 0.8 s hold
        rule.onRoot().performTouchInput { up(0); up(1) }
        rule.waitForIdle()
        assertEquals("resets", 0, c.resets); assertEquals("media toggles", 1, c.mediaToggle)
    }

    @Test fun threeFingerTapIsStillATapNotASwipe() {
        val c = Counts(); show(c)
        rule.onRoot().performTouchInput { down(0, Offset(200f, 900f)); down(1, Offset(600f, 900f)); down(2, Offset(1000f, 900f)); advanceEventTime(40); up(2); up(0); up(1) }
        rule.waitForIdle()
        assertEquals(1, c.mediaToggle); assertEquals(0, c.next + c.prev)
    }

    @Test fun earlyReleaseClearsTheRingInsteadOfLeavingItStuck() {
        // The reported bug: a partial ring stayed on screen after releasing before the hold completed.
        val c = Counts(); c.paused = true; show(c)
        rule.onRoot().performTouchInput { down(0, Offset(550f, 1200f)) }
        waitReal(450)                                             // ring partly charged
        rule.onRoot().performTouchInput { up(0) }
        rule.waitForIdle()
        assertEquals("ring ended at", 0f, c.lastRing, 0f)
        assertEquals("resets", 0, c.resets)
    }

    @Test fun completedResetShowsFullThenClearsWhenTheFingerLifts() {
        val c = Counts(); c.paused = true; show(c)
        rule.onRoot().performTouchInput { down(0, Offset(550f, 1200f)) }
        waitReal(1000)
        assertEquals("resets", 1, c.resets); assertEquals("ring was full", 1f, c.maxRing, 0f)
        assertEquals("ring stays full while the finger is down", 1f, c.lastRing, 0f)
        rule.onRoot().performTouchInput { up(0) }
        rule.waitForIdle()
        assertEquals("ring cleared after lift", 0f, c.lastRing, 0f)
    }

    @Test fun ringIsClearedAfterAnOrdinaryTapWhilePaused() {
        val c = Counts(); c.paused = true; show(c)
        rule.onRoot().performTouchInput { down(0, Offset(550f, 1200f)); advanceEventTime(40); up(0) }
        rule.waitForIdle()
        assertEquals(0f, c.lastRing, 0f); assertEquals(1, c.timerToggle)
    }

    @Test fun ringIsClearedWhenAFingerDragsAwayFromAHold() {
        val c = Counts(); c.paused = true; show(c)
        rule.onRoot().performTouchInput { down(0, Offset(550f, 1200f)) }
        waitReal(300)
        rule.onRoot().performTouchInput { repeat(6) { moveBy(0, Offset(30f, 0f)) } }
        rule.onRoot().performTouchInput { up(0) }
        rule.waitForIdle()
        assertEquals(0f, c.lastRing, 0f); assertEquals(0, c.resets)
    }
}
