package com.airtouch.app.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.airtouch.app.settings.AppSettings
import com.airtouch.app.ui.CameraWithHand
import com.airtouch.app.ui.ChoiceRow
import com.airtouch.app.ui.InfoCard
import com.airtouch.app.ui.PageTitle
import com.airtouch.app.ui.ScreenColumn
import com.airtouch.handcontrol.HandControlSession
import com.airtouch.handcontrol.core.HandControlState

@Composable
fun CameraScreen(
    settings: AppSettings,
    session: HandControlSession?,
    state: HandControlState,
    status: HandControlSession.Status?,
    thermal: String,
    message: String?,
    onBack: () -> Unit,
    onFront: (Boolean) -> Unit,
    onPreview: (Boolean) -> Unit,
    onFps: (Int) -> Unit,
) {
    ScreenColumn {
        PageTitle("دوربین", onBack)
        if (!message.isNullOrBlank()) InfoCard("پیام دوربین", message)
        Text("کدام دوربین", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(
            options = listOf(true to "جلو (سلفی)", false to "عقب"),
            selected = settings.useFrontCamera,
            onSelect = onFront,
        )
        Text("نرخ تحلیل (فریم در ثانیه)", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(
            options = listOf(10 to "۱۰", 15 to "۱۵", 20 to "۲۰", 30 to "۳۰"),
            selected = settings.targetFps,
            onSelect = onFps,
        )
        Text(
            "نرخ بیشتر لمس‌های سریع را بهتر می‌گیرد و باتری بیشتری مصرف می‌کند. بدون دست، تحلیل خودکار به ۵ فریم کم می‌شود.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("نمایش تصویر دوربین")
            Switch(checked = settings.showPreview, onCheckedChange = onPreview)
        }
        val aspect = state.frame?.let { it.imageWidth.toFloat() / it.imageHeight } ?: (3f / 4f)
        CameraWithHand(session, state, settings.showPreview, aspect)
        status?.let {
            InfoCard(
                "عملکرد",
                "تصویر ${it.frameWidth}×${it.frameHeight}؛ تحلیل ${it.analysisFps} فریم در ثانیه؛ زمان مدل ${it.inferenceMs} میلی‌ثانیه",
            )
        }
        InfoCard("دما", thermal)
    }
}
