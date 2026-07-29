package ru.pavel.locationtasks.location

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.pavel.locationtasks.data.GeofenceLogDao
import ru.pavel.locationtasks.data.GeofenceLogEntity
import ru.pavel.locationtasks.data.GeofenceStatus
import ru.pavel.locationtasks.data.TaskDao
import ru.pavel.locationtasks.data.TaskEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReliableGeofenceCoordinator @Inject constructor(
    private val taskDao: TaskDao,
    private val geofencePlatform: GeofencePlatform,
    private val permissionSource: GeofencePermissionSource,
    private val retryScheduler: GeofenceRetryScheduler,
    private val logDao: GeofenceLogDao,
) : GeofenceCoordinator {
    private val reconciliationMutex = Mutex()

    override suspend fun reconcileTask(taskId: Long): GeofenceReconcileResult =
        reconciliationMutex.withLock {
            reconcileLocked(forceTaskIds = setOf(taskId), forceAll = false)
        }

    override suspend fun reconcileAll(force: Boolean): GeofenceReconcileResult =
        reconciliationMutex.withLock {
            reconcileLocked(forceTaskIds = emptySet(), forceAll = force)
        }

    override suspend fun deactivate(taskId: Long) {
        if (taskId <= 0) return
        reconciliationMutex.withLock {
            geofencePlatform.remove(taskId)
            taskDao.setGeofenceStatus(
                id = taskId,
                status = GeofenceStatus.DISABLED.name,
                details = null,
                registeredAt = null,
            )
        }
    }

    private suspend fun reconcileLocked(
        forceTaskIds: Set<Long>,
        forceAll: Boolean,
    ): GeofenceReconcileResult {
        val tasks = taskDao.getTasksToMonitor()
        val permissions = permissionSource.current()
        if (!permissions.canRegisterGeofences) {
            tasks.forEach { task ->
                if (task.resolvedGeofenceStatus == GeofenceStatus.ACTIVE ||
                    task.geofenceRegisteredAt != null
                ) {
                    geofencePlatform.remove(task.id)
                }
                updateStatus(
                    task = task,
                    status = GeofenceStatus.MISSING_PERMISSION,
                    details = missingPermissionDetails(permissions),
                    registeredAt = null,
                    logOutcome = GeofenceLogEntity.OUTCOME_MISSING_PERMISSION,
                )
            }
            return GeofenceReconcileResult(
                activeCount = 0,
                retryableFailureCount = 0,
                limitReachedCount = 0,
            )
        }

        val selectedTasks = tasks.take(GeofenceManager.MAX_GEOFENCES)
        val overflowTasks = tasks.drop(GeofenceManager.MAX_GEOFENCES)
        overflowTasks.forEach { task ->
            if (task.resolvedGeofenceStatus == GeofenceStatus.ACTIVE ||
                task.geofenceRegisteredAt != null
            ) {
                geofencePlatform.remove(task.id)
            }
            updateStatus(
                task = task,
                status = GeofenceStatus.LIMIT_REACHED,
                details = null,
                registeredAt = null,
                logOutcome = GeofenceLogEntity.OUTCOME_LIMIT_REACHED,
            )
        }

        var activeCount = 0
        var retryableFailures = 0
        for (task in selectedTasks) {
            val currentStatus = task.resolvedGeofenceStatus
            val needsRegistration = forceAll ||
                task.id in forceTaskIds ||
                currentStatus != GeofenceStatus.ACTIVE
            if (!needsRegistration) {
                activeCount += 1
                continue
            }

            taskDao.setGeofenceStatus(
                id = task.id,
                status = GeofenceStatus.PENDING.name,
                details = null,
                registeredAt = null,
            )
            when (val result = geofencePlatform.register(task)) {
                GeofenceRegistrationResult.Registered -> {
                    val registeredAt = System.currentTimeMillis()
                    taskDao.setGeofenceStatus(
                        id = task.id,
                        status = GeofenceStatus.ACTIVE.name,
                        details = null,
                        registeredAt = registeredAt,
                    )
                    activeCount += 1
                    logDao.record(
                        GeofenceLogEntity(
                            taskId = task.id,
                            taskTitle = task.title,
                            event = if (forceAll) {
                                GeofenceLogEntity.EVENT_RESTORE
                            } else {
                                GeofenceLogEntity.EVENT_REGISTRATION
                            },
                            outcome = GeofenceLogEntity.OUTCOME_ACTIVE,
                        ),
                    )
                }

                GeofenceRegistrationResult.MissingPermission -> {
                    val latestPermissions = permissionSource.current()
                    updateStatus(
                        task = task,
                        status = GeofenceStatus.MISSING_PERMISSION,
                        details = missingPermissionDetails(latestPermissions),
                        registeredAt = null,
                        logOutcome = GeofenceLogEntity.OUTCOME_MISSING_PERMISSION,
                        forceLog = true,
                    )
                }

                GeofenceRegistrationResult.InvalidTask -> {
                    updateStatus(
                        task = task,
                        status = GeofenceStatus.ERROR,
                        details = DETAIL_INVALID_TASK,
                        registeredAt = null,
                        logOutcome = GeofenceLogEntity.OUTCOME_ERROR,
                        forceLog = true,
                    )
                }

                is GeofenceRegistrationResult.Failed -> {
                    retryableFailures += 1
                    updateStatus(
                        task = task,
                        status = GeofenceStatus.ERROR,
                        details = "$DETAIL_RETRY_SCHEDULED|${result.cause.toDiagnosticMessage()}",
                        registeredAt = null,
                        logOutcome = GeofenceLogEntity.OUTCOME_ERROR,
                        forceLog = true,
                    )
                }
            }
        }

        if (retryableFailures > 0) {
            retryScheduler.scheduleRetry()
        }
        return GeofenceReconcileResult(
            activeCount = activeCount,
            retryableFailureCount = retryableFailures,
            limitReachedCount = overflowTasks.size,
        )
    }

    private suspend fun updateStatus(
        task: TaskEntity,
        status: GeofenceStatus,
        details: String?,
        registeredAt: Long?,
        logOutcome: String,
        forceLog: Boolean = false,
    ) {
        taskDao.setGeofenceStatus(
            id = task.id,
            status = status.name,
            details = details,
            registeredAt = registeredAt,
        )
        if (forceLog ||
            task.resolvedGeofenceStatus != status ||
            task.geofenceStatusDetails != details
        ) {
            logDao.record(
                GeofenceLogEntity(
                    taskId = task.id,
                    taskTitle = task.title,
                    event = GeofenceLogEntity.EVENT_REGISTRATION,
                    outcome = logOutcome,
                    details = details,
                ),
            )
        }
    }

    private fun missingPermissionDetails(permissions: LocationPermissionState): String =
        when {
            !permissions.preciseLocation -> DETAIL_MISSING_PRECISE_LOCATION
            !permissions.backgroundLocation -> DETAIL_MISSING_BACKGROUND_LOCATION
            else -> DETAIL_MISSING_PERMISSION
        }

    private fun Throwable.toDiagnosticMessage(): String {
        val type = this::class.simpleName ?: "Error"
        val message = localizedMessage
            ?.replace(Regex("\\s+"), " ")
            ?.take(180)
            ?.takeIf(String::isNotBlank)
        return if (message == null) type else "$type: $message"
    }

    companion object {
        const val DETAIL_INVALID_TASK = "INVALID_TASK"
        const val DETAIL_RETRY_SCHEDULED = "RETRY_SCHEDULED"
        private const val DETAIL_MISSING_PRECISE_LOCATION = "MISSING_PRECISE_LOCATION"
        private const val DETAIL_MISSING_BACKGROUND_LOCATION = "MISSING_BACKGROUND_LOCATION"
        private const val DETAIL_MISSING_PERMISSION = "MISSING_PERMISSION"
    }
}
