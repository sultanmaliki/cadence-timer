package dev.fitnesstimer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.fitnesstimer.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Debug-only hook: `adb shell am start -n dev.fitnesstimer/.MainActivity
        // --es debug_media_uri file:///...` loads a file without the SAF picker.
        // Exists because the test phone's MIUI blocks scripted taps, so the
        // picker can't be driven from adb — this lets the playback pipeline
        // be tested independently of it. Harmless when the extra is absent.
        val debugUri = intent.getStringExtra("debug_media_uri")?.let { Uri.parse(it) }
        setContent {
            MainScreen(debugUri = debugUri)
        }
    }
}
