package dev.fitnesstimer.nowplaying

import android.media.session.PlaybackState

/** The few facts selection needs about one active session (keeps the logic pure/testable). */
data class SessionCandidate(
    val id: String,
    val packageName: String,
    val playbackState: Int,
)

/**
 * Chooses which of the phone's active media sessions to show.
 *
 * Learned from the on-device probe (PLAN.md N.4): the stack also contains a
 * stale STOPPED session left by the YouTube app, and our own PlaybackService
 * session. So: our own package is always excluded, and only playing /
 * buffering / paused sessions are eligible. Ties keep the input order, which
 * getActiveSessions() returns in priority order (assumed, per its docs).
 */
object SessionSelector {
    fun select(
        candidates: List<SessionCandidate>,
        ownPackage: String,
        preferredPackage: String? = null,
    ): SessionCandidate? {
        fun rank(state: Int): Int = when {
            isPlayingState(state) -> 3
            state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING -> 2
            state == PlaybackState.STATE_PAUSED -> 1
            else -> 0
        }
        return candidates
            .filter { it.packageName != ownPackage && rank(it.playbackState) > 0 }
            .maxWithOrNull(
                compareBy<SessionCandidate> { rank(it.playbackState) }
                    .thenBy { if (it.packageName == preferredPackage) 1 else 0 }
            )
    }
}
