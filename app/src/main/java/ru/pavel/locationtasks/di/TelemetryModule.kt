package ru.pavel.locationtasks.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.pavel.locationtasks.analytics.ConsentAwareProductTelemetry
import ru.pavel.locationtasks.analytics.ProductTelemetry
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
