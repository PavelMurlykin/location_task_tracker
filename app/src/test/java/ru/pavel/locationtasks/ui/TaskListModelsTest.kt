package ru.pavel.locationtasks.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.data.CategoryEntity
import ru.pavel.locationtasks.data.TaskPriority
import ru.pavel.locationtasks.data.encodeTags
import java.time.LocalDate
import java.time.ZoneId

class TaskListModelsTest {
    private val zoneId = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 7, 29)
    private val now = today.atTime(12, 0).atZone(zoneId).toInstant().toEpochMilli()

    @Test
    fun `search matches title description and address ignoring case`() {
        val tasks = listOf(
            task(1, title = "Купить молоко"),
            task(2, description = "Зайти в АПТЕКУ"),
            task(3, address = "Ленинградский вокзал"),
            task(4, title = "Позвонить"),
        )

        assertEquals(
            listOf(1L),
            filtered(tasks, query = "МОЛОКО").map(TaskEntity::id),
        )
        assertEquals(
            listOf(2L),
            filtered(tasks, query = "аптеку").map(TaskEntity::id),
        )
        assertEquals(
            listOf(3L),
            filtered(tasks, query = "вокзал").map(TaskEntity::id),
        )
    }

    @Test
    fun `overdue and today filters use local calendar dates`() {
        val tasks = listOf(
            task(1, dueAt = dateMillis(today.minusDays(1))),
            task(2, dueAt = dateMillis(today)),
            task(3, dueAt = dateMillis(today.plusDays(1))),
        )

        assertEquals(
            listOf(1L),
            filtered(tasks, filter = TaskQuickFilter.OVERDUE).map(TaskEntity::id),
        )
        assertEquals(
            listOf(2L),
            filtered(tasks, filter = TaskQuickFilter.TODAY).map(TaskEntity::id),
        )
    }

    @Test
    fun `location filters distinguish geofences and tasks without place`() {
        val tasks = listOf(
            task(1, latitude = 55.75, longitude = 37.61, geofenceEnabled = true),
            task(2, latitude = 55.76, longitude = 37.62, geofenceEnabled = false),
            task(3),
        )

        assertEquals(
            listOf(1L),
            filtered(tasks, filter = TaskQuickFilter.GEOFENCE).map(TaskEntity::id),
        )
        assertEquals(
            listOf(3L),
            filtered(tasks, filter = TaskQuickFilter.WITHOUT_LOCATION).map(TaskEntity::id),
        )
    }

    @Test
    fun `priority sort puts high priority first`() {
        val tasks = listOf(
            task(1, priority = TaskPriority.LOW),
            task(2, priority = TaskPriority.HIGH),
            task(3, priority = TaskPriority.NORMAL),
        )

        assertEquals(
            listOf(2L, 3L, 1L),
            filtered(tasks, sort = TaskSort.PRIORITY).map(TaskEntity::id),
        )
    }

    @Test
    fun `distance sort puts nearest located task first and missing location last`() {
        val tasks = listOf(
            task(1, latitude = 55.80, longitude = 37.61),
            task(2),
            task(3, latitude = 55.751, longitude = 37.611),
        )

        val result = filterAndSortTasks(
            tasks = tasks,
            criteria = TaskListCriteria(sort = TaskSort.DISTANCE),
            currentLocation = GeoPoint(55.75, 37.61),
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(listOf(3L, 1L, 2L), result.map(TaskEntity::id))
    }

    @Test
    fun `completed section excludes active tasks`() {
        val tasks = listOf(task(1), task(2).copy(isCompleted = true))

        val result = filterAndSortTasks(
            tasks = tasks,
            criteria = TaskListCriteria(section = TaskSection.COMPLETED),
            currentLocation = null,
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(listOf(2L), result.map(TaskEntity::id))
    }

    @Test
    fun `archive section excludes active and completed tasks`() {
        val tasks = listOf(
            task(1),
            task(2).copy(isCompleted = true),
            task(3).copy(isCompleted = true, isArchived = true),
        )

        val result = filterAndSortTasks(
            tasks = tasks,
            criteria = TaskListCriteria(section = TaskSection.ARCHIVED),
            currentLocation = null,
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(listOf(3L), result.map(TaskEntity::id))
    }

    @Test
    fun `category filter and tag search organize tasks`() {
        val tasks = listOf(
            task(1).copy(
                category = CategoryEntity.SHOPPING_ID,
                tags = encodeTags(listOf("срочно")),
            ),
            task(2).copy(category = CategoryEntity.WORK_ID),
        )

        val byCategory = filterAndSortTasks(
            tasks,
            TaskListCriteria(categoryId = CategoryEntity.SHOPPING_ID),
            currentLocation = null,
            nowMillis = now,
            zoneId = zoneId,
        )
        val byTag = filtered(tasks, query = "СРОЧНО")

        assertEquals(listOf(1L), byCategory.map(TaskEntity::id))
        assertEquals(listOf(1L), byTag.map(TaskEntity::id))
    }

    @Test
    fun `snooze moves overdue task to tomorrow`() {
        val overdue = dateMillis(today.minusDays(3))

        val snoozed = calculateSnoozedDueAt(overdue, now, zoneId)

        assertEquals(dateMillis(today.plusDays(1)), snoozed)
    }

    @Test
    fun `snooze adds one day to future due date`() {
        val future = dateMillis(today.plusDays(2))

        val snoozed = calculateSnoozedDueAt(future, now, zoneId)

        assertEquals(dateMillis(today.plusDays(3)), snoozed)
    }

    private fun filtered(
        tasks: List<TaskEntity>,
        query: String = "",
        filter: TaskQuickFilter? = null,
        sort: TaskSort = TaskSort.DUE_DATE,
    ) = filterAndSortTasks(
        tasks = tasks,
        criteria = TaskListCriteria(query = query, quickFilter = filter, sort = sort),
        currentLocation = null,
        nowMillis = now,
        zoneId = zoneId,
    )

    private fun task(
        id: Long,
        title: String = "Задача $id",
        description: String = "",
        address: String? = null,
        dueAt: Long? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        geofenceEnabled: Boolean = false,
        priority: TaskPriority = TaskPriority.NORMAL,
    ) = TaskEntity(
        id = id,
        title = title,
        description = description,
        address = address,
        dueAt = dueAt,
        latitude = latitude,
        longitude = longitude,
        geofenceEnabled = geofenceEnabled,
        priority = priority.name,
        createdAt = id,
        updatedAt = id,
    )

    private fun dateMillis(date: LocalDate): Long =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()
}
