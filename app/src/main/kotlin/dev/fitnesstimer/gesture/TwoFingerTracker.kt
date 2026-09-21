package dev.fitnesstimer.gesture

import kotlin.math.abs

enum class TwoFingerResult { TAP, NEXT, PREVIOUS }

/**
 * Pure. Decides whether a multi-finger gesture was a tap or a horizontal swipe.
 *
 * Each pointer is tracked by ID: its displacement is (last pressed position -
 * where it started), and it is FROZEN the moment that finger lifts. The old
 * logic read "the first pointer currently down"; when one finger lifted a
 * moment before the other, the surviving finger became "first", its x was a
 * whole finger-gap away from the previous "first", and a plain two-finger tap
 * looked like a big swipe (the reported "1 mm movement skips the song").
 *
 * The result uses the average displacement over every pointer seen, and a
 * swipe must also be mostly horizontal.
 */
class TwoFingerTracker {
    private class Track(val startX: Float, val startY: Float) {
        var lastX = startX
        var lastY = startY
    }

    private val tracks = HashMap<Long, Track>()

    fun update(id: Long, x: Float, y: Float, pressed: Boolean) {
        val t = tracks[id]
        if (t == null) {
            if (pressed) tracks[id] = Track(x, y)
        } else if (pressed) {
            t.lastX = x
            t.lastY = y
        } // a lift event's position is ignored: the finger's displacement stays frozen
    }

    fun averageDx(): Float = if (tracks.isEmpty()) 0f else tracks.values.map { it.lastX - it.startX }.average().toFloat()
    fun averageDy(): Float = if (tracks.isEmpty()) 0f else tracks.values.map { it.lastY - it.startY }.average().toFloat()

    /** [swipeMinPx]: how far the fingers must travel together; also must be >= 1.5x the vertical travel. */
    fun result(swipeMinPx: Float): TwoFingerResult {
        val dx = averageDx()
        val dy = averageDy()
        if (abs(dx) < swipeMinPx || abs(dx) < 1.5f * abs(dy)) return TwoFingerResult.TAP
        return if (dx > 0) TwoFingerResult.PREVIOUS else TwoFingerResult.NEXT
    }
}
