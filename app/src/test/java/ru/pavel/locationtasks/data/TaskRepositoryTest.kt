package ru.pavel.locationtasks.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.pavel.locationtasks.testing.FakeGeofenceCoordinator
import ru.pavel.locationtasks.testing.FakeTaskDao
import ru.pavel.locationtasks.testing.FakeReminderWorkScheduler
import ru.pavel.locationtasks.data.ChecklistCodec
import ru.pavel.locationtasks.data.ChecklistItem
import ru.pavel.locationtasks.data.TaskRecurrence

class TaskRepositoryTest {
    @Test
    fun `creation persists task and requests geofence registration`() = runBlocking {
        val taskDao = FakeTaskDao()
        val coordinator = FakeGeofenceCoordinator()
        val repository = TaskRepository(taskDao, coordinator, FakeReminderWorkScheduler())

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
        val repository = TaskRepository(taskDao, coordinator, FakeReminderWorkScheduler())

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
        val repository = TaskRepository(taskDao, coordinator, FakeReminderWorkScheduler())

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
        val repository = TaskRepository(taskDao, coordinator, FakeReminderWorkScheduler())

        repository.setCompleted(task, false)

        assertFalse(taskDao.getById(4L)?.isCompleted == true)
        assertEquals(listOf(4L), coordinator.reconciledTaskIds)
    }

    @Test
    fun `restoring deleted task keeps its id and geofence`() = runBlocking {
        val task = monitoredTask(id = 9L)
        val taskDao = FakeTaskDao()
        val coordinator = FakeGeofenceCoordinator()
        val repository = TaskRepository(taskDao, coordinator, FakeReminderWorkScheduler())

        repository.restore(task)

        assertEquals(task, taskDao.getById(9L))
        assertEquals(listOf(9L), coordinator.reconciledTaskIds)
    }

    @Test
    fun `changing due date does not force geofence re-registration`() = runBlocking {
        val task = monitoredTask(id = 10L)
        val taskDao = FakeTaskDao(listOf(task))
        val coordinator = FakeGeofenceCoordinator()
        val repository = TaskRepository(taskDao, coordinator, FakeReminderWorkScheduler())

        repository.setDueAt(10L, 123_456L)

        assertEquals(123_456L, taskDao.getById(10L)?.dueAt)
        assertTrue(coordinator.reconciledTaskIds.isEmpty())
        assertEquals(1, coordinator.reconcileAllCalls)
    }

    @Test
    fun `saving task without geofence synchronizes due reminder`() = runBlocking {
        val taskDao = FakeTaskDao()
        val coordinator = FakeGeofenceCoordinator()
        val scheduler = FakeReminderWorkScheduler()
        val repository = TaskRepository(taskDao, coordinator, scheduler)

        val id = repository.save(
            TaskEntity(
                title = "Оплатить счёт",
                dueAt = 123_456L,
            ),
        )

        assertEquals(id, scheduler.syncedTasks.single().id)
        assertEquals(123_456L, scheduler.syncedTasks.single().dueAt)
    }

    @Test
    fun `completing recurring task advances due date and resets checklist`() = runBlocking {
        val dueAt = System.currentTimeMillis() + 86_400_000L
        val task = TaskEntity(
            id = 20,
            title = "Полить цветы",
            dueAt = dueAt,
            recurrence = TaskRecurrence.DAILY.name,
            checklistData = ChecklistCodec.encode(
                listOf(ChecklistItem(title = "Проверить землю", isCompleted = true)),
            ),
        )
        val taskDao = FakeTaskDao(listOf(task))
        val repository = TaskRepository(
            taskDao,
            FakeGeofenceCoordinator(),
            FakeReminderWorkScheduler(),
        )

        val outcome = repository.setCompleted(task, true)
        val updated = requireNotNull(taskDao.getById(task.id))

        assertEquals(TaskCompletionOutcome.RESCHEDULED, outcome)
        assertFalse(updated.isCompleted)
        assertEquals(dueAt + 86_400_000L, updated.dueAt)
        assertFalse(updated.checklistItems.single().isCompleted)
    }

    @Test
    fun `archiving task disables its reminders`() = runBlocking {
        val task = monitoredTask(id = 21L).copy(isCompleted = true)
        val taskDao = FakeTaskDao(listOf(task))
        val coordinator = FakeGeofenceCoordinator()
        val scheduler = FakeReminderWorkScheduler()
        val repository = TaskRepository(taskDao, coordinator, scheduler)

        repository.setArchived(task, true)

        assertTrue(taskDao.getById(task.id)?.isArchived == true)
        assertEquals(listOf(task.id), coordinator.deactivatedTaskIds)
        assertTrue(scheduler.syncedTasks.single().isArchived)
    }

    @Test
    fun `duplicating task creates active copy and resets checklist`() = runBlocking {
        val task = TaskEntity(
            id = 22,
            title = "Собрать документы",
            isCompleted = true,
            isArchived = true,
            checklistData = ChecklistCodec.encode(
                listOf(ChecklistItem(title = "Паспорт", isCompleted = true)),
            ),
        )
        val taskDao = FakeTaskDao(listOf(task))
        val repository = TaskRepository(
            taskDao,
            FakeGeofenceCoordinator(),
            FakeReminderWorkScheduler(),
        )

        val duplicateId = repository.duplicate(task)
        val duplicate = requireNotNull(taskDao.getById(duplicateId))

        assertFalse(duplicate.isCompleted)
        assertFalse(duplicate.isArchived)
        assertFalse(duplicate.checklistItems.single().isCompleted)
        assertEquals(2, taskDao.snapshot().size)
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
