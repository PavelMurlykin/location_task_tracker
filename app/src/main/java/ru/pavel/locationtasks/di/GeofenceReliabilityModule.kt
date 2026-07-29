package ru.pavel.locationtasks.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.pavel.locationtasks.location.AndroidGeofencePermissionSource
import ru.pavel.locationtasks.location.GeofenceCoordinator
import ru.pavel.locationtasks.location.GeofenceManager
import ru.pavel.locationtasks.location.GeofencePermissionSource
import ru.pavel.locationtasks.location.GeofencePlatform
import ru.pavel.locationtasks.location.GeofenceRetryScheduler
import ru.pavel.locationtasks.location.ReliableGeofenceCoordinator
import ru.pavel.locationtasks.location.WorkManagerGeofenceRetryScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GeofenceReliabilityModule {
    @Binds
    @Singleton
    abstract fun bindGeofencePlatform(implementation: GeofenceManager): GeofencePlatform

    @Binds
    @Singleton
    abstract fun bindGeofencePermissionSource(
        implementation: AndroidGeofencePermissionSource,
    ): GeofencePermissionSource

    @Binds
    @Singleton
    abstract fun bindGeofenceRetryScheduler(
        implementation: WorkManagerGeofenceRetryScheduler,
    ): GeofenceRetryScheduler

    @Binds
    @Singleton
    abstract fun bindGeofenceCoordinator(
        implementation: ReliableGeofenceCoordinator,
    ): GeofenceCoordinator
}
