package com.airtouch.handcontrol.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.viewinterop.AndroidView
import com.airtouch.handcontrol.HandControlSession
import com.airtouch.handcontrol.core.Finger
import com.airtouch.handcontrol.core.FingerState
import com.airtouch.handcontrol.core.HandControlState
import com.airtouch.handcontrol.core.HandFrame
import com.airtouch.handcontrol.core.HandLandmark
import kotlin.math.max

/**
 * Camera preview for a running [HandControlSession]. Pair it with [HandSkeletonOverlay] in a Box
 * of the same size; both use center-crop scaling so the drawn hand sits on the real one.
 */
@Composable
fun HandCameraPreview(session: HandControlSession, modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf<PreviewView?>(null) }
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                view = this
            }
        },
        modifier = modifier,
    )
    val current = view
    DisposableEffect(session, current) {
        if (current != null) session.setPreviewSurface(current.surfaceProvider)
        onDispose { session.setPreviewSurface(null) }
    }
}

data class OverlayColors(
    val bone: Color = Color(0xFFFFFFFF),
    val joint: Color = Color(0xFF00BFA5),
    val touch: Color = Color(0xFFFFC400),
)

@Composable
fun HandSkeletonOverlay(
    state: HandControlState,
    modifier: Modifier = Modifier,
    colors: OverlayColors = OverlayColors(),
) {
    Canvas(modifier) {
        val frame = state.frame ?: return@Canvas
        val points = frame.image.map { mapToView(frame, it.x, it.y, size.width, size.height) }
        val stroke = max(3f, size.minDimension / 110f)
        for ((start, end) in HandLandmark.CONNECTIONS) {
            drawLine(colors.bone.copy(alpha = 0.85f), points[start], points[end], strokeWidth = stroke, cap = StrokeCap.Round)
        }
        for (point in points) drawCircle(colors.joint, radius = stroke * 1.4f, center = point)
        if (state.touches.isNotEmpty()) {
            drawCircle(colors.touch, radius = stroke * 3.2f, center = points[HandLandmark.THUMB_TIP])
            for (finger in state.touches) drawCircle(colors.touch, radius = stroke * 3.2f, center = points[finger.tip])
        }
        for (finger in Finger.entries) {
            val open = state.fingerStates.getOrNull(finger.ordinal) == FingerState.OPEN
            if (open) drawCircle(colors.joint, radius = stroke * 2.2f, center = points[finger.tip])
        }
    }
}

/** Maps normalized frame coordinates to a view that shows the frame center-cropped. */
fun mapToView(frame: HandFrame, x: Float, y: Float, viewWidth: Float, viewHeight: Float): Offset {
    val scale = max(viewWidth / frame.imageWidth, viewHeight / frame.imageHeight)
    val dx = (viewWidth - frame.imageWidth * scale) / 2f
    val dy = (viewHeight - frame.imageHeight * scale) / 2f
    return Offset(x * frame.imageWidth * scale + dx, y * frame.imageHeight * scale + dy)
}
