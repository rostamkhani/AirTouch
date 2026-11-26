package com.airtouch.app.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.airtouch.app.gestures.GestureCommand
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "airtouch_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException || error is CorruptionException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }
        .map { sanitize(it.toRaw()) }
        .distinctUntilChanged()

    suspend fun update(block: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val updated = block(sanitize(prefs.toRaw()))
            prefs.clear()
            prefs.write(updated)
        }
    }

    private fun Preferences.toRaw(): RawSettings {
        return RawSettings(
            useFrontCamera = this[Keys.FRONT],
            showPreview = this[Keys.PREVIEW],
            targetFps = this[Keys.FPS],
            sensitivity = this[Keys.SENSITIVITY],
            cooldownMs = this[Keys.COOLDOWN],
            holdMs = this[Keys.HOLD],
            stabilizeMs = this[Keys.STABILIZE],
            enabledCommands = this[Keys.ENABLED]?.let { names ->
                if (names == setOf(NONE_SENTINEL)) {
                    emptySet()
                } else {
                    names.mapNotNull { name ->
                        runCatching { GestureCommand.valueOf(name) }.getOrNull()
                    }.toSet()
                }
            },
            targetMode = this[Keys.TARGET_MODE],
            manualPackage = this[Keys.MANUAL_PACKAGE],
            mediaKeyFallback = this[Keys.FALLBACK],
            feedbackSound = this[Keys.FEEDBACK],
            onboardingDone = this[Keys.ONBOARDING],
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(settings: AppSettings) {
        this[Keys.FRONT] = settings.useFrontCamera
        this[Keys.PREVIEW] = settings.showPreview
        this[Keys.FPS] = settings.targetFps
        this[Keys.SENSITIVITY] = settings.sensitivity
        this[Keys.COOLDOWN] = settings.cooldownMs
        this[Keys.HOLD] = settings.holdMs
        this[Keys.STABILIZE] = settings.stabilizeMs
        this[Keys.ENABLED] = settings.enabledCommands.map { it.name }.toSet().ifEmpty { setOf(NONE_SENTINEL) }
        this[Keys.TARGET_MODE] = settings.targetMode.name
        settings.manualPackage?.let { this[Keys.MANUAL_PACKAGE] = it }
        this[Keys.FALLBACK] = settings.mediaKeyFallback
        this[Keys.FEEDBACK] = settings.feedbackSound
        this[Keys.ONBOARDING] = settings.onboardingDone
    }

    private companion object {
        const val NONE_SENTINEL = "__none__"
    }

    private object Keys {
        val FRONT = booleanPreferencesKey("front_camera")
        val PREVIEW = booleanPreferencesKey("show_preview")
        val FPS = intPreferencesKey("target_fps")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val COOLDOWN = longPreferencesKey("cooldown_ms")
        val HOLD = longPreferencesKey("hold_ms")
        val STABILIZE = longPreferencesKey("stabilize_ms")
        val ENABLED = stringSetPreferencesKey("enabled_commands")
        val TARGET_MODE = stringPreferencesKey("target_mode")
        val MANUAL_PACKAGE = stringPreferencesKey("manual_package")
        val FALLBACK = booleanPreferencesKey("media_key_fallback")
        val FEEDBACK = booleanPreferencesKey("feedback_sound")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
    }
}
