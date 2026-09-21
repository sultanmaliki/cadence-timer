package dev.fitnesstimer.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import dev.fitnesstimer.MainActivity

private const val TAG = "FitnessTimer"
private const val CHANNEL_ID = "countdown_done"
private const val NOTIFICATION_ID = 2001
private const val ALARM_REQUEST_CODE = 1

/**
 * Pure: how long until a running countdown completes, or null when no alarm
 * is needed (not a countdown, not running, or already at/over its target).
 * [TimerSnapshot.elapsedMs] is computed from the clock at snapshot time, so
 * this is accurate even if the UI's ticking state is stale.
 */
fun alarmDelayMs(snap: TimerSnapshot): Long? {
    if (snap.mode != TimerMode.COUNTDOWN || !snap.running) return null
    val remaining = snap.targetMs - snap.elapsedMs
    return if (remaining > 0) remaining else null
}

/**
 * PLAN.md section F: the countdown must finish audibly even with the app
 * backgrounded or the screen off (the normal state now that music plays in
 * another app). An exact, Doze-piercing alarm fires [CountdownAlarmReceiver],
 * which posts a high-importance alarm-sound notification (a bare sound call
 * from the background is unreliable on recent Android, PLAN.md F).
 *
 * Alarms are cancelled by Android on shutdown and on force-stop (documented),
 * so [BootReceiver] reschedules after a reboot.
 */
object CountdownAlarm {
    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, CountdownAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Schedules or cancels the alarm so it matches [snap]. Idempotent. */
    fun sync(context: Context, snap: TimerSnapshot) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        val delay = alarmDelayMs(snap)
        if (delay == null) {
            am.cancel(pi)
            return
        }
        val at = SystemClock.elapsedRealtime() + delay
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            // Exact alarms revoked between the check and the call: degrade to inexact.
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
        Log.d(TAG, "[alarm] scheduled in ${delay}ms exact=$exact")
    }

    fun notifyFinished(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) {
            Log.w(TAG, "[alarm] countdown finished but notifications are disabled/denied")
            return
        }
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val channel = NotificationChannel(CHANNEL_ID, "Countdown finished", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Sounds when a countdown reaches zero"
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
        }
        nm.createNotificationChannel(channel) // no-op if it already exists
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time's up")
            .setContentText("Countdown finished")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "[alarm] finished notification posted")
    }

    fun cancelFinishedNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
