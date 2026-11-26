package com.airtouch.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPolicyTest {
    private val music = SessionSnapshot("music", "com.music", "Music", playing = false)
    private val spotify = SessionSnapshot("spotify", "com.spotify.music", "Spotify", playing = true)
    private val other = SessionSnapshot("other", "com.other", "Other", playing = false)

    @Test
    fun manualTargetNeverFallsThroughToAnotherApp() {
        assertNull(selectSession(listOf(music, spotify), TargetMode.MANUAL, "com.missing", null))
        assertEquals(
            "com.spotify.music",
            selectSession(listOf(music, spotify), TargetMode.MANUAL, "com.spotify.music", "com.music")?.packageName,
        )
    }

    @Test
    fun autoPrefersPlayingThenLastPackageThenFirst() {
        assertEquals("com.spotify.music", selectSession(listOf(music, spotify, other), TargetMode.AUTO, null, "com.other")?.packageName)
        val paused = listOf(music, other)
        assertEquals("com.other", selectSession(paused, TargetMode.AUTO, null, "com.other")?.packageName)
        assertEquals("com.music", selectSession(paused, TargetMode.AUTO, null, null)?.packageName)
        assertNull(selectSession(emptyList(), TargetMode.AUTO, null, "com.music"))
    }

    @Test
    fun playPauseFollowsPhaseAndDoesNotInventSupport() {
        val playing = caps(phase = PlaybackPhase.PLAYING, canPause = true)
        assertEquals(TransportAction.PAUSE, (choosePlayPause(playing) as TransportChoice.Send).action)
        val paused = caps(phase = PlaybackPhase.PAUSED, canPlay = true)
        assertEquals(TransportAction.PLAY, (choosePlayPause(paused) as TransportChoice.Send).action)
        assertTrue(chooseSkip(next = true, caps(canNext = false)) is TransportChoice.Reject)
        val unknown = choosePlayPause(caps(phase = PlaybackPhase.UNKNOWN, canPlayPause = true))
        assertEquals(TransportAction.TOGGLE, (unknown as TransportChoice.Send).action)
    }

    @Test
    fun volumeStaysInsideBoundsAndMuteRemembersThePreviousLevel() {
        val normal = volume(current = 4)
        val up = adjustVolume(normal, up = true) as VolumeDecision.SetLevel
        assertEquals(5, up.level)
        val top = adjustVolume(volume(current = 10, max = 10), up = true)
        assertTrue(top is VolumeDecision.Reject)
        val muted = toggleMute(normal) as VolumeDecision.SetLevel
        assertEquals(0, muted.level)
        assertEquals(4, muted.savedLevel)
        val restored = toggleMute(volume(current = 0, saved = 4)) as VolumeDecision.SetLevel
        assertEquals(4, restored.level)
        assertNull(restored.savedLevel)
        assertTrue(toggleMute(volume(current = 0, saved = null)) is VolumeDecision.Reject)
        assertTrue(adjustVolume(volume(inCall = true), up = true) is VolumeDecision.Reject)
        assertTrue(adjustVolume(volume(remote = true, remoteAbsolute = false), up = false) is VolumeDecision.Reject)
        assertTrue(toggleMute(volume(fixed = true)) is VolumeDecision.Reject)
    }

    private fun caps(
        phase: PlaybackPhase = PlaybackPhase.UNKNOWN,
        canPlay: Boolean = false,
        canPause: Boolean = false,
        canPlayPause: Boolean = false,
        canNext: Boolean = false,
        canPrevious: Boolean = false,
    ) = TransportCapabilities(phase, canPlay, canPause, canPlayPause, canNext, canPrevious)

    private fun volume(
        current: Int = 3,
        min: Int = 0,
        max: Int = 10,
        saved: Int? = null,
        fixed: Boolean = false,
        inCall: Boolean = false,
        remote: Boolean = false,
        remoteAbsolute: Boolean = true,
    ) = VolumeSnapshot(current, min, max, saved, fixed, inCall, remote, remoteAbsolute)
}
