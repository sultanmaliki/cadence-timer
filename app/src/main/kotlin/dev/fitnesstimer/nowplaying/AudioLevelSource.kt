package dev.fitnesstimer.nowplaying

import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "FitnessTimer"

/**
 * Real-time low/mid/high levels of whatever the phone is currently playing,
 * for the beat-reactive wave (PLAN.md N.5e).
 *
 * Uses android.media.audiofx.Visualizer on audio session 0 (the output mix),
 * the only unprivileged way to see another app's audio. It needs the
 * RECORD_AUDIO permission (the Visualizer is documented as gated on it) plus
 * MODIFY_AUDIO_SETTINGS, and is documented as partial/low-quality data — fine
 * for a visualization, and nothing is recorded, stored or sent anywhere.
 *
 * It only runs while (a) the UI wants it, (b) the Activity is visible and
 * (c) the permission is granted, so it costs nothing in the background.
 * Whether session 0 actually delivers data on a given Android version/ROM is
 * device-dependent: [status] reports ACTIVE / SILENT / FAILED honestly and
 * the UI falls back to a quiet idle wave rather than faking motion.
 */
object AudioLevelSource {
    enum class Status { STOPPED, ACTIVE, SILENT, FAILED }

    private val _status = MutableStateFlow(Status.STOPPED)
    val status: StateFlow<Status> = _status

    private val _permission = MutableStateFlow(false)
    val permission: StateFlow<Boolean> = _permission

    /** Latest smoothed low/mid/high levels, 0..1. Written from the capture thread. */
    @Volatile
    var levels: FloatArray = FloatArray(BAND_COUNT)
        private set

    private var visualizer: Visualizer? = null
    private var wanted = false
    private var visible = false
    private var normalizer = BandNormalizer()
    private var smoother = BandSmoother()
    private var silentCallbacks = 0
    private var callbacks = 0

    fun refreshPermission(context: Context) {
        _permission.value = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        apply()
    }

    fun setWanted(value: Boolean) {
        wanted = value
        apply()
    }

    fun setVisible(value: Boolean) {
        visible = value
        apply()
    }

    private fun apply() {
        val shouldRun = wanted && visible && _permission.value
        if (shouldRun && visualizer == null) start() else if (!shouldRun && visualizer != null) stop()
    }

    private fun start() {
        try {
            val v = Visualizer(0) // 0 = output mix
            val sizeRange = Visualizer.getCaptureSizeRange()
            v.captureSize = sizeRange[1].coerceAtMost(1024)
            normalizer = BandNormalizer()
            smoother = BandSmoother()
            silentCallbacks = 0
            callbacks = 0
            val rate = Visualizer.getMaxCaptureRate().coerceAtLeast(1)
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

                    override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null) return
                        onFft(fft, samplingRate / 1000)
                    }
                },
                rate,
                /* waveform = */ false,
                /* fft = */ true,
            )
            v.enabled = true
            visualizer = v
            Log.d(TAG, "[viz] started session=0 captureSize=${v.captureSize} rate=${rate}mHz samplingRate=${v.samplingRate}mHz")
        } catch (e: Exception) {
            Log.w(TAG, "[viz] FAILED to start: ${e.javaClass.simpleName}: ${e.message}")
            visualizer = null
            _status.value = Status.FAILED
        }
    }

    private fun onFft(fft: ByteArray, samplingRateHz: Int) {
        callbacks++
        val peak = peakMagnitude(fft)
        if (peak <= 0f) {
            silentCallbacks++
            if (silentCallbacks >= 60 && _status.value != Status.SILENT) {
                _status.value = Status.SILENT
                Log.w(TAG, "[viz] SILENT: capture returns only zeros (blocked or nothing playing)")
            }
        } else {
            silentCallbacks = 0
            if (_status.value != Status.ACTIVE) {
                _status.value = Status.ACTIVE
                Log.d(TAG, "[viz] ACTIVE: first non-zero data peak=$peak samplingRate=${samplingRateHz}Hz size=${fft.size}")
            }
        }
        val bands = smoother.smooth(normalizer.normalize(computeBandMagnitudes(fft, samplingRateHz)))
        levels = bands
        if (callbacks % 40 == 1) {
            Log.d(TAG, "[viz] peak=${"%.1f".format(peak)} low=${"%.2f".format(bands[0])} mid=${"%.2f".format(bands[1])} high=${"%.2f".format(bands[2])}")
        }
    }

    private fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "[viz] error while stopping", e)
        }
        visualizer = null
        levels = FloatArray(BAND_COUNT)
        _status.value = Status.STOPPED
        Log.d(TAG, "[viz] stopped")
    }
}
