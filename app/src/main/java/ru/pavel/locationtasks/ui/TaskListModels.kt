package ru.pavel.locationtasks.ui

import ru.pavel.locationtasks.data.TaskEntity
import java.time.Instant
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TaskSection {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}

enum class TaskQuickFilter {
    OVERDUE,
    TODAY,
    GEOFENCE,
    WITHOUT_LOCATION,
}

enum class TaskSort {
    DUE_DATE,
    DISTANCE,
    CREATED_AT,
    PRIORITY,
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

data class TaskListCriteria(
    val section: TaskSection = TaskSection.ACTIVE,
    val query: String = "",
    val quickFilter: TaskQuickFilter? = null,
    val categoryId: String? = null,
    val sort: TaskSort = TaskSort.DUE_DATE,
)

fun filterAndSortTasks(
    tasks: List<TaskEntity>,
    criteria: TaskListCriteria,
    currentLocation: GeoPoint?,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<TaskEntity> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val normalizedQuery = criteria.query.trim()
    val filtered = tasks.asSequence()
        .filter { task ->
            when (criteria.section) {
                TaskSection.ACTIVE -> !task.isCompleted && !task.isArchived
                TaskSection.COMPLETED -> task.isCompleted && !task.isArchived
                TaskSection.ARCHIVED -> task.isArchived
            }
        }
        .filter { task ->
            normalizedQuery.isEmpty() ||
                task.title.contains(normalizedQuery, ignoreCase = true) ||
                task.description.contains(normalizedQuery, ignoreCase = true) ||
                task.address.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                task.tagNames.any { it.contains(normalizedQuery, ignoreCase = true) }
        }
        .filter { task ->
            criteria.categoryId == null || task.category == criteria.categoryId
        }
        .filter { task ->
            when (criteria.quickFilter) {
                TaskQuickFilter.OVERDUE -> task.dueAt
                    ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate().isBefore(today) }
                    ?: false
                TaskQuickFilter.TODAY -> task.dueAt
                    ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() == today }
                    ?: false
                TaskQuickFilter.GEOFENCE -> task.geofenceEnabled && task.hasLocation
                TaskQuickFilter.WITHOUT_LOCATION -> !task.hasLocation
                null -> true
            }
        }
        .toList()

    val dueDateComparator = compareBy<TaskEntity> { it.dueAt == null }
        .thenBy { it.dueAt ?: Long.MAX_VALUE }
        .thenByDescending(TaskEntity::updatedAt)
    val comparator = when (criteria.sort) {
        TaskSort.DUE_DATE -> dueDateComparator
        TaskSort.DISTANCE -> compareBy<TaskEntity> {
            distanceMeters(task = it, currentLocation = currentLocation)
                ?: Double.POSITIVE_INFINITY
        }.then(dueDateComparator)
        TaskSort.CREATED_AT -> compareByDescending<TaskEntity>(TaskEntity::createdAt)
            .then(dueDateComparator)
        TaskSort.PRIORITY -> compareByDescending<TaskEntity> {
            it.resolvedPriority.sortRank
        }.then(dueDateComparator)
    }
    return filtered.sortedWith(comparator)
}

fun distanceMeters(task: TaskEntity, currentLocation: GeoPoint?): Double? {
    val latitude = task.latitude ?: return null
    val longitude = task.longitude ?: return null
    val origin = currentLocation ?: return null
    return distanceMeters(origin, GeoPoint(latitude, longitude))
}

fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(to.latitude - from.latitude)
    val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
    val startLatitude = Math.toRadians(from.latitude)
    val endLatitude = Math.toRadians(to.latitude)
    val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(startLatitude) * cos(endLatitude) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    val normalizedHaversine = haversine.coerceIn(0.0, 1.0)
    return earthRadiusMeters * 2 * atan2(
        sqrt(normalizedHaversine),
        sqrt(1 - normalizedHaversine),
    )
}
