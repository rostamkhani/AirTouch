package com.airtouch.app.service

import com.airtouch.app.media.SessionSnapshot
import com.airtouch.handcontrol.HandControlSession
import kotlinx.coroutines.flow.MutableStateFlow

enum class ServicePhase {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR,
}

data class CommandRecord(
    val name: String,
    val gesture: String,
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
)

class RuntimeState {
    val phase = MutableStateFlow(ServicePhase.STOPPED)
    val statusText = MutableStateFlow("دوربین خاموش")
    val errorText = MutableStateFlow<String?>(null)
    val session = MutableStateFlow<HandControlSession?>(null)
    val media = MutableStateFlow(MediaUiState())
    val lastCommand = MutableStateFlow<CommandRecord?>(null)
    val allowBackground = MutableStateFlow(false)
    val thermalText = MutableStateFlow("نامشخص")
    val cameraMessage = MutableStateFlow<String?>(null)
    val locked = MutableStateFlow(false)

    /** While true (the test page is open), recognized gestures are shown but not executed. */
    val labActive = MutableStateFlow(false)

    /** Trigger id to the uptime when it last fired; the test page flashes these. */
    val recentTriggers = MutableStateFlow<Map<String, Long>>(emptyMap())

    fun noteTrigger(id: String, atMs: Long) {
        recentTriggers.value = recentTriggers.value + (id to atMs)
    }

    fun resetAfterStop() {
        phase.value = ServicePhase.STOPPED
        statusText.value = "دوربین خاموش"
        session.value = null
        allowBackground.value = false
        recentTriggers.value = emptyMap()
    }
}
