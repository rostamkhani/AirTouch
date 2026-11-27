package com.airtouch.app.ui.media

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import com.airtouch.app.media.TargetMode
import com.airtouch.app.service.MediaUiState
import com.airtouch.app.settings.AppSettings
import com.airtouch.app.settings.PlaybackSource
import com.airtouch.app.ui.ChoiceRow
import com.airtouch.app.ui.InfoCard
import com.airtouch.app.ui.PageTitle
import com.airtouch.app.ui.ScreenColumn
import com.airtouch.app.ui.WideButton
import com.airtouch.app.ui.WideOutlinedButton

@Composable
fun MediaScreen(
    settings: AppSettings,
    media: MediaUiState,
    listenerEnabled: Boolean,
    onBack: () -> Unit,
    onSource: (PlaybackSource) -> Unit,
    onMode: (TargetMode) -> Unit,
    onPackage: (String) -> Unit,
    onFallback: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    ScreenColumn {
        PageTitle("برنامه‌های موسیقی", onBack)
        if (settings.playbackSource != PlaybackSource.SYSTEM) {
            InfoCard("الان", "فرمان‌ها به پخش‌کننده خود AirTouch می‌رود. برای کنترل برنامه دیگر، این گزینه را عوض کنید.")
            WideButton("فرمان‌ها به برنامه موسیقی دیگر برود") { onSource(PlaybackSource.SYSTEM) }
        }
        InfoCard(
            "دسترسی اعلان",
            when {
                media.listenerConnected -> "وصل است"
                listenerEnabled -> "گزینه روشن است ولی سرویس هنوز وصل نشده"
                else -> "برای دیدن و کنترل برنامه موسیقی دیگر، دسترسی اعلان AirTouch را آگاهانه روشن کنید. فقط برای کنترل پخش استفاده می‌شود."
            },
        )
        WideOutlinedButton("باز کردن تنظیم دسترسی اعلان") {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        Text("انتخاب برنامه", style = MaterialTheme.typography.titleMedium)
        ChoiceRow(
            options = listOf(TargetMode.AUTO to "خودکار", TargetMode.MANUAL to "دستی"),
            selected = settings.targetMode,
            onSelect = onMode,
        )
        if (media.sessions.isEmpty()) {
            Text("برنامه موسیقی فعالی دیده نمی‌شود")
        } else {
            media.sessions.forEach { session ->
                val selected = session.packageName == media.targetPackage
                WideOutlinedButton((if (selected) "✓ " else "") + session.label + if (session.playing) " (در حال پخش)" else "") {
                    onPackage(session.packageName)
                }
            }
        }
        InfoCard("برنامه انتخاب‌شده", media.targetLabel ?: "نیست")
        Text(media.title ?: "نام آهنگ در دسترس نیست", style = TextStyle(textDirection = TextDirection.Content))
        media.artist?.let { Text(it, style = TextStyle(textDirection = TextDirection.Content)) }
        Text("پخش: ${media.playbackLabel}؛ خروجی: ${media.outputLabel}")
        Text("فرمان‌های اعلام‌شده: ${media.supportedActions.joinToString("، ").ifBlank { "موردی اعلام نشده" }}")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("کلید رسانه‌ای سراسری وقتی برنامه‌ای پیدا نشد", modifier = Modifier.weight(1f))
            Switch(checked = settings.mediaKeyFallback, onCheckedChange = onFallback)
        }
    }
}
