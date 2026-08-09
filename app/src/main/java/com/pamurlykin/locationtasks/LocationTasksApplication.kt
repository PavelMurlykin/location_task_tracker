package com.pamurlykin.locationtasks

import android.app.Application
import com.yandex.mapkit.MapKitFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.pamurlykin.locationtasks.analytics.ProductTelemetry
import com.pamurlykin.locationtasks.data.TaskDao
import com.pamurlykin.locationtasks.location.GeofenceCoordinator
import com.pamurlykin.locationtasks.notifications.ReminderWorkScheduler
import com.pamurlykin.locationtasks.notifications.TaskNotificationManager
import javax.inject.Inject

@HiltAndroidApp
class LocationTasksApplication : Application() {
    @Inject lateinit var notificationManager: TaskNotificationManager
    @Inject lateinit var geofenceCoordinator: GeofenceCoordinator
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var reminderScheduler: ReminderWorkScheduler
    @Inject lateinit var productTelemetry: ProductTelemetry

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        productTelemetry.start()
        if (BuildConfig.MAPKIT_API_KEY_PRESENT) {
            MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
            MapKitFactory.initialize(this)
        }
        notificationManager.createChannel()
        applicationScope.launch {
            geofenceCoordinator.reconcileAll()
            taskDao.getTasksWithDueReminders().forEach(reminderScheduler::syncDueReminder)
        }
    }
}
