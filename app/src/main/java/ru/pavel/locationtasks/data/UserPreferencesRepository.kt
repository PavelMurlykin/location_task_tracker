package ru.pavel.locationtasks.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

data class ReminderPreferences(
    val notificationCooldownHours: Int = UserPreferencesRepository.DEFAULT_COOLDOWN_HOURS,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartMinutes: Int = UserPreferencesRepository.DEFAULT_QUIET_START_MINUTES,
    val quietHoursEndMinutes: Int = UserPreferencesRepository.DEFAULT_QUIET_END_MINUTES,
)

data class SecurityPreferences(
    val appLockEnabled: Boolean = false,
)

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class ProductPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val analyticsConsent: Boolean = false,
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val reminderPreferences: Flow<ReminderPreferences> = context.dataStore.data.map { preferences ->
        ReminderPreferences(
            notificationCooldownHours =
                preferences[NOTIFICATION_COOLDOWN_HOURS] ?: DEFAULT_COOLDOWN_HOURS,
            quietHoursEnabled = preferences[QUIET_HOURS_ENABLED] ?: false,
            quietHoursStartMinutes =
                preferences[QUIET_HOURS_START_MINUTES] ?: DEFAULT_QUIET_START_MINUTES,
            quietHoursEndMinutes =
                preferences[QUIET_HOURS_END_MINUTES] ?: DEFAULT_QUIET_END_MINUTES,
        )
    }
    val notificationCooldownHours: Flow<Int> =
        reminderPreferences.map { it.notificationCooldownHours }
    val securityPreferences: Flow<SecurityPreferences> = context.dataStore.data.map { preferences ->
        SecurityPreferences(
            appLockEnabled = preferences[APP_LOCK_ENABLED] ?: false,
        )
    }
    val productPreferences: Flow<ProductPreferences> = context.dataStore.data.map { preferences ->
        ProductPreferences(
            onboardingCompleted = preferences[ONBOARDING_COMPLETED] ?: false,
            themeMode = AppThemeMode.fromStorage(preferences[THEME_MODE]),
            analyticsConsent = preferences[ANALYTICS_CONSENT] ?: false,
        )
    }

    suspend fun setNotificationCooldownHours(hours: Int) {
        require(hours in ALLOWED_COOLDOWNS)
        context.dataStore.edit { it[NOTIFICATION_COOLDOWN_HOURS] = hours }
    }

    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        context.dataStore.edit { it[QUIET_HOURS_ENABLED] = enabled }
    }

    suspend fun setQuietHours(startMinutes: Int, endMinutes: Int) {
        require(startMinutes in MINUTES_IN_DAY)
        require(endMinutes in MINUTES_IN_DAY)
        context.dataStore.edit {
            it[QUIET_HOURS_START_MINUTES] = startMinutes
            it[QUIET_HOURS_END_MINUTES] = endMinutes
        }
    }

    suspend fun restoreReminderPreferences(preferences: ReminderPreferences) {
        require(preferences.notificationCooldownHours in ALLOWED_COOLDOWNS)
        require(preferences.quietHoursStartMinutes in MINUTES_IN_DAY)
        require(preferences.quietHoursEndMinutes in MINUTES_IN_DAY)
        context.dataStore.edit {
            it[NOTIFICATION_COOLDOWN_HOURS] = preferences.notificationCooldownHours
            it[QUIET_HOURS_ENABLED] = preferences.quietHoursEnabled
            it[QUIET_HOURS_START_MINUTES] = preferences.quietHoursStartMinutes
            it[QUIET_HOURS_END_MINUTES] = preferences.quietHoursEndMinutes
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[APP_LOCK_ENABLED] = enabled }
    }

    suspend fun completeOnboarding(analyticsConsent: Boolean) {
        context.dataStore.edit {
            it[ANALYTICS_CONSENT] = analyticsConsent
            it[ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setAnalyticsConsent(consent: Boolean) {
        context.dataStore.edit { it[ANALYTICS_CONSENT] = consent }
    }

    companion object {
        val ALLOWED_COOLDOWNS = setOf(1, 4, 12, 24)
        const val DEFAULT_COOLDOWN_HOURS = 4
        const val DEFAULT_QUIET_START_MINUTES = 22 * 60
        const val DEFAULT_QUIET_END_MINUTES = 8 * 60
        private val MINUTES_IN_DAY = 0 until 24 * 60
        private val NOTIFICATION_COOLDOWN_HOURS = intPreferencesKey("notification_cooldown_hours")
        private val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        private val QUIET_HOURS_START_MINUTES = intPreferencesKey("quiet_hours_start_minutes")
        private val QUIET_HOURS_END_MINUTES = intPreferencesKey("quiet_hours_end_minutes")
        private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ANALYTICS_CONSENT = booleanPreferencesKey("analytics_consent")
    }
}
