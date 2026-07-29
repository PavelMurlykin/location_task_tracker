package ru.pavel.locationtasks.notifications

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import ru.pavel.locationtasks.data.GeofenceTransition
import ru.pavel.locationtasks.data.TaskDao
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.data.UserPreferencesRepository
import ru.pavel.locationtasks.location.LocationPermissionState

class DueReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(WorkManagerReminderScheduler.KEY_TASK_ID, -1L)
        if (taskId <= 0) return Result.success()
        val dependencies = dependencies()
        val task = dependencies.taskDao().getById(taskId) ?: return Result.success()
        if (task.isCompleted || task.isArchived || task.dueAt == null || task.geofenceEnabled) {
            return Result.success()
        }
        if (task.lastNotifiedTransition == ReminderKind.DUE.name &&
            task.lastNotifiedAt != null &&
            task.lastNotifiedAt >= task.dueAt &&
            task.snoozedUntil == null
        ) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val snoozedUntil = task.snoozedUntil
        if (snoozedUntil != null && snoozedUntil > now) {
            dependencies.scheduler().scheduleDueReminder(taskId, snoozedUntil)
            return Result.success()
        }
        val preferences = dependencies.preferences().reminderPreferences.first()
        if (!ReminderSchedule.isAllowedNow(
                task = task,
                preferences = preferences,
                nowMillis = now,
                respectTaskWindow = false,
            )
        ) {
            ReminderSchedule.nextAllowedAt(
                task = task,
                preferences = preferences,
                fromMillis = now,
                respectTaskWindow = false,
            )?.let { dependencies.scheduler().scheduleDueReminder(taskId, it) }
            return Result.success()
        }

        if (dependencies.notificationManager().showDueTask(task)) {
            dependencies.taskDao().setLastNotifiedAt(taskId, now, ReminderKind.DUE.name)
            if (task.snoozedUntil != null) {
                dependencies.taskDao().setSnoozeState(
                    id = taskId,
                    snoozedUntil = null,
                    skipUntilNextVisit = false,
                )
            }
        }
        return Result.success()
    }

    private fun dependencies(): ReminderWorkerEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, ReminderWorkerEntryPoint::class.java)
}

class DeferredLocationReminderWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(WorkManagerReminderScheduler.KEY_TASK_ID, -1L)
        val transition = inputData.getString(WorkManagerReminderScheduler.KEY_TRANSITION)
            ?.let { runCatching { GeofenceTransition.valueOf(it) }.getOrNull() }
            ?: return Result.success()
        if (taskId <= 0) return Result.success()

        val dependencies = dependencies()
        val task = dependencies.taskDao().getById(taskId) ?: return Result.success()
        if (!task.shouldMonitor || !task.resolvedTransitionMode.includes(transition)) {
            return Result.success()
        }
        if (!LocationPermissionState.from(applicationContext).preciseLocation) {
            return Result.success()
        }
        val location = runCatching {
            dependencies.locationClient()
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .await()
        }.getOrNull() ?: return Result.success()
        val isInside = task.contains(location)
        if (transition == GeofenceTransition.ENTER && !isInside) return Result.success()
        if (transition == GeofenceTransition.EXIT && isInside) return Result.success()

        dependencies.locationDispatcher().dispatch(taskId, transition)
        return Result.success()
    }

    private fun dependencies(): ReminderWorkerEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, ReminderWorkerEntryPoint::class.java)

    private fun TaskEntity.contains(location: Location): Boolean {
        val targetLatitude = latitude ?: return false
        val targetLongitude = longitude ?: return false
        val result = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            targetLatitude,
            targetLongitude,
            result,
        )
        return result[0] <= geofenceRadiusMeters
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderWorkerEntryPoint {
    fun taskDao(): TaskDao
    fun preferences(): UserPreferencesRepository
    fun notificationManager(): TaskNotificationManager
    fun scheduler(): ReminderWorkScheduler
    fun locationDispatcher(): LocationReminderDispatcher
    fun locationClient(): FusedLocationProviderClient
}
