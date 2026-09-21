package dev.fitnesstimer.timer

import android.content.Context
import android.util.Log

/**
 * Single shared [TimerEngine] for the whole app. Needed because both the
 * Compose UI and `media.PlaybackService`'s notification "toggle timer"
 * button must read/mutate the same timer state, and the service isn't part
 * of the Composition (can't just `remember { TimerEngine() }`).
 *
 * A plain object, not a ViewModel/DI graph — deliberately the simplest
 * thing that works for a personal, single-window, single-process app
 * (matches the project's "don't over-engineer" standard from the start).
 *
 * State is persisted to SharedPreferences on every transition so a timer
 * survives the process being killed (aggressive on HyperOS). [init] must be
 * called from every entry point (Activity and Service) — it is idempotent.
 */
object AppTimer {
    val engine = TimerEngine()
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.applicationContext.getSharedPreferences("timer_state", Context.MODE_PRIVATE)
        try {
            if (prefs.contains("mode")) {
                val snap = TimerSnapshot(
                    mode = TimerMode.valueOf(prefs.getString("mode", null) ?: "STOPWATCH"),
                    targetMs = prefs.getLong("targetMs", 0L),
                    elapsedMs = prefs.getLong("elapsedMs", 0L),
                    running = prefs.getBoolean("running", false),
                    savedWallMs = prefs.getLong("savedWallMs", 0L),
                )
                engine.restore(snap, System.currentTimeMillis())
                Log.d("FitnessTimer", "[timer] restored $snap -> display=${engine.displayMs}ms running=${engine.isRunning}")
            }
        } catch (e: Exception) {
            Log.w("FitnessTimer", "[timer] ignoring unreadable saved state", e)
        }
        engine.onStateChanged = {
            val snap = engine.snapshot(System.currentTimeMillis())
            prefs.edit()
                .putString("mode", snap.mode.name)
                .putLong("targetMs", snap.targetMs)
                .putLong("elapsedMs", snap.elapsedMs)
                .putBoolean("running", snap.running)
                .putLong("savedWallMs", snap.savedWallMs)
                .apply()
        }
    }
}
