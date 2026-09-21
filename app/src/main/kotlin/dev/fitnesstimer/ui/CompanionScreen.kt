package dev.fitnesstimer.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.fitnesstimer.nowplaying.NowPlaying
import dev.fitnesstimer.nowplaying.extrapolatePosition
import dev.fitnesstimer.nowplaying.progressFraction
import dev.fitnesstimer.render.AudioVisual
import dev.fitnesstimer.render.NegativeTimerText
import kotlinx.coroutines.delay

/**
 * Companion-mode body (PLAN.md section N): shows what ANOTHER app is playing.
 * Lives inside MainScreen's gesture Box, so it must not contain any clickable
 * elements (a tap here would also fire the timer's tap-to-start gesture);
 * the interactive onboarding card is a separate overlay, outside that Box.
 */
@Composable
fun CompanionBody(
    nowPlaying: NowPlaying?,
    accessGranted: Boolean,
    ambientColor: Color,
    timerText: () -> String,
    holdProgress: () -> Float,
) {
    Box(Modifier.fillMaxSize()) {
        if (nowPlaying != null) {
            AudioVisual(
                artwork = nowPlaying.artwork,
                ambientColor = ambientColor,
                isPlaying = nowPlaying.isPlaying,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        nowPlaying.title ?: "Unknown title",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    val subtitle = listOfNotNull(nowPlaying.artist, nowPlaying.album).joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                        )
                    }
                    NegativeTimerText(
                        text = timerText,
                        holdProgress = holdProgress,
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                    )
                }
            }
            NowPlayingProgress(
                nowPlaying,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            )
        } else {
            if (accessGranted) {
                Text(
                    "Nothing playing — start music in any app",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
                )
            }
            NegativeTimerText(text = timerText, holdProgress = holdProgress, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Thin progress line; ticks only while playing, hidden if position/duration are unknown. */
@Composable
private fun NowPlayingProgress(np: NowPlaying, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(np.isPlaying, np.positionUpdateElapsedMs, np.positionMs) {
        now = SystemClock.elapsedRealtime()
        while (np.isPlaying) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }
    val pos = extrapolatePosition(np.positionMs, np.positionUpdateElapsedMs, np.speed, np.isPlaying, now, np.durationMs)
    val fraction = progressFraction(pos, np.durationMs) ?: return
    Box(modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White.copy(alpha = 0.6f)))
    }
}

/**
 * Onboarding for notification access. Explains exactly what is read, opens
 * the system settings page, and mentions the Android 13+ "restricted
 * setting" unblock that sideloaded installs may hit (PLAN.md N, DECISIONS.md).
 * A separate overlay (not inside the gesture Box) so its buttons don't also
 * trigger the timer gestures.
 */
@Composable
fun NotificationAccessCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1C1C))
                .padding(16.dp),
        ) {
            Text("Show what's playing", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Allow notification access so this app can show the song, artwork and progress from " +
                    "your music apps and control them. It only reads media playback info — never your " +
                    "notifications — and nothing leaves your phone.",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "If Android says \"Restricted setting\": open this app's App info, tap ⋮ (top right), " +
                    "choose \"Allow restricted settings\", then try again.",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Not now") }
                Button(onClick = { openNotificationAccessSettings(context) }) { Text("Open settings") }
            }
        }
    }
}

private fun openNotificationAccessSettings(context: android.content.Context) {
    val primary = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(primary)
    } catch (e: ActivityNotFoundException) {
        // Some ROMs lack the dedicated page; App info is the fallback.
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
