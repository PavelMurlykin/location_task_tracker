package ru.pavel.locationtasks.notifications

import kotlinx.coroutines.flow.first
import ru.pavel.locationtasks.data.GeofenceLogDao
import ru.pavel.locationtasks.data.GeofenceLogEntity
import ru.pavel.locationtasks.data.GeofenceTransition
import ru.pavel.locationtasks.data.TaskDao
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.data.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationReminderDispatcher @Inject constructor(
    private val taskDao: TaskDao,
    private val logDao: GeofenceLogDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val notificationManager: TaskNotificationManager,
    private val reminderScheduler: ReminderWorkScheduler,
) {
    suspend fun dispatch(
        taskId: Long,
        transition: GeofenceTransition,
        now: Long = System.currentTimeMillis(),
    ) {
        var task = taskDao.getById(taskId) ?: return
        if (!task.shouldMonitor) {
            logDao.record(task.triggerLog(GeofenceLogEntity.OUTCOME_TASK_INACTIVE, transition, now))
            return
        }

        if (transition == GeofenceTransition.EXIT) {
            reminderScheduler.cancelLocationReminder(taskId, GeofenceTransition.ENTER)
            if (task.skipUntilNextVisit || task.snoozedUntil != null) {
                taskDao.setSnoozeState(taskId, snoozedUntil = null, skipUntilNextVisit = false)
                task = task.copy(snoozedUntil = null, skipUntilNextVisit = false)
            }
        } else if (task.skipUntilNextVisit) {
            logDao.record(task.triggerLog(GeofenceLogEntity.OUTCOME_NEXT_VISIT, transition, now))
            return
        }

        if (!task.resolvedTransitionMode.includes(transition)) return

        val snoozedUntil = task.snoozedUntil
        if (snoozedUntil != null && snoozedUntil > now) {
            reminderScheduler.scheduleLocationReminder(taskId, transition, snoozedUntil)
            logDao.record(task.triggerLog(GeofenceLogEntity.OUTCOME_DEFERRED, transition, now))
            return
        }

        val preferences = preferencesRepository.reminderPreferences.first()
        if (!ReminderSchedule.isAllowedNow(task, preferences, now)) {
            ReminderSchedule.nextAllowedAt(task, preferences, now)?.let { nextAllowedAt ->
                reminderScheduler.scheduleLocationReminder(taskId, transition, nextAllowedAt)
            }
            logDao.record(task.triggerLog(GeofenceLogEntity.OUTCOME_DEFERRED, transition, now))
            return
        }

        val lastNotifiedAt = task.lastNotifiedAt
        val cooldownActive = lastNotifiedAt != null &&
            task.lastNotifiedTransition == transition.name &&
            now - lastNotifiedAt < ReminderSchedule.cooldownMillis(task, preferences)
        if (cooldownActive) {
            logDao.record(task.triggerLog(GeofenceLogEntity.OUTCOME_COOLDOWN, transition, now))
            return
        }

        if (notificationManager.showLocationTask(task, transition)) {
            taskDao.setLastNotifiedAt(taskId, now, transition.name)
            if (task.snoozedUntil != null) {
                taskDao.setSnoozeState(taskId, snoozedUntil = null, skipUntilNextVisit = false)
            }
            logDao.record(task.triggerLog(GeofenceLogEntity.OUTCOME_NOTIFIED, transition, now))
        } else {
            logDao.record(
                task.triggerLog(GeofenceLogEntity.OUTCOME_NOTIFICATIONS_BLOCKED, transition, now),
            )
        }
    }

    private fun TaskEntity.triggerLog(
        outcome: String,
        transition: GeofenceTransition,
        occurredAt: Long,
    ) = GeofenceLogEntity(
        taskId = id,
        taskTitle = title,
        event = GeofenceLogEntity.EVENT_TRIGGER,
        outcome = outcome,
        details = transition.name,
        occurredAt = occurredAt,
    )
}
