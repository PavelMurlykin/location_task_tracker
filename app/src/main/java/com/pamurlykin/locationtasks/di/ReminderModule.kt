package com.pamurlykin.locationtasks.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.pamurlykin.locationtasks.notifications.ReminderWorkScheduler
import com.pamurlykin.locationtasks.notifications.WorkManagerReminderScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds
    @Singleton
    abstract fun bindReminderWorkScheduler(
        implementation: WorkManagerReminderScheduler,
    ): ReminderWorkScheduler
}
