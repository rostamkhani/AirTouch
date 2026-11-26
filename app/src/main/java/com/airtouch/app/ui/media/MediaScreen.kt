package com.airtouch.app.ui.media

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import com.airtouch.app.media.TargetMode
import com.airtouch.app.service.MediaUiState
import com.airtouch.app.settings.AppSettings
import com.airtouch.app.ui.InfoCard
import com.airtouch.app.ui.PageTitle
import com.airtouch.app.ui.ScreenColumn
import com.airtouch.app.ui.WideButton

@Composable
fun MediaScreen(
    settings: AppSettings,
    media: MediaUiState,
    listenerEnabled: Boolean,
    onBack: () -> Unit,
    onMode: (TargetMode) -> Unit,
    onPackage: (String) -> Unit,
    onFallback: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    ScreenColumn {
        PageTitle("وضعیت موسیقی", onBack)
        InfoCard(
            "دسترسی اعلان",
            if (media.listenerConnected) {
                "سرویس اعلان وصل است"
            } else if (listenerEnabled) {
                "گزینه روشن است ولی سرویس هنوز وصل نشده"
            } else {
                "برای دیدن نشست موسیقی، دسترسی اعلان AirTouch را آگاهانه روشن کنید. این دسترسی وسیع است و فقط برای کنترل پخش استفاده می‌شود."
            },
        )
        WideButton("باز کردن تنظیم دسترسی اعلان") {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        Text(if (settings.targetMode == TargetMode.AUTO) "انتخاب خودکار" else "انتخاب دستی")
        WideButton(if (settings.targetMode == TargetMode.AUTO) "رفتن به انتخاب دستی" else "رفتن به انتخاب خودکار") {
            onMode(if (settings.targetMode == TargetMode.AUTO) TargetMode.MANUAL else TargetMode.AUTO)
        }
        if (media.sessions.isEmpty()) {
            Text("نشست فعالی دیده نمی‌شود")
        } else {
            media.sessions.forEach { session ->
                val selected = session.packageName == media.targetPackage
                WideButton((if (selected) "انتخاب شده: " else "") + session.label) {
                    onPackage(session.packageName)
                }
            }
        }
        Text("برنامه: ${media.targetLabel ?: "نیست"}")
        Text(media.title ?: "نام آهنگ در دسترس نیست", style = TextStyle(textDirection = TextDirection.Content))
        Text(media.artist ?: "", style = TextStyle(textDirection = TextDirection.Content))
        Text("پخش: ${media.playbackLabel}")
        Text("خروجی: ${media.outputLabel}")
        Text("فرمان‌های اعلام‌شده: ${media.supportedActions.joinToString("، ").ifBlank { "موردی اعلام نشده" }}")
        media.lastCommand?.let { command ->
            InfoCard(
                "نتیجه آخرین فرمان",
                "تشخیص: ${if (command.recognized) "بله" else "خیر"}؛ ارسال: ${if (command.sent) "بله" else "خیر"}؛ تأیید: ${
                    when (command.confirmed) {
                        true -> "دیده شد"
                        false -> "دیده نشد"
                        null -> "نامعلوم"
                    }
                }. ${command.detail}",
            )
        }
        Text("کلید رسانه‌ای سراسری فقط در حالت خودکار و وقتی نشستی نباشد")
        Switch(checked = settings.mediaKeyFallback, onCheckedChange = onFallback)
    }
}
