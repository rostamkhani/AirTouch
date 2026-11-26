package com.airtouch.app.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.airtouch.app.player.MusicLibrary
import com.airtouch.app.player.PlayerConnection
import com.airtouch.app.player.PlayerUiState
import com.airtouch.app.player.Track
import com.airtouch.app.settings.PlaybackSource
import com.airtouch.app.ui.ChoiceRow
import com.airtouch.app.ui.InfoCard
import com.airtouch.app.ui.PageTitle
import com.airtouch.app.ui.ScreenColumn
import com.airtouch.app.ui.WideOutlinedButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    connection: PlayerConnection,
    library: MusicLibrary,
    state: PlayerUiState,
    tracks: List<Track>,
    source: PlaybackSource,
    onSource: (PlaybackSource) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val added = library.addFiles(uris)
            message = "$added آهنگ اضافه شد"
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = "در حال خواندن پوشه…"
            val added = library.addFolder(uri)
            message = if (added == 0) "آهنگی در این پوشه پیدا نشد" else "$added آهنگ از پوشه اضافه شد"
        }
    }

    LaunchedEffect(Unit) { connection.connect() }
    LaunchedEffect(tracks) { connection.syncQueue(tracks) }

    var position by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.connected) {
        while (true) {
            if (!dragging) position = connection.positionMs().toFloat()
            delay(500)
        }
    }

    ScreenColumn {
        PageTitle("پخش‌کننده", onBack)
        Text("فرمان‌های حرکتی به کجا بروند؟", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(
            options = listOf(PlaybackSource.BUILT_IN to "پخش‌کننده AirTouch", PlaybackSource.SYSTEM to "برنامه موسیقی دیگر"),
            selected = source,
            onSelect = onSource,
        )
        if (source == PlaybackSource.SYSTEM) {
            InfoCard("حالت فعلی", "فرمان‌ها به برنامه موسیقی‌ای می‌رود که در حال پخش است. این پخش‌کننده همچنان قابل استفاده است.")
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    state.title ?: if (tracks.isEmpty()) "هنوز آهنگی اضافه نشده" else "آماده پخش",
                    style = MaterialTheme.typography.titleLarge.merge(TextStyle(textDirection = TextDirection.Content)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                state.artist?.let { Text(it, style = TextStyle(textDirection = TextDirection.Content)) }
                if (state.count > 0) Text("آهنگ ${state.index + 1} از ${state.count}", style = MaterialTheme.typography.bodySmall)
                val duration = state.durationMs.toFloat()
                if (duration > 0f) {
                    Slider(
                        value = position.coerceIn(0f, duration),
                        onValueChange = {
                            dragging = true
                            position = it
                        },
                        onValueChangeFinished = {
                            dragging = false
                            connection.use { player -> player.seekTo(position.toLong()) }
                        },
                        valueRange = 0f..duration,
                    )
                    Text("${time(position.toLong())} / ${time(state.durationMs)}", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { connection.use { it.seekToPrevious() } },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 52.dp),
                    ) { Text("قبلی") }
                    Button(
                        onClick = {
                            connection.use { player ->
                                if (player.mediaItemCount == 0) {
                                    connection.loadQueue(tracks, 0, play = true)
                                } else if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0L)
                                    player.prepare()
                                    player.play()
                                }
                            }
                        },
                        enabled = tracks.isNotEmpty(),
                        modifier = Modifier.weight(1.4f).defaultMinSize(minHeight = 52.dp),
                    ) { Text(if (state.isPlaying) "توقف" else "پخش") }
                    OutlinedButton(
                        onClick = { connection.use { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 52.dp),
                    ) { Text("بعدی") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { connection.use { it.shuffleModeEnabled = !it.shuffleModeEnabled } }, modifier = Modifier.weight(1f)) {
                        Text(if (state.shuffle) "پخش تصادفی: روشن" else "پخش تصادفی: خاموش")
                    }
                    TextButton(
                        onClick = {
                            connection.use {
                                it.repeatMode = when (it.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            when (state.repeatMode) {
                                Player.REPEAT_MODE_ALL -> "تکرار همه"
                                Player.REPEAT_MODE_ONE -> "تکرار یک آهنگ"
                                else -> "بدون تکرار"
                            },
                        )
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        WideOutlinedButton("افزودن آهنگ") { pickFiles.launch(arrayOf("audio/*")) }
        WideOutlinedButton("افزودن همه آهنگ‌های یک پوشه") { pickFolder.launch(null) }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Text("فهرست پخش (${tracks.size})", style = MaterialTheme.typography.titleMedium)
        tracks.forEachIndexed { index, track ->
            val current = index == state.index && state.count == tracks.size
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { connection.loadQueue(tracks, index, play = true) },
                colors = if (current) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        track.title,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(textDirection = TextDirection.Content),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { scope.launch { library.remove(track) } }) { Text("حذف") }
                }
            }
        }
        if (tracks.isNotEmpty()) {
            WideOutlinedButton("پاک کردن فهرست") {
                scope.launch { library.clear() }
            }
        }
    }
}

private fun time(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
