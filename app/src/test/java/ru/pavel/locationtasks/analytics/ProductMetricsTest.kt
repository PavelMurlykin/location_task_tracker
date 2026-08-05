package ru.pavel.locationtasks.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductMetricsTest {
    @Test
    fun `rates are calculated from aggregate counters`() {
        val metrics = ProductMetrics(
            geofenceRegistrationAttempts = 10,
            geofenceRegistrationSuccesses = 8,
            geofenceTriggers = 5,
            remindersDelivered = 4,
            notificationsShown = 4,
            notificationCompletions = 2,
        )

        assertEquals(0.8, metrics.geofenceRegistrationSuccessRate!!, 0.0001)
        assertEquals(0.8, metrics.reminderDeliveryRate!!, 0.0001)
        assertEquals(0.5, metrics.notificationCompletionRate!!, 0.0001)
    }

    @Test
    fun `rates are absent until denominator is available`() {
        val metrics = ProductMetrics()

        assertNull(metrics.geofenceRegistrationSuccessRate)
        assertNull(metrics.reminderDeliveryRate)
        assertNull(metrics.notificationCompletionRate)
    }
}
