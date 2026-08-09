package com.pamurlykin.locationtasks.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.pamurlykin.locationtasks.analytics.ConsentAwareProductTelemetry
import com.pamurlykin.locationtasks.analytics.ProductTelemetry
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {
    @Binds
    @Singleton
    abstract fun bindProductTelemetry(
        implementation: ConsentAwareProductTelemetry,
    ): ProductTelemetry
}
