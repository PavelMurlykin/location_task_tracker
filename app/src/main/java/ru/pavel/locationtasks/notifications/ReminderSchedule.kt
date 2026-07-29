package ru.pavel.locationtasks.notifications

import ru.pavel.locationtasks.data.ReminderPreferences
import ru.pavel.locationtasks.data.TaskEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object ReminderSchedule {
    const val ALL_DAYS_MASK = 0b1111111
    const val DEFAULT_WINDOW_START_MINUTES = 9 * 60
    const val DEFAULT_WINDOW_END_MINUTES = 21 * 60

    fun isAllowedNow(
        task: TaskEntity,
        preferences: ReminderPreferences,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        respectTaskWindow: Boolean = true,
    ): Boolean {
        val dateTime = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        return isAllowed(
            task = task,
            preferences = preferences,
            dateTime = dateTime,
            respectTaskWindow = respectTaskWindow,
        )
    }

    fun nextAllowedAt(
        task: TaskEntity,
        preferences: ReminderPreferences,
        fromMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        respectTaskWindow: Boolean = true,
    ): Long? {
        if (respectTaskWindow && task.allowedDaysMask == 0) return null
        if (isAllowedNow(task, preferences, fromMillis, zoneId, respectTaskWindow)) {
            return fromMillis
        }

        var candidate = Instant.ofEpochMilli(fromMillis)
            .atZone(zoneId)
            .truncatedTo(ChronoUnit.MINUTES)
            .plusMinutes(1)
        repeat(MAX_SEARCH_MINUTES) {
            if (isAllowed(task, preferences, candidate, respectTaskWindow)) {
                return candidate.toInstant().toEpochMilli()
            }
            candidate = candidate.plusMinutes(1)
        }
        return null
    }

    fun cooldownMillis(task: TaskEntity, preferences: ReminderPreferences): Long {
        val minutes = task.notificationCooldownMinutes
            ?: preferences.notificationCooldownHours * 60
        return minutes.toLong() * 60_000L
    }

    fun dayBit(dayOfWeek: DayOfWeek): Int = 1 shl (dayOfWeek.value - 1)

    private fun isAllowed(
        task: TaskEntity,
        preferences: ReminderPreferences,
        dateTime: ZonedDateTime,
        respectTaskWindow: Boolean,
    ): Boolean {
        val minuteOfDay = dateTime.hour * 60 + dateTime.minute
        if (preferences.quietHoursEnabled &&
            isInsideWindow(
                minuteOfDay,
                preferences.quietHoursStartMinutes,
                preferences.quietHoursEndMinutes,
            )
        ) {
            return false
        }
        if (!respectTaskWindow) return true
        if (task.allowedDaysMask and dayBit(dateTime.dayOfWeek) == 0) return false
        val start = task.reminderWindowStartMinutes
        val end = task.reminderWindowEndMinutes
        return if (start == null || end == null) {
            true
        } else {
            isInsideWindow(minuteOfDay, start, end)
        }
    }

    private fun isInsideWindow(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> true
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }

    private const val MAX_SEARCH_MINUTES = 8 * 24 * 60
}
