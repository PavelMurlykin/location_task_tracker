package ru.pavel.locationtasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
