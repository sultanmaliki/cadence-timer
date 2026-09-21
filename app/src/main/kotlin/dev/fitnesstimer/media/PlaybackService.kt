package dev.fitnesstimer.media

import android.os.Bundle
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.fitnesstimer.timer.AppTimer

private const val CMD_TIMER_TOGGLE = "dev.fitnesstimer.TIMER_TOGGLE"

/**
 * PLAN.md sections D/G, finally built: the ExoPlayer + MediaSession live
 * here (not in a Composable) so playback survives backgrounding and the
 * system gets a real media notification with the standard play/pause/
 * next/prev transport — Media3 generates that automatically once a session
 * exists, no extra code needed for those four.
 *
 * The timer toggle is a custom session command surfaced as an extra button
 * on that same notification, reading/writing AppTimer's shared TimerEngine
 * (see AppTimer.kt for why it's a singleton, not passed in some other way).
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("FitnessTimer", "[service] onPlayerError: ${error.errorCodeName} - ${error.message}", error)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d("FitnessTimer", "[service] onPlaybackStateChanged: $playbackState")
            }
        })
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()
        AppTimer.engine.onRunningChanged = {
            mediaSession.setCustomLayout(listOf(timerCommandButton()))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        AppTimer.engine.onRunningChanged = null
        mediaSession.run {
            player.release()
            release()
        }
        super.onDestroy()
    }

    // A real icon resource is required here even though CommandButton also
    // takes an ICON_* constant: Media3's legacy-compat layer (for
    // Bluetooth/Auto/older controllers) builds an Android
    // PlaybackStateCompat.CustomAction from this button, and that throws
    // IllegalArgumentException without a resource id — confirmed on-device,
    // this is what was crashing the app on every launch (the exception in
    // onConnect made Media3 reject the whole session, not just this button).
    private fun timerCommandButton(): CommandButton =
        CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setIconResId(android.R.drawable.ic_lock_idle_alarm)
            .setDisplayName(if (AppTimer.engine.isRunning) "Pause timer" else "Resume timer")
            .setSessionCommand(SessionCommand(CMD_TIMER_TOGGLE, Bundle.EMPTY))
            .setEnabled(true)
            .build()

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            // Grant everything explicitly. super.onConnect()'s result was
            // logged on-device and both its command sets hashed as EMPTY
            // (Player$Commands@0, SessionCommands@1f = empty-set hashes), so
            // inheriting from it granted nothing and every setMediaItem/play
            // from the controller was silently denied (no player state
            // change ever fired on either side).
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(CMD_TIMER_TOGGLE, Bundle.EMPTY))
                .build()
            val playerCommands = Player.Commands.Builder().addAllCommands().build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .setCustomLayout(listOf(timerCommandButton()))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_TIMER_TOGGLE) {
                AppTimer.engine.toggleStartPause()
                mediaSession.setCustomLayout(listOf(timerCommandButton()))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }
}
