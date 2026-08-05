package ru.pavel.locationtasks.notifications

import kotlinx.coroutines.flow.first
import ru.pavel.locationtasks.analytics.ProductTelemetry
import ru.pavel.locationtasks.data.GeofenceLogDao
import ru.pavel.locationtasks.data.GeofenceLogEntity
import ru.pavel.locationtasks.data.GeofenceTransition
import ru.pavel.locationtasks.data.TaskDao
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.data.TaskRecurrence
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
    private val productTelemetry: ProductTelemetry,
) {
    suspend fun dispatch(
        taskId: Long,
        transition: GeofenceTransition,
        now: Long = System.currentTimeMillis(),
    ) {
        var task = taskDao.getById(taskId) ?: run {
            productTelemetry.trackGeofenceTrigger(OUTCOME_TASK_MISSING)
            return
        }
        if (!task.shouldMonitor) {
            recordOutcome(task, GeofenceLogEntity.OUTCOME_TASK_INACTIVE, transition, now)
            return
        }

        if (transition == GeofenceTransition.EXIT) {
            reminderScheduler.cancelLocationReminder(taskId, GeofenceTransition.ENTER)
            if (task.skipUntilNextVisit || task.snoozedUntil != null) {
                taskDao.setSnoozeState(taskId, snoozedUntil = null, skipUntilNextVisit = false)
                task = task.copy(snoozedUntil = null, skipUntilNextVisit = false)
            }
        } else if (task.skipUntilNextVisit) {
            recordOutcome(task, GeofenceLogEntity.OUTCOME_NEXT_VISIT, transition, now)
            return
        }

        if (!task.resolvedTransitionMode.includes(transition)) {
            recordOutcome(task, OUTCOME_TRANSITION_FILTERED, transition, now)
            return
        }

        val recurrenceDueAt = task.dueAt
        if (task.resolvedRecurrence != TaskRecurrence.NONE &&
            recurrenceDueAt != null &&
            recurrenceDueAt > now
        ) {
            if (transition == GeofenceTransition.ENTER) {
                reminderScheduler.scheduleLocationReminder(taskId, transition, recurrenceDueAt)
            }
            recordOutcome(task, GeofenceLogEntity.OUTCOME_DEFERRED, transition, now)
            return
        }

        val snoozedUntil = task.snoozedUntil
        if (snoozedUntil != null && snoozedUntil > now) {
            reminderScheduler.scheduleLocationReminder(taskId, transition, snoozedUntil)
            recordOutcome(task, GeofenceLogEntity.OUTCOME_DEFERRED, transition, now)
            return
        }

        val preferences = preferencesRepository.reminderPreferences.first()
        if (!ReminderSchedule.isAllowedNow(task, preferences, now)) {
            ReminderSchedule.nextAllowedAt(task, preferences, now)?.let { nextAllowedAt ->
                reminderScheduler.scheduleLocationReminder(taskId, transition, nextAllowedAt)
            }
            recordOutcome(task, GeofenceLogEntity.OUTCOME_DEFERRED, transition, now)
            return
        }

        val lastNotifiedAt = task.lastNotifiedAt
        val cooldownActive = lastNotifiedAt != null &&
            task.lastNotifiedTransition == transition.name &&
            now - lastNotifiedAt < ReminderSchedule.cooldownMillis(task, preferences)
        if (cooldownActive) {
            recordOutcome(task, GeofenceLogEntity.OUTCOME_COOLDOWN, transition, now)
            return
        }

        if (notificationManager.showLocationTask(task, transition)) {
            taskDao.setLastNotifiedAt(taskId, now, transition.name)
            if (task.snoozedUntil != null) {
                taskDao.setSnoozeState(taskId, snoozedUntil = null, skipUntilNextVisit = false)
            }
            recordOutcome(task, GeofenceLogEntity.OUTCOME_NOTIFIED, transition, now)
        } else {
            recordOutcome(
                task,
                GeofenceLogEntity.OUTCOME_NOTIFICATIONS_BLOCKED,
                transition,
                now,
            )
        }
    }

    private suspend fun recordOutcome(
        task: TaskEntity,
        outcome: String,
        transition: GeofenceTransition,
        occurredAt: Long,
    ) {
        logDao.record(task.triggerLog(outcome, transition, occurredAt))
        productTelemetry.trackGeofenceTrigger(outcome.lowercase())
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

    companion object {
        private const val OUTCOME_TASK_MISSING = "task_missing"
        private const val OUTCOME_TRANSITION_FILTERED = "TRANSITION_FILTERED"
    }
}
