package dev.fitnesstimer.render

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private val ARTWORK_CORNER_RADIUS = 32.dp

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
    modifier: Modifier = Modifier,
    belowArtwork: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ArtworkTile(
                artwork = artwork,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.5f),
            )
            Spacer(Modifier.height(28.dp))
            EqualizerBars(
                color = ambientColor,
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
private fun ArtworkTile(artwork: Bitmap?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (artwork != null && artwork.width > 0 && artwork.height > 0) {
            val bitmapAspect = artwork.width.toFloat() / artwork.height.toFloat()
            val boxAspect = maxWidth / maxHeight
            val w: Dp
            val h: Dp
            if (bitmapAspect > boxAspect) {
                w = maxWidth
                h = maxWidth / bitmapAspect
            } else {
                h = maxHeight
                w = maxHeight * bitmapAspect
            }
            Image(
                bitmap = artwork.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(w, h).clip(RoundedCornerShape(ARTWORK_CORNER_RADIUS)),
            )
        } else {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            Box(
                Modifier
                    .size(side)
                    .clip(RoundedCornerShape(ARTWORK_CORNER_RADIUS))
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
    }
}

@Composable
private fun EqualizerBars(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val bars = 5
    Box(modifier, contentAlignment = Alignment.Center) {
        // Soft glow: a blurred wash of the ambient color behind the bars.
        // Modifier.blur is a no-op below API 31 (confirmed in PLAN.md/
        // DECISIONS.md research) — degrades gracefully to no glow, not a crash.
        Box(
            Modifier
                .fillMaxSize()
                .background(color.copy(alpha = 0.35f))
                .blur(24.dp)
        )
        Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            repeat(bars) { index ->
                // Staggered duration/phase per bar so they don't move in
                // lockstep — a plausible-looking loop, not real amplitude
                // data (see AudioVisual's doc comment for why).
                val duration = 500 + index * 90
                val phase by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = (2 * Math.PI).toFloat(),
                    animationSpec = infiniteRepeatable(
                        animation = tween(duration, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "bar$index",
                )
                val heightFraction = 0.25f + 0.75f * ((sin(phase) + 1f) / 2f)
                Box(
                    Modifier
                        .width(12.dp)
                        .fillMaxHeight(heightFraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.85f))
                )
            }
        }
    }
}
