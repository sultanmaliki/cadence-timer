package dev.fitnesstimer.nowplaying

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingLogicTest {
    private val own = "dev.fitnesstimer"
    private fun c(id: String, pkg: String, state: Int) = SessionCandidate(id, pkg, state)

    // ---- SessionSelector ----
    @Test fun picksPlayingSession() {
        val r = SessionSelector.select(
            listOf(c("0", "a", PlaybackState.STATE_PAUSED), c("1", "b", PlaybackState.STATE_PLAYING)), own)
        assertEquals("1", r?.id)
    }

    @Test fun excludesOwnPackageEvenIfPlaying() {
        assertNull(SessionSelector.select(listOf(c("0", own, PlaybackState.STATE_PLAYING)), own))
    }

    @Test fun ignoresStaleStoppedAndNoneSessions() { // the YouTube-app leftover seen on the device
        val r = SessionSelector.select(
            listOf(
                c("0", "yt", PlaybackState.STATE_STOPPED),
                c("1", "x", PlaybackState.STATE_NONE),
                c("2", "err", PlaybackState.STATE_ERROR),
            ), own)
        assertNull(r)
    }

    @Test fun realWorldStackFromProbe() {
        val r = SessionSelector.select(
            listOf(
                c("0", "com.miui.player", PlaybackState.STATE_PLAYING),
                c("1", "com.google.android.youtube", PlaybackState.STATE_STOPPED),
                c("2", own, PlaybackState.STATE_NONE),
            ), own)
        assertEquals("com.miui.player", r?.packageName)
    }

    @Test fun pausedIsShownWhenNothingPlays() {
        val r = SessionSelector.select(
            listOf(c("0", "a", PlaybackState.STATE_PAUSED), c("1", "b", PlaybackState.STATE_STOPPED)), own)
        assertEquals("0", r?.id)
    }

    @Test fun playingBeatsBufferingBeatsPaused() {
        val list = listOf(
            c("p", "a", PlaybackState.STATE_PAUSED),
            c("b", "b", PlaybackState.STATE_BUFFERING),
            c("g", "c", PlaybackState.STATE_PLAYING),
        )
        assertEquals("g", SessionSelector.select(list, own)?.id)
        assertEquals("b", SessionSelector.select(list.filter { it.id != "g" }, own)?.id)
    }

    @Test fun tiesKeepInputOrder() {
        val list = listOf(c("0", "a", PlaybackState.STATE_PLAYING), c("1", "b", PlaybackState.STATE_PLAYING))
        assertEquals("0", SessionSelector.select(list, own)?.id)
    }

    @Test fun preferredPackageWinsAmongEquals() {
        val list = listOf(c("0", "a", PlaybackState.STATE_PLAYING), c("1", "b", PlaybackState.STATE_PLAYING))
        assertEquals("1", SessionSelector.select(list, own, preferredPackage = "b")?.id)
    }

    @Test fun preferredPackageDoesNotOverrideRank() {
        val list = listOf(c("0", "a", PlaybackState.STATE_PLAYING), c("1", "b", PlaybackState.STATE_PAUSED))
        assertEquals("0", SessionSelector.select(list, own, preferredPackage = "b")?.id)
    }

    @Test fun emptyListGivesNull() = assertNull(SessionSelector.select(emptyList(), own))

    @Test fun seekingAndSkippingCountAsPlaying() {
        assertTrue(isPlayingState(PlaybackState.STATE_FAST_FORWARDING))
        assertTrue(isPlayingState(PlaybackState.STATE_SKIPPING_TO_NEXT))
        assertFalse(isPlayingState(PlaybackState.STATE_PAUSED))
    }

    // ---- extrapolatePosition ----
    @Test fun playingAdvancesBySpeedTimesElapsed() =
        assertEquals(12_500L, extrapolatePosition(10_000, 1_000, 1f, true, 3_500, 200_000))

    @Test fun halfSpeedAdvancesHalf() =
        assertEquals(11_000L, extrapolatePosition(10_000, 1_000, 0.5f, true, 3_000, 200_000))

    @Test fun pausedDoesNotAdvance() =
        assertEquals(10_000L, extrapolatePosition(10_000, 1_000, 1f, false, 9_000, 200_000))

    @Test fun clampsToDuration() =
        assertEquals(60_000L, extrapolatePosition(59_000, 1_000, 1f, true, 50_000, 60_000))

    @Test fun unknownPositionStaysUnknown() =
        assertEquals(POSITION_UNKNOWN, extrapolatePosition(-1, 1_000, 1f, true, 5_000, 60_000))

    @Test fun unknownDurationDoesNotClamp() =
        assertEquals(1_000_000L, extrapolatePosition(999_000, 1, 1f, true, 1_001, -1))

    @Test fun clockGoingBackwardsDoesNotRewind() =
        assertEquals(10_000L, extrapolatePosition(10_000, 5_000, 1f, true, 4_000, 60_000))

    @Test fun zeroUpdateTimeMeansNoExtrapolation() =
        assertEquals(10_000L, extrapolatePosition(10_000, 0, 1f, true, 9_999, 60_000))

    @Test fun progressFractions() {
        assertEquals(0.5f, progressFraction(30_000, 60_000)!!, 0.0001f)
        assertNull(progressFraction(-1, 60_000))
        assertNull(progressFraction(1_000, -1))
        assertNull(progressFraction(1_000, 0))
        assertEquals(1f, progressFraction(90_000, 60_000)!!, 0.0001f)
    }

    // ---- action flags ----
    private fun np(actions: Long) =
        NowPlaying("p", null, null, null, null, false, -1, PlaybackState.STATE_PLAYING, 0, 0, 1f, actions)

    @Test fun actionFlagsMatchMiMusicProbe() { // actions=2360319 observed on the device
        val mi = np(2360319L)
        assertTrue(mi.canSkipNext); assertTrue(mi.canSkipPrevious); assertTrue(mi.canSeek)
    }

    @Test fun actionFlagsMatchStaleYouTubeSession() { // actions=8615: skip-next + seek, no previous
        val yt = np(8615L)
        assertTrue(yt.canSkipNext); assertFalse(yt.canSkipPrevious); assertTrue(yt.canSeek)
    }

    @Test fun noActionsMeansNoCapabilities() {
        val n = np(0L)
        assertFalse(n.canSkipNext); assertFalse(n.canSkipPrevious); assertFalse(n.canSeek)
    }
}
