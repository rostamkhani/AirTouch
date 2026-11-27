package com.airtouch.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.airtouch.handcontrol.HandControlSession
import com.airtouch.handcontrol.core.HandControlState
import com.airtouch.handcontrol.ui.HandCameraPreview
import com.airtouch.handcontrol.ui.HandSkeletonOverlay

@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun PageTitle(title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (onBack != null) {
            OutlinedButton(onClick = onBack, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
                Text("بازگشت")
            }
        }
    }
}

@Composable
fun InfoCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun WideButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp),
    ) {
        Text(text)
    }
}

@Composable
fun WideOutlinedButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
    ) {
        Text(text)
    }
}

@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Live camera with the detected hand drawn on top. The box takes the analyzed frame's aspect
 * ratio so preview and overlay use identical scaling.
 */
@Composable
fun CameraWithHand(
    session: HandControlSession?,
    state: HandControlState,
    showPreview: Boolean,
    frameAspect: Float,
) {
    val aspect = frameAspect.takeIf { it.isFinite() && it > 0f } ?: (3f / 4f)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, 460.dp * aspect)
        Box(
            modifier = Modifier
                .size(width, width / aspect)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF14232B)),
            contentAlignment = Alignment.Center,
        ) {
            CameraContent(session, state, showPreview)
        }
    }
}

@Composable
private fun BoxScope.CameraContent(session: HandControlSession?, state: HandControlState, showPreview: Boolean) {
    if (session != null && showPreview) {
        HandCameraPreview(session, Modifier.matchParentSize())
    } else {
        Text(
            if (session == null) "دوربین خاموش است" else "پیش‌نمایش خاموش است؛ فقط اسکلت دست نشان داده می‌شود",
            color = Color.White,
            modifier = Modifier.padding(24.dp),
        )
    }
    HandSkeletonOverlay(state, Modifier.matchParentSize())
}

/** Keeps "width×height" in left-to-right order inside Persian text. */
fun dimensions(width: Int, height: Int): String = "\u2066${width}×${height}\u2069"
