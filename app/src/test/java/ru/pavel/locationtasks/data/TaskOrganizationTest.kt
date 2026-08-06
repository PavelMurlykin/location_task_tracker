package ru.pavel.locationtasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskOrganizationTest {
    @Test
    fun `checklist codec preserves unicode and completion state`() {
        val items = listOf(
            ChecklistItem(id = "one", title = "Купить молоко", isCompleted = true),
            ChecklistItem(id = "two", title = "Строка\tс разделителем\nи переносом"),
        )

        assertEquals(items, ChecklistCodec.decode(ChecklistCodec.encode(items)))
    }

    @Test
    fun `tags are trimmed deduplicated and limited`() {
        val tags = parseTags(" срочно, продукты;СРОЧНО\n дом ")

        assertEquals(listOf("срочно", "продукты", "дом"), tags)
        assertEquals(tags, decodeTags(encodeTags(tags)))
    }

    @Test
    fun `daily recurrence advances future due date by one day`() {
        val zone = ZoneId.of("Europe/Moscow")
        val due = ZonedDateTime.of(2026, 7, 30, 18, 0, 0, 0, zone)
        val now = due.minusHours(2)

        val next = nextOccurrenceAt(
            recurrence = TaskRecurrence.DAILY,
            currentDueAt = due.toInstant().toEpochMilli(),
            nowMillis = now.toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(due.plusDays(1).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `missed weekly recurrence always lands in future`() {
        val zone = ZoneId.of("Europe/Moscow")
        val due = ZonedDateTime.of(2026, 7, 1, 9, 0, 0, 0, zone)
        val now = ZonedDateTime.of(2026, 7, 29, 12, 0, 0, 0, zone)

        val next = requireNotNull(
            nextOccurrenceAt(
                recurrence = TaskRecurrence.WEEKLY,
                currentDueAt = due.toInstant().toEpochMilli(),
                nowMillis = now.toInstant().toEpochMilli(),
                zoneId = zone,
            ),
        )

        assertTrue(next > now.toInstant().toEpochMilli())
    }

    @Test
    fun `weekly recurrence can select Tuesday`() {
        val zone = ZoneId.of("Europe/Moscow")
        val due = ZonedDateTime.of(2026, 8, 4, 9, 0, 0, 0, zone)

        val next = nextOccurrenceAt(
            rule = TaskRecurrenceRule(
                recurrence = TaskRecurrence.WEEKLY,
                daysOfWeekMask = recurrenceDayBit(DayOfWeek.TUESDAY),
                anchorAt = due.toInstant().toEpochMilli(),
            ),
            currentDueAt = due.toInstant().toEpochMilli(),
            nowMillis = due.minusHours(1).toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(due.plusWeeks(1).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `Wednesday and Friday every two weeks keep the original week phase`() {
        val zone = ZoneId.of("Europe/Moscow")
        val wednesday = ZonedDateTime.of(2026, 9, 2, 18, 30, 0, 0, zone)
        val friday = wednesday.plusDays(2)
        val rule = TaskRecurrenceRule(
            recurrence = TaskRecurrence.WEEKLY,
            interval = 2,
            daysOfWeekMask = recurrenceDayBit(DayOfWeek.WEDNESDAY) or
                recurrenceDayBit(DayOfWeek.FRIDAY),
            anchorAt = wednesday.toInstant().toEpochMilli(),
        )

        val afterWednesday = nextOccurrenceAt(
            rule = rule,
            currentDueAt = wednesday.toInstant().toEpochMilli(),
            nowMillis = wednesday.minusMinutes(1).toInstant().toEpochMilli(),
            zoneId = zone,
        )
        val afterFriday = nextOccurrenceAt(
            rule = rule,
            currentDueAt = friday.toInstant().toEpochMilli(),
            nowMillis = friday.minusMinutes(1).toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(friday.toInstant().toEpochMilli(), afterWednesday)
        assertEquals(wednesday.plusWeeks(2).toInstant().toEpochMilli(), afterFriday)
    }

    @Test
    fun `monthly recurrence can run on the third day`() {
        val zone = ZoneId.of("Europe/Moscow")
        val due = ZonedDateTime.of(2026, 8, 3, 8, 15, 0, 0, zone)

        val next = nextOccurrenceAt(
            rule = TaskRecurrenceRule(
                recurrence = TaskRecurrence.MONTHLY,
                dayOfMonth = 3,
                anchorAt = due.toInstant().toEpochMilli(),
            ),
            currentDueAt = due.toInstant().toEpochMilli(),
            nowMillis = due.minusMinutes(1).toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(due.plusMonths(1).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `recurrence stops after configured end date`() {
        val zone = ZoneId.of("Europe/Moscow")
        val due = ZonedDateTime.of(2026, 8, 4, 9, 0, 0, 0, zone)

        val next = nextOccurrenceAt(
            rule = TaskRecurrenceRule(
                recurrence = TaskRecurrence.WEEKLY,
                daysOfWeekMask = recurrenceDayBit(DayOfWeek.TUESDAY),
                anchorAt = due.toInstant().toEpochMilli(),
                endAt = due.plusDays(3).toInstant().toEpochMilli(),
            ),
            currentDueAt = due.toInstant().toEpochMilli(),
            nowMillis = due.toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertNull(next)
    }
}
