package ru.pavel.locationtasks.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class GeofenceRegistrationWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val coordinator = EntryPointAccessors.fromApplication(
            applicationContext,
            GeofenceWorkerEntryPoint::class.java,
        ).coordinator()
        val result = coordinator.reconcileAll()
        return if (result.retryableFailureCount > 0) Result.retry() else Result.success()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GeofenceWorkerEntryPoint {
    fun coordinator(): GeofenceCoordinator
}
