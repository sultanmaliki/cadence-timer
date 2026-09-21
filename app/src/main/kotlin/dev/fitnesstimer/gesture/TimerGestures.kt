package dev.fitnesstimer.gesture

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Callbacks the gesture engine fires — PLAN.md section E's gesture table. */
class TimerGestureActions(
    val isPaused: () -> Boolean,
    val onTogglePause: () -> Unit,
    val onReset: () -> Unit,
    val onHoldProgress: (Float) -> Unit,
    val onSeekBack: () -> Unit,
    val onSeekForward: () -> Unit,
    val onScrub: (fractionDelta: Float) -> Unit,
    val onToggleMediaPlayPause: () -> Unit,
    val onSkipNext: () -> Unit,
    val onSkipPrevious: () -> Unit,
    val onTimerModePicker: () -> Unit,
    val onMediaSourcePicker: () -> Unit,
    val onScrubEnd: () -> Unit = {},
)

private const val CORNER_ZONE_DP = 72
private const val TOUCH_SLOP_DP = 12
private const val DOUBLE_TAP_WINDOW_MS = 300L
private const val HOLD_RESET_MS = 800L
private const val SWIPE_MIN_DP = 56
private const val POLL_MS = 16L
// Brief grace period before a small first-finger movement is allowed to
// commit the gesture to DRAG. Without this, natural finger jitter while
// positioning a SECOND finger for a two-finger tap/swipe can exceed touch
// slop first, locking in DRAG mode (mode != UNDECIDED gates the two-finger
// upgrade check) before the second touch is ever recognized. 100ms is
// imperceptible as added latency for a deliberate single-finger drag.
private const val TWO_FINGER_GRACE_MS = 100L

private enum class Mode { UNDECIDED, DRAG, TWO_FINGER }

/**
 * PLAN.md section E — the gesture state machine, as ONE unified
 * pointerInput consumer for the whole screen. Deliberately not several
 * overlapping sibling detectors (e.g. a separate detectTapGestures per
 * zone): with overlapping Composables, Compose's pass order between
 * siblings governs who sees an event first, which is exactly the kind of
 * implicit priority PLAN.md's disp-2/disp-3 findings warned about. Doing it
 * as one state machine keeps priority order explicit in code instead.
 *
 * Zone model (screen split by x, top corners carved out first):
 *  - top-left / top-right squares -> timer mode / media source pickers.
 *  - left third                   -> double-tap only -> seek -10s.
 *  - right third                  -> double-tap only -> seek +10s.
 *  - center third                 -> tap -> start/pause; hold (paused only) -> reset.
 *  - anywhere (not zone-restricted): drag -> scrub; 2-finger tap -> media
 *    play/pause; 2-finger horizontal swipe -> prev/next. These pre-empt the
 *    zone-specific tap/hold logic the instant they're detected.
 *
 * Known simplification (flagged, not silently dropped): two-finger swipe
 * direction is read from the first-detected pointer's movement once in
 * TWO_FINGER mode, not a true two-pointer average. In practice both fingers
 * move together in a deliberate swipe, so this is a reasonable v1
 * approximation — revisit if on-device testing shows it misreads.
 */
fun Modifier.timerGestures(actions: TimerGestureActions): Modifier = this.pointerInput(actions) {
    val cornerPx = CORNER_ZONE_DP.dp.toPx()
    val touchSlopPx = TOUCH_SLOP_DP.dp.toPx()
    val swipeMinPx = SWIPE_MIN_DP.dp.toPx()

    // Correlates a tap in a double-tap zone with the PREVIOUS tap in the same
    // zone. Lives outside awaitEachGesture (which only covers one down-to-up
    // cycle) since a double-tap spans two full cycles.
    var pendingTapZone = 0
    var pendingTapUpMs = Long.MIN_VALUE / 2

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()

        val inCorner = down.position.y < cornerPx &&
            (down.position.x < cornerPx || down.position.x > width - cornerPx)
        if (inCorner) {
            val isLeft = down.position.x < cornerPx
            if (waitForUpOrCancellation() != null) {
                if (isLeft) actions.onTimerModePicker() else actions.onMediaSourcePicker()
            }
            return@awaitEachGesture
        }

        val zone = when {
            down.position.x < width / 3f -> -1
            down.position.x > width * 2f / 3f -> 1
            else -> 0
        }
        val holdEligible = zone == 0 && actions.isPaused()
        val downTimeMs = SystemClock.uptimeMillis()

        var mode = Mode.UNDECIDED
        var lastX = down.position.x
        val twoFinger = TwoFingerTracker()
        var resetFired = false
        var movedPastSlop = false // a finger that travelled is not a tap, even if it was too quick to become a drag

        // A plain concurrent coroutine (coroutineScope { launch { ... } })
        // isn't legal here — AwaitPointerEventScope is a restricted suspend
        // scope that only permits calling its own suspend members. So the
        // reset ring's progress is driven by polling with a short timeout
        // instead of a separate ticking coroutine: when nothing arrives
        // within POLL_MS we still get a chance to check elapsed hold time,
        // which is what keeps the ring animating while the finger sits
        // still. Once hold-eligibility is off the table (moved, second
        // finger, or not paused), we go back to a plain blocking wait —
        // no need to poll when there's nothing to animate.
        while (true) {
            val elapsedSinceDown = SystemClock.uptimeMillis() - downTimeMs
            val undecided = mode == Mode.UNDECIDED && !resetFired
            // Poll (rather than block indefinitely) whenever there's a
            // time-based condition to re-check even with no new pointer
            // activity: the hold-reset timeout, or the two-finger grace
            // window below.
            val shouldPoll = undecided && (holdEligible || elapsedSinceDown < TWO_FINGER_GRACE_MS)
            val event = if (shouldPoll) {
                withTimeoutOrNull(POLL_MS) { awaitPointerEvent(PointerEventPass.Main) }
            } else {
                awaitPointerEvent(PointerEventPass.Main)
            }

            if (event == null) {
                // Timed out with no new pointer activity.
                if (holdEligible && undecided) {
                    val elapsed = SystemClock.uptimeMillis() - downTimeMs
                    if (elapsed >= HOLD_RESET_MS) {
                        actions.onHoldProgress(1f)
                        resetFired = true
                        actions.onReset()
                        break
                    }
                    actions.onHoldProgress(elapsed / HOLD_RESET_MS.toFloat())
                }
                continue
            }

            val pressed = event.changes.filter { it.pressed }

            if (mode == Mode.UNDECIDED && pressed.size >= 2 && !resetFired) {
                mode = Mode.TWO_FINGER
                actions.onHoldProgress(0f)
            }

            when (mode) {
                Mode.UNDECIDED -> {
                    val primary = event.changes.firstOrNull { it.id == down.id }
                    if (primary != null) {
                        val dx = primary.position.x - down.position.x
                        val dy = primary.position.y - down.position.y
                        val pastGrace = SystemClock.uptimeMillis() - downTimeMs >= TWO_FINGER_GRACE_MS
                        if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                            movedPastSlop = true
                            if (pastGrace) {
                                mode = Mode.DRAG
                                actions.onHoldProgress(0f)
                                lastX = primary.position.x
                            }
                        }
                    }
                }
                Mode.DRAG -> {
                    val primary = event.changes.firstOrNull { it.id == down.id }
                    if (primary != null && primary.pressed) {
                        val delta = primary.position.x - lastX
                        lastX = primary.position.x
                        actions.onScrub(delta / width)
                        primary.consume()
                    }
                }
                Mode.TWO_FINGER -> {
                    // Tracked per pointer ID, frozen when a finger lifts (see TwoFingerTracker).
                    for (c in event.changes) twoFinger.update(c.id.value, c.position.x, c.position.y, c.pressed)
                }
            }

            if (pressed.isEmpty()) break
        }

        when (mode) {
            Mode.TWO_FINGER -> when (twoFinger.result(swipeMinPx)) {
                TwoFingerResult.PREVIOUS -> actions.onSkipPrevious()
                TwoFingerResult.NEXT -> actions.onSkipNext()
                TwoFingerResult.TAP -> actions.onToggleMediaPlayPause()
            }
            Mode.DRAG -> actions.onScrubEnd() // moves were streamed via onScrub; flush any throttled tail
            Mode.UNDECIDED -> if (!resetFired && !movedPastSlop) {
                val upTimeMs = SystemClock.uptimeMillis()
                when (zone) {
                    -1, 1 -> {
                        val isDoubleTap = pendingTapZone == zone &&
                            (upTimeMs - pendingTapUpMs) < DOUBLE_TAP_WINDOW_MS
                        if (isDoubleTap) {
                            if (zone == -1) actions.onSeekBack() else actions.onSeekForward()
                            pendingTapZone = 0
                            pendingTapUpMs = Long.MIN_VALUE / 2
                        } else {
                            pendingTapZone = zone
                            pendingTapUpMs = upTimeMs
                        }
                    }
                    else -> actions.onTogglePause()
                }
            }
        }
    }
}
