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
import ru.pavel.locationtasks.data.TaskRepository
import javax.inject.Inject

@AndroidEntryPoint
class TaskActionReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: TaskRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMPLETE) return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it > 0 } ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.setCompleted(taskId, true)
                NotificationManagerCompat.from(context).cancel(taskId.hashCode())
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "ru.pavel.locationtasks.action.COMPLETE"
        const val EXTRA_TASK_ID = "task_id"
    }
}
