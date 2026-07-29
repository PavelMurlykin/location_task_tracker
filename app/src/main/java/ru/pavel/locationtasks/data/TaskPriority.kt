package ru.pavel.locationtasks.data

enum class TaskPriority(val sortRank: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2);

    companion object {
        fun fromStorage(value: String): TaskPriority =
            entries.firstOrNull { it.name == value } ?: NORMAL
    }
}
