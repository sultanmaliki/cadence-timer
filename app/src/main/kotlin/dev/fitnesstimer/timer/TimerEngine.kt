package dev.fitnesstimer.timer

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class TimerMode { STOPWATCH, COUNTDOWN }

/**
 * PLAN.md section F. Basis is [SystemClock.elapsedRealtime], monotonic
 * across deep sleep; reboot/process-death persistence isn't wired up yet.
 * Countdown-complete while backgrounded (exact alarm + notification, per
 * PLAN.md section F) also isn't built — completion is only detected while
 * the app is in the foreground and actively ticking (see MainScreen).
 */
class TimerEngine {
    var isRunning by mutableStateOf(false)
        private set

    var mode by mutableStateOf(TimerMode.STOPWATCH)
        private set

    /** Only meaningful in COUNTDOWN mode. */
    var countdownTargetMs by mutableLongStateOf(0L)
        private set

    /** Advanced by the ticking effect in the composable while running, to drive recomposition. */
    var nowElapsedRealtime by mutableLongStateOf(SystemClock.elapsedRealtime())

    private var startElapsedRealtime = 0L
    private var accumulatedMs = 0L

    private val runningMs: Long
        get() = accumulatedMs + if (isRunning) (nowElapsedRealtime - startElapsedRealtime) else 0L

    /** What the UI should show: counts up for STOPWATCH, down to zero for COUNTDOWN. */
    val displayMs: Long
        get() = when (mode) {
            TimerMode.STOPWATCH -> runningMs
            TimerMode.COUNTDOWN -> (countdownTargetMs - runningMs).coerceAtLeast(0L)
        }

    val isCountdownFinished: Boolean
        get() = mode == TimerMode.COUNTDOWN && runningMs >= countdownTargetMs

    fun toggleStartPause() {
        if (isRunning) pause() else start()
    }

    fun start() {
        if (isRunning || isCountdownFinished) return
        startElapsedRealtime = SystemClock.elapsedRealtime()
        nowElapsedRealtime = startElapsedRealtime
        isRunning = true
    }

    fun pause() {
        if (!isRunning) return
        accumulatedMs += SystemClock.elapsedRealtime() - startElapsedRealtime
        isRunning = false
    }

    /** Only invoked while paused — the hold-to-reset gesture is paused-only by design (section E). */
    fun reset() {
        isRunning = false
        accumulatedMs = 0L
    }

    fun switchToStopwatch() {
        isRunning = false
        accumulatedMs = 0L
        mode = TimerMode.STOPWATCH
    }

    fun switchToCountdown(targetMs: Long) {
        isRunning = false
        accumulatedMs = 0L
        mode = TimerMode.COUNTDOWN
        countdownTargetMs = targetMs
    }
}

fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
