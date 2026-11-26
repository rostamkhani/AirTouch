package com.airtouch.app.ui.camera

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.airtouch.app.camera.CameraPipeline
import com.airtouch.app.settings.AppSettings
import com.airtouch.app.ui.CameraPreview
import com.airtouch.app.ui.InfoCard
import com.airtouch.app.ui.PageTitle
import com.airtouch.app.ui.ScreenColumn
import com.airtouch.app.ui.WideButton

@Composable
fun CameraScreen(
    settings: AppSettings,
    pipeline: CameraPipeline?,
    resolution: String,
    cameraFps: Float,
    analysisFps: Float,
    thermal: String,
    message: String?,
    onBack: () -> Unit,
    onFront: (Boolean) -> Unit,
    onPreview: (Boolean) -> Unit,
    onFps: (Int) -> Unit,
    onSensitivity: (Float) -> Unit,
) {
    ScreenColumn {
        PageTitle("دوربین و حساسیت", onBack)
        if (!message.isNullOrBlank()) InfoCard("پیام دوربین", message)
        InfoCard("تصویر تحلیل", "$resolution؛ نرخ دوربین ${cameraFps.toInt()}؛ نرخ تحلیل ${analysisFps.toInt()}")
        InfoCard("دما", thermal)
        Text(if (settings.useFrontCamera) "دوربین جلو انتخاب شده" else "دوربین عقب انتخاب شده")
        WideButton(if (settings.useFrontCamera) "رفتن به دوربین عقب" else "رفتن به دوربین جلو") {
            onFront(!settings.useFrontCamera)
        }
        Text("نمایش تصویر")
        Switch(checked = settings.showPreview, onCheckedChange = onPreview)
        Text("نرخ هدف: ${settings.targetFps}")
        listOf(10, 15, 20).forEach { fps ->
            WideButton(if (settings.targetFps == fps) "$fps فریم انتخاب شده" else "$fps فریم") {
                onFps(fps)
            }
        }
        Text("حساسیت ${"%.2f".format(settings.sensitivity)}")
        Slider(
            value = settings.sensitivity,
            onValueChange = onSensitivity,
            valueRange = 0.7f..1.4f,
            modifier = Modifier.fillMaxWidth(),
        )
        CameraPreview(pipeline, settings.showPreview)
    }
}
