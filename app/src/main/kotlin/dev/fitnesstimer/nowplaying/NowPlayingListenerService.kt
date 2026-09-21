package dev.fitnesstimer.nowplaying

import android.service.notification.NotificationListenerService

/**
 * Exists ONLY so the user can grant notification access: an enabled
 * listener is what authorizes MediaSessionManager.getActiveSessions() for
 * this app. It deliberately ignores every notification (no
 * onNotificationPosted/onNotificationRemoved overrides) — media sessions are
 * the only thing this app reads.
 */
class NowPlayingListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        android.util.Log.d("FitnessTimer", "[nowplaying] onListenerConnected")
        NowPlayingRepository.init(this)
        NowPlayingRepository.refresh()
    }

    override fun onListenerDisconnected() {
        NowPlayingRepository.refresh()
    }
}
