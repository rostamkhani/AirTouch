package com.airtouch.app.ui.gestures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airtouch.app.control.ControlAction
import com.airtouch.app.settings.AppSettings
import com.airtouch.app.ui.InfoCard
import com.airtouch.app.ui.PageTitle
import com.airtouch.app.ui.ScreenColumn
import com.airtouch.app.ui.WideOutlinedButton
import com.airtouch.handcontrol.PersianLabels
import com.airtouch.handcontrol.core.Finger
import com.airtouch.handcontrol.core.GestureTrigger
import com.airtouch.handcontrol.core.HandPose
import com.airtouch.handcontrol.core.TouchKind
import kotlin.math.roundToInt

@Composable
fun GesturesScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onBind: (GestureTrigger, ControlAction) -> Unit,
    onHold: (Long) -> Unit,
    onCooldown: (Long) -> Unit,
    onSensitivity: (Float) -> Unit,
    onReset: () -> Unit,
) {
    ScreenColumn {
        PageTitle("حرکات و فرمان‌ها", onBack)
        InfoCard(
            "چطور کار می‌کند",
            "همه فرمان‌ها با دست ثابت جلوی دوربین است. حالت دست باید کمی ثابت بماند تا فرمان اجرا شود. لمس نوک شست با هر انگشت جداگانه شناخته می‌شود: یک ضربه، دو ضربه یا نگه‌داشتن.",
        )

        Text("حالت‌های دست", style = MaterialTheme.typography.titleMedium)
        HandPose.entries.forEach { pose ->
            val trigger = GestureTrigger.Hold(pose)
            BindingRow(
                title = PersianLabels.symbol(pose) + "  " + PersianLabels.pose(pose),
                hint = PersianLabels.howTo(pose),
                action = settings.bindings[trigger] ?: ControlAction.NONE,
                onSelect = { onBind(trigger, it) },
            )
        }

        Text("لمس نوک شست", style = MaterialTheme.typography.titleMedium)
        Finger.TOUCHABLE.forEach { finger ->
            TouchKind.entries.forEach { kind ->
                val trigger = GestureTrigger.Touch(finger, kind)
                BindingRow(
                    title = PersianLabels.trigger(trigger),
                    hint = when (kind) {
                        TouchKind.TAP -> "کوتاه بزنید و جدا کنید"
                        TouchKind.DOUBLE_TAP -> "دو بار پشت سر هم؛ اگر فعال باشد یک ضربه کمی دیرتر اجرا می‌شود"
                        TouchKind.LONG_PRESS -> "حدود یک ثانیه نگه دارید"
                    },
                    action = settings.bindings[trigger] ?: ControlAction.NONE,
                    onSelect = { onBind(trigger, it) },
                )
            }
        }

        Text("حرکت انگشت", style = MaterialTheme.typography.titleMedium)
        BindingRow(
            title = PersianLabels.trigger(GestureTrigger.IndexClick),
            hint = "فقط اشاره باز؛ یک‌بار سریع خم کنید و دوباره راست کنید",
            action = settings.bindings[GestureTrigger.IndexClick] ?: ControlAction.NONE,
            onSelect = { onBind(GestureTrigger.IndexClick, it) },
        )

        Text("زمان نگه‌داشتن حالت: ${settings.holdMs} میلی‌ثانیه")
        Slider(
            value = settings.holdMs.toFloat(),
            onValueChange = { onHold((it / 50f).roundToInt() * 50L) },
            valueRange = 300f..1_500f,
            steps = 23,
        )
        Text("فاصله بین دو فرمان: ${settings.cooldownMs} میلی‌ثانیه")
        Slider(
            value = settings.cooldownMs.toFloat(),
            onValueChange = { onCooldown((it / 50f).roundToInt() * 50L) },
            valueRange = 200f..1_500f,
            steps = 25,
        )
        Text("حساسیت لمس و حرکت: ${"%.2f".format(settings.sensitivity)}")
        Slider(
            value = settings.sensitivity,
            onValueChange = { onSensitivity((it * 20f).roundToInt() / 20f) },
            valueRange = 0.7f..1.4f,
            steps = 13,
        )
        Text(
            "حساسیت بیشتر لمس‌های شل‌تر و تکان بیشتر را می‌پذیرد؛ کمتر یعنی حرکت دقیق‌تر و خطای کمتر.",
            style = MaterialTheme.typography.bodySmall,
        )
        WideOutlinedButton("بازگشت به تنظیمات پیش‌فرض", onClick = onReset)
    }
}

@Composable
private fun BindingRow(title: String, hint: String, action: ControlAction, onSelect: (ControlAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
            Box {
                OutlinedButton(onClick = { open = true }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(action.label)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    ControlAction.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                open = false
                                onSelect(option)
                            },
                        )
                    }
                }
            }
        }
    }
}
