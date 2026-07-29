package ru.pavel.locationtasks.data

import kotlinx.coroutines.flow.Flow
import ru.pavel.locationtasks.location.GeofenceCoordinator
import ru.pavel.locationtasks.notifications.ReminderWorkScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val geofenceCoordinator: GeofenceCoordinator,
    private val reminderScheduler: ReminderWorkScheduler,
) {
    fun observeAll(): Flow<List<TaskEntity>> = taskDao.observeAll()

    fun observeById(id: Long): Flow<TaskEntity?> = taskDao.observeById(id)

    suspend fun getById(id: Long): TaskEntity? = taskDao.getById(id)

    suspend fun save(task: TaskEntity): Long {
        val now = System.currentTimeMillis()
        val previousTask = task.id.takeIf { it > 0 }?.let { taskDao.getById(it) }
        val savedTask = if (task.id == 0L) {
            val newTask = task.copy(createdAt = now, updatedAt = now)
            val id = taskDao.insert(newTask)
            newTask.copy(id = id)
        } else {
            val updatedTask = task.copy(updatedAt = now)
            taskDao.update(updatedTask)
            updatedTask
        }

        if (savedTask.shouldMonitor) {
            if (previousTask == null ||
                previousTask.locationReminderConfigurationDiffersFrom(savedTask)
            ) {
                reminderScheduler.cancelLocationReminder(savedTask.id)
            }
            if (previousTask == null || previousTask.geofenceConfigurationDiffersFrom(savedTask)) {
                geofenceCoordinator.reconcileTask(savedTask.id)
            } else {
                geofenceCoordinator.reconcileAll()
            }
        } else {
            reminderScheduler.cancelLocationReminder(savedTask.id)
            geofenceCoordinator.deactivate(savedTask.id)
            geofenceCoordinator.reconcileAll()
        }
        reminderScheduler.syncDueReminder(savedTask)
        return savedTask.id
    }

    suspend fun restore(task: TaskEntity) {
        taskDao.insert(task)
        if (task.shouldMonitor) {
            geofenceCoordinator.reconcileTask(task.id)
        } else {
            geofenceCoordinator.reconcileAll()
        }
        reminderScheduler.syncDueReminder(task)
    }

    suspend fun setDueAt(taskId: Long, dueAt: Long?) {
        val task = taskDao.getById(taskId) ?: return
        save(task.copy(dueAt = dueAt))
    }

    suspend fun setCompleted(task: TaskEntity, completed: Boolean) {
        taskDao.setCompleted(task.id, completed, System.currentTimeMillis())
        if (completed) {
            geofenceCoordinator.deactivate(task.id)
            geofenceCoordinator.reconcileAll()
            reminderScheduler.cancelAll(task.id)
        } else if (task.copy(isCompleted = false).shouldMonitor) {
            geofenceCoordinator.reconcileTask(task.id)
            reminderScheduler.cancelDueReminder(task.id)
        } else {
            taskDao.getById(task.id)?.let(reminderScheduler::syncDueReminder)
        }
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        val task = taskDao.getById(id) ?: return
        setCompleted(task, completed)
    }

    suspend fun delete(task: TaskEntity) {
        taskDao.delete(task)
        geofenceCoordinator.deactivate(task.id)
        geofenceCoordinator.reconcileAll()
        reminderScheduler.cancelAll(task.id)
    }

    private fun TaskEntity.geofenceConfigurationDiffersFrom(other: TaskEntity): Boolean =
        latitude != other.latitude ||
            longitude != other.longitude ||
            geofenceRadiusMeters != other.geofenceRadiusMeters ||
            geofenceEnabled != other.geofenceEnabled ||
            geofenceTransitionMode != other.geofenceTransitionMode ||
            isCompleted != other.isCompleted

    private fun TaskEntity.locationReminderConfigurationDiffersFrom(
        other: TaskEntity,
    ): Boolean =
        geofenceConfigurationDiffersFrom(other) ||
            notificationCooldownMinutes != other.notificationCooldownMinutes ||
            allowedDaysMask != other.allowedDaysMask ||
            reminderWindowStartMinutes != other.reminderWindowStartMinutes ||
            reminderWindowEndMinutes != other.reminderWindowEndMinutes
}
