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
    @ColumnInfo(defaultValue = "'NORMAL'")
    val priority: String = TaskPriority.NORMAL.name,
    val isCompleted: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val geofenceRadiusMeters: Float = DEFAULT_RADIUS_METERS,
    val geofenceEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "'ENTER'")
    val geofenceTransitionMode: String = GeofenceTransitionMode.ENTER.name,
    val notificationCooldownMinutes: Int? = null,
    @ColumnInfo(defaultValue = "127")
    val allowedDaysMask: Int = 127,
    val reminderWindowStartMinutes: Int? = null,
    val reminderWindowEndMinutes: Int? = null,
    val snoozedUntil: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val skipUntilNextVisit: Boolean = false,
    val lastNotifiedAt: Long? = null,
    val lastNotifiedTransition: String? = null,
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

    val resolvedPriority: TaskPriority
        get() = TaskPriority.fromStorage(priority)

    val resolvedTransitionMode: GeofenceTransitionMode
        get() = GeofenceTransitionMode.fromStorage(geofenceTransitionMode)

    companion object {
        const val DEFAULT_RADIUS_METERS = 250f
    }
}
