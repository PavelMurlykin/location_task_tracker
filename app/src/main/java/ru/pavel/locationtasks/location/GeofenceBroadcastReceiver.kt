package ru.pavel.locationtasks.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.data.TaskDao
import ru.pavel.locationtasks.data.GeofenceLogDao
import ru.pavel.locationtasks.data.GeofenceLogEntity
import ru.pavel.locationtasks.data.UserPreferencesRepository
import ru.pavel.locationtasks.notifications.TaskNotificationManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var logDao: GeofenceLogDao
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var notificationManager: TaskNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val cooldownHours = preferencesRepository.notificationCooldownHours.first()
                val cooldownMillis = TimeUnit.HOURS.toMillis(cooldownHours.toLong())
                event.triggeringGeofences.orEmpty()
                    .mapNotNull { GeofenceManager.taskId(it.requestId) }
                    .distinct()
                    .forEach { taskId ->
                        val task = taskDao.getById(taskId) ?: return@forEach
                        if (!task.shouldMonitor) {
                            logDao.record(
                                task.triggerLog(GeofenceLogEntity.OUTCOME_TASK_INACTIVE),
                            )
                            return@forEach
                        }
                        val canNotify = task.lastNotifiedAt == null ||
                            now - task.lastNotifiedAt >= cooldownMillis
                        when {
                            !canNotify -> logDao.record(
                                task.triggerLog(
                                    outcome = GeofenceLogEntity.OUTCOME_COOLDOWN,
                                    details = "Повторное уведомление временно подавлено",
                                    occurredAt = now,
                                ),
                            )

                            notificationManager.showNearbyTask(task) -> {
                                taskDao.setLastNotifiedAt(taskId, now)
                                logDao.record(
                                    task.triggerLog(
                                        outcome = GeofenceLogEntity.OUTCOME_NOTIFIED,
                                        occurredAt = now,
                                    ),
                                )
                            }

                            else -> logDao.record(
                                task.triggerLog(
                                    outcome = GeofenceLogEntity.OUTCOME_NOTIFICATIONS_BLOCKED,
                                    details = "Нет разрешения на показ уведомлений",
                                    occurredAt = now,
                                ),
                            )
                        }
                    }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun ru.pavel.locationtasks.data.TaskEntity.triggerLog(
        outcome: String,
        details: String? = null,
        occurredAt: Long = System.currentTimeMillis(),
    ) = GeofenceLogEntity(
        taskId = id,
        taskTitle = title,
        event = GeofenceLogEntity.EVENT_TRIGGER,
        outcome = outcome,
        details = details,
        occurredAt = occurredAt,
    )
}
