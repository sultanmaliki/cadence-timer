package dev.fitnesstimer.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBandsTest {
    private val n = 1024
    private val rate = 44100
    private val binHz = rate.toFloat() / n // ~43 Hz

    /** Builds a Visualizer-style FFT capture with energy only at the given frequencies. */
    private fun fftWith(vararg energy: Pair<Float, Int>): ByteArray {
        val fft = ByteArray(n)
        for ((hz, mag) in energy) {
            val k = (hz / binHz).toInt()
            fft[2 * k] = mag.toByte()
        }
        return fft
    }

    @Test fun silenceGivesZeros() {
        assertTrue(computeBandMagnitudes(ByteArray(n), rate).all { it == 0f })
        assertEquals(0f, peakMagnitude(ByteArray(n)), 0f)
    }

    @Test fun invalidInputGivesZerosNotCrash() {
        assertTrue(computeBandMagnitudes(ByteArray(0), rate).all { it == 0f })
        assertTrue(computeBandMagnitudes(ByteArray(4), rate).all { it == 0f })
        assertTrue(computeBandMagnitudes(ByteArray(n), 0).all { it == 0f })
    }

    @Test fun bassEnergyLandsInLowBand() {
        val m = computeBandMagnitudes(fftWith(100f to 100), rate)
        assertTrue(m[BAND_LOW] > 0f); assertEquals(0f, m[BAND_MID], 0f); assertEquals(0f, m[BAND_HIGH], 0f)
    }

    @Test fun midEnergyLandsInMidBand() {
        val m = computeBandMagnitudes(fftWith(1000f to 100), rate)
        assertEquals(0f, m[BAND_LOW], 0f); assertTrue(m[BAND_MID] > 0f); assertEquals(0f, m[BAND_HIGH], 0f)
    }

    @Test fun trebleEnergyLandsInHighBand() {
        val m = computeBandMagnitudes(fftWith(6000f to 100), rate)
        assertEquals(0f, m[BAND_LOW], 0f); assertEquals(0f, m[BAND_MID], 0f); assertTrue(m[BAND_HIGH] > 0f)
    }

    @Test fun imaginaryPartCountsTowardMagnitude() {
        val re = ByteArray(n); val im = ByteArray(n)
        val k = (1000f / binHz).toInt()
        re[2 * k] = 60; im[2 * k + 1] = 80          // 3-4-5 triangle: magnitude 100 in that bin
        val both = ByteArray(n).also { it[2 * k] = 60; it[2 * k + 1] = 80 }
        assertEquals(100f, peakMagnitude(both), 0.01f)
        assertTrue(computeBandMagnitudes(both, rate)[BAND_MID] > computeBandMagnitudes(re, rate)[BAND_MID])
    }

    @Test fun sampleRateChangesBinMapping() {
        // At 48 kHz, bin k=23 is ~1078 Hz (mid); the same bin at 8 kHz would be ~180 Hz (low).
        val fft = ByteArray(n).also { it[2 * 23] = 100 }
        assertTrue(computeBandMagnitudes(fft, 48000)[BAND_MID] > 0f)
        assertTrue(computeBandMagnitudes(fft, 8000)[BAND_LOW] > 0f)
    }

    // ---- normalizer ----
    @Test fun normalizerOutputStaysInRange() {
        val nrm = BandNormalizer()
        repeat(200) { i -> nrm.normalize(floatArrayOf(i * 3f, 500f, 0.1f)).forEach { assertTrue(it in 0f..1f) } }
    }

    @Test fun silenceIsNotAmplifiedIntoMotion() {
        val out = BandNormalizer().normalize(floatArrayOf(0.5f, 0.5f, 0.5f)) // below minPeak
        assertTrue(out.all { it < 0.35f })
    }

    @Test fun aKickAfterSilenceIsStrong() {
        val nrm = BandNormalizer()
        repeat(30) { nrm.normalize(floatArrayOf(0f, 0f, 0f)) }
        assertTrue(nrm.normalize(floatArrayOf(300f, 0f, 0f))[BAND_LOW] > 0.8f)
    }

    @Test fun steadyLoudSoundReadsCalmerThanAKick() {
        val nrm = BandNormalizer()
        var steady = 0f
        repeat(200) { steady = nrm.normalize(floatArrayOf(300f, 0f, 0f))[BAND_LOW] }
        val kick = BandNormalizer().also { n -> repeat(30) { n.normalize(floatArrayOf(0f, 0f, 0f)) } }
            .normalize(floatArrayOf(300f, 0f, 0f))[BAND_LOW]
        assertTrue("steady=$steady kick=$kick", steady < 0.6f && kick > steady + 0.3f)
    }

    @Test fun quietAndLoudSongsReactAlike() {
        fun run(scale: Float): List<Float> {
            val nrm = BandNormalizer()
            val pattern = listOf(0f, 0f, 1f, 0.3f, 0.3f, 1f, 0.2f, 0f)   // same rhythm, different loudness
            return pattern.map { nrm.normalize(floatArrayOf(it * scale, 0f, 0f))[BAND_LOW] }
        }
        val quiet = run(40f); val loud = run(4000f)
        for (i in quiet.indices) assertEquals("step $i", quiet[i], loud[i], 0.08f)
    }

    @Test fun gainRecoversAfterALoudPassageEnds() {
        val nrm = BandNormalizer()
        repeat(5) { nrm.normalize(floatArrayOf(1000f, 0f, 0f)) }
        val right = nrm.normalize(floatArrayOf(100f, 0f, 0f))[0]
        var later = right
        repeat(1500) { later = nrm.normalize(floatArrayOf(100f, 0f, 0f))[0] }
        assertTrue("right=$right later=$later", later > right)
    }

    // ---- smoother ----
    @Test fun smootherAttacksFasterThanItReleases() {
        val up = BandSmoother().smooth(floatArrayOf(1f, 1f, 1f))[0]
        val sm = BandSmoother(); sm.smooth(floatArrayOf(1f, 1f, 1f))
        val afterDrop = sm.smooth(floatArrayOf(0f, 0f, 0f))[0]
        assertTrue(up > 0.5f)                    // jumps up quickly
        assertTrue(afterDrop > 0.5f)             // but eases down slowly
        assertTrue((1f - up) < (up - afterDrop) + 1f)
    }

    @Test fun smootherConvergesToConstantInput() {
        val sm = BandSmoother(); var v = 0f
        repeat(100) { v = sm.smooth(floatArrayOf(0.6f, 0.6f, 0.6f))[0] }
        assertEquals(0.6f, v, 0.001f)
    }
}
