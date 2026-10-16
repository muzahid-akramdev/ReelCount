package dev.akram.reelcount.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class UserSettings(
    val dailyLimit: Int = 50,
    val nudgesEnabled: Boolean = true,
    val vibrate: Boolean = true,
    val overlayEnabled: Boolean = true,
    val countBackward: Boolean = false,
    val languageTag: String = "system",
    val enabledPackages: Set<String> = DefaultPackages,
    val onboardingDone: Boolean = false,
    val overlayX: Int = 0,
    val overlayY: Int = 32,
    val snoozeUntilEpochMs: Long = 0,
    val lastNudgeDay: String = "",
    val lastNudgedCount: Int = 0,
)

private val Context.dataStore by preferencesDataStore(name = "reelcount_settings")

class SettingsStore(
    private val context: Context,
) {
    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setDailyLimit(limit: Int) {
        context.dataStore.edit { it[Keys.LIMIT] = limit.coerceIn(1, 500) }
    }

    suspend fun setNudges(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NUDGES] = enabled }
    }

    suspend fun setVibrate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATE] = enabled }
    }

    suspend fun setOverlay(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OVERLAY] = enabled }
    }

    suspend fun setCountBackward(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKWARD] = enabled }
    }

    suspend fun setLanguage(tag: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = tag }
    }

    suspend fun setPackageEnabled(
        packageName: String,
        enabled: Boolean,
    ) {
        context.dataStore.edit { prefs ->
            val current = prefs.toSettings().enabledPackages.toMutableSet()
            if (enabled) current.add(packageName) else current.remove(packageName)
            prefs[Keys.PACKAGES] = current.joinToString(",")
        }
    }

    suspend fun addPackages(packageNames: Set<String>) {
        if (packageNames.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs.toSettings().enabledPackages.toMutableSet()
            current.addAll(packageNames)
            prefs[Keys.PACKAGES] = current.joinToString(",")
        }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING] = true }
    }

    suspend fun setOverlayPosition(
        x: Int,
        y: Int,
    ) {
        context.dataStore.edit {
            it[Keys.OVERLAY_X] = x
            it[Keys.OVERLAY_Y] = y
        }
    }

    suspend fun setSnoozeUntil(epochMs: Long) {
        context.dataStore.edit { it[Keys.SNOOZE] = epochMs }
    }

    suspend fun setLastNudge(
        day: String,
        count: Int,
    ) {
        context.dataStore.edit {
            it[Keys.NUDGE_DAY] = day
            it[Keys.NUDGE_COUNT] = count
        }
    }
}

private object Keys {
    val LIMIT = intPreferencesKey("daily_limit")
    val NUDGES = booleanPreferencesKey("nudges")
    val VIBRATE = booleanPreferencesKey("vibrate")
    val OVERLAY = booleanPreferencesKey("overlay")
    val BACKWARD = booleanPreferencesKey("count_backward")
    val LANGUAGE = stringPreferencesKey("language")
    val PACKAGES = stringPreferencesKey("packages")
    val ONBOARDING = booleanPreferencesKey("onboarding_done")
    val OVERLAY_X = intPreferencesKey("overlay_x")
    val OVERLAY_Y = intPreferencesKey("overlay_y")
    val SNOOZE = longPreferencesKey("snooze_until")
    val NUDGE_DAY = stringPreferencesKey("nudge_day")
    val NUDGE_COUNT = intPreferencesKey("nudge_count")
}

val DefaultPackages =
    setOf(
        "com.facebook.katana",
        "com.facebook.lite",
        "com.instagram.android",
        "com.google.android.youtube",
    )

private fun Preferences.toSettings(): UserSettings {
    val rawPackages = this[Keys.PACKAGES]
    val packages =
        if (rawPackages.isNullOrBlank()) {
            DefaultPackages
        } else {
            rawPackages
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    return UserSettings(
        dailyLimit = this[Keys.LIMIT] ?: 50,
        nudgesEnabled = this[Keys.NUDGES] ?: true,
        vibrate = this[Keys.VIBRATE] ?: true,
        overlayEnabled = this[Keys.OVERLAY] ?: true,
        countBackward = this[Keys.BACKWARD] ?: false,
        languageTag = this[Keys.LANGUAGE] ?: "system",
        enabledPackages = packages,
        onboardingDone = this[Keys.ONBOARDING] ?: false,
        overlayX = this[Keys.OVERLAY_X] ?: 0,
        overlayY = this[Keys.OVERLAY_Y] ?: 32,
        snoozeUntilEpochMs = this[Keys.SNOOZE] ?: 0L,
        lastNudgeDay = this[Keys.NUDGE_DAY] ?: "",
        lastNudgedCount = this[Keys.NUDGE_COUNT] ?: 0,
    )
}
