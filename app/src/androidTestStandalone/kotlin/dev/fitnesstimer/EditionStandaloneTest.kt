package dev.fitnesstimer

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.test.advanceEventTime
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fitnesstimer.timer.AppTimer
import dev.fitnesstimer.ui.MainScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The STANDALONE edition exists so it can be sideloaded on phones where Google Play Protect blocks apps
 * declaring notification-listener (and SMS / accessibility) access. These tests check the installed
 * package itself, so the blocked declarations can never creep back in unnoticed.
 */
@RunWith(AndroidJUnit4::class)
class EditionStandaloneTest {
    @get:Rule val rule = createComposeRule()
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val pm = ctx.packageManager

    @Test fun noNotificationListenerServiceIsDeclared() {
        val cn = ComponentName(ctx.packageName, "dev.fitnesstimer.nowplaying.NowPlayingListenerService")
        try {
            pm.getServiceInfo(cn, 0)
            throw AssertionError("NowPlayingListenerService must not be declared in the standalone edition")
        } catch (expected: PackageManager.NameNotFoundException) { /* good */ }
    }

    @Test fun noServiceRequiresTheSensitiveBindPermissions() {
        val services = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SERVICES).services.orEmpty()
        val sensitive = setOf("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "android.permission.BIND_ACCESSIBILITY_SERVICE")
        services.forEach { assertFalse("${it.name} needs ${it.permission}", it.permission in sensitive) }
    }

    @Test fun noSensitivePermissionsAreRequested() {
        val requested = pm.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        for (p in listOf("RECEIVE_SMS", "READ_SMS", "RECORD_AUDIO", "MODIFY_AUDIO_SETTINGS", "INTERNET")) {
            assertFalse("android.permission.$p must not be requested", "android.permission.$p" in requested)
        }
    }

    @Test fun companionModeIsOffAndTheMenuHasNoNowPlayingEntry() {
        assertFalse(ctx.resources.getBoolean(R.bool.companion_enabled))
        AppTimer.init(ctx)
        rule.setContent { MainScreen() }
        rule.waitForIdle()
        rule.onRoot().performTouchInput { down(0, Offset(width - 30f, 30f)); advanceEventTime(50); up(0) }
        rule.waitForIdle()
        rule.onNodeWithText("Choose file").assertIsDisplayed()
        rule.onNodeWithText("Playlists").assertIsDisplayed()
        rule.onNodeWithText("Now playing (other apps)").assertDoesNotExist()
        // and no notification-access onboarding card is ever shown
        rule.onNodeWithText("Show what's playing").assertDoesNotExist()
    }
}
