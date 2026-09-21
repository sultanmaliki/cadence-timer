package dev.fitnesstimer.nowplaying

const val POSITION_UNKNOWN = -1L

/**
 * Current position from a PlaybackState's raw fields:
 * `position + (now - lastUpdate) * speed` while playing (the formula the
 * PlaybackState accessors are documented around), clamped to the duration
 * when it is known. Returns [POSITION_UNKNOWN] if the app reported none.
 */
fun extrapolatePosition(
    positionMs: Long,
    updateElapsedMs: Long,
    speed: Float,
    isPlaying: Boolean,
    nowElapsedMs: Long,
    durationMs: Long,
): Long {
    if (positionMs < 0) return POSITION_UNKNOWN
    var p = positionMs
    if (isPlaying && updateElapsedMs > 0 && nowElapsedMs > updateElapsedMs && speed > 0f) {
        p += ((nowElapsedMs - updateElapsedMs) * speed).toLong()
    }
    if (durationMs > 0) p = p.coerceAtMost(durationMs)
    return p.coerceAtLeast(0L)
}

/** 0f..1f progress, or null when position or duration is unknown. */
fun progressFraction(positionMs: Long, durationMs: Long): Float? =
    if (positionMs < 0 || durationMs <= 0) null else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

/** Position of [this] right now (elapsedRealtime basis), or [POSITION_UNKNOWN]. */
fun NowPlaying.positionNow(nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime()): Long =
    extrapolatePosition(positionMs, positionUpdateElapsedMs, speed, isPlaying, nowElapsedMs, durationMs)
