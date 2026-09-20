package dev.fitnesstimer.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.fitnesstimer.playlist.Playlist
import dev.fitnesstimer.playlist.PlaylistRepository
import java.util.UUID

/**
 * Create / play / delete local playlists, and add more files to an existing
 * one. Renaming and removing individual items aren't built yet — a
 * documented simplification, not a silent gap: add via a new "Edit" action
 * later if it's actually needed.
 */
@Composable
fun PlaylistScreen(
    onDismiss: () -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { PlaylistRepository(context) }
    var playlists by remember { mutableStateOf(repo.load()) }

    var showNameDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var pendingNewPlaylistName by remember { mutableStateOf<String?>(null) }
    var addingToPlaylistId by remember { mutableStateOf<String?>(null) }

    fun persist(updated: List<Playlist>) {
        playlists = updated
        repo.save(updated)
    }

    val createPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val name = pendingNewPlaylistName
        pendingNewPlaylistName = null
        if (name != null && uris.isNotEmpty()) {
            uris.forEach { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            persist(playlists + Playlist(UUID.randomUUID().toString(), name, uris.map { it.toString() }))
        }
    }

    val addFilesPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val targetId = addingToPlaylistId
        addingToPlaylistId = null
        if (targetId != null && uris.isNotEmpty()) {
            uris.forEach { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            persist(
                playlists.map { p ->
                    if (p.id == targetId) p.copy(itemUris = p.itemUris + uris.map { it.toString() }) else p
                }
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0B))) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Playlists", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                nameInput = ""
                showNameDialog = true
            }) {
                Text("+ New playlist")
            }
            Spacer(Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Text("No playlists yet.", color = Color.White.copy(alpha = 0.6f))
            } else {
                LazyColumn {
                    items(playlists, key = { it.id }) { playlist ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1C1C1C))
                                .padding(12.dp)
                        ) {
                            Text(playlist.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${playlist.itemUris.size} item(s)",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onPlayPlaylist(playlist) }) { Text("Play") }
                                TextButton(onClick = {
                                    addingToPlaylistId = playlist.id
                                    addFilesPicker.launch(arrayOf("video/*", "audio/*"))
                                }) { Text("Add files") }
                                TextButton(onClick = { persist(playlists.filterNot { it.id == playlist.id }) }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nameInput.isNotBlank(),
                    onClick = {
                        showNameDialog = false
                        pendingNewPlaylistName = nameInput.trim()
                        createPicker.launch(arrayOf("video/*", "audio/*"))
                    },
                ) { Text("Pick files") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            },
        )
    }
}
