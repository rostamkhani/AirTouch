package com.airtouch.app.media

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController

class VolumeController(context: Context) {
    // PlaybackInfo no longer exposes these names in API 36, but getVolumeControl()
    // still returns the historical values: fixed 0, relative 1, absolute 2.
    private val audio = context.getSystemService(AudioManager::class.java)
    var savedLevel: Int? = null
        private set

    fun localSnapshot(): VolumeSnapshot {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        return VolumeSnapshot(
            current = current,
            min = min,
            max = max,
            savedLevel = savedLevel,
            fixed = audio.isVolumeFixed || max <= min,
            inCall = inCall(),
            remote = false,
            remoteSupportsAbsolute = false,
        )
    }

    fun remoteSnapshot(controller: MediaController): VolumeSnapshot {
        val info = controller.playbackInfo
        val max = info?.maxVolume ?: 0
        val current = info?.currentVolume ?: 0
        val absolute = info?.volumeControl == VOLUME_CONTROL_ABSOLUTE
        return VolumeSnapshot(
            current = current,
            min = 0,
            max = max,
            savedLevel = savedLevel,
            fixed = max <= 0,
            inCall = inCall(),
            remote = true,
            remoteSupportsAbsolute = absolute,
        )
    }

    fun applyLocal(level: Int, newSaved: Int?): Boolean {
        savedLevel = newSaved
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) == level
    }

    fun applyRemote(controller: MediaController, level: Int, newSaved: Int?) {
        savedLevel = newSaved
        controller.setVolumeTo(level, 0)
    }

    fun isRemote(controller: MediaController?): Boolean {
        return controller?.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE
    }

    private companion object {
        const val VOLUME_CONTROL_ABSOLUTE = 2
    }

    private fun inCall(): Boolean {
        return audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION
    }
}
