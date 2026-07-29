package ru.pavel.locationtasks.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "geofence_logs",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["taskId"]),
    ],
)
data class GeofenceLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val taskTitle: String,
    val event: String,
    val outcome: String,
    val details: String? = null,
    val occurredAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val EVENT_REGISTRATION = "REGISTRATION"
        const val EVENT_TRIGGER = "TRIGGER"
        const val EVENT_RESTORE = "RESTORE"

        const val OUTCOME_ACTIVE = "ACTIVE"
        const val OUTCOME_ERROR = "ERROR"
        const val OUTCOME_MISSING_PERMISSION = "MISSING_PERMISSION"
        const val OUTCOME_LIMIT_REACHED = "LIMIT_REACHED"
        const val OUTCOME_NOTIFIED = "NOTIFIED"
        const val OUTCOME_COOLDOWN = "COOLDOWN"
        const val OUTCOME_DEFERRED = "DEFERRED"
        const val OUTCOME_NEXT_VISIT = "NEXT_VISIT"
        const val OUTCOME_NOTIFICATIONS_BLOCKED = "NOTIFICATIONS_BLOCKED"
        const val OUTCOME_TASK_INACTIVE = "TASK_INACTIVE"

        const val MAX_ENTRIES = 200
    }
}
