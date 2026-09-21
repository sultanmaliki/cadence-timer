package dev.fitnesstimer.render

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.fitnesstimer.nowplaying.NowPlaying
import dev.fitnesstimer.nowplaying.extrapolatePosition
import dev.fitnesstimer.nowplaying.progressFraction
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** "m:ss" (or "h:mm:ss"); "--:--" when unknown. */
fun formatClock(totalSeconds: Long): String {
    if (totalSeconds < 0) return "--:--"
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.ROOT, "%d:%02d", m, s)
}

/**
 * One UI-style waveform progress (PLAN.md N; design per press descriptions of
 * One UI 9's media player — colorful waveform progress driven by the album
 * art's colors): a moving sine wave up to the current position, a flat dim
 * line after it, and a round thumb. The wave flattens smoothly when paused.
 *
 * Performance (learned the hard way, see PLAN.md N.5c): one ~30 Hz ticker
 * that only runs while playing; the wave and progress are computed inside the
 * Canvas draw lambda, so a tick invalidates only this draw, and the time
 * labels use derivedStateOf so they recompose once per second. Fully idle
 * (zero frames) while paused.
 */
@Composable
fun WaveProgress(np: NowPlaying, accent: Color, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(np.isPlaying, np.positionUpdateElapsedMs, np.positionMs) {
        now = SystemClock.elapsedRealtime()
        while (np.isPlaying) {
            delay(33)
            now = SystemClock.elapsedRealtime()
        }
    }
    val amplitude by animateFloatAsState(if (np.isPlaying) 1f else 0f, tween(450), label = "waveAmplitude")
    fun positionAt(t: Long) =
        extrapolatePosition(np.positionMs, np.positionUpdateElapsedMs, np.speed, np.isPlaying, t, np.durationMs)
    val elapsedSeconds by remember(np) { derivedStateOf { positionAt(now).let { if (it < 0) -1L else it / 1000 } } }

    Column(modifier) {
        // graphicsLayer: without its own layer, every ~30 Hz tick re-records the
        // draw ops of the whole screen (artwork, gradient, text) - measured on
        // device as 16 ms median frames vs 8 ms. Isolated, only this canvas is redrawn.
        Canvas(Modifier.fillMaxWidth().height(36.dp).graphicsLayer()) {
            val w = size.width
            val mid = size.height / 2f
            val fraction = progressFraction(positionAt(now), np.durationMs)
            val progressX = w * (fraction ?: 0f)
            val amp = 6.dp.toPx() * amplitude
            val wavelength = 30.dp.toPx()
            val stroke = 4.dp.toPx()
            val phase = (now % 1600L) / 1600f * (2f * PI.toFloat())
            fun waveY(x: Float) = mid + amp * sin(2f * PI.toFloat() * x / wavelength - phase)

            drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(progressX, mid),
                end = Offset(w, mid),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            if (progressX > 0f) {
                val path = Path()
                path.moveTo(0f, waveY(0f))
                var x = 0f
                val step = 3.dp.toPx()
                while (x < progressX) {
                    x = minOf(x + step, progressX)
                    path.lineTo(x, waveY(x))
                }
                drawPath(path, accent, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (fraction != null) {
                drawCircle(accent, radius = 7.dp.toPx(), center = Offset(progressX, waveY(progressX)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatClock(elapsedSeconds),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                formatClock(if (np.durationMs > 0) np.durationMs / 1000 else -1),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
