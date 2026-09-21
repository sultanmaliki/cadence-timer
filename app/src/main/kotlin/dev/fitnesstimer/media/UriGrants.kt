package dev.fitnesstimer.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * takePersistableUriPermission throws SecurityException if the provider
 * didn't offer a persistable grant; that must not crash the app — the file
 * still plays this session, it just won't survive a restart. Returns whether
 * the grant was persisted.
 */
fun tryPersistReadGrant(context: Context, uri: Uri): Boolean =
    try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: Exception) {
        Log.w("FitnessTimer", "could not persist read grant for $uri", e)
        false
    }

/** Android caps persisted grants per app; free ones we no longer reference. */
fun releaseReadGrant(context: Context, uri: Uri) {
    try {
        context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (e: Exception) {
        Log.w("FitnessTimer", "could not release read grant for $uri", e)
    }
}
