package ru.pavel.locationtasks.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val notificationCooldownHours: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_COOLDOWN_HOURS] ?: DEFAULT_COOLDOWN_HOURS
    }

    suspend fun setNotificationCooldownHours(hours: Int) {
        require(hours in ALLOWED_COOLDOWNS)
        context.dataStore.edit { it[NOTIFICATION_COOLDOWN_HOURS] = hours }
    }

    companion object {
        val ALLOWED_COOLDOWNS = setOf(1, 4, 12, 24)
        const val DEFAULT_COOLDOWN_HOURS = 4
        private val NOTIFICATION_COOLDOWN_HOURS = intPreferencesKey("notification_cooldown_hours")
    }
}
