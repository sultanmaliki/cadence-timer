package dev.fitnesstimer.nowplaying

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure audio-analysis maths for the beat-reactive wave (no Android types, so
 * it is unit-tested). Input is a Visualizer FFT capture.
 *
 * Layout assumption (Visualizer.getFft, from the API as documented): for a
 * capture of N bytes, fft[0] = DC real, fft[1] = Nyquist real, then for
 * k = 1 until N/2: fft[2k] = real, fft[2k+1] = imaginary, all signed bytes.
 * Bin k is at k * samplingRate / N Hz. This layout is verified against real
 * device data in the on-device probe (see PLAN.md N.5e), not just assumed.
 */
const val BAND_LOW = 0
const val BAND_MID = 1
const val BAND_HIGH = 2
const val BAND_COUNT = 3

private val EDGES_HZ = floatArrayOf(20f, 250f, 2500f, 12000f)

/**
 * Raw (unnormalized) magnitude per band: RMS of the bins that fall in
 * [low 20-250 Hz, mid 250-2500 Hz, high 2500-12000 Hz]. Returns zeros for
 * empty/invalid input.
 */
fun computeBandMagnitudes(fft: ByteArray, samplingRateHz: Int): FloatArray {
    val out = FloatArray(BAND_COUNT)
    if (fft.size < 8 || samplingRateHz <= 0) return out
    val n = fft.size
    val binHz = samplingRateHz.toFloat() / n
    val maxBin = n / 2 - 1
    for (band in 0 until BAND_COUNT) {
        val from = max(1, kotlin.math.ceil(EDGES_HZ[band] / binHz).toInt())
        val to = minOf(maxBin, (EDGES_HZ[band + 1] / binHz).toInt())
        if (to < from) continue
        var sumSq = 0f
        for (k in from..to) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            sumSq += re * re + im * im
        }
        out[band] = sqrt(sumSq / (to - from + 1))
    }
    return out
}

/** Largest raw magnitude across all bins; 0 means the capture is silent/blocked. */
fun peakMagnitude(fft: ByteArray): Float {
    var m = 0f
    var k = 1
    while (2 * k + 1 < fft.size) {
        val re = fft[2 * k].toFloat()
        val im = fft[2 * k + 1].toFloat()
        m = max(m, sqrt(re * re + im * im))
        k++
    }
    return m
}

/**
 * Turns raw band magnitudes into a 0..1 "how alive is this band right now".
 *
 * 1. Adaptive gain: each band is divided by its own slowly decaying recent
 *    peak, so quiet and loud songs both use the full range and the bands are
 *    comparable. [minPeak] stops silence/noise being amplified into motion.
 * 2. Beat emphasis: the result is mostly the *rise above the recent average*
 *    (kicks, snares, note onsets), plus a small share of the steady level
 *    (expanded with a power curve). A constantly loud passage therefore reads
 *    calm-ish, while hits stand out as peaks. Scaling every band to its own
 *    peak alone gave flat-topped plateaus (measured on device).
 */
class BandNormalizer(
    private val decay: Float = 0.985f,
    private val minPeak: Float = 6f,
    private val avgAlpha: Float = 0.06f,
    private val floor: Float = 0.16f,
) {
    private val peak = FloatArray(BAND_COUNT) { minPeak }
    private val avg = FloatArray(BAND_COUNT)

    fun normalize(raw: FloatArray): FloatArray {
        val out = FloatArray(BAND_COUNT)
        for (i in 0 until BAND_COUNT) {
            peak[i] = max(max(raw[i], peak[i] * decay), minPeak)
            val x = (raw[i] / peak[i]).coerceIn(0f, 1f)
            val beat = max(0f, x - avg[i])
            avg[i] += (x - avg[i]) * avgAlpha
            // A floor keeps quiet/steady passages visibly alive (the wave used to shrink to
            // nothing mid-song); the steady level and the beat rise ride on top of it.
            val mix = 0.45f * x.pow(1.3f) + 1.6f * beat
            out[i] = (floor + (1f - floor) * mix).coerceIn(0f, 1f)
        }
        return out
    }
}

/** Fast attack, slower release, per band: reads as a beat that punches up and eases off. */
class BandSmoother(private val attack: Float = 0.65f, private val release: Float = 0.18f) {
    private val state = FloatArray(BAND_COUNT)

    fun smooth(input: FloatArray): FloatArray {
        for (i in 0 until BAND_COUNT) {
            val x = input[i]
            state[i] += (x - state[i]) * if (x > state[i]) attack else release
        }
        return state.copyOf()
    }
}
