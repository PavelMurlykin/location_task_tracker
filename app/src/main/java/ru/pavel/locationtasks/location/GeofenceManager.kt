package ru.pavel.locationtasks.location

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import ru.pavel.locationtasks.data.TaskEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofencingClient: GeofencingClient,
) {
    private val pendingIntent: PendingIntent by lazy {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        PendingIntent.getBroadcast(
            context,
            GEOFENCE_PENDING_INTENT_REQUEST_CODE,
            Intent(context, GeofenceBroadcastReceiver::class.java),
            flags,
        )
    }

    suspend fun register(task: TaskEntity): GeofenceRegistrationResult {
        if (!task.shouldMonitor) return GeofenceRegistrationResult.InvalidTask
        if (!LocationPermissionState.from(context).canRegisterGeofences) {
            return GeofenceRegistrationResult.MissingPermission
        }

        val latitude = task.latitude ?: return GeofenceRegistrationResult.InvalidTask
        val longitude = task.longitude ?: return GeofenceRegistrationResult.InvalidTask
        val geofence = Geofence.Builder()
            .setRequestId(requestId(task.id))
            .setCircularRegion(latitude, longitude, task.geofenceRadiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        return runCatching {
            geofencingClient.addGeofences(request, pendingIntent).await()
        }.fold(
            onSuccess = { GeofenceRegistrationResult.Registered },
            onFailure = { GeofenceRegistrationResult.Failed(it) },
        )
    }

    suspend fun remove(taskId: Long) {
        if (taskId <= 0) return
        runCatching {
            geofencingClient.removeGeofences(listOf(requestId(taskId))).await()
        }
    }

    suspend fun restore(tasks: List<TaskEntity>) {
        val tasksToRestore = tasks.asSequence()
            .filter(TaskEntity::shouldMonitor)
            .take(MAX_GEOFENCES)
            .toList()
        for (task in tasksToRestore) {
            register(task)
        }
    }

    companion object {
        const val REQUEST_ID_PREFIX = "task:"
        const val MAX_GEOFENCES = 100
        private const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 7001
        private const val NOTIFICATION_RESPONSIVENESS_MS = 60_000

        fun requestId(taskId: Long): String = "$REQUEST_ID_PREFIX$taskId"

        fun taskId(requestId: String): Long? =
            requestId.removePrefix(REQUEST_ID_PREFIX)
                .takeIf { requestId.startsWith(REQUEST_ID_PREFIX) }
                ?.toLongOrNull()
    }
}

sealed interface GeofenceRegistrationResult {
    data object Registered : GeofenceRegistrationResult
    data object MissingPermission : GeofenceRegistrationResult
    data object InvalidTask : GeofenceRegistrationResult
    data class Failed(val cause: Throwable) : GeofenceRegistrationResult
}
