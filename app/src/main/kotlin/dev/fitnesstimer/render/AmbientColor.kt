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
            val frame = retriever.getFrameAtTime(1_000_000L) ?: return@withContext fallback
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
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        } finally {
            retriever?.release()
        }
    }
