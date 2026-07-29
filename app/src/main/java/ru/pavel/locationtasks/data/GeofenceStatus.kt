package ru.pavel.locationtasks.data

enum class GeofenceStatus {
    DISABLED,
    PENDING,
    ACTIVE,
    MISSING_PERMISSION,
    LIMIT_REACHED,
    ERROR;

    companion object {
        fun fromStorage(value: String): GeofenceStatus =
            entries.firstOrNull { it.name == value } ?: DISABLED
    }
}
