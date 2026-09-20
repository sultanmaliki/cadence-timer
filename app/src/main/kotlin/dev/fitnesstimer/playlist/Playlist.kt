package dev.fitnesstimer.playlist

/** A named, saved list of local files, referenced by content URI — never copied. */
data class Playlist(
    val id: String,
    val name: String,
    val itemUris: List<String>,
)
