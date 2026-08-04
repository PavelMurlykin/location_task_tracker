package ru.pavel.locationtasks.ui

import ru.pavel.locationtasks.data.TaskEntity
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

data class TaskMapCluster(
    val tasks: List<TaskEntity>,
    val center: GeoPoint,
) {
    val count: Int get() = tasks.size
}

fun activeMapTasks(tasks: List<TaskEntity>): List<TaskEntity> = tasks.filter {
    !it.isCompleted && !it.isArchived && it.hasLocation
}

fun clusterMapTasks(
    tasks: List<TaskEntity>,
    thresholdMeters: Double = DEFAULT_CLUSTER_DISTANCE_METERS,
): List<TaskMapCluster> {
    val remaining = activeMapTasks(tasks).toMutableList()
    val clusters = mutableListOf<TaskMapCluster>()

    while (remaining.isNotEmpty()) {
        val members = mutableListOf(remaining.removeAt(0))
        var memberIndex = 0
        while (memberIndex < members.size) {
            val memberPoint = members[memberIndex].requireGeoPoint()
            var candidateIndex = remaining.lastIndex
            while (candidateIndex >= 0) {
                val candidate = remaining[candidateIndex]
                if (distanceMeters(memberPoint, candidate.requireGeoPoint()) <= thresholdMeters) {
                    members += candidate
                    remaining.removeAt(candidateIndex)
                }
                candidateIndex--
            }
            memberIndex++
        }
        clusters += TaskMapCluster(
            tasks = members.sortedBy(TaskEntity::id),
            center = GeoPoint(
                latitude = members.mapNotNull(TaskEntity::latitude).average(),
                longitude = members.mapNotNull(TaskEntity::longitude).average(),
            ),
        )
    }

    return clusters
}

fun sortNearbyTasks(
    tasks: List<TaskEntity>,
    currentLocation: GeoPoint,
): List<TaskEntity> = activeMapTasks(tasks).sortedWith(
    compareBy<TaskEntity> { distanceMeters(it.requireGeoPoint(), currentLocation) }
        .thenBy(TaskEntity::id),
)

fun optimizeTaskRoute(
    tasks: List<TaskEntity>,
    currentLocation: GeoPoint,
): List<TaskEntity> {
    val remaining = activeMapTasks(tasks).toMutableList()
    val route = mutableListOf<TaskEntity>()
    var cursor = currentLocation
    while (remaining.isNotEmpty()) {
        val next = remaining.minWithOrNull(
            compareBy<TaskEntity> { distanceMeters(cursor, it.requireGeoPoint()) }
                .thenBy(TaskEntity::id),
        ) ?: break
        route += next
        remaining.remove(next)
        cursor = next.requireGeoPoint()
    }
    return route
}

fun routeDistanceMeters(
    currentLocation: GeoPoint,
    route: List<TaskEntity>,
): Double {
    var cursor = currentLocation
    return route.sumOf { task ->
        val point = task.requireGeoPoint()
        distanceMeters(cursor, point).also { cursor = point }
    }
}

fun tasksAlongRoute(
    candidates: List<TaskEntity>,
    currentLocation: GeoPoint,
    route: List<TaskEntity>,
    corridorMeters: Double = DEFAULT_ROUTE_CORRIDOR_METERS,
): List<TaskEntity> {
    if (route.isEmpty()) return emptyList()
    val selectedIds = route.mapTo(mutableSetOf(), TaskEntity::id)
    val routePoints = buildList {
        add(currentLocation)
        route.mapTo(this) { it.requireGeoPoint() }
    }
    return activeMapTasks(candidates)
        .asSequence()
        .filterNot { it.id in selectedIds }
        .filter { task ->
            routePoints.zipWithNext().any { (start, end) ->
                distanceToSegmentMeters(task.requireGeoPoint(), start, end) <= corridorMeters
            }
        }
        .sortedBy { candidate ->
            routePoints.zipWithNext().minOf { (start, end) ->
                distanceToSegmentMeters(candidate.requireGeoPoint(), start, end)
            }
        }
        .toList()
}

private fun distanceToSegmentMeters(
    point: GeoPoint,
    start: GeoPoint,
    end: GeoPoint,
): Double {
    val referenceLatitude = Math.toRadians(start.latitude)
    fun GeoPoint.toLocalMeters(): Pair<Double, Double> {
        val x = Math.toRadians(longitude - start.longitude) *
            cos(referenceLatitude) * EARTH_RADIUS_METERS
        val y = Math.toRadians(latitude - start.latitude) * EARTH_RADIUS_METERS
        return x to y
    }

    val (pointX, pointY) = point.toLocalMeters()
    val (endX, endY) = end.toLocalMeters()
    val segmentLengthSquared = endX.pow(2) + endY.pow(2)
    if (segmentLengthSquared == 0.0) return sqrt(pointX.pow(2) + pointY.pow(2))
    val projection = ((pointX * endX + pointY * endY) / segmentLengthSquared)
        .coerceIn(0.0, 1.0)
    return sqrt(
        (pointX - projection * endX).pow(2) +
            (pointY - projection * endY).pow(2),
    )
}

internal fun TaskEntity.requireGeoPoint() = GeoPoint(
    latitude = requireNotNull(latitude),
    longitude = requireNotNull(longitude),
)

const val MAX_ROUTE_TASKS = 8
private const val DEFAULT_CLUSTER_DISTANCE_METERS = 120.0
private const val DEFAULT_ROUTE_CORRIDOR_METERS = 300.0
private const val EARTH_RADIUS_METERS = 6_371_000.0
