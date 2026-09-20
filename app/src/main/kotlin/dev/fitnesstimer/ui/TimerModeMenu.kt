package dev.fitnesstimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * PLAN.md section E's "top-left tap = timer mode picker", finally wired up.
 * Countdown duration entry is a plain "mm:ss" text field — the simplest
 * thing that works, not a wheel/dial picker.
 */
@Composable
fun TimerModeMenu(
    onDismiss: () -> Unit,
    onChooseStopwatch: () -> Unit,
    onChooseCountdown: (targetMs: Long) -> Unit,
) {
    var showDurationDialog by remember { mutableStateOf(false) }
    var durationInput by remember { mutableStateOf("05:00") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = 56.dp, start = 16.dp)
                .width(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1C1C1C))
        ) {
            Text(
                "Stopwatch",
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onChooseStopwatch)
                    .padding(16.dp),
            )
            Text(
                "Countdown",
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { showDurationDialog = true })
                    .padding(16.dp),
            )
        }
    }

    if (showDurationDialog) {
        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = { Text("Countdown length") },
            text = {
                OutlinedTextField(
                    value = durationInput,
                    onValueChange = { durationInput = it },
                    label = { Text("mm:ss") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetMs = parseMmSs(durationInput)
                    if (targetMs != null && targetMs > 0) {
                        showDurationDialog = false
                        onChooseCountdown(targetMs)
                    }
                }) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showDurationDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private fun parseMmSs(input: String): Long? {
    val parts = input.trim().split(":")
    if (parts.size != 2) return null
    val minutes = parts[0].toLongOrNull() ?: return null
    val seconds = parts[1].toLongOrNull() ?: return null
    if (seconds !in 0..59) return null
    return (minutes * 60 + seconds) * 1000
}
