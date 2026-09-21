package dev.fitnesstimer.timer

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class TimerMode { STOPWATCH, COUNTDOWN }

/**
 * Persisted timer state. [elapsedMs] is the running total at save time;
 * [savedWallMs] (wall clock — elapsedRealtime resets on reboot) lets a
 * restore add the time that passed while the process was dead.
 */
data class TimerSnapshot(
    val mode: TimerMode,
    val targetMs: Long,
    val elapsedMs: Long,
    val running: Boolean,
    val savedWallMs: Long,
)

/** A "running" timer older than this is restored paused, not credited days of time. */
const val MAX_RESTORE_GAP_MS = 12L * 60 * 60 * 1000

/**
 * PLAN.md section F. Basis is [SystemClock.elapsedRealtime], monotonic
 * across deep sleep; reboot/process-death persistence isn't wired up yet.
 * Countdown-complete while backgrounded (exact alarm + notification, per
 * PLAN.md section F) also isn't built — completion is only detected while
 * the app is in the foreground and actively ticking (see MainScreen).
 */
class TimerEngine(private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {
    private var runningState by mutableStateOf(false)

    val isRunning: Boolean get() = runningState

    private fun setRunning(value: Boolean) {
        if (runningState == value) return
        runningState = value
        onRunningChanged?.invoke(value)
    }

    /**
     * Plain callback (not Compose snapshot state) so the playback service can
     * refresh its notification button even when no UI is composing.
     */
    var onRunningChanged: ((Boolean) -> Unit)? = null

    var mode by mutableStateOf(TimerMode.STOPWATCH)
        private set

    /** Only meaningful in COUNTDOWN mode. */
    var countdownTargetMs by mutableLongStateOf(0L)
        private set

    /** Advanced by the ticking effect in the composable while running, to drive recomposition. */
    var nowElapsedRealtime by mutableLongStateOf(clock())

    /** Fired after every user-visible state change (start/pause/reset/mode); used for persistence. */
    var onStateChanged: (() -> Unit)? = null

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

    /**
     * How long until [displayMs] next changes what [formatElapsed] shows
     * (or, for a countdown, until it finishes) — lets the UI tick once per
     * displayed second instead of polling at 10Hz. Always in 1..1000.
     */
    fun msUntilDisplayChange(): Long {
        val d = displayMs
        val ms = when (mode) {
            TimerMode.STOPWATCH -> 1000L - (d % 1000L)
            TimerMode.COUNTDOWN -> minOf((d % 1000L) + 1L, maxOf(d, 1L))
        }
        return ms.coerceIn(1L, 1000L)
    }

    fun snapshot(wallNowMs: Long): TimerSnapshot {
        val live = if (isRunning) clock() - startElapsedRealtime else 0L
        return TimerSnapshot(mode, countdownTargetMs, accumulatedMs + live, isRunning, wallNowMs)
    }

    /** Restores without firing callbacks. Silent: not a user-visible transition. */
    fun restore(snap: TimerSnapshot, wallNowMs: Long) {
        val gap = wallNowMs - snap.savedWallMs
        val credit = snap.running && gap in 0..MAX_RESTORE_GAP_MS
        val elapsed = snap.elapsedMs.coerceAtLeast(0L) + if (credit) gap else 0L
        mode = snap.mode
        countdownTargetMs = snap.targetMs.coerceAtLeast(0L)
        accumulatedMs = elapsed
        nowElapsedRealtime = clock()
        val finished = mode == TimerMode.COUNTDOWN && elapsed >= countdownTargetMs
        if (credit && !finished) {
            startElapsedRealtime = clock()
            runningState = true
        } else {
            runningState = false
        }
    }

    fun toggleStartPause() {
        if (isRunning) pause() else start()
    }

    fun start() {
        if (isRunning || isCountdownFinished) return
        startElapsedRealtime = clock()
        nowElapsedRealtime = startElapsedRealtime
        setRunning(true)
        onStateChanged?.invoke()
    }

    fun pause() {
        if (!isRunning) return
        accumulatedMs += clock() - startElapsedRealtime
        setRunning(false)
        onStateChanged?.invoke()
    }

    /** Only invoked while paused — the hold-to-reset gesture is paused-only by design (section E). */
    fun reset() {
        setRunning(false)
        accumulatedMs = 0L
        onStateChanged?.invoke()
    }

    fun switchToStopwatch() {
        setRunning(false)
        accumulatedMs = 0L
        mode = TimerMode.STOPWATCH
        onStateChanged?.invoke()
    }

    fun switchToCountdown(targetMs: Long) {
        setRunning(false)
        accumulatedMs = 0L
        mode = TimerMode.COUNTDOWN
        countdownTargetMs = targetMs.coerceAtLeast(0L)
        onStateChanged?.invoke()
    }
}

fun formatElapsed(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    // Locale.ROOT: the default locale would render Arabic-Indic digits on e.g. ar-SA phones.
    return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
}
