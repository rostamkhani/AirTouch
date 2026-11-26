package com.airtouch.app.ui.gestures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.airtouch.app.gestures.GestureCommand
import com.airtouch.app.settings.AppSettings
import com.airtouch.app.ui.InfoCard
import com.airtouch.app.ui.PageTitle
import com.airtouch.app.ui.ScreenColumn
import com.airtouch.app.ui.WideButton
import com.airtouch.app.ui.hintFa
import com.airtouch.app.ui.labelFa

@Composable
fun GesturesScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onToggle: (GestureCommand, Boolean) -> Unit,
    onCooldown: (Long) -> Unit,
    onFeedback: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    ScreenColumn {
        PageTitle("حرکت‌ها", onBack)
        InfoCard(
            "بازآماده‌سازی",
            "بعد از هر حرکت دست باید به نقطه شروع برگردد. نگه داشتن تماس انگشت فرمان را تکرار نمی‌کند.",
        )
        listOf(
            GestureCommand.NEXT,
            GestureCommand.PREVIOUS,
            GestureCommand.PLAY_PAUSE,
            GestureCommand.VOLUME_UP,
            GestureCommand.VOLUME_DOWN,
            GestureCommand.MUTE_TOGGLE,
        ).forEach { command ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                GestureGlyph(command, Modifier.size(48.dp))
                Text(command.labelFa(), modifier = Modifier.weight(1f))
                Switch(
                    checked = command in settings.enabledCommands,
                    onCheckedChange = { onToggle(command, it) },
                )
            }
            Text(command.hintFa())
        }
        Text("تعلیق با مشت و ادامه با کف دست همیشه روشن می‌مانند تا کنترل گیر نکند.")
        Text("فاصله فرمان‌ها: ${settings.cooldownMs} میلی‌ثانیه")
        Slider(
            value = settings.cooldownMs.toFloat(),
            onValueChange = { onCooldown(it.toLong()) },
            valueRange = 400f..1500f,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("صدای بازخورد")
        Switch(checked = settings.feedbackSound, onCheckedChange = onFeedback)
        WideButton("بازگشت تنظیمات به حالت اولیه", onClick = onReset)
    }
}

@Composable
private fun GestureGlyph(command: GestureCommand, modifier: Modifier) {
    Canvas(modifier.height(48.dp)) {
        val color = Color(0xFF0F6E56)
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color, 6f, center)
        val end = when (command) {
            GestureCommand.NEXT -> Offset(size.width - 8f, center.y)
            GestureCommand.PREVIOUS -> Offset(8f, center.y)
            GestureCommand.VOLUME_UP -> Offset(center.x, 8f)
            GestureCommand.VOLUME_DOWN -> Offset(center.x, size.height - 8f)
            else -> center
        }
        if (command == GestureCommand.PLAY_PAUSE || command == GestureCommand.MUTE_TOGGLE) {
            drawCircle(color, 10f, Offset(size.width * 0.35f, center.y))
            drawCircle(color, 10f, Offset(size.width * 0.65f, center.y))
        } else {
            drawLine(color, center, end, strokeWidth = 6f, cap = StrokeCap.Round)
        }
    }
}
