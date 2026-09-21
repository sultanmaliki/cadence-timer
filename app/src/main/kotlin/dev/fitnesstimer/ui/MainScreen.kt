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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.fitnesstimer.gesture.TimerGestureActions
import dev.fitnesstimer.gesture.timerGestures
import dev.fitnesstimer.media.connectMediaController
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
fun MainScreen(debugUri: Uri? = null) {
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

    var queue by remember { mutableStateOf<List<Uri>>(listOfNotNull(debugUri)) }
    var queueIndex by remember { mutableIntStateOf(0) }
    var ambientColor by remember { mutableStateOf(DEFAULT_AMBIENT) }
    var audioArtwork by remember { mutableStateOf<Bitmap?>(null) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var showSourceMenu by remember { mutableStateOf(false) }
    var showPlaylistScreen by remember { mutableStateOf(false) }
    var showTimerModeMenu by remember { mutableStateOf(false) }

    val currentUri = queue.getOrNull(queueIndex)

    // Whether the currently loaded item has a video track — drives the
    // ContentFrame-vs-AudioVisual choice below. Reads the Tracks payload
    // delivered by this exact callback (any group of type TRACK_TYPE_VIDEO)
    // rather than a currently-selected-format signal, which was seen going
    // stale for a beat after a real video's tracks had already resolved.
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                isAudioOnly = tracks.groups.none { it.type == C.TRACK_TYPE_VIDEO }
                Log.d(TAG, "onTracksChanged: groups=${tracks.groups.map { it.type }} isAudioOnly=$isAudioOnly")
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, "onPlaybackStateChanged: $playbackState (1=IDLE,2=BUFFERING,3=READY,4=ENDED)")
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "onIsPlayingChanged: $isPlaying")
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.errorCodeName} - ${error.message}", error)
            }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        Log.d(TAG, "pickMedia result: uri=$uri")
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Log.e(TAG, "takePersistableUriPermission FAILED for $uri", e)
            }
            queue = listOf(uri)
            queueIndex = 0
        }
    }

    // Loads whatever the queue points at; resamples ambient color + (in
    // case it's audio) embedded artwork once per file.
    LaunchedEffect(currentUri, controller) {
        val uri = currentUri ?: return@LaunchedEffect
        val c = controller
        Log.d(TAG, "load effect: uri=$uri controller=$c isConnected=${c?.isConnected}")
        if (c == null) return@LaunchedEffect
        isAudioOnly = false // reset until the new item's tracks resolve, avoids a stale-art flash
        audioArtwork = null
        c.setMediaItem(MediaItem.fromUri(uri))
        Log.d(TAG, "setMediaItem done, mediaItemCount=${c.mediaItemCount}")
        c.prepare()
        c.play()
        Log.d(TAG, "prepare()+play() called, playWhenReady=${c.playWhenReady} playbackState=${c.playbackState}")
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
            delay(100)
        }
    }

    val actions = remember(controller, queue, queueIndex) {
        TimerGestureActions(
            isPaused = { !timer.isRunning },
            onTogglePause = { timer.toggleStartPause() },
            onReset = {
                timer.reset()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onHoldProgress = { holdProgress = it },
            onSeekBack = {
                controller?.let { c -> c.seekTo((c.currentPosition - 10_000L).coerceAtLeast(0L)) }
            },
            onSeekForward = {
                controller?.let { c ->
                    val duration = c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                    c.seekTo((c.currentPosition + 10_000L).coerceAtMost(duration))
                }
            },
            onScrub = { fractionDelta ->
                controller?.let { c ->
                    val duration = c.duration
                    if (duration > 0) {
                        val target = (c.currentPosition + fractionDelta * duration).toLong()
                        c.seekTo(target.coerceIn(0L, duration))
                    }
                }
            },
            onToggleMediaPlayPause = { controller?.let { c -> c.playWhenReady = !c.playWhenReady } },
            // Clamped, not wraparound — simplest v1 behaviour; no-op past
            // either end of the queue (including the common one-item case).
            onSkipNext = { if (queueIndex < queue.lastIndex) queueIndex += 1 },
            onSkipPrevious = { if (queueIndex > 0) queueIndex -= 1 },
            onTimerModePicker = { showTimerModeMenu = true },
            onMediaSourcePicker = { showSourceMenu = true },
        )
    }

    val hasMedia = currentUri != null

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
                    modifier = Modifier.fillMaxSize(),
                ) {
                    NegativeTimerText(
                        text = formatElapsed(timer.displayMs),
                        holdProgress = holdProgress,
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
                    text = formatElapsed(timer.displayMs),
                    holdProgress = holdProgress,
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
                    text = formatElapsed(timer.displayMs),
                    holdProgress = holdProgress,
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
                queue = playlist.itemUris.map { Uri.parse(it) }
                queueIndex = 0
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
