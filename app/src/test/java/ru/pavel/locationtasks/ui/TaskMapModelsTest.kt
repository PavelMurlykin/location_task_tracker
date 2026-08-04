package ru.pavel.locationtasks.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.pavel.locationtasks.data.TaskEntity

class TaskMapModelsTest {
    @Test
    fun `nearby tasks form a cluster and expose its count`() {
        val clusters = clusterMapTasks(
            listOf(
                task(1, 55.7500, 37.6100),
                task(2, 55.7504, 37.6100),
                task(3, 55.7600, 37.6100),
            ),
            thresholdMeters = 100.0,
        )

        assertEquals(2, clusters.size)
        assertEquals(listOf(1L, 2L), clusters.first().tasks.map(TaskEntity::id))
        assertEquals(2, clusters.first().count)
    }

    @Test
    fun `completed and archived tasks are absent from the map`() {
        val tasks = listOf(
            task(1, 55.75, 37.61),
            task(2, 55.75, 37.61).copy(isCompleted = true),
            task(3, 55.75, 37.61).copy(isArchived = true),
        )

        assertEquals(listOf(1L), activeMapTasks(tasks).map(TaskEntity::id))
    }

    @Test
    fun `nearby screen sorts tasks by distance`() {
        val origin = GeoPoint(55.75, 37.61)
        val sorted = sortNearbyTasks(
            listOf(
                task(1, 55.78, 37.61),
                task(2, 55.751, 37.61),
            ),
            origin,
        )

        assertEquals(listOf(2L, 1L), sorted.map(TaskEntity::id))
    }

    @Test
    fun `route chooses the nearest next stop`() {
        val origin = GeoPoint(0.0, 0.0)
        val route = optimizeTaskRoute(
            listOf(
                task(1, 0.0, 0.03),
                task(2, 0.0, 0.01),
                task(3, 0.0, 0.02),
            ),
            origin,
        )

        assertEquals(listOf(2L, 3L, 1L), route.map(TaskEntity::id))
        assertTrue(routeDistanceMeters(origin, route) in 3_300.0..3_400.0)
    }

    @Test
    fun `tasks inside route corridor are suggested along the way`() {
        val origin = GeoPoint(55.0, 37.0)
        val destination = task(1, 55.0, 37.02)
        val alongTheWay = task(2, 55.001, 37.01)
        val detour = task(3, 55.01, 37.01)

        val suggestions = tasksAlongRoute(
            candidates = listOf(destination, alongTheWay, detour),
            currentLocation = origin,
            route = listOf(destination),
            corridorMeters = 200.0,
        )

        assertEquals(listOf(2L), suggestions.map(TaskEntity::id))
    }

    private fun task(id: Long, latitude: Double, longitude: Double) = TaskEntity(
        id = id,
        title = "Task $id",
        latitude = latitude,
        longitude = longitude,
    )
}
