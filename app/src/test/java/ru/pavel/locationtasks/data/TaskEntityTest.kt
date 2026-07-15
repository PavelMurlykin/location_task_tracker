package ru.pavel.locationtasks.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskEntityTest {
    @Test
    fun `task with enabled location is monitored`() {
        val task = taskWithLocation(geofenceEnabled = true)

        assertTrue(task.shouldMonitor)
    }

    @Test
    fun `completed task is not monitored`() {
        val task = taskWithLocation(geofenceEnabled = true).copy(isCompleted = true)

        assertFalse(task.shouldMonitor)
    }

    @Test
    fun `task without coordinates is not monitored`() {
        val task = TaskEntity(title = "Купить молоко", geofenceEnabled = true)

        assertFalse(task.hasLocation)
        assertFalse(task.shouldMonitor)
    }

    @Test
    fun `disabled geofence is not monitored`() {
        val task = taskWithLocation(geofenceEnabled = false)

        assertTrue(task.hasLocation)
        assertFalse(task.shouldMonitor)
    }

    private fun taskWithLocation(geofenceEnabled: Boolean) = TaskEntity(
        id = 1,
        title = "Забрать заказ",
        latitude = 55.7558,
        longitude = 37.6173,
        geofenceEnabled = geofenceEnabled,
    )
}
