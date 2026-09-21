package dev.fitnesstimer.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Connects to [PlaybackService]'s session — the UI's only way to reach the player (see PlaybackService's doc comment). */
@androidx.annotation.OptIn(UnstableApi::class)
suspend fun connectMediaController(context: Context): MediaController {
    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val future = MediaController.Builder(context, token).buildAsync()
    return suspendCancellableCoroutine { cont ->
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            MoreExecutors.directExecutor(),
        )
        cont.invokeOnCancellation { MediaController.releaseFuture(future) }
    }
}
