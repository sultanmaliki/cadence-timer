package dev.fitnesstimer.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SAMPLE_PX = 128
private const val MAX_ARTWORK_PX = 1024

/** Decodes [bytes] downsampled so neither side exceeds ~[maxPx] (power-of-two inSampleSize). */
internal fun decodeBounded(bytes: ByteArray, maxPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPx)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

internal fun sampleSizeFor(width: Int, height: Int, maxPx: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= maxPx || height / (sample * 2) >= maxPx) sample *= 2
    return sample
}

/**
 * PLAN.md section H step 7 / DECISIONS.md — one-time dominant-color sample
 * used behind a letterboxed video, instead of a plain black bar or (the
 * deferred, more expensive) live blurred replay of the video. Runs once per
 * selected file, off the main thread.
 */
suspend fun sampleAmbientColor(context: Context, uri: Uri, fallback: Color): Color =
    withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            // 1s in, so an opening black/title frame doesn't skew the sample.
            // Scaled decode: a full-size 4K frame is ~33MB just to pick one color.
            val frame = retriever.getScaledFrameAtTime(
                1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, SAMPLE_PX, SAMPLE_PX,
            ) ?: return@withContext fallback
            val palette = Palette.from(frame).generate()
            val swatch = palette.dominantSwatch ?: palette.mutedSwatch ?: palette.darkVibrantSwatch
            swatch?.let { Color(it.rgb) } ?: fallback
        } catch (e: Exception) {
            fallback
        } finally {
            retriever?.release()
        }
    }

/**
 * Embedded cover art for an audio file (ID3/MP4 art), if any — `null` if
 * the file has none (common for e.g. voice memos or bare WAV files). Used
 * for the audio-mode squarcle artwork (render/AudioVisual.kt); the caller
 * falls back to a plain ambient-colored tile when this is null.
 */
suspend fun sampleAudioArtwork(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return@withContext null
            decodeBounded(bytes, MAX_ARTWORK_PX)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            // Pathological embedded art; fall back to the plain tile.
            null
        } finally {
            retriever?.release()
        }
    }

/** Colors for the One UI-style player: a dark gradient behind it and a bright accent for the wave. */
data class ArtColors(val top: Color, val bottom: Color, val accent: Color)

val DEFAULT_ART_COLORS = ArtColors(Color(0xFF1B1B1F), Color(0xFF0B0B0D), Color(0xFFC9D0FF))

/** RGB -> (hue 0..360, saturation 0..1, lightness 0..1). Pure. */
internal fun rgbToHsl(c: Color): Triple<Float, Float, Float> {
    val r = c.red; val g = c.green; val b = c.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min
    if (d < 1e-6f) return Triple(0f, 0f, l)
    val s = d / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when (max) {
        r -> ((g - b) / d) % 6f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return Triple(if (h < 0f) h + 360f else h, s.coerceIn(0f, 1f), l)
}

/** (hue 0..360, saturation 0..1, lightness 0..1) -> opaque Color. Pure. */
internal fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = (h % 360f) / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r1 + m, g1 + m, b1 + m)
}

/**
 * Pure: derive [ArtColors] from a base (dominant) color and an accent source.
 * Background: the base hue, always dark (so white text reads) but tinted
 * rather than near-black, top lighter than bottom. Accent: the accent
 * source's hue pushed to a bright, saturated tone so the wave is colorful
 * (a plain lightened dominant color came out grey on dark artwork). Greys stay
 * neutral instead of being given an arbitrary hue.
 */
fun deriveArtColors(base: Color, accentSource: Color): ArtColors {
    val (bh, bs, _) = rgbToHsl(base)
    val tint = if (bs < 0.1f) 0f else bs.coerceIn(0.25f, 0.6f)
    val (ah, asat, _) = rgbToHsl(accentSource)
    val accent = if (asat < 0.1f) hslToColor(0f, 0f, 0.8f) else hslToColor(ah, asat.coerceAtLeast(0.55f), 0.7f)
    return ArtColors(
        top = hslToColor(bh, tint, 0.24f),
        bottom = hslToColor(bh, tint, 0.09f),
        accent = accent,
    )
}

/** Palette-based [ArtColors] for a bitmap (another app's artwork). Off the main thread. */
suspend fun sampleArtColors(bitmap: Bitmap): ArtColors =
    withContext(Dispatchers.Default) {
        try {
            val p = Palette.from(bitmap).generate()
            val base = (p.dominantSwatch ?: p.mutedSwatch ?: p.darkVibrantSwatch)?.rgb
                ?: return@withContext DEFAULT_ART_COLORS
            val accentRgb = p.vibrantSwatch?.rgb ?: p.lightVibrantSwatch?.rgb ?: p.darkVibrantSwatch?.rgb
                ?: p.mutedSwatch?.rgb ?: p.lightMutedSwatch?.rgb ?: base
            deriveArtColors(Color(base), Color(accentRgb))
        } catch (e: Exception) {
            DEFAULT_ART_COLORS
        }
    }
