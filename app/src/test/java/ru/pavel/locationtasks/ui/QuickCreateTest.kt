package ru.pavel.locationtasks.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.pavel.locationtasks.data.PlaceEntity

class QuickCreateTest {
    private val home = PlaceEntity(
        id = 1,
        name = "Дом",
        address = "Домашний адрес",
        latitude = 55.7,
        longitude = 37.5,
    )

    @Test
    fun `voice phrase extracts title and saved place`() {
        val parsed = parseVoiceTask("Купить молоко возле дома", listOf(home))

        assertEquals("Купить молоко", parsed.title)
        assertEquals(home.id, parsed.placeId)
    }

    @Test
    fun `voice phrase without matching place remains unchanged`() {
        val parsed = parseVoiceTask("Позвонить родителям", listOf(home))

        assertEquals("Позвонить родителям", parsed.title)
        assertNull(parsed.placeId)
    }
}
