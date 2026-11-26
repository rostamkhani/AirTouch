package com.airtouch.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.airtouch.app.AppContainer
import com.airtouch.app.control.ControlAction
import com.airtouch.app.control.DefaultBindings
import com.airtouch.app.media.TargetMode
import com.airtouch.app.settings.AppSettings
import com.airtouch.app.settings.PlaybackSource
import com.airtouch.app.settings.defaultSettings
import com.airtouch.handcontrol.core.GestureTrigger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AirTouchViewModel(val container: AppContainer) : ViewModel() {
    val runtime = container.runtime
    val settings = container.settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        defaultSettings(),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch { container.settingsRepository.update(block) }
    }

    fun setFrontCamera(front: Boolean) = update { it.copy(useFrontCamera = front) }
    fun setPreview(enabled: Boolean) = update { it.copy(showPreview = enabled) }
    fun setFps(fps: Int) = update { it.copy(targetFps = fps) }
    fun setSensitivity(value: Float) = update { it.copy(sensitivity = value) }
    fun setHold(ms: Long) = update { it.copy(holdMs = ms) }
    fun setCooldown(ms: Long) = update { it.copy(cooldownMs = ms) }
    fun setSource(source: PlaybackSource) = update { it.copy(playbackSource = source) }
    fun setTargetMode(mode: TargetMode) = update { it.copy(targetMode = mode) }
    fun setManualPackage(packageName: String) = update { it.copy(manualPackage = packageName, targetMode = TargetMode.MANUAL) }
    fun setFallback(enabled: Boolean) = update { it.copy(mediaKeyFallback = enabled) }
    fun finishOnboarding() = update { it.copy(onboardingDone = true) }

    fun bind(trigger: GestureTrigger, action: ControlAction) = update { settings ->
        val next = settings.bindings.toMutableMap()
        if (action == ControlAction.NONE) next.remove(trigger) else next[trigger] = action
        settings.copy(bindings = next)
    }

    fun resetGestures() = update {
        val defaults = defaultSettings()
        it.copy(bindings = DefaultBindings.map, holdMs = defaults.holdMs, cooldownMs = defaults.cooldownMs, sensitivity = defaults.sensitivity)
    }

    fun setLabActive(active: Boolean) {
        runtime.labActive.value = active
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AirTouchViewModel(container) as T
                }
            }
        }
    }
}
