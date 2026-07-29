package ru.pavel.locationtasks.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.pavel.locationtasks.data.GeofenceLogDao
import ru.pavel.locationtasks.data.GeofenceLogEntity
import ru.pavel.locationtasks.data.GeofenceTransition
import ru.pavel.locationtasks.data.TaskDao
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.location.GeofenceCoordinator
import ru.pavel.locationtasks.location.GeofencePermissionSource
import ru.pavel.locationtasks.location.GeofencePlatform
import ru.pavel.locationtasks.location.GeofenceReconcileResult
import ru.pavel.locationtasks.location.GeofenceRegistrationResult
import ru.pavel.locationtasks.location.GeofenceRetryScheduler
import ru.pavel.locationtasks.location.LocationPermissionState
import ru.pavel.locationtasks.notifications.ReminderWorkScheduler

class FakeTaskDao(
    initialTasks: List<TaskEntity> = emptyList(),
) : TaskDao {
    private val tasks = MutableStateFlow(initialTasks)
    private var nextId = (initialTasks.maxOfOrNull(TaskEntity::id) ?: 0L) + 1L

    override fun observeAll(): Flow<List<TaskEntity>> = tasks

    override fun observeById(id: Long): Flow<TaskEntity?> =
        tasks.map { values -> values.firstOrNull { it.id == id } }

    override suspend fun getById(id: Long): TaskEntity? =
        tasks.value.firstOrNull { it.id == id }

    override suspend fun getTasksToMonitor(): List<TaskEntity> =
        tasks.value
            .filter(TaskEntity::shouldMonitor)
            .sortedBy(TaskEntity::id)

    override suspend fun getTasksWithDueReminders(): List<TaskEntity> =
        tasks.value.filter { !it.isCompleted && it.dueAt != null }

    override suspend fun insert(task: TaskEntity): Long {
        val id = if (task.id == 0L) nextId++ else task.id
        check(tasks.value.none { it.id == id })
        tasks.value += task.copy(id = id)
        return id
    }

    override suspend fun update(task: TaskEntity) {
        updateTask(task.id) { task }
    }

    override suspend fun delete(task: TaskEntity) {
        tasks.value = tasks.value.filterNot { it.id == task.id }
    }

    override suspend fun setCompleted(id: Long, completed: Boolean, updatedAt: Long) {
        updateTask(id) { it.copy(isCompleted = completed, updatedAt = updatedAt) }
    }

    override suspend fun setLastNotifiedAt(id: Long, notifiedAt: Long, transition: String?) {
        updateTask(id) {
            it.copy(lastNotifiedAt = notifiedAt, lastNotifiedTransition = transition)
        }
    }

    override suspend fun clearLastNotified(id: Long) {
        updateTask(id) { it.copy(lastNotifiedAt = null, lastNotifiedTransition = null) }
    }

    override suspend fun setSnoozeState(
        id: Long,
        snoozedUntil: Long?,
        skipUntilNextVisit: Boolean,
    ) {
        updateTask(id) {
            it.copy(
                snoozedUntil = snoozedUntil,
                skipUntilNextVisit = skipUntilNextVisit,
            )
        }
    }

    override suspend fun setGeofenceStatus(
        id: Long,
        status: String,
        details: String?,
        registeredAt: Long?,
    ) {
        updateTask(id) {
            it.copy(
                geofenceStatus = status,
                geofenceStatusDetails = details,
                geofenceRegisteredAt = registeredAt,
            )
        }
    }

    fun snapshot(): List<TaskEntity> = tasks.value

    private fun updateTask(id: Long, transform: (TaskEntity) -> TaskEntity) {
        tasks.value = tasks.value.map { task ->
            if (task.id == id) transform(task) else task
        }
    }
}

class FakeGeofenceLogDao : GeofenceLogDao {
    private val entries = MutableStateFlow<List<GeofenceLogEntity>>(emptyList())
    private var nextId = 1L

    override fun observeRecent(limit: Int): Flow<List<GeofenceLogEntity>> =
        entries.map { values ->
            values.sortedWith(
                compareByDescending<GeofenceLogEntity> { it.occurredAt }
                    .thenByDescending { it.id },
            ).take(limit)
        }

    override suspend fun insert(entry: GeofenceLogEntity): Long {
        val id = if (entry.id == 0L) nextId++ else entry.id
        entries.value += entry.copy(id = id)
        return id
    }

    override suspend fun trimToSize(entriesToKeep: Int) {
        entries.value = entries.value
            .sortedWith(
                compareByDescending<GeofenceLogEntity> { it.occurredAt }
                    .thenByDescending { it.id },
            )
            .take(entriesToKeep)
    }

    fun snapshot(): List<GeofenceLogEntity> = entries.value
}

class FakeGeofenceCoordinator : GeofenceCoordinator {
    val reconciledTaskIds = mutableListOf<Long>()
    val deactivatedTaskIds = mutableListOf<Long>()
    var reconcileAllCalls = 0

    override suspend fun reconcileTask(taskId: Long): GeofenceReconcileResult {
        reconciledTaskIds += taskId
        return EMPTY_RESULT
    }

    override suspend fun reconcileAll(force: Boolean): GeofenceReconcileResult {
        reconcileAllCalls += 1
        return EMPTY_RESULT
    }

    override suspend fun deactivate(taskId: Long) {
        deactivatedTaskIds += taskId
    }

    companion object {
        private val EMPTY_RESULT = GeofenceReconcileResult(
            activeCount = 0,
            retryableFailureCount = 0,
            limitReachedCount = 0,
        )
    }
}

class FakeGeofencePlatform(
    var registrationResult: GeofenceRegistrationResult =
        GeofenceRegistrationResult.Registered,
) : GeofencePlatform {
    val registeredTaskIds = mutableListOf<Long>()
    val removedTaskIds = mutableListOf<Long>()

    override suspend fun register(task: TaskEntity): GeofenceRegistrationResult {
        registeredTaskIds += task.id
        return registrationResult
    }

    override suspend fun remove(taskId: Long) {
        removedTaskIds += taskId
    }
}

class FakeGeofencePermissionSource(
    var state: LocationPermissionState = LocationPermissionState(
        preciseLocation = true,
        backgroundLocation = true,
        notifications = true,
    ),
) : GeofencePermissionSource {
    override fun current(): LocationPermissionState = state
}

class FakeGeofenceRetryScheduler : GeofenceRetryScheduler {
    var scheduledRetries = 0

    override fun scheduleRetry() {
        scheduledRetries += 1
    }
}

class FakeReminderWorkScheduler : ReminderWorkScheduler {
    val syncedTasks = mutableListOf<TaskEntity>()
    val dueSchedules = mutableListOf<Pair<Long, Long>>()
    val dueCancellations = mutableListOf<Long>()
    val locationSchedules = mutableListOf<Triple<Long, GeofenceTransition, Long>>()
    val locationCancellations = mutableListOf<Pair<Long, GeofenceTransition?>>()
    val allCancellations = mutableListOf<Long>()

    override fun syncDueReminder(task: TaskEntity) {
        syncedTasks += task
    }

    override fun scheduleDueReminder(taskId: Long, triggerAt: Long) {
        dueSchedules += taskId to triggerAt
    }

    override fun cancelDueReminder(taskId: Long) {
        dueCancellations += taskId
    }

    override fun scheduleLocationReminder(
        taskId: Long,
        transition: GeofenceTransition,
        triggerAt: Long,
    ) {
        locationSchedules += Triple(taskId, transition, triggerAt)
    }

    override fun cancelLocationReminder(
        taskId: Long,
        transition: GeofenceTransition?,
    ) {
        locationCancellations += taskId to transition
    }

    override fun cancelAll(taskId: Long) {
        allCancellations += taskId
    }
}
