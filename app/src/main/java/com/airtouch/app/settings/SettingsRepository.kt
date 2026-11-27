package com.airtouch.app.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.airtouch.app.control.DefaultBindings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "airtouch_settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException || error is CorruptionException) emit(emptyPreferences()) else throw error
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

    private fun Preferences.toRaw(): RawSettings = RawSettings(
        useFrontCamera = this[Keys.FRONT],
        showPreview = this[Keys.PREVIEW],
        targetFps = this[Keys.FPS],
        sensitivity = this[Keys.SENSITIVITY],
        holdMs = this[Keys.HOLD],
        cooldownMs = this[Keys.COOLDOWN],
        bindings = this[Keys.BINDINGS],
        playbackSource = this[Keys.SOURCE],
        targetMode = this[Keys.TARGET_MODE],
        manualPackage = this[Keys.MANUAL_PACKAGE],
        mediaKeyFallback = this[Keys.FALLBACK],
        onboardingDone = this[Keys.ONBOARDING],
    )

    private fun MutablePreferences.write(settings: AppSettings) {
        this[Keys.FRONT] = settings.useFrontCamera
        this[Keys.PREVIEW] = settings.showPreview
        this[Keys.FPS] = settings.targetFps
        this[Keys.SENSITIVITY] = settings.sensitivity
        this[Keys.HOLD] = settings.holdMs
        this[Keys.COOLDOWN] = settings.cooldownMs
        this[Keys.BINDINGS] = DefaultBindings.encode(settings.bindings)
        this[Keys.SOURCE] = settings.playbackSource.name
        this[Keys.TARGET_MODE] = settings.targetMode.name
        settings.manualPackage?.let { this[Keys.MANUAL_PACKAGE] = it }
        this[Keys.FALLBACK] = settings.mediaKeyFallback
        this[Keys.ONBOARDING] = settings.onboardingDone
    }

    private object Keys {
        val FRONT = booleanPreferencesKey("front_camera")
        val PREVIEW = booleanPreferencesKey("show_preview")
        val FPS = intPreferencesKey("target_fps")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val HOLD = longPreferencesKey("hold_ms_v2")
        val COOLDOWN = longPreferencesKey("cooldown_ms_v2")
        val BINDINGS = stringPreferencesKey("bindings_v2")
        val SOURCE = stringPreferencesKey("playback_source")
        val TARGET_MODE = stringPreferencesKey("target_mode")
        val MANUAL_PACKAGE = stringPreferencesKey("manual_package")
        val FALLBACK = booleanPreferencesKey("media_key_fallback")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
    }
}
