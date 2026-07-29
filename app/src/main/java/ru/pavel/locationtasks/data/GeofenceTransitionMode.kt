package ru.pavel.locationtasks.data

enum class GeofenceTransitionMode {
    ENTER,
    EXIT,
    BOTH;

    fun includes(transition: GeofenceTransition): Boolean = when (this) {
        ENTER -> transition == GeofenceTransition.ENTER
        EXIT -> transition == GeofenceTransition.EXIT
        BOTH -> true
    }

    companion object {
        fun fromStorage(value: String): GeofenceTransitionMode =
            entries.firstOrNull { it.name == value } ?: ENTER
    }
}

enum class GeofenceTransition {
    ENTER,
    EXIT,
}
