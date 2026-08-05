package ru.pavel.locationtasks.analytics

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private val Context.metricsDataStore by preferencesDataStore(name = "product_metrics")

data class ProductMetrics(
    val firstSeenAt: Long? = null,
    val lastSeenAt: Long? = null,
    val sessionCount: Int = 0,
    val activeDays: Int = 0,
    val retainedDayOne: Boolean = false,
    val retainedDaySeven: Boolean = false,
    val geofenceRegistrationAttempts: Int = 0,
    val geofenceRegistrationSuccesses: Int = 0,
    val geofenceTriggers: Int = 0,
    val remindersDelivered: Int = 0,
    val notificationsShown: Int = 0,
    val notificationCompletions: Int = 0,
) {
    val geofenceRegistrationSuccessRate: Double?
        get() = ratio(geofenceRegistrationSuccesses, geofenceRegistrationAttempts)
    val reminderDeliveryRate: Double?
        get() = ratio(remindersDelivered, geofenceTriggers)
    val notificationCompletionRate: Double?
        get() = ratio(notificationCompletions, notificationsShown)

    private fun ratio(value: Int, total: Int): Double? =
        if (total == 0) null else value.toDouble() / total
}

@Singleton
class ProductMetricsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val metrics: Flow<ProductMetrics> = context.metricsDataStore.data.map { values ->
        ProductMetrics(
            firstSeenAt = values[FIRST_SEEN_AT],
            lastSeenAt = values[LAST_SEEN_AT],
            sessionCount = values[SESSION_COUNT] ?: 0,
            activeDays = values[ACTIVE_DAYS] ?: 0,
            retainedDayOne = values[RETAINED_DAY_ONE] ?: false,
            retainedDaySeven = values[RETAINED_DAY_SEVEN] ?: false,
            geofenceRegistrationAttempts = values[GEOFENCE_REGISTRATION_ATTEMPTS] ?: 0,
            geofenceRegistrationSuccesses = values[GEOFENCE_REGISTRATION_SUCCESSES] ?: 0,
            geofenceTriggers = values[GEOFENCE_TRIGGERS] ?: 0,
            remindersDelivered = values[REMINDERS_DELIVERED] ?: 0,
            notificationsShown = values[NOTIFICATIONS_SHOWN] ?: 0,
            notificationCompletions = values[NOTIFICATION_COMPLETIONS] ?: 0,
        )
    }

    suspend fun recordAppOpen(now: Long = System.currentTimeMillis()): Int {
        var daysSinceFirstOpen = 0
        context.metricsDataStore.edit { values ->
            val firstSeenAt = values[FIRST_SEEN_AT] ?: now.also { values[FIRST_SEEN_AT] = it }
            val currentDay = now.epochDay()
            val firstDay = firstSeenAt.epochDay()
            daysSinceFirstOpen = (currentDay - firstDay).toInt().coerceAtLeast(0)
            val lastActiveDay = values[LAST_ACTIVE_EPOCH_DAY]
            if (lastActiveDay != currentDay) {
                values[ACTIVE_DAYS] = (values[ACTIVE_DAYS] ?: 0) + 1
                values[LAST_ACTIVE_EPOCH_DAY] = currentDay
            }
            values[LAST_SEEN_AT] = now
            values[SESSION_COUNT] = (values[SESSION_COUNT] ?: 0) + 1
            if (daysSinceFirstOpen >= 1) values[RETAINED_DAY_ONE] = true
            if (daysSinceFirstOpen >= 7) values[RETAINED_DAY_SEVEN] = true
        }
        return daysSinceFirstOpen
    }

    suspend fun recordGeofenceRegistration(success: Boolean) {
        context.metricsDataStore.edit { values ->
            values[GEOFENCE_REGISTRATION_ATTEMPTS] =
                (values[GEOFENCE_REGISTRATION_ATTEMPTS] ?: 0) + 1
            if (success) {
                values[GEOFENCE_REGISTRATION_SUCCESSES] =
                    (values[GEOFENCE_REGISTRATION_SUCCESSES] ?: 0) + 1
            }
        }
    }

    suspend fun recordGeofenceTrigger(delivered: Boolean) {
        context.metricsDataStore.edit { values ->
            values[GEOFENCE_TRIGGERS] = (values[GEOFENCE_TRIGGERS] ?: 0) + 1
            if (delivered) {
                values[REMINDERS_DELIVERED] = (values[REMINDERS_DELIVERED] ?: 0) + 1
                values[NOTIFICATIONS_SHOWN] = (values[NOTIFICATIONS_SHOWN] ?: 0) + 1
            }
        }
    }

    suspend fun recordDueReminderDelivery(delivered: Boolean) {
        if (!delivered) return
        context.metricsDataStore.edit { values ->
            values[NOTIFICATIONS_SHOWN] = (values[NOTIFICATIONS_SHOWN] ?: 0) + 1
        }
    }

    suspend fun recordNotificationCompletion() {
        context.metricsDataStore.edit { values ->
            values[NOTIFICATION_COMPLETIONS] =
                (values[NOTIFICATION_COMPLETIONS] ?: 0) + 1
        }
    }

    suspend fun clear() {
        context.metricsDataStore.edit { it.clear() }
    }

    private fun Long.epochDay(): Long = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()

    private companion object {
        val FIRST_SEEN_AT = longPreferencesKey("first_seen_at")
        val LAST_SEEN_AT = longPreferencesKey("last_seen_at")
        val LAST_ACTIVE_EPOCH_DAY = longPreferencesKey("last_active_epoch_day")
        val SESSION_COUNT = intPreferencesKey("session_count")
        val ACTIVE_DAYS = intPreferencesKey("active_days")
        val RETAINED_DAY_ONE = booleanPreferencesKey("retained_day_one")
        val RETAINED_DAY_SEVEN = booleanPreferencesKey("retained_day_seven")
        val GEOFENCE_REGISTRATION_ATTEMPTS = intPreferencesKey("geofence_attempts")
        val GEOFENCE_REGISTRATION_SUCCESSES = intPreferencesKey("geofence_successes")
        val GEOFENCE_TRIGGERS = intPreferencesKey("geofence_triggers")
        val REMINDERS_DELIVERED = intPreferencesKey("reminders_delivered")
        val NOTIFICATIONS_SHOWN = intPreferencesKey("notifications_shown")
        val NOTIFICATION_COMPLETIONS = intPreferencesKey("notification_completions")
    }
}
