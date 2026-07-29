package ru.pavel.locationtasks.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.pavel.locationtasks.notifications.ReminderWorkScheduler
import ru.pavel.locationtasks.notifications.WorkManagerReminderScheduler
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
