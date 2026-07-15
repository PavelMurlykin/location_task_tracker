package ru.pavel.locationtasks

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.data.TaskDao
import ru.pavel.locationtasks.location.GeofenceManager
import ru.pavel.locationtasks.notifications.TaskNotificationManager
import javax.inject.Inject

@HiltAndroidApp
class LocationTasksApplication : Application() {
    @Inject lateinit var notificationManager: TaskNotificationManager
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var geofenceManager: GeofenceManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        notificationManager.createChannel()
        applicationScope.launch {
            geofenceManager.restore(taskDao.getTasksToMonitor())
        }
    }
}
