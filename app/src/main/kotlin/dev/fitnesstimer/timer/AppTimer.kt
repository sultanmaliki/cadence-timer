package dev.fitnesstimer.timer

import android.content.Context
import android.util.Log

/**
 * Single shared [TimerEngine] for the whole app. Needed because the Compose
 * UI, `media.PlaybackService`'s notification "toggle timer" button, and the
 * countdown alarm receivers must all read/mutate the same timer state, and
 * none of the latter are part of the Composition.
 *
 * A plain object, not a ViewModel/DI graph — deliberately the simplest
 * thing that works for a personal, single-window, single-process app
 * (matches the project's "don't over-engineer" standard from the start).
 *
 * State is persisted to SharedPreferences on every transition so a timer
 * survives the process being killed (aggressive on HyperOS), and every
 * transition re-syncs the countdown alarm. [init] must be called from every
 * entry point (Activity, Service, receivers) — it is idempotent.
 */
object AppTimer {
    val engine = TimerEngine()

    /** True while the Activity is resumed; the alarm skips its notification then. */
    @Volatile
    var uiVisible = false

    /** A running countdown reached zero while the process was dead (set by [init]). */
    @Volatile
    var completedWhileAway = false

    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences("timer_state", Context.MODE_PRIVATE)

        fun persistAndSync() {
            val snap = engine.snapshot(System.currentTimeMillis())
            prefs.edit()
                .putString("mode", snap.mode.name)
                .putLong("targetMs", snap.targetMs)
                .putLong("elapsedMs", snap.elapsedMs)
                .putBoolean("running", snap.running)
                .putLong("savedWallMs", snap.savedWallMs)
                .apply()
            CountdownAlarm.sync(ctx, snap)
        }

        try {
            if (prefs.contains("mode")) {
                val snap = TimerSnapshot(
                    mode = TimerMode.valueOf(prefs.getString("mode", null) ?: "STOPWATCH"),
                    targetMs = prefs.getLong("targetMs", 0L),
                    elapsedMs = prefs.getLong("elapsedMs", 0L),
                    running = prefs.getBoolean("running", false),
                    savedWallMs = prefs.getLong("savedWallMs", 0L),
                )
                completedWhileAway = engine.restore(snap, System.currentTimeMillis())
                Log.d("FitnessTimer", "[timer] restored $snap -> display=${engine.displayMs}ms running=${engine.isRunning} doneAway=$completedWhileAway")
            }
        } catch (e: Exception) {
            Log.w("FitnessTimer", "[timer] ignoring unreadable saved state", e)
        }
        engine.onStateChanged = { persistAndSync() }
        // Settle what a restore found (finished -> persist as stopped) and make
        // sure the alarm matches the restored state (also covers reboot).
        persistAndSync()
    }
}
