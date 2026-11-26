package com.airtouch.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.airtouch.app.AppContainer
import com.airtouch.app.gestures.GestureCommand
import com.airtouch.app.media.TargetMode
import com.airtouch.app.service.ExecutionMode
import com.airtouch.app.settings.AppSettings
import com.airtouch.app.settings.defaultSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AirTouchViewModel(private val container: AppContainer) : ViewModel() {
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
    fun setCooldown(ms: Long) = update { it.copy(cooldownMs = ms) }
    fun setCommand(command: GestureCommand, enabled: Boolean) = update { settings ->
        val next = settings.enabledCommands.toMutableSet()
        if (enabled) next += command else next -= command
        settings.copy(enabledCommands = next)
    }

    fun setTargetMode(mode: TargetMode) = update { it.copy(targetMode = mode) }
    fun setManualPackage(packageName: String?) = update { it.copy(manualPackage = packageName, targetMode = TargetMode.MANUAL) }
    fun setFallback(enabled: Boolean) = update { it.copy(mediaKeyFallback = enabled) }
    fun setFeedback(enabled: Boolean) = update { it.copy(feedbackSound = enabled) }
    fun finishOnboarding() = update { it.copy(onboardingDone = true) }
    fun resetSettings() = update { defaultSettings().copy(onboardingDone = true) }
    fun setExecutionMode(mode: ExecutionMode) {
        runtime.executionMode.value = mode
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
