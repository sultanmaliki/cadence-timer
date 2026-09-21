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
