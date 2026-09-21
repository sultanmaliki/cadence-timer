package dev.fitnesstimer.playlist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local-only playlist storage: a small JSON file in the app's private
 * storage (never synced/cloud, matches PLAN.md's no-backend requirement),
 * holding each playlist's name and the content:// URIs of the files it
 * references. No file copying — just persisted-permission URIs (see
 * MainScreen's takePersistableUriPermission calls), same as a single picked
 * file.
 *
 * Uses org.json (built into Android) rather than adding a serialization
 * dependency for a data shape this small.
 */
class PlaylistRepository(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "playlists.json"))

    fun load(): List<Playlist> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val uris = obj.getJSONArray("itemUris")
                Playlist(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    itemUris = (0 until uris.length()).map { uris.getString(it) },
                )
            }
        } catch (e: Exception) {
            // Keep the unreadable file instead of letting the next save
            // silently overwrite (and permanently lose) it.
            val backup = File(file.parentFile, file.name + ".corrupt")
            backup.delete()
            file.renameTo(backup)
            emptyList()
        }
    }

    fun save(playlists: List<Playlist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            obj.put("itemUris", JSONArray(playlist.itemUris))
            array.put(obj)
        }
        // Write-then-rename so a crash/kill mid-write can't leave a truncated
        // file (load() would treat that as "no playlists" and lose them all).
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "could not replace ${file.name}" }
        }
    }
}
