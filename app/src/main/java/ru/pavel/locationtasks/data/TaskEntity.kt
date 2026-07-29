package ru.pavel.locationtasks.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueAt: Long? = null,
    val isCompleted: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val geofenceRadiusMeters: Float = DEFAULT_RADIUS_METERS,
    val geofenceEnabled: Boolean = false,
    val lastNotifiedAt: Long? = null,
    @ColumnInfo(defaultValue = "'DISABLED'")
    val geofenceStatus: String = GeofenceStatus.DISABLED.name,
    val geofenceStatusDetails: String? = null,
    val geofenceRegisteredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val hasLocation: Boolean
        get() = latitude != null && longitude != null

    val shouldMonitor: Boolean
        get() = geofenceEnabled && hasLocation && !isCompleted

    val resolvedGeofenceStatus: GeofenceStatus
        get() = GeofenceStatus.fromStorage(geofenceStatus)

    companion object {
        const val DEFAULT_RADIUS_METERS = 250f
    }
}
