package dev.fitnesstimer.gesture

/**
 * Coalesces drag-scrub movement into at most one seek per [minIntervalMs].
 * A raw drag produces a pointer event every few ms, and each seekTo on a
 * MediaController is an IPC round trip to the playback service. The target
 * is tracked as an absolute position from where the drag began, so nothing
 * depends on the (lagging) player position mid-drag, and a trailing
 * un-emitted move is delivered by [end].
 */
class ScrubAccumulator(private val minIntervalMs: Long = 100L) {
    private var target = -1L
    private var lastEmitMs = Long.MIN_VALUE / 2
    private var dirty = false

    /** Returns a position to seek to now, or null if throttled. */
    fun move(startPosition: () -> Long, deltaMs: Long, durationMs: Long, nowMs: Long): Long? {
        if (durationMs <= 0) return null
        if (target < 0) target = startPosition()
        target = (target + deltaMs).coerceIn(0L, durationMs)
        if (nowMs - lastEmitMs >= minIntervalMs) {
            lastEmitMs = nowMs
            dirty = false
            return target
        }
        dirty = true
        return null
    }

    /** Ends the drag; returns a final position if the last move was throttled. */
    fun end(): Long? {
        val pending = if (dirty) target else null
        target = -1L
        lastEmitMs = Long.MIN_VALUE / 2
        dirty = false
        return pending
    }
}
