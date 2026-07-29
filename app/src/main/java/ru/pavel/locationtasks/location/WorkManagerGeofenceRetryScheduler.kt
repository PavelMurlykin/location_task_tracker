package ru.pavel.locationtasks.location

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerGeofenceRetryScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : GeofenceRetryScheduler {
    override fun scheduleRetry() {
        val request = OneTimeWorkRequestBuilder<GeofenceRegistrationWorker>()
            .setInitialDelay(INITIAL_RETRY_DELAY_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "geofence-registration-retry"
        const val WORK_TAG = "geofence-reliability"
        private const val INITIAL_RETRY_DELAY_SECONDS = 30L
        private const val MIN_BACKOFF_SECONDS = 30L
    }
}
