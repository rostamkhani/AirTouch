package com.airtouch.app.service

import com.airtouch.app.gestures.EngineSnapshot
import com.airtouch.app.media.SessionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow

enum class ServicePhase {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR,
}

enum class ExecutionMode {
    CONTROL,
    DRY_RUN,
}

data class CommandRecord(
    val name: String,
    val recognized: Boolean,
    val sent: Boolean,
    val confirmed: Boolean?,
    val detail: String,
)

data class MediaUiState(
    val listenerConnected: Boolean = false,
    val targetLabel: String? = null,
    val targetPackage: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val playbackLabel: String = "نامشخص",
    val outputLabel: String = "نامشخص",
    val supportedActions: List<String> = emptyList(),
    val sessions: List<SessionSnapshot> = emptyList(),
    val lastCommand: CommandRecord? = null,
)

class RuntimeState {
    val phase = MutableStateFlow(ServicePhase.STOPPED)
    val statusText = MutableStateFlow("دوربین خاموش")
    val errorText = MutableStateFlow<String?>(null)
    val hand = MutableStateFlow(EngineSnapshot.EMPTY)
    val media = MutableStateFlow(MediaUiState())
    val executionMode = MutableStateFlow(ExecutionMode.CONTROL)
    val allowBackground = MutableStateFlow(false)
    val thermalText = MutableStateFlow("نامشخص")
    val resolutionText = MutableStateFlow("نامشخص")
    val cameraFps = MutableStateFlow(0f)
    val analysisFps = MutableStateFlow(0f)
    val usingFrontCamera = MutableStateFlow(true)
    val cameraMessage = MutableStateFlow<String?>(null)

    fun resetAfterStop() {
        phase.value = ServicePhase.STOPPED
        statusText.value = "دوربین خاموش"
        hand.value = EngineSnapshot.EMPTY
        allowBackground.value = false
        cameraFps.value = 0f
        analysisFps.value = 0f
        resolutionText.value = "نامشخص"
    }
}
