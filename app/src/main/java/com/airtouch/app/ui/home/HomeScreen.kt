package com.airtouch.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.airtouch.app.player.PlayerUiState
import com.airtouch.app.service.CommandRecord
import com.airtouch.app.service.MediaUiState
import com.airtouch.app.service.ServicePhase
import com.airtouch.app.settings.PlaybackSource
import com.airtouch.app.ui.ChoiceRow
import com.airtouch.app.ui.InfoCard
import com.airtouch.app.ui.ScreenColumn
import com.airtouch.app.ui.WideButton
import com.airtouch.app.ui.WideOutlinedButton
import com.airtouch.app.ui.labelFa
import com.airtouch.handcontrol.PersianLabels
import com.airtouch.handcontrol.core.HandControlState

@Composable
fun HomeScreen(
    phase: ServicePhase,
    status: String,
    error: String?,
    cameraMessage: String?,
    hand: HandControlState,
    locked: Boolean,
    source: PlaybackSource,
    player: PlayerUiState,
    media: MediaUiState,
    lastCommand: CommandRecord?,
    onSource: (PlaybackSource) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUnlock: () -> Unit,
    onOpenLab: () -> Unit,
    onOpenGestures: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    ScreenColumn {
        Text("AirTouch", style = MaterialTheme.typography.displaySmall)
        InfoCard("کنترل حرکتی", phase.labelFa() + "؛ " + status)
        if (!error.isNullOrBlank()) InfoCard("خطا", error)
        if (!cameraMessage.isNullOrBlank()) InfoCard("دوربین", cameraMessage)
        if (phase == ServicePhase.RUNNING) {
            InfoCard(
                "دست",
                when {
                    hand.pose != null -> "${PersianLabels.symbol(hand.pose!!)}  ${PersianLabels.pose(hand.pose!!)}"
                    else -> PersianLabels.quality(hand.quality)
                },
            )
        }
        if (locked) {
            InfoCard("قفل", "کنترل حرکتی قفل است. با همان حرکت قفل یا دکمه زیر باز می‌شود.")
            WideOutlinedButton("باز کردن قفل", onClick = onUnlock)
        }
        if (phase == ServicePhase.RUNNING || phase == ServicePhase.STARTING) {
            WideButton("توقف کامل دوربین", onClick = onStop)
        } else {
            WideButton("شروع کنترل حرکتی", onClick = onStart)
        }

        Text("فرمان‌ها برای", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(
            options = listOf(PlaybackSource.BUILT_IN to "پخش‌کننده AirTouch", PlaybackSource.SYSTEM to "برنامه موسیقی دیگر"),
            selected = source,
            onSelect = onSource,
        )
        if (source == PlaybackSource.BUILT_IN) {
            InfoCard(
                "در حال پخش",
                player.title?.let { "$it${if (player.isPlaying) "  (در حال پخش)" else ""}" } ?: "آهنگی بارگذاری نشده؛ از صفحه پخش‌کننده آهنگ اضافه کنید",
            )
        } else {
            InfoCard("برنامه موسیقی", media.targetLabel ?: "پخش‌کننده‌ای پیدا نشد؛ دسترسی اعلان را بررسی کنید")
            media.title?.let { Text(it, style = TextStyle(textDirection = TextDirection.Content)) }
        }
        lastCommand?.let { command ->
            val result = when {
                !command.sent -> "اجرا نشد"
                command.confirmed == true -> "انجام شد"
                command.confirmed == false -> "ارسال شد ولی تغییری دیده نشد"
                else -> "ارسال شد"
            }
            InfoCard("آخرین فرمان", "${command.gesture} ← ${command.name}: $result. ${command.detail}")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WideOutlinedButton("آزمایش حرکات", onClick = onOpenLab)
                WideOutlinedButton("پخش‌کننده", onClick = onOpenPlayer)
                WideOutlinedButton("دوربین", onClick = onOpenCamera)
            }
            androidx.compose.foundation.layout.Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WideOutlinedButton("حرکات و فرمان‌ها", onClick = onOpenGestures)
                WideOutlinedButton("برنامه‌های موسیقی", onClick = onOpenMedia)
            }
        }
    }
}
