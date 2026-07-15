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
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceRestoreReceiver : BroadcastReceiver() {
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var geofenceManager: GeofenceManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                geofenceManager.restore(taskDao.getTasksToMonitor())
            } finally {
                pendingResult.finish()
            }
        }
    }
}
