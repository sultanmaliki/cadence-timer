package dev.fitnesstimer.timer

/**
 * Single shared [TimerEngine] for the whole app. Needed because both the
 * Compose UI and `media.PlaybackService`'s notification "toggle timer"
 * button must read/mutate the same timer state, and the service isn't part
 * of the Composition (can't just `remember { TimerEngine() }`).
 *
 * A plain object, not a ViewModel/DI graph — deliberately the simplest
 * thing that works for a personal, single-window, single-process app
 * (matches the project's "don't over-engineer" standard from the start).
 * Revisit if the app ever needs multiple windows/instances.
 */
object AppTimer {
    val engine = TimerEngine()
}
