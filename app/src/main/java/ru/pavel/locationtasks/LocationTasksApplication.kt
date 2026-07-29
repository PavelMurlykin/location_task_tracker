package ru.pavel.locationtasks

import android.app.Application
import com.yandex.mapkit.MapKitFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.location.GeofenceCoordinator
import ru.pavel.locationtasks.notifications.TaskNotificationManager
import javax.inject.Inject

@HiltAndroidApp
class LocationTasksApplication : Application() {
    @Inject lateinit var notificationManager: TaskNotificationManager
    @Inject lateinit var geofenceCoordinator: GeofenceCoordinator

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MAPKIT_API_KEY_PRESENT) {
            MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
            MapKitFactory.initialize(this)
        }
        notificationManager.createChannel()
        applicationScope.launch {
            geofenceCoordinator.reconcileAll()
        }
    }
}
