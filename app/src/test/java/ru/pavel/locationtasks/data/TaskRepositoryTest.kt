package ru.pavel.locationtasks.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.pavel.locationtasks.testing.FakeGeofenceCoordinator
import ru.pavel.locationtasks.testing.FakeTaskDao

class TaskRepositoryTest {
    @Test
    fun `creation persists task and requests geofence registration`() = runBlocking {
        val taskDao = FakeTaskDao()
        val coordinator = FakeGeofenceCoordinator()
        val repository = TaskRepository(taskDao, coordinator)

        val id = repository.save(monitoredTask(title = "Забрать заказ"))

        assertEquals(1L, id)
        assertEquals("Забрать заказ", taskDao.getById(id)?.title)
        assertEquals(listOf(id), coordinator.reconciledTaskIds)
    }

    @Test
    fun `editing updates existing task without creating a duplicate`() = runBlocking {
        val original = monitoredTask(id = 7L, title = "Старое название")
        val taskDao = FakeTaskDao(listOf(original))
        val coordinator = FakeGeofenceCoordinator()
        val repository = TaskRepository(taskDao, coordinator)

        val savedId = repository.save(original.copy(title = "Новое название"))

        assertEquals(7L, savedId)
        assertEquals(1, taskDao.snapshot().size)
        assertEquals("Новое название", taskDao.getById(7L)?.title)
        assertTrue(coordinator.reconciledTaskIds.isEmpty())
        assertEquals(1, coordinator.reconcileAllCalls)
    }

    @Test
    fun `completion disables geofence and frees a slot`() = runBlocking {
        val task = monitoredTask(id = 3L)
        val taskDao = FakeTaskDao(listOf(task))
        val coordinator = FakeGeofenceCoordinator()
        val repository = TaskRepository(taskDao, coordinator)

        repository.setCompleted(task, true)

        assertTrue(taskDao.getById(3L)?.isCompleted == true)
        assertEquals(listOf(3L), coordinator.deactivatedTaskIds)
        assertEquals(1, coordinator.reconcileAllCalls)
    }

    @Test
    fun `reopening completed task requests geofence registration again`() = runBlocking {
        val task = monitoredTask(id = 4L).copy(isCompleted = true)
        val taskDao = FakeTaskDao(listOf(task))
        val coordinator = FakeGeofenceCoordinator()
        val repository = TaskRepository(taskDao, coordinator)

        repository.setCompleted(task, false)

        assertFalse(taskDao.getById(4L)?.isCompleted == true)
        assertEquals(listOf(4L), coordinator.reconciledTaskIds)
    }

    @Test
    fun `restoring deleted task keeps its id and geofence`() = runBlocking {
        val task = monitoredTask(id = 9L)
        val taskDao = FakeTaskDao()
        val coordinator = FakeGeofenceCoordinator()
        val repository = TaskRepository(taskDao, coordinator)

        repository.restore(task)

        assertEquals(task, taskDao.getById(9L))
        assertEquals(listOf(9L), coordinator.reconciledTaskIds)
    }

    @Test
    fun `changing due date does not force geofence re-registration`() = runBlocking {
        val task = monitoredTask(id = 10L)
        val taskDao = FakeTaskDao(listOf(task))
        val coordinator = FakeGeofenceCoordinator()
        val repository = TaskRepository(taskDao, coordinator)

        repository.setDueAt(10L, 123_456L)

        assertEquals(123_456L, taskDao.getById(10L)?.dueAt)
        assertTrue(coordinator.reconciledTaskIds.isEmpty())
        assertEquals(1, coordinator.reconcileAllCalls)
    }

    private fun monitoredTask(
        id: Long = 0,
        title: String = "Купить продукты",
    ) = TaskEntity(
        id = id,
        title = title,
        latitude = 55.7558,
        longitude = 37.6173,
        geofenceEnabled = true,
    )
}
