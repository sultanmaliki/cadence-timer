package dev.fitnesstimer.nowplaying

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "FitnessTimer"
private const val GRACE_MS = 1500L

/**
 * Bridges other apps' media sessions into a [StateFlow]. Main-thread only
 * (callbacks are delivered to the thread that registers them; init/refresh
 * are called from the Activity / listener service, both on main).
 *
 * Requires notification access: without it getActiveSessions() throws
 * SecurityException, which is reported as `accessGranted = false` instead of
 * crashing. Nothing is fetched from the network; artwork is only ever a
 * bitmap the source app put in its metadata.
 */
object NowPlayingRepository {
    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = _state

    private var appContext: Context? = null
    private var manager: MediaSessionManager? = null
    private var component: ComponentName? = null
    private var listening = false

    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null
    private val callbacks = HashMap<MediaController, MediaController.Callback>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onSessions(list.orEmpty())
    }

    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        manager = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        component = ComponentName(ctx, NowPlayingListenerService::class.java)
    }

    /** Re-checks access and re-reads sessions. Call on app resume and when the listener (dis)connects. */
    fun refresh() {
        val ctx = appContext ?: return
        val granted = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        Log.d(TAG, "[nowplaying] refresh: granted=$granted")
        if (!granted) {
            stopListening()
            grace.reset()
            publish(NowPlayingState(accessGranted = false))
            return
        }
        try {
            val mgr = manager ?: return
            val comp = component ?: return
            val sessions = mgr.getActiveSessions(comp)
            if (!listening) {
                mgr.addOnActiveSessionsChangedListener(sessionsListener, comp)
                listening = true
            }
            onSessions(sessions)
        } catch (e: SecurityException) {
            Log.w(TAG, "[nowplaying] access revoked or not yet effective", e)
            stopListening()
            grace.reset()
            publish(NowPlayingState(accessGranted = false))
        }
    }

    // ---- controls: forwarded to the source app; it may ignore them (actions are advisory) ----
    fun playPause() {
        val c = current ?: return
        if (isPlayingState(c.playbackState?.state ?: PlaybackState.STATE_NONE)) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun skipNext() {
        current?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        current?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        current?.transportControls?.seekTo(positionMs)
    }

    private fun onSessions(list: List<MediaController>) {
        val keep = list.toSet()
        callbacks.keys.filter { it !in keep }.forEach { detach(it) }
        list.forEach { attach(it) }
        controllers = list
        recompute()
    }

    private fun attach(c: MediaController) {
        if (callbacks.containsKey(c)) return
        val cb = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = recompute()
            override fun onMetadataChanged(metadata: MediaMetadata?) = recompute()
            override fun onSessionDestroyed() = refresh()
        }
        callbacks[c] = cb
        c.registerCallback(cb)
    }

    private fun detach(c: MediaController) {
        callbacks.remove(c)?.let { c.unregisterCallback(it) }
    }

    private fun stopListening() {
        callbacks.keys.toList().forEach { detach(it) }
        controllers = emptyList()
        current = null
        if (listening) {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
            listening = false
        }
    }

    // Between tracks an app can report "no session" for ~150 ms; hold the last value briefly.
    private val grace = NullGrace<NowPlaying>(GRACE_MS)
    private val handler = Handler(Looper.getMainLooper())
    private val recheck = Runnable { recompute() }

    private fun recompute() {
        val ctx = appContext ?: return
        val candidates = controllers.mapIndexed { i, c ->
            SessionCandidate(i.toString(), c.packageName, c.playbackState?.state ?: PlaybackState.STATE_NONE)
        }
        val chosen = SessionSelector.select(candidates, ctx.packageName)
        current = chosen?.let { controllers[it.id.toInt()] }
        val now = SystemClock.elapsedRealtime()
        val fresh = current?.let { toNowPlaying(it) }
        val shown = grace.filter(fresh, now)
        handler.removeCallbacks(recheck)
        if (fresh == null && shown != null) handler.postDelayed(recheck, grace.remainingMs(now) + 30L)
        publish(NowPlayingState(accessGranted = true, nowPlaying = shown))
    }

    private fun toNowPlaying(c: MediaController): NowPlaying {
        val m = c.metadata
        val ps = c.playbackState
        val bitmaps = listOf(
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        ).mapNotNull { m?.getBitmap(it) }
        val hasUri = listOf(
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
        ).any { !m?.getString(it).isNullOrEmpty() }
        return NowPlaying(
            packageName = c.packageName,
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            album = m?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            artwork = stableArtwork(c.packageName, m, bitmaps.maxByOrNull { it.width * it.height }),
            hasArtworkUri = hasUri,
            durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: -1L,
            playbackState = ps?.state ?: PlaybackState.STATE_NONE,
            positionMs = ps?.position ?: POSITION_UNKNOWN,
            positionUpdateElapsedMs = ps?.lastPositionUpdateTime ?: 0L,
            speed = ps?.playbackSpeed ?: 1f,
            actions = ps?.actions ?: 0L,
        )
    }

    // Each callback can hand back a fresh Bitmap copy of the same artwork;
    // reuse the previous instance while the track identity is unchanged so
    // UI effects keyed on the bitmap (color sampling) run once per track.
    private var artKey: String? = null
    private var artBitmap: Bitmap? = null

    private fun stableArtwork(pkg: String, m: MediaMetadata?, fresh: Bitmap?): Bitmap? {
        if (fresh == null) { artKey = null; artBitmap = null; return null }
        val key = "$pkg|${m?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)}|" +
            "${m?.getString(MediaMetadata.METADATA_KEY_TITLE)}|${m?.getString(MediaMetadata.METADATA_KEY_ARTIST)}|" +
            "${fresh.width}x${fresh.height}"
        if (key == artKey && artBitmap != null) return artBitmap
        artKey = key
        // Crop flat bars some apps bake into the sides (see ArtTrim); cached with the key.
        val trimmed = dev.fitnesstimer.render.trimUniformBars(fresh)
        artBitmap = trimmed
        return trimmed
    }

    private var lastLogged: String? = null

    private fun publish(newState: NowPlayingState) {
        _state.value = newState
        // Probe logging (no personal data: key names, sizes, flags only).
        val np = newState.nowPlaying
        val line = when {
            !newState.accessGranted -> "access=false"
            np == null -> "access=true nothing-playing"
            else -> "access=true pkg=${np.packageName} state=${np.playbackState} " +
                "art=${np.artwork?.let { "${it.width}x${it.height}" } ?: "none"} artUri=${np.hasArtworkUri} " +
                "dur=${np.durationMs} pos=${np.positionMs} speed=${np.speed} actions=${np.actions} " +
                "keys=${current?.metadata?.keySet()?.sorted()}"
        }
        if (line != lastLogged) {
            lastLogged = line
            Log.d(TAG, "[nowplaying] $line")
        }
    }
}
