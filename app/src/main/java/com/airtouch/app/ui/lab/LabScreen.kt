package com.airtouch.app.ui.lab

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtouch.app.ui.CameraWithHand
import com.airtouch.app.ui.InfoCard
import com.airtouch.app.ui.PageTitle
import com.airtouch.app.ui.ScreenColumn
import com.airtouch.app.ui.WideButton
import com.airtouch.app.ui.dimensions
import com.airtouch.handcontrol.HandControlSession
import com.airtouch.handcontrol.PersianLabels
import com.airtouch.handcontrol.core.Finger
import com.airtouch.handcontrol.core.FingerState
import com.airtouch.handcontrol.core.GestureTrigger
import com.airtouch.handcontrol.core.HandControlState
import com.airtouch.handcontrol.core.HandPose
import com.airtouch.handcontrol.core.TouchKind
import kotlinx.coroutines.delay

private data class Tile(val key: String, val title: String, val hint: String)

private val poseTiles = HandPose.entries.map { pose ->
    Tile(pose.name, PersianLabels.symbol(pose) + "  " + PersianLabels.pose(pose), PersianLabels.howTo(pose))
}

private val touchTiles = Finger.TOUCHABLE.map { finger ->
    Tile(finger.name, PersianLabels.touch(finger), "نوک شست روی نوک ${PersianLabels.finger(finger)}؛ بقیه انگشت‌ها باز")
}

private val eventTiles: List<Pair<GestureTrigger, Tile>> =
    Finger.TOUCHABLE.flatMap { finger ->
        TouchKind.entries.map { kind ->
            val trigger = GestureTrigger.Touch(finger, kind)
            trigger to Tile(trigger.id, PersianLabels.trigger(trigger), "")
        }
    } + (GestureTrigger.IndexClick to Tile(GestureTrigger.IndexClick.id, PersianLabels.trigger(GestureTrigger.IndexClick), "با اشاره تنها، یک‌بار سریع خم و راست کنید"))

@Composable
fun LabScreen(
    session: HandControlSession?,
    state: HandControlState,
    status: HandControlSession.Status?,
    showPreview: Boolean,
    recentTriggers: Map<String, Long>,
    holdMs: Long,
    onLabVisible: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    DisposableEffect(Unit) {
        onLabVisible(true)
        onDispose { onLabVisible(false) }
    }
    var now by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.uptimeMillis()
            delay(100)
        }
    }

    ScreenColumn {
        PageTitle("آزمایش حرکات", onBack)
        Text(
            "در این صفحه فرمانی اجرا نمی‌شود. هر حالتی که دست بگیرد، دکمه‌اش روشن می‌شود و تا وقتی در آن حالت بمانید روشن می‌ماند.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (session == null) {
            WideButton("روشن کردن دوربین برای آزمایش", onClick = onStart)
        }
        val aspect = state.frame?.let { it.imageWidth.toFloat() / it.imageHeight } ?: status?.let {
            if (it.frameWidth > 0 && it.frameHeight > 0) it.frameWidth.toFloat() / it.frameHeight else null
        } ?: (3f / 4f)
        CameraWithHand(session, state, showPreview, aspect)
        StatusLine(state, status, holdMs)

        SectionTitle("حالت‌های دست (ثابت نگه دارید)")
        TileGrid(poseTiles) { tile ->
            when {
                state.pose?.name == tile.key -> TileLevel.ON
                state.poseCandidate?.name == tile.key -> TileLevel.CANDIDATE
                else -> TileLevel.OFF
            }
        }

        SectionTitle("لمس نوک شست با هر انگشت")
        TileGrid(touchTiles) { tile ->
            when {
                state.touches.any { it.name == tile.key } -> TileLevel.ON
                state.touchCandidates.any { it.name == tile.key } -> TileLevel.CANDIDATE
                else -> TileLevel.OFF
            }
        }

        SectionTitle("حرکت انگشت‌ها (یک لحظه روشن می‌شود)")
        TileGrid(eventTiles.map { it.second }) { tile ->
            val firedAt = recentTriggers[tile.key]
            if (firedAt != null && now - firedAt < FLASH_MS) TileLevel.ON else TileLevel.OFF
        }

        SectionTitle("باز بودن انگشت‌ها")
        FingerBars(state)
        InfoCard(
            "راهنما",
            "دست را ۳۰ تا ۸۰ سانتی‌متر جلوی دوربین و کامل در کادر نگه دارید. برای هر حالت، دست را حدود ${holdMs} میلی‌ثانیه ثابت نگه دارید. برای لمس، بقیه انگشت‌ها را باز بگذارید.",
        )
    }
}

private enum class TileLevel { OFF, CANDIDATE, ON }

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun TileGrid(tiles: List<Tile>, level: (Tile) -> TileLevel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tile -> TileView(tile, level(tile), Modifier.weight(1f)) }
                repeat(COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TileView(tile: Tile, level: TileLevel, modifier: Modifier) {
    val on = MaterialTheme.colorScheme.primary
    val background by animateColorAsState(
        when (level) {
            TileLevel.ON -> on
            TileLevel.CANDIDATE -> on.copy(alpha = 0.18f)
            TileLevel.OFF -> MaterialTheme.colorScheme.surface
        },
        label = "tile",
    )
    val content = if (level == TileLevel.ON) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier
            .heightIn(min = 72.dp)
            .semantics {
                contentDescription = tile.title
                stateDescription = if (level == TileLevel.ON) "روشن" else "خاموش"
            },
        shape = RoundedCornerShape(12.dp),
        color = background,
        border = BorderStroke(1.dp, if (level == TileLevel.OFF) MaterialTheme.colorScheme.outlineVariant else on),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                tile.title,
                color = content,
                textAlign = TextAlign.Center,
                fontWeight = if (level == TileLevel.ON) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
            if (tile.hint.isNotEmpty()) {
                Text(tile.hint, color = content.copy(alpha = 0.7f), textAlign = TextAlign.Center, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
private fun StatusLine(state: HandControlState, status: HandControlSession.Status?, holdMs: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val pose = state.pose
            Text(
                if (pose != null) "${PersianLabels.symbol(pose)}  ${PersianLabels.pose(pose)}" else PersianLabels.quality(state.quality),
                style = MaterialTheme.typography.titleLarge,
            )
            if (pose != null) {
                LinearProgressIndicator(progress = { state.holdProgress }, modifier = Modifier.fillMaxWidth())
                Text(
                    if (state.holdProgress >= 1f) "فرمان این حالت صادر شد" else "ثابت نگه دارید (${holdMs} میلی‌ثانیه)",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (state.handPresent) {
                Text(PersianLabels.quality(state.quality), style = MaterialTheme.typography.bodySmall)
            }
            status?.let {
                Text(
                    "تحلیل ${it.analysisFps} فریم در ثانیه، زمان مدل ${it.inferenceMs} میلی‌ثانیه، تصویر ${dimensions(it.frameWidth, it.frameHeight)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun FingerBars(state: HandControlState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Finger.entries.forEach { finger ->
            val value = state.fingerOpenness.getOrNull(finger.ordinal) ?: 0f
            val open = state.fingerStates.getOrNull(finger.ordinal) == FingerState.OPEN
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(PersianLabels.finger(finger), modifier = Modifier.width(64.dp))
                LinearProgressIndicator(
                    progress = { if (state.handPresent) value else 0f },
                    modifier = Modifier.weight(1f).height(10.dp),
                    color = if (open) MaterialTheme.colorScheme.primary else Color(0xFFB5523A),
                )
                Text(if (!state.handPresent) "—" else if (open) "باز" else "بسته", modifier = Modifier.width(40.dp))
            }
        }
    }
}

private const val COLUMNS = 3
private const val FLASH_MS = 900L
