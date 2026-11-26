package com.airtouch.app.ui.diagnostics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.airtouch.app.camera.CameraPipeline
import com.airtouch.app.gestures.EngineSnapshot
import com.airtouch.app.gestures.Finger
import com.airtouch.app.settings.AppSettings
import com.airtouch.app.ui.CameraPreview
import com.airtouch.app.ui.InfoCard
import com.airtouch.app.ui.PageTitle
import com.airtouch.app.ui.ScreenColumn
import com.airtouch.app.ui.labelFa
import kotlin.math.max

private val bones = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 17 to 18, 18 to 19, 19 to 20,
    0 to 17,
)

@Composable
fun DiagnosticsScreen(
    settings: AppSettings,
    pipeline: CameraPipeline?,
    hand: EngineSnapshot,
    cameraFps: Float,
    analysisFps: Float,
    onBack: () -> Unit,
) {
    ScreenColumn {
        PageTitle("آزمایش دست", onBack)
        InfoCard("حالت آزمایش", "در این صفحه فرمان موسیقی ارسال نمی‌شود. برای کنترل واقعی باید از صفحه خانه آن را روشن کنید.")
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            CameraPreview(pipeline, settings.showPreview && pipeline != null)
            Canvas(Modifier.fillMaxWidth().height(320.dp)) {
                if (hand.points.size != 21) return@Canvas
                val color = Color(0xFFB5523A)
                bones.forEach { (start, end) ->
                    drawLine(
                        color,
                        mapPoint(hand.points[start], size.width, size.height, hand.imageWidth, hand.imageHeight),
                        mapPoint(hand.points[end], size.width, size.height, hand.imageWidth, hand.imageHeight),
                        strokeWidth = 5f,
                    )
                }
                hand.points.forEach { point ->
                    drawCircle(color, 7f, mapPoint(point, size.width, size.height, hand.imageWidth, hand.imageHeight))
                }
            }
        }
        InfoCard("موتور", hand.state.labelFa())
        InfoCard("دست", hand.handedness.labelFa())
        InfoCard("نامزد حرکت", hand.candidate.labelFa())
        InfoCard(
            "فاصله تماس",
            "شست و اشاره ${hand.pinchIndex?.let { "%.2f".format(it) } ?: "—"}; شست و میانی ${hand.pinchMiddle?.let { "%.2f".format(it) } ?: "—"}",
        )
        Finger.entries.forEach { finger ->
            Text("${finger.labelFa()}: ${hand.fingers[finger]?.labelFa() ?: "دیده نشد"}")
        }
        Text("تأخیر ${hand.lastLatencyMs} میلی‌ثانیه")
        Text("نرخ دوربین ${cameraFps.toInt()} و نرخ تحلیل ${analysisFps.toInt()}")
        Text(
            "تخمین عمق مچ: ${hand.wristWorldZ?.let { "%.3f".format(it) } ?: "نیست"}. این عدد اندازه‌گیری فیزیکی نیست.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (hand.neutralPending) {
            Text("دست باید به محدوده شروع حرکت برگردد و کمی آرام بماند.")
        }
    }
}

private fun mapPoint(
    point: com.airtouch.app.gestures.Point2,
    viewW: Float,
    viewH: Float,
    imageW: Int,
    imageH: Int,
): Offset {
    if (imageW <= 0 || imageH <= 0) return Offset(point.x * viewW, point.y * viewH)
    val scale = max(viewW / imageW, viewH / imageH)
    val dx = (viewW - imageW * scale) / 2f
    val dy = (viewH - imageH * scale) / 2f
    return Offset(point.x * imageW * scale + dx, point.y * imageH * scale + dy)
}
