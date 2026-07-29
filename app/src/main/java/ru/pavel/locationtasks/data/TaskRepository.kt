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

    suspend fun setCompleted(
        task: TaskEntity,
        completed: Boolean,
    ): TaskCompletionOutcome {
        if (completed && task.resolvedRecurrence != TaskRecurrence.NONE) {
            val now = System.currentTimeMillis()
            val nextDueAt = nextOccurrenceAt(
                recurrence = task.resolvedRecurrence,
                currentDueAt = task.dueAt,
                nowMillis = now,
            )
            val resetChecklist = task.checklistItems.map { it.copy(isCompleted = false) }
            save(
                task.copy(
                    dueAt = nextDueAt,
                    isCompleted = false,
                    isArchived = false,
                    archivedAt = null,
                    checklistData = ChecklistCodec.encode(resetChecklist),
                    snoozedUntil = null,
                    skipUntilNextVisit = false,
                    lastNotifiedAt = null,
                    lastNotifiedTransition = null,
                ),
            )
            return TaskCompletionOutcome.RESCHEDULED
        }
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
        return TaskCompletionOutcome.UPDATED
    }

    suspend fun setCompleted(id: Long, completed: Boolean): TaskCompletionOutcome? {
        val task = taskDao.getById(id) ?: return null
        return setCompleted(task, completed)
    }

    suspend fun setArchived(task: TaskEntity, archived: Boolean) {
        save(
            task.copy(
                isArchived = archived,
                archivedAt = if (archived) System.currentTimeMillis() else null,
            ),
        )
    }

    suspend fun duplicate(task: TaskEntity): Long {
        val now = System.currentTimeMillis()
        val resetChecklist = task.checklistItems.map { it.copy(isCompleted = false) }
        val duplicatedDueAt = when {
            task.dueAt == null -> null
            task.dueAt > now -> task.dueAt
            task.resolvedRecurrence != TaskRecurrence.NONE -> nextOccurrenceAt(
                recurrence = task.resolvedRecurrence,
                currentDueAt = task.dueAt,
                nowMillis = now,
            )
            else -> null
        }
        return save(
            task.copy(
                id = 0,
                dueAt = duplicatedDueAt,
                isCompleted = false,
                isArchived = false,
                archivedAt = null,
                checklistData = ChecklistCodec.encode(resetChecklist),
                snoozedUntil = null,
                skipUntilNextVisit = false,
                lastNotifiedAt = null,
                lastNotifiedTransition = null,
                geofenceStatus = if (task.geofenceEnabled) {
                    GeofenceStatus.PENDING.name
                } else {
                    GeofenceStatus.DISABLED.name
                },
                geofenceStatusDetails = null,
                geofenceRegisteredAt = null,
                createdAt = 0,
                updatedAt = 0,
            ),
        )
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
            isCompleted != other.isCompleted ||
            isArchived != other.isArchived

    private fun TaskEntity.locationReminderConfigurationDiffersFrom(
        other: TaskEntity,
    ): Boolean {
        val recurrenceScheduleDiffers =
            recurrence != other.recurrence ||
                (
                    (
                        resolvedRecurrence != TaskRecurrence.NONE ||
                            other.resolvedRecurrence != TaskRecurrence.NONE
                        ) &&
                        dueAt != other.dueAt
                    )
        return geofenceConfigurationDiffersFrom(other) ||
            notificationCooldownMinutes != other.notificationCooldownMinutes ||
            allowedDaysMask != other.allowedDaysMask ||
            reminderWindowStartMinutes != other.reminderWindowStartMinutes ||
            reminderWindowEndMinutes != other.reminderWindowEndMinutes ||
            recurrenceScheduleDiffers
    }
}

enum class TaskCompletionOutcome {
    UPDATED,
    RESCHEDULED,
}
