package ru.pavel.locationtasks.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.data.GeofenceTransition
import ru.pavel.locationtasks.data.TaskDao
import ru.pavel.locationtasks.data.TaskRepository
import javax.inject.Inject

@AndroidEntryPoint
class TaskActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: TaskRepository
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var reminderScheduler: ReminderWorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it > 0 } ?: return
        val kind = intent.getStringExtra(EXTRA_REMINDER_KIND)
            ?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() }
            ?: ReminderKind.LOCATION
        val transition = intent.getStringExtra(EXTRA_TRANSITION)
            ?.let { runCatching { GeofenceTransition.valueOf(it) }.getOrNull() }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> repository.setCompleted(taskId, true)
                    ACTION_SNOOZE_15 -> snooze(
                        taskId = taskId,
                        kind = kind,
                        transition = transition,
                        delayMinutes = 15,
                    )
                    ACTION_SNOOZE_60 -> snooze(
                        taskId = taskId,
                        kind = kind,
                        transition = transition,
                        delayMinutes = 60,
                    )
                    ACTION_NEXT_VISIT -> {
                        taskDao.setSnoozeState(
                            id = taskId,
                            snoozedUntil = null,
                            skipUntilNextVisit = true,
                        )
                        taskDao.clearLastNotified(taskId)
                        reminderScheduler.cancelLocationReminder(
                            taskId,
                            GeofenceTransition.ENTER,
                        )
                    }
                }
                NotificationManagerCompat.from(context)
                    .cancel(TaskNotificationManager.notificationId(taskId))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun snooze(
        taskId: Long,
        kind: ReminderKind,
        transition: GeofenceTransition?,
        delayMinutes: Int,
    ) {
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        taskDao.setSnoozeState(
            id = taskId,
            snoozedUntil = triggerAt,
            skipUntilNextVisit = false,
        )
        taskDao.clearLastNotified(taskId)
        when (kind) {
            ReminderKind.DUE -> reminderScheduler.scheduleDueReminder(taskId, triggerAt)
            ReminderKind.LOCATION -> reminderScheduler.scheduleLocationReminder(
                taskId = taskId,
                transition = transition ?: GeofenceTransition.ENTER,
                triggerAt = triggerAt,
            )
        }
    }

    companion object {
        const val ACTION_COMPLETE = "ru.pavel.locationtasks.action.COMPLETE"
        const val ACTION_SNOOZE_15 = "ru.pavel.locationtasks.action.SNOOZE_15"
        const val ACTION_SNOOZE_60 = "ru.pavel.locationtasks.action.SNOOZE_60"
        const val ACTION_NEXT_VISIT = "ru.pavel.locationtasks.action.NEXT_VISIT"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_REMINDER_KIND = "reminder_kind"
        const val EXTRA_TRANSITION = "transition"
        private val SUPPORTED_ACTIONS = setOf(
            ACTION_COMPLETE,
            ACTION_SNOOZE_15,
            ACTION_SNOOZE_60,
            ACTION_NEXT_VISIT,
        )
    }
}
