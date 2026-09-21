package dev.fitnesstimer.nowplaying

/**
 * Pure. Apps briefly report "no session / stopped" between tracks (measured on
 * Mi Music: ~150 ms at every track change). Showing that as "Nothing playing"
 * flashed the screen and restarted the audio capture at each song change.
 * This keeps the last real value alive for [graceMs] after the source goes
 * null; a real, lasting absence still shows through once the grace expires.
 */
class NullGrace<T : Any>(private val graceMs: Long) {
    private var last: T? = null
    private var lastAtMs = Long.MIN_VALUE

    /** What to show now, given the latest [value] (null = source has nothing). */
    fun filter(value: T?, nowMs: Long): T? {
        if (value != null) {
            last = value
            lastAtMs = nowMs
            return value
        }
        val held = last ?: return null
        return if (nowMs - lastAtMs < graceMs) held else {
            last = null
            null
        }
    }

    /** How long until a held value expires (0 if nothing is held) — when to re-check. */
    fun remainingMs(nowMs: Long): Long {
        if (last == null) return 0L
        return (graceMs - (nowMs - lastAtMs)).coerceAtLeast(0L)
    }

    fun reset() {
        last = null
        lastAtMs = Long.MIN_VALUE
    }
}
