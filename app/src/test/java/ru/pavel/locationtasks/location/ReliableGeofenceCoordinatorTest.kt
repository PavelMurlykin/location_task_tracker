package ru.pavel.locationtasks.location

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.pavel.locationtasks.data.GeofenceLogEntity
import ru.pavel.locationtasks.data.GeofenceStatus
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.testing.FakeGeofenceLogDao
import ru.pavel.locationtasks.testing.FakeGeofencePermissionSource
import ru.pavel.locationtasks.testing.FakeGeofencePlatform
import ru.pavel.locationtasks.testing.FakeGeofenceRetryScheduler
import ru.pavel.locationtasks.testing.FakeTaskDao
import ru.pavel.locationtasks.testing.FakeProductTelemetry

class ReliableGeofenceCoordinatorTest {
    @Test
    fun `restore force-registers active geofences after reboot`() = runBlocking {
        val task = monitoredTask(1L).copy(geofenceStatus = GeofenceStatus.ACTIVE.name)
        val fixture = fixture(listOf(task))

        val result = fixture.coordinator.reconcileAll(force = true)

        assertEquals(listOf(1L), fixture.platform.registeredTaskIds)
        assertEquals(1, result.activeCount)
        assertEquals(listOf("success"), fixture.telemetry.geofenceRegistrationOutcomes)
        assertEquals(GeofenceStatus.ACTIVE, fixture.taskDao.getById(1L)?.resolvedGeofenceStatus)
        assertEquals(
            GeofenceLogEntity.EVENT_RESTORE,
            fixture.logDao.snapshot().single().event,
        )
    }

    @Test
    fun `only first one hundred geofences are registered`() = runBlocking {
        val tasks = (1L..101L).map(::monitoredTask)
        val fixture = fixture(tasks)

        val result = fixture.coordinator.reconcileAll(force = true)

        assertEquals(100, fixture.platform.registeredTaskIds.size)
        assertEquals(100, result.activeCount)
        assertEquals(1, result.limitReachedCount)
        assertEquals(
            100,
            fixture.taskDao.snapshot().count {
                it.resolvedGeofenceStatus == GeofenceStatus.ACTIVE
            },
        )
        assertEquals(
            1,
            fixture.taskDao.snapshot().count {
                it.resolvedGeofenceStatus == GeofenceStatus.LIMIT_REACHED
            },
        )
    }

    @Test
    fun `registration failure is stored and scheduled for retry`() = runBlocking {
        val fixture = fixture(listOf(monitoredTask(1L)))
        fixture.platform.registrationResult =
            GeofenceRegistrationResult.Failed(IllegalStateException("service unavailable"))

        val result = fixture.coordinator.reconcileAll()

        val task = fixture.taskDao.getById(1L)
        assertEquals(GeofenceStatus.ERROR, task?.resolvedGeofenceStatus)
        assertTrue(task?.geofenceStatusDetails?.contains("service unavailable") == true)
        assertEquals(1, result.retryableFailureCount)
        assertEquals(1, fixture.retryScheduler.scheduledRetries)
        assertEquals(listOf("error"), fixture.telemetry.geofenceRegistrationOutcomes)
    }

    @Test
    fun `missing background permission is visible and registration is skipped`() = runBlocking {
        val fixture = fixture(listOf(monitoredTask(1L)))
        fixture.permissions.state = LocationPermissionState(
            preciseLocation = true,
            backgroundLocation = false,
            notifications = true,
        )

        fixture.coordinator.reconcileAll()

        assertTrue(fixture.platform.registeredTaskIds.isEmpty())
        assertEquals(
            GeofenceStatus.MISSING_PERMISSION,
            fixture.taskDao.getById(1L)?.resolvedGeofenceStatus,
        )
    }

    private fun fixture(tasks: List<TaskEntity>): Fixture {
        val taskDao = FakeTaskDao(tasks)
        val platform = FakeGeofencePlatform()
        val permissions = FakeGeofencePermissionSource()
        val retryScheduler = FakeGeofenceRetryScheduler()
        val logDao = FakeGeofenceLogDao()
        val telemetry = FakeProductTelemetry()
        return Fixture(
            taskDao = taskDao,
            platform = platform,
            permissions = permissions,
            retryScheduler = retryScheduler,
            logDao = logDao,
            telemetry = telemetry,
            coordinator = ReliableGeofenceCoordinator(
                taskDao = taskDao,
                geofencePlatform = platform,
                permissionSource = permissions,
                retryScheduler = retryScheduler,
                logDao = logDao,
                productTelemetry = telemetry,
            ),
        )
    }

    private fun monitoredTask(id: Long) = TaskEntity(
        id = id,
        title = "Задача $id",
        latitude = 55.7558,
        longitude = 37.6173,
        geofenceEnabled = true,
        geofenceStatus = GeofenceStatus.PENDING.name,
    )

    private data class Fixture(
        val taskDao: FakeTaskDao,
        val platform: FakeGeofencePlatform,
        val permissions: FakeGeofencePermissionSource,
        val retryScheduler: FakeGeofenceRetryScheduler,
        val logDao: FakeGeofenceLogDao,
        val telemetry: FakeProductTelemetry,
        val coordinator: ReliableGeofenceCoordinator,
    )
}
