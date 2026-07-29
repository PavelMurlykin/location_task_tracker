package ru.pavel.locationtasks.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.data.GeofenceTransition
import ru.pavel.locationtasks.notifications.LocationReminderDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    @Inject lateinit var dispatcher: LocationReminderDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> GeofenceTransition.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> GeofenceTransition.EXIT
            else -> return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                event.triggeringGeofences.orEmpty()
                    .mapNotNull { GeofenceManager.taskId(it.requestId) }
                    .distinct()
                    .forEach { dispatcher.dispatch(it, transition) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
