package dev.fitnesstimer.media

import android.os.Bundle
import android.util.Log
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
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
            // Start from the base implementation's result instead of
            // building one from scratch: it grants the normal set of
            // player commands (play, pause, seek, setMediaItem, ...) that
            // every controller needs. The previous version only ever set
            // *session* commands and never touched player commands at all,
            // which meant nothing here explicitly granted them — this is
            // what made "play anything" silently do nothing (no crash,
            // the controller's commands were just being denied).
            val defaultResult = super.onConnect(session, controller)
            Log.d(
                "FitnessTimer",
                "onConnect from ${controller.packageName}: defaultPlayerCommands=${defaultResult.availablePlayerCommands} " +
                    "defaultSessionCommands=${defaultResult.availableSessionCommands}",
            )
            val sessionCommands = defaultResult.availableSessionCommands.buildUpon()
                .add(SessionCommand(CMD_TIMER_TOGGLE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(defaultResult.availablePlayerCommands)
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
