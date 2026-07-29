package ru.pavel.locationtasks.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.pavel.locationtasks.data.ReminderPreferences
import ru.pavel.locationtasks.data.TaskEntity
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun `overnight quiet hours block night and allow daytime`() {
        val preferences = ReminderPreferences(
            quietHoursEnabled = true,
            quietHoursStartMinutes = 22 * 60,
            quietHoursEndMinutes = 8 * 60,
        )
        val task = TaskEntity(title = "Task")

        assertFalse(ReminderSchedule.isAllowedNow(task, preferences, at(23, 0), zone))
        assertFalse(ReminderSchedule.isAllowedNow(task, preferences, at(7, 59), zone))
        assertTrue(ReminderSchedule.isAllowedNow(task, preferences, at(8, 0), zone))
    }

    @Test
    fun `task day mask and time window are both required`() {
        val mondayOnly = ReminderSchedule.dayBit(DayOfWeek.MONDAY)
        val task = TaskEntity(
            title = "Task",
            allowedDaysMask = mondayOnly,
            reminderWindowStartMinutes = 18 * 60,
            reminderWindowEndMinutes = 21 * 60,
        )
        val preferences = ReminderPreferences()

        assertFalse(ReminderSchedule.isAllowedNow(task, preferences, at(17, 59), zone))
        assertTrue(ReminderSchedule.isAllowedNow(task, preferences, at(18, 0), zone))
        assertFalse(
            ReminderSchedule.isAllowedNow(
                task,
                preferences,
                at(18, 0, dayOfMonth = 28),
                zone,
            ),
        )
    }

    @Test
    fun `next allowed time moves reminder to end of quiet hours`() {
        val task = TaskEntity(title = "Task")
        val preferences = ReminderPreferences(
            quietHoursEnabled = true,
            quietHoursStartMinutes = 22 * 60,
            quietHoursEndMinutes = 8 * 60,
        )

        val next = ReminderSchedule.nextAllowedAt(task, preferences, at(23, 15), zone)

        assertEquals(at(8, 0, dayOfMonth = 28), next)
    }

    @Test
    fun `task cooldown overrides global cooldown`() {
        val task = TaskEntity(title = "Task", notificationCooldownMinutes = 15)

        assertEquals(
            15 * 60_000L,
            ReminderSchedule.cooldownMillis(
                task,
                ReminderPreferences(notificationCooldownHours = 24),
            ),
        )
    }

    private fun at(hour: Int, minute: Int, dayOfMonth: Int = 27): Long =
        ZonedDateTime.of(2026, 7, dayOfMonth, hour, minute, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
}
