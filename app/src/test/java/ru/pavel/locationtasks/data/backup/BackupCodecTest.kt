package ru.pavel.locationtasks.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.pavel.locationtasks.data.GeofenceStatus
import ru.pavel.locationtasks.data.PlaceEntity
import ru.pavel.locationtasks.data.ReminderPreferences
import ru.pavel.locationtasks.data.TaskCategory
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.data.TaskPriority

class BackupCodecTest {
    private val password = "correct horse battery staple".toCharArray()

    @Test
    fun `encrypted backup round trip preserves tasks places and preferences`() {
        val snapshot = BackupSnapshot(
            createdAt = 1_786_000_000_000,
            reminderPreferences = ReminderPreferences(
                notificationCooldownHours = 12,
                quietHoursEnabled = true,
                quietHoursStartMinutes = 23 * 60,
                quietHoursEndMinutes = 7 * 60,
            ),
            tasks = listOf(
                TaskEntity(
                    id = 7,
                    title = "Купить лекарства",
                    description = "После работы",
                    dueAt = 1_786_100_000_000,
                    priority = TaskPriority.HIGH.name,
                    category = TaskCategory.SHOPPING.name,
                    tags = "важно,аптека",
                    latitude = 55.7558,
                    longitude = 37.6176,
                    address = "Москва",
                    geofenceEnabled = true,
                    geofenceStatus = GeofenceStatus.ACTIVE.name,
                    geofenceRegisteredAt = 1_786_000_100_000,
                ),
            ),
            places = listOf(
                PlaceEntity(
                    id = 3,
                    name = "Дом",
                    address = "Москва",
                    latitude = 55.75,
                    longitude = 37.61,
                ),
            ),
        )

        val encrypted = BackupCodec.encode(snapshot, password)
        val restored = BackupCodec.decode(encrypted, password)

        assertEquals(snapshot, restored)
        assertEquals(0x4C, encrypted.first().toInt() and 0xFF)
        assertEquals(-1, encrypted.toString(Charsets.ISO_8859_1).indexOf("Купить"))
    }

    @Test
    fun `wrong password cannot decrypt backup`() {
        val encrypted = BackupCodec.encode(emptySnapshot(), password)

        val exception = assertThrows(BackupCodecException::class.java) {
            BackupCodec.decode(encrypted, "definitely wrong".toCharArray())
        }

        assertEquals(BackupCodecFailure.INVALID_PASSWORD, exception.failure)
    }

    @Test
    fun `tampered backup is rejected`() {
        val encrypted = BackupCodec.encode(emptySnapshot(), password)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        assertThrows(BackupCodecException::class.java) {
            BackupCodec.decode(encrypted, password)
        }
    }

    private fun emptySnapshot() = BackupSnapshot(
        createdAt = 1_786_000_000_000,
        reminderPreferences = ReminderPreferences(),
        tasks = emptyList(),
        places = emptyList(),
    )
}
