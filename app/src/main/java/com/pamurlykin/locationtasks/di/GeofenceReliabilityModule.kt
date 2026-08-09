package com.pamurlykin.locationtasks.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.pamurlykin.locationtasks.location.AndroidGeofencePermissionSource
import com.pamurlykin.locationtasks.location.GeofenceCoordinator
import com.pamurlykin.locationtasks.location.GeofenceManager
import com.pamurlykin.locationtasks.location.GeofencePermissionSource
import com.pamurlykin.locationtasks.location.GeofencePlatform
import com.pamurlykin.locationtasks.location.GeofenceRetryScheduler
import com.pamurlykin.locationtasks.location.ReliableGeofenceCoordinator
import com.pamurlykin.locationtasks.location.WorkManagerGeofenceRetryScheduler
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
