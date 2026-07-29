package ru.pavel.locationtasks.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.pavel.locationtasks.data.GeofenceTransition
import ru.pavel.locationtasks.data.TaskEntity
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

interface ReminderWorkScheduler {
    fun syncDueReminder(task: TaskEntity)
    fun scheduleDueReminder(taskId: Long, triggerAt: Long)
    fun cancelDueReminder(taskId: Long)
    fun scheduleLocationReminder(
        taskId: Long,
        transition: GeofenceTransition,
        triggerAt: Long,
    )
    fun cancelLocationReminder(taskId: Long, transition: GeofenceTransition? = null)
    fun cancelAll(taskId: Long)
}

@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ReminderWorkScheduler {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun syncDueReminder(task: TaskEntity) {
        val alreadyDelivered = task.lastNotifiedTransition == ReminderKind.DUE.name &&
            task.lastNotifiedAt != null &&
            task.lastNotifiedAt >= (task.dueAt ?: Long.MAX_VALUE) &&
            task.snoozedUntil == null
        if (task.isCompleted || task.dueAt == null || task.geofenceEnabled || alreadyDelivered) {
            cancelDueReminder(task.id)
            return
        }
        val triggerAt = max(task.dueAt, task.snoozedUntil ?: Long.MIN_VALUE)
        scheduleDueReminder(task.id, triggerAt)
    }

    override fun scheduleDueReminder(taskId: Long, triggerAt: Long) {
        if (taskId <= 0) return
        val request = OneTimeWorkRequestBuilder<DueReminderWorker>()
            .setInitialDelay(delayMillis(triggerAt), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(KEY_TASK_ID, taskId).build())
            .addTag(taskTag(taskId))
            .build()
        workManager.enqueueUniqueWork(
            dueWorkName(taskId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancelDueReminder(taskId: Long) {
        workManager.cancelUniqueWork(dueWorkName(taskId))
    }

    override fun scheduleLocationReminder(
        taskId: Long,
        transition: GeofenceTransition,
        triggerAt: Long,
    ) {
        if (taskId <= 0) return
        val request = OneTimeWorkRequestBuilder<DeferredLocationReminderWorker>()
            .setInitialDelay(delayMillis(triggerAt), TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(KEY_TASK_ID, taskId)
                    .putString(KEY_TRANSITION, transition.name)
                    .build(),
            )
            .addTag(taskTag(taskId))
            .build()
        workManager.enqueueUniqueWork(
            locationWorkName(taskId, transition),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancelLocationReminder(taskId: Long, transition: GeofenceTransition?) {
        if (transition == null) {
            GeofenceTransition.entries.forEach {
                workManager.cancelUniqueWork(locationWorkName(taskId, it))
            }
        } else {
            workManager.cancelUniqueWork(locationWorkName(taskId, transition))
        }
    }

    override fun cancelAll(taskId: Long) {
        cancelDueReminder(taskId)
        cancelLocationReminder(taskId)
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_TRANSITION = "transition"

        private fun dueWorkName(taskId: Long) = "due-reminder:$taskId"
        private fun locationWorkName(taskId: Long, transition: GeofenceTransition) =
            "location-reminder:$taskId:${transition.name}"
        private fun taskTag(taskId: Long) = "task-reminders:$taskId"
        private fun delayMillis(triggerAt: Long): Long =
            (triggerAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }
}
