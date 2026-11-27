package com.airtouch.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val index: Int = -1,
    val count: Int = 0,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null,
)

/** Main-thread wrapper around a Media3 controller for [PlaybackService]. */
class PlayerConnection(private val context: Context) {
    private val mutableState = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val waiting = ArrayList<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlayerError(error: PlaybackException) {
            mutableState.value = mutableState.value.copy(error = "این فایل پخش نشد")
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener(
            {
                val connected = runCatching { pending.get() }.getOrNull()
                if (connected == null) {
                    future = null
                    waiting.clear()
                    mutableState.value = PlayerUiState(error = "پخش‌کننده داخلی در دسترس نیست")
                    return@addListener
                }
                controller = connected
                connected.addListener(listener)
                publish()
                val queued = waiting.toList()
                waiting.clear()
                queued.forEach { it(connected) }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
        waiting.clear()
        mutableState.value = PlayerUiState()
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    /** Runs [action] now if connected, or right after connecting. */
    fun use(action: (MediaController) -> Unit) {
        val current = controller
        if (current != null) {
            action(current)
        } else {
            waiting += action
            connect()
        }
    }

    fun loadQueue(tracks: List<Track>, startIndex: Int, play: Boolean) = use { player ->
        if (tracks.isEmpty()) {
            player.clearMediaItems()
            return@use
        }
        player.setMediaItems(tracks.map(::toMediaItem), startIndex.coerceIn(0, tracks.lastIndex), 0L)
        player.prepare()
        if (play) player.play()
    }

    /** Keeps the queue in step with the library without interrupting the current song. */
    fun syncQueue(tracks: List<Track>) = use { player ->
        val current = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
        val wanted = tracks.map { it.uri }
        if (current == wanted) return@use
        if (player.mediaItemCount == 0 || player.currentMediaItem?.mediaId !in wanted) {
            loadQueue(tracks, 0, play = false)
            return@use
        }
        val playingId = player.currentMediaItem?.mediaId
        val position = player.currentPosition
        val wasPlaying = player.isPlaying
        val newIndex = wanted.indexOf(playingId).coerceAtLeast(0)
        player.setMediaItems(tracks.map(::toMediaItem), newIndex, position)
        player.prepare()
        if (wasPlaying) player.play()
    }

    fun publish() {
        val player = controller ?: return
        val metadata = player.mediaMetadata
        mutableState.value = PlayerUiState(
            connected = true,
            isPlaying = player.isPlaying,
            title = (metadata.title ?: metadata.displayTitle)?.toString(),
            artist = metadata.artist?.toString(),
            index = player.currentMediaItemIndex,
            count = player.mediaItemCount,
            durationMs = player.duration.takeIf { it > 0 } ?: 0L,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            error = if (player.playerError != null) mutableState.value.error else null,
        )
    }

    private fun toMediaItem(track: Track): MediaItem {
        val uri = Uri.parse(track.uri)
        return MediaItem.Builder()
            .setMediaId(track.uri)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(MediaMetadata.Builder().setDisplayTitle(track.title).build())
            .build()
    }
}
