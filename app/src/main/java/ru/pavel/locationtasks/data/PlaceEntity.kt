package ru.pavel.locationtasks.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "places",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["latitude", "longitude"], unique = true),
        Index(value = ["lastUsedAt"]),
    ],
)
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String? = null,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = TaskEntity.DEFAULT_RADIUS_METERS,
    val lastUsedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isSaved: Boolean get() = !name.isNullOrBlank()
    val displayName: String
        get() = name?.takeIf(String::isNotBlank)
            ?: address.takeIf(String::isNotBlank)
            ?: "%.5f, %.5f".format(latitude, longitude)
}
