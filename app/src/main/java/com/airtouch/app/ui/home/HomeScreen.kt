package com.airtouch.app.ui.home

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import com.airtouch.app.service.ExecutionMode
import com.airtouch.app.service.MediaUiState
import com.airtouch.app.service.ServicePhase
import com.airtouch.app.ui.InfoCard
import com.airtouch.app.ui.ScreenColumn
import com.airtouch.app.ui.WideButton
import com.airtouch.app.ui.labelFa

@Composable
fun HomeScreen(
    phase: ServicePhase,
    status: String,
    error: String?,
    cameraMessage: String?,
    media: MediaUiState,
    executionMode: ExecutionMode,
    handSeen: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEnableControl: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGestures: () -> Unit,
    onOpenLab: () -> Unit,
    onOpenMedia: () -> Unit,
) {
    ScreenColumn {
        Text("AirTouch", style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        InfoCard("وضعیت دوربین", phase.labelFa() + "؛ " + status)
        if (!error.isNullOrBlank()) InfoCard("خطا", error)
        if (!cameraMessage.isNullOrBlank()) InfoCard("دوربین", cameraMessage)
        InfoCard(
            "دست",
            if (phase != ServicePhase.RUNNING) "کنترل خاموش است" else if (handSeen) "دست دیده می‌شود" else "دستی در تصویر نیست",
        )
        val title = media.title ?: "آهنگی در دسترس نیست"
        InfoCard("موسیقی", media.targetLabel ?: "پخش‌کننده‌ای انتخاب نشده")
        Text(
            text = title,
            style = TextStyle(textDirection = TextDirection.Content),
        )
        Text(media.artist.orEmpty(), style = TextStyle(textDirection = TextDirection.Content))
        Text(media.playbackLabel)
        media.lastCommand?.let { command ->
            val confirmed = when (command.confirmed) {
                true -> "تغییر وضعیت دیده شد"
                false -> "نتیجه تأیید نشد"
                null -> "در انتظار نتیجه"
            }
            InfoCard(
                "آخرین فرمان",
                "${command.name}: ${if (command.sent) "ارسال شد" else "ارسال نشد"}. $confirmed. ${command.detail}",
            )
        }
        if (executionMode == ExecutionMode.DRY_RUN) {
            InfoCard("حالت آزمایش", "فرمان موسیقی ارسال نمی‌شود تا کنترل واقعی را روشن کنید.")
            WideButton("فعال‌کردن کنترل واقعی", onClick = onEnableControl)
        }
        if (phase == ServicePhase.RUNNING || phase == ServicePhase.STARTING) {
            WideButton("توقف کامل دوربین", onClick = onStop)
        } else {
            WideButton("شروع کنترل حرکتی", onClick = onStart)
        }
        OutlinedButton(onClick = onOpenCamera, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
            Text("دوربین و حساسیت")
        }
        OutlinedButton(onClick = onOpenGestures, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
            Text("حرکت‌ها")
        }
        OutlinedButton(onClick = onOpenLab, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
            Text("آزمایش دست")
        }
        OutlinedButton(onClick = onOpenMedia, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
            Text("وضعیت موسیقی")
        }
    }
}
