package dev.fitnesstimer.nowplaying

import android.graphics.Bitmap
import android.media.session.PlaybackState

/**
 * What another app's active media session is currently publishing
 * (PLAN.md section N). Position fields are the RAW values from the
 * session's PlaybackState: use [extrapolatePosition] to get "now".
 */
data class NowPlaying(
    val packageName: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    /** Largest metadata bitmap the app supplied, or null. Never fetched from a URI (no INTERNET). */
    val artwork: Bitmap?,
    /** The app set an artwork URI (we deliberately don't fetch remote images). */
    val hasArtworkUri: Boolean,
    /** -1 when unknown. */
    val durationMs: Long,
    /** A PlaybackState.STATE_* constant. */
    val playbackState: Int,
    /** Raw reported position (-1 unknown) and the elapsedRealtime it was reported at. */
    val positionMs: Long,
    val positionUpdateElapsedMs: Long,
    val speed: Float,
    /** PlaybackState.ACTION_* bitmask the app advertises. Advisory: the app may still ignore a command. */
    val actions: Long,
) {
    val isPlaying: Boolean get() = isPlayingState(playbackState)
    val canSkipNext: Boolean get() = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L
    val canSkipPrevious: Boolean get() = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L
    val canSeek: Boolean get() = actions and PlaybackState.ACTION_SEEK_TO != 0L
}

/** [accessGranted] is false until the user enables notification access for this app. */
data class NowPlayingState(
    val accessGranted: Boolean = false,
    val nowPlaying: NowPlaying? = null,
)

/** States in which the source app is actively producing audio. */
fun isPlayingState(state: Int): Boolean = when (state) {
    PlaybackState.STATE_PLAYING,
    PlaybackState.STATE_FAST_FORWARDING,
    PlaybackState.STATE_REWINDING,
    PlaybackState.STATE_SKIPPING_TO_NEXT,
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> true
    else -> false
}
