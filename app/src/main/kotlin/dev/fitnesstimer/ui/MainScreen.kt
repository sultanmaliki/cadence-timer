package dev.fitnesstimer.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import dev.fitnesstimer.gesture.ScrubAccumulator
import dev.fitnesstimer.gesture.TimerGestureActions
import dev.fitnesstimer.gesture.timerGestures
import dev.fitnesstimer.media.connectMediaController
import dev.fitnesstimer.media.tryPersistReadGrant
import dev.fitnesstimer.render.AudioVisual
import dev.fitnesstimer.render.NegativeTimerText
import dev.fitnesstimer.render.sampleAmbientColor
import dev.fitnesstimer.render.sampleAudioArtwork
import dev.fitnesstimer.timer.AppTimer
import dev.fitnesstimer.timer.formatElapsed
import kotlinx.coroutines.delay

private val DEFAULT_AMBIENT = Color(0xFF101010) // near-black, used before a color is sampled / no media
private const val TAG = "FitnessTimer"

/**
 * PLAN.md roadmap step 5 — timer + local media (single file or a saved
 * playlist) + gestures on one screen.
 *
 * The player is NOT owned here anymore — it lives in media.PlaybackService
 * (a real MediaSessionService) so playback survives backgrounding and gets
 * a system notification with transport controls. This screen just connects
 * a MediaController to it. `controller` is null for a brief moment on
 * first composition until that connection completes.
 *
 * The playback queue is just `queue` + `queueIndex`: a single picked file
 * is treated as a one-item, unsaved queue, and a played playlist as a
 * multi-item one — same downstream logic either way, no special-casing.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MainScreen(debugUris: List<Uri> = emptyList()) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val timer = AppTimer.engine

    var controller by remember { mutableStateOf<MediaController?>(null) }
    LaunchedEffect(Unit) {
        Log.d(TAG, "connecting MediaController...")
        try {
            controller = connectMediaController(context)
            Log.d(TAG, "MediaController connected: isConnected=${controller?.isConnected}")
        } catch (e: Exception) {
            Log.e(TAG, "MediaController connection FAILED", e)
        }
    }
    DisposableEffect(controller) {
        // Must capture `controller` into a local val here, NOT read the
        // outer var inside onDispose: onDispose reads whatever the closure
        // captures at CALL time, and a bare `controller` reference re-reads
        // the (by-now-updated) outer state, not the value this specific
        // effect instance was keyed on. That bug released the controller
        // moments after every connection (the key=null instance's dispose
        // fired the instant `controller` flipped non-null, reading the NEW
        // value) — confirmed on-device via isConnected flipping true->false
        // right after connecting, which was the actual "can't play
        // anything" bug.
        val toRelease = controller
        onDispose { toRelease?.release() }
    }

    // The PLAYER is the single source of truth for the queue (item count,
    // current item, index): everything below is mirrored from the controller
    // by the listener, so an Activity recreation (font/locale/dark-mode
    // change) just re-syncs instead of showing "no media" over live audio,
    // and next/previous/auto-advance are native player behaviour (which is
    // also what makes the notification's next/prev buttons work).
    var mediaItemCount by remember { mutableIntStateOf(0) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var pendingQueue by remember { mutableStateOf<List<Uri>?>(debugUris.ifEmpty { null }) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var ambientColor by remember { mutableStateOf(DEFAULT_AMBIENT) }
    var audioArtwork by remember { mutableStateOf<Bitmap?>(null) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var showSourceMenu by remember { mutableStateOf(false) }
    var showPlaylistScreen by remember { mutableStateOf(false) }
    var showTimerModeMenu by remember { mutableStateOf(false) }

    val hasMedia = mediaItemCount > 0

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        mediaItemCount = c.mediaItemCount
        isPlaying = c.isPlaying
        currentUri = c.currentMediaItem?.localConfiguration?.uri
        val initialGroups = c.currentTracks.groups
        if (initialGroups.isNotEmpty()) isAudioOnly = initialGroups.none { it.type == C.TRACK_TYPE_VIDEO }
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                mediaItemCount = c.mediaItemCount
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentUri = mediaItem?.localConfiguration?.uri
                isAudioOnly = false // reset until this item's tracks resolve (avoids a stale-art flash)
                audioArtwork = null
                playbackError = null
            }
            override fun onTracksChanged(tracks: Tracks) {
                if (tracks.groups.isNotEmpty()) {
                    isAudioOnly = tracks.groups.none { it.type == C.TRACK_TYPE_VIDEO }
                }
                Log.d(TAG, "onTracksChanged: groups=${tracks.groups.map { it.type }} isAudioOnly=$isAudioOnly")
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "onPlaybackStateChanged: $playbackState (1=IDLE,2=BUFFERING,3=READY,4=ENDED)")
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                Log.d(TAG, "onIsPlayingChanged: $playing")
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.errorCodeName} - ${error.message}", error)
                // A deleted/moved/unreadable file: say so and skip on rather
                // than leaving a black screen stuck on the bad item.
                playbackError = "Can't play this file"
                if (c.hasNextMediaItem()) {
                    c.seekToNextMediaItem()
                    c.prepare()
                    c.play()
                }
            }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }

    // Starts a fresh queue on the player as ONE setMediaItems call, so the
    // player owns real next/previous/auto-advance. Waits for the controller
    // if it hasn't connected yet.
    LaunchedEffect(controller, pendingQueue) {
        val c = controller ?: return@LaunchedEffect
        val queue = pendingQueue ?: return@LaunchedEffect
        pendingQueue = null
        if (queue.isEmpty()) return@LaunchedEffect
        playbackError = null
        c.setMediaItems(queue.map { MediaItem.fromUri(it) }, 0, 0L)
        c.prepare()
        c.play()
        Log.d(TAG, "queue started: ${queue.size} item(s)")
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        Log.d(TAG, "pickMedia result: uri=$uri")
        if (uri != null) {
            tryPersistReadGrant(context, uri)
            pendingQueue = listOf(uri)
        }
    }

    // Once per current item: sample ambient color, and embedded artwork
    // (used only in audio mode).
    LaunchedEffect(currentUri) {
        audioArtwork = null
        val uri = currentUri ?: return@LaunchedEffect
        ambientColor = sampleAmbientColor(context, uri, DEFAULT_AMBIENT)
        audioArtwork = sampleAudioArtwork(context, uri)
    }

    // Drives recomposition of the timer text while running, and detects
    // countdown completion (foreground-only — no background alarm yet, see
    // PLAN.md section F / DECISIONS.md).
    LaunchedEffect(timer.isRunning) {
        while (timer.isRunning) {
            timer.nowElapsedRealtime = android.os.SystemClock.elapsedRealtime()
            if (timer.isCountdownFinished) {
                timer.pause()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                break
            }
            delay(timer.msUntilDisplayChange())
        }
    }

    // Stable across recompositions/controller connect: pointerInput is keyed on
    // this object, and a key change would cancel an in-flight gesture and
    // forget a pending double-tap. Lambdas read the live controller instead.
    val liveController by rememberUpdatedState(controller)
    val scrub = remember { ScrubAccumulator() }
    val actions = remember {
        TimerGestureActions(
            isPaused = { !timer.isRunning },
            onTogglePause = { timer.toggleStartPause() },
            onReset = {
                timer.reset()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onHoldProgress = { holdProgress = it },
            onSeekBack = {
                liveController?.let { c -> c.seekTo((c.currentPosition - 10_000L).coerceAtLeast(0L)) }
            },
            onSeekForward = {
                liveController?.let { c ->
                    val duration = c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    c.seekTo((c.currentPosition + 10_000L).coerceAtMost(duration))
                }
            },
            onScrub = { fractionDelta ->
                liveController?.let { c ->
                    val duration = c.duration
                    if (duration > 0) {
                        scrub.move(
                            startPosition = { c.currentPosition },
                            deltaMs = (fractionDelta * duration).toLong(),
                            durationMs = duration,
                            nowMs = android.os.SystemClock.uptimeMillis(),
                        )?.let { c.seekTo(it) }
                    }
                }
            },
            onScrubEnd = {
                val tail = scrub.end()
                if (tail != null) liveController?.seekTo(tail)
            },
            onToggleMediaPlayPause = {
                liveController?.let { c ->
                    when {
                        c.mediaItemCount == 0 -> {}
                        c.isPlaying -> c.pause()
                        else -> {
                            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
                            if (c.playbackState == Player.STATE_IDLE) c.prepare()
                            c.play()
                        }
                    }
                }
            },
            // Clamped, not wraparound — no-op past either end of the queue.
            onSkipNext = { liveController?.let { c -> if (c.hasNextMediaItem()) c.seekToNextMediaItem() } },
            onSkipPrevious = { liveController?.let { c -> if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() } },
            onTimerModePicker = { showTimerModeMenu = true },
            onMediaSourcePicker = { showSourceMenu = true },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (hasMedia) ambientColor else DEFAULT_AMBIENT)
            .timerGestures(actions)
    ) {
        when {
            hasMedia && isAudioOnly -> {
                // Timer goes BELOW the artwork here (AudioVisual's slot),
                // not centered over it — the two were overlapping/clashing
                // before this was a dedicated layout.
                AudioVisual(
                    artwork = audioArtwork,
                    ambientColor = ambientColor,
                    isPlaying = isPlaying,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    NegativeTimerText(
                        text = { formatElapsed(timer.displayMs) },
                        holdProgress = { holdProgress },
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                    )
                }
            }
            hasMedia -> {
                controller?.let { c ->
                    ContentFrame(
                        player = c,
                        modifier = Modifier.fillMaxSize(),
                        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                        contentScale = ContentScale.Fit,
                    )
                }
                NegativeTimerText(
                    text = { formatElapsed(timer.displayMs) },
                    holdProgress = { holdProgress },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                // No-media state (PLAN.md section H.6): plain background,
                // tap the top-right corner to pick a file or a playlist.
                Text(
                    "Tap top-right to choose a local file or playlist",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                NegativeTimerText(
                    text = { formatElapsed(timer.displayMs) },
                    holdProgress = { holdProgress },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    playbackError?.let { msg ->
        Box(Modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
            Text(msg, color = Color.White)
        }
    }

    if (showSourceMenu) {
        MediaSourceMenu(
            onDismiss = { showSourceMenu = false },
            onChooseFile = {
                showSourceMenu = false
                pickMedia.launch(arrayOf("video/*", "audio/*"))
            },
            onOpenPlaylists = {
                showSourceMenu = false
                showPlaylistScreen = true
            },
        )
    }

    if (showPlaylistScreen) {
        PlaylistScreen(
            onDismiss = { showPlaylistScreen = false },
            onPlayPlaylist = { playlist ->
                pendingQueue = playlist.itemUris.map { Uri.parse(it) }
                showPlaylistScreen = false
            },
        )
    }

    if (showTimerModeMenu) {
        TimerModeMenu(
            onDismiss = { showTimerModeMenu = false },
            onChooseStopwatch = {
                showTimerModeMenu = false
                timer.switchToStopwatch()
            },
            onChooseCountdown = { targetMs ->
                showTimerModeMenu = false
                timer.switchToCountdown(targetMs)
            },
        )
    }
}
