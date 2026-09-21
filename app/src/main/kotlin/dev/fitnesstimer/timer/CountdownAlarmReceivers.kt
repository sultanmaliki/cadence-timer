package dev.fitnesstimer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "FitnessTimer"

/** Fires when the exact alarm goes off, possibly in a freshly started process. */
class CountdownAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // In a cold process this restores the saved timer (crediting the time
        // since it was saved); in a warm one it is a no-op.
        AppTimer.init(context)
        val finished = AppTimer.engine.settleIfFinished()
        Log.d(TAG, "[alarm] fired: finished=$finished uiVisible=${AppTimer.uiVisible}")
        // If the screen is up, the UI's own loop already gives haptic feedback.
        if (finished && !AppTimer.uiVisible) CountdownAlarm.notifyFinished(context)
    }
}

/** Alarms don't survive a reboot (documented): restore the timer and reschedule. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AppTimer.init(context) // restores state and reschedules a still-running countdown
        Log.d(TAG, "[alarm] boot: completedWhileAway=${AppTimer.completedWhileAway}")
        if (AppTimer.completedWhileAway) CountdownAlarm.notifyFinished(context)
    }
}
