package dev.fitnesstimer.render

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * PLAN.md section H — confirmed working on-device (DECISIONS.md,
 * 2026-09-20): a Compose `drawText(blendMode = Difference)` overlay
 * genuinely inverts pixels of a TextureView-backed video behind it, as long
 * as this draw is NOT wrapped in `CompositingStrategy.Offscreen`.
 *
 * [holdProgress] (0f..1f, 0 = idle) draws the hold-to-reset ring from
 * section E around the text; growing clockwise, cleared by the caller on
 * cancel/completion.
 *
 * Sizing/position is entirely up to [modifier] — this composable centers
 * the text within whatever bounds it's given, not the whole screen. Video
 * mode passes `Modifier.fillMaxSize()` (timer centered over the video);
 * audio mode passes a bounded modifier positioned below the artwork
 * (MainScreen), so the two don't overlap.
 */
@Composable
fun NegativeTimerText(
    text: String,
    holdProgress: Float,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    Box(modifier.drawWithContent {
        drawContent()
        val layout = textMeasurer.measure(
            text = text,
            style = TextStyle(fontSize = 64.sp, color = Color.White),
        )
        val topLeft = Offset(
            x = (size.width - layout.size.width) / 2f,
            y = (size.height - layout.size.height) / 2f,
        )
        drawText(textLayoutResult = layout, topLeft = topLeft, blendMode = BlendMode.Difference)

        if (holdProgress > 0f) {
            val ringRadius = (layout.size.width.coerceAtLeast(layout.size.height)) * 0.75f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * holdProgress,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = 6f),
                blendMode = BlendMode.Difference,
            )
        }
    })
}
