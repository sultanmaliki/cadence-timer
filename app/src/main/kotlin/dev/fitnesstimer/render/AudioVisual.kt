package dev.fitnesstimer.render

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import kotlin.math.sin

private val ARTWORK_CORNER_RADIUS = 32.dp
private const val MAX_UPSCALE = 6f // screen pixels per artwork pixel

/**
 * Audio-mode visual (PLAN.md section H, expanded 2026-09-20): cover art at
 * its OWN aspect ratio — not forced into a square — with a rounded-corner
 * border, sized as large as fits, over the ambient background. Equalizer
 * bars sit below it, colored from the same ambient/dominant color as the
 * background rather than plain white. [belowArtwork] is a slot for the
 * caller's timer text, rendered right after the bars — MainScreen uses this
 * instead of overlaying the timer on top of the artwork.
 */
@Composable
fun AudioVisual(
    artwork: Bitmap?,
    ambientColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    belowArtwork: @Composable () -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Captured here: BoxWithConstraintsScope's maxWidth/maxHeight aren't
        // reachable from inside the nested Column (DSL scope marker).
        val tileMaxWidth = maxWidth * 0.85f
        val tileMaxHeight = maxHeight * 0.5f
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArtworkTile(
                artwork = artwork,
                maxWidth = tileMaxWidth,
                maxHeight = tileMaxHeight,
            )
            Spacer(Modifier.height(28.dp))
            EqualizerBars(
                color = ambientColor,
                isPlaying = isPlaying,
                modifier = Modifier.width(140.dp).height(40.dp),
            )
            Spacer(Modifier.height(24.dp))
            belowArtwork()
        }
    }
}

/**
 * Sizes itself to the largest rectangle matching [artwork]'s own aspect
 * ratio that fits within the space [modifier] grants — computed manually
 * via BoxWithConstraints rather than chaining `aspectRatio()` +
 * `heightIn(max=)`, which fight each other when both width and height are
 * bounded. No artwork (no embedded art in the file) falls back to a plain
 * square tile, since there's no real aspect ratio to preserve.
 */
@Composable
internal fun ArtworkTile(
    artwork: Bitmap?,
    maxWidth: Dp,
    maxHeight: Dp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    if (artwork != null && artwork.width > 0 && artwork.height > 0) {
        val bitmapAspect = artwork.width.toFloat() / artwork.height.toFloat()
        val boxAspect = maxWidth / maxHeight
        var w: Dp
        var h: Dp
        if (bitmapAspect > boxAspect) {
            w = maxWidth
            h = maxWidth / bitmapAspect
        } else {
            h = maxHeight
            w = maxHeight * bitmapAspect
        }
        // Cap the upscale so a small (e.g. trimmed) image stays reasonably sharp.
        val density = LocalDensity.current.density
        val capFactor = minOf(1f, MAX_UPSCALE * artwork.width / density / w.value, MAX_UPSCALE * artwork.height / density / h.value)
        w *= capFactor
        h *= capFactor
        Box(Modifier.size(w, h).clip(RoundedCornerShape(ARTWORK_CORNER_RADIUS))) {
            Image(
                bitmap = artwork.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            overlay()
        }
    } else {
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        Box(
            Modifier
                .size(side)
                .clip(RoundedCornerShape(ARTWORK_CORNER_RADIUS))
                .background(Color.White.copy(alpha = 0.08f))
        ) { overlay() }
    }
}

@Composable
private fun EqualizerBars(color: Color, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val bars = 5
    // The ambient color IS the background color, so bars in exactly that color
    // are invisible; lighten toward white to keep the hue but contrast.
    val barColor = lerp(color, Color.White, 0.6f)
    // ~30 "frames" per second, not the display's 120Hz: bar motion doesn't
    // need more, and continuous per-vsync animation is a real battery cost
    // (measured on-device: ~120 fps while animating). Only ticks while playing.
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var t = seconds
        while (true) {
            delay(33)
            t += 0.033f
            seconds = t
        }
    }
    Box(
        modifier.background(
            // Static radial glow. Modifier.blur (RenderEffect) was tried first
            // and measured 20ms median frame time vs 8ms without it: it gets
            // re-rendered every frame while the bars animate.
            Brush.radialGradient(listOf(barColor.copy(alpha = 0.22f), Color.Transparent)),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            repeat(bars) { index ->
                // Staggered period per bar so they don't move in lockstep — a
                // plausible-looking loop, not real amplitude data (see
                // AudioVisual's doc comment for why). Read inside the
                // graphicsLayer lambda: draw-phase only, no recomposition.
                val period = 0.5f + index * 0.09f
                Bar(barColor) {
                    if (!isPlaying) 0.25f
                    else 0.25f + 0.75f * ((sin(2f * Math.PI.toFloat() * seconds / period) + 1f) / 2f)
                }
            }
        }
    }
}

@Composable
private fun Bar(color: Color, heightFraction: () -> Float) {
    Box(
        Modifier
            .width(12.dp)
            .fillMaxHeight()
            .graphicsLayer {
                scaleY = heightFraction()
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.85f))
    )
}
