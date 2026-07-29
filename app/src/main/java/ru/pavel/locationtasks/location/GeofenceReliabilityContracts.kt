package ru.pavel.locationtasks.location

interface GeofencePlatform {
    suspend fun register(task: ru.pavel.locationtasks.data.TaskEntity): GeofenceRegistrationResult
    suspend fun remove(taskId: Long)
}

interface GeofencePermissionSource {
    fun current(): LocationPermissionState
}

interface GeofenceRetryScheduler {
    fun scheduleRetry()
}

data class GeofenceReconcileResult(
    val activeCount: Int,
    val retryableFailureCount: Int,
    val limitReachedCount: Int,
)

interface GeofenceCoordinator {
    suspend fun reconcileTask(taskId: Long): GeofenceReconcileResult
    suspend fun reconcileAll(force: Boolean = false): GeofenceReconcileResult
    suspend fun deactivate(taskId: Long)
}
