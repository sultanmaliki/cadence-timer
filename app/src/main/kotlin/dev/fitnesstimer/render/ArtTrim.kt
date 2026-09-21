package dev.fitnesstimer.render

import android.graphics.Bitmap
import android.util.Log
import kotlin.math.abs
import kotlin.math.max

/** How many pixels to crop from each side. */
data class Insets(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val isEmpty: Boolean get() = left == 0 && top == 0 && right == 0 && bottom == 0
}

private fun channelDistance(a: Int, b: Int): Int =
    max(
        max(abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)), abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))),
        abs((a and 0xFF) - (b and 0xFF)),
    )

/**
 * Pure. Some apps (e.g. Mi Music) hand over 16:9 artwork with flat-colour bars
 * baked into the sides of a portrait cover. This finds those bars so they can
 * be cropped and the real cover shown at its own aspect ratio.
 *
 * Deliberately conservative, because wrongly cropping real artwork is worse
 * than leaving bars: a border only counts if it is present on BOTH opposite
 * sides, every pixel in it is within [tolerance] of a single flat colour, the
 * two sides are the same colour, it is at least [minBarFraction] wide, and at
 * least [minContentFraction] of the image remains. One extra pixel is shaved
 * from each cropped side to drop compression ringing at the edge.
 */
fun findUniformInsets(
    pixels: IntArray,
    width: Int,
    height: Int,
    tolerance: Int = 14,
    minBarFraction: Float = 0.03f,
    minContentFraction: Float = 0.4f,
): Insets {
    if (width < 16 || height < 16 || pixels.size < width * height) return Insets(0, 0, 0, 0)

    fun columnIsFlat(x: Int, ref: Int): Boolean {
        for (y in 0 until height) if (channelDistance(pixels[y * width + x], ref) > tolerance) return false
        return true
    }
    fun rowIsFlat(y: Int, ref: Int): Boolean {
        for (x in 0 until width) if (channelDistance(pixels[y * width + x], ref) > tolerance) return false
        return true
    }

    // Left/right bars.
    val leftRef = pixels[height / 2 * width]
    val rightRef = pixels[height / 2 * width + width - 1]
    var left = 0
    while (left < width && columnIsFlat(left, leftRef)) left++
    var right = 0
    while (right < width - left && columnIsFlat(width - 1 - right, rightRef)) right++
    val sidesOk = left >= max(3, (width * minBarFraction).toInt()) &&
        right >= max(3, (width * minBarFraction).toInt()) &&
        channelDistance(leftRef, rightRef) <= tolerance * 2 &&
        (width - left - right) >= width * minContentFraction
    // Top/bottom bars.
    val topRef = pixels[width / 2]
    val bottomRef = pixels[(height - 1) * width + width / 2]
    var top = 0
    while (top < height && rowIsFlat(top, topRef)) top++
    var bottom = 0
    while (bottom < height - top && rowIsFlat(height - 1 - bottom, bottomRef)) bottom++
    val endsOk = top >= max(3, (height * minBarFraction).toInt()) &&
        bottom >= max(3, (height * minBarFraction).toInt()) &&
        channelDistance(topRef, bottomRef) <= tolerance * 2 &&
        (height - top - bottom) >= height * minContentFraction

    val l = if (sidesOk) left + 1 else 0
    val r = if (sidesOk) right + 1 else 0
    val t = if (endsOk) top + 1 else 0
    val b = if (endsOk) bottom + 1 else 0
    return Insets(l, t, r, b)
}

/** Crops flat-colour bars off [bitmap] if there are any; otherwise returns it unchanged. */
fun trimUniformBars(bitmap: Bitmap): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w < 16 || h < 16 || w * h > 4_000_000) return bitmap
    return try {
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val insets = findUniformInsets(px, w, h)
        if (insets.isEmpty) bitmap
        else {
            Log.d("FitnessTimer", "[art] trimmed bars ${w}x$h -> ${w - insets.left - insets.right}x${h - insets.top - insets.bottom}")
            Bitmap.createBitmap(bitmap, insets.left, insets.top, w - insets.left - insets.right, h - insets.top - insets.bottom)
        }
    } catch (e: Exception) {
        bitmap
    }
}
