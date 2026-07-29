package ru.pavel.locationtasks.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.data.TaskDao
import ru.pavel.locationtasks.notifications.ReminderWorkScheduler
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceRestoreReceiver : BroadcastReceiver() {
    @Inject lateinit var geofenceCoordinator: GeofenceCoordinator
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var reminderScheduler: ReminderWorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                geofenceCoordinator.reconcileAll(force = true)
                taskDao.getTasksWithDueReminders().forEach(reminderScheduler::syncDueReminder)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
