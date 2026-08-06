package ru.pavel.locationtasks.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.pavel.locationtasks.data.AppDatabase
import ru.pavel.locationtasks.data.CategoryDao
import ru.pavel.locationtasks.data.GeofenceLogDao
import ru.pavel.locationtasks.data.GeofenceStatus
import ru.pavel.locationtasks.data.PlaceDao
import ru.pavel.locationtasks.data.TaskDao
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.data.UserPreferencesRepository
import ru.pavel.locationtasks.location.GeofenceCoordinator
import ru.pavel.locationtasks.notifications.ReminderWorkScheduler
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

enum class BackupOperationFailure {
    WRONG_PASSWORD,
    INVALID_FILE,
    UNSUPPORTED_VERSION,
    STORAGE_ERROR,
}

class BackupOperationException(
    val failure: BackupOperationFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

data class BackupOperationSummary(
    val taskCount: Int,
    val placeCount: Int,
)

@Singleton
class DataBackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val taskDao: TaskDao,
    private val placeDao: PlaceDao,
    private val categoryDao: CategoryDao,
    private val logDao: GeofenceLogDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val geofenceCoordinator: GeofenceCoordinator,
    private val reminderScheduler: ReminderWorkScheduler,
) {
    suspend fun exportTo(uri: Uri, password: CharArray): BackupOperationSummary =
        withContext(Dispatchers.IO) {
            try {
                val reminderPreferences = preferencesRepository.reminderPreferences.first()
                val snapshot = database.withTransaction {
                    BackupSnapshot(
                        createdAt = System.currentTimeMillis(),
                        reminderPreferences = reminderPreferences,
                        tasks = taskDao.getAllForBackup(),
                        places = placeDao.getAllForBackup(),
                        categories = categoryDao.getAllForBackup(),
                    )
                }
                val encoded = BackupCodec.encode(snapshot, password)
                try {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        output.write(encoded)
                        output.flush()
                    } ?: throw IOException("Cannot open backup destination")
                } finally {
                    encoded.fill(0)
                }
                BackupOperationSummary(snapshot.tasks.size, snapshot.places.size)
            } catch (exception: BackupOperationException) {
                throw exception
            } catch (exception: BackupCodecException) {
                throw exception.toOperationException()
            } catch (exception: IOException) {
                throw BackupOperationException(BackupOperationFailure.STORAGE_ERROR, exception)
            } finally {
                password.fill('\u0000')
            }
        }

    suspend fun importFrom(uri: Uri, password: CharArray): BackupOperationSummary =
        withContext(Dispatchers.IO) {
            var data: ByteArray? = null
            try {
                data = context.contentResolver.openInputStream(uri)?.use(::readLimited)
                    ?: throw IOException("Cannot open backup source")
                val snapshot = BackupCodec.decode(data, password)
                val importedTasks = snapshot.tasks.map { it.forRestore() }
                val previousTasks = taskDao.getAllForBackup()
                val previousPlaces = placeDao.getAllForBackup()
                val previousCategories = categoryDao.getAllForBackup()
                val previousPreferences = preferencesRepository.reminderPreferences.first()

                previousTasks.forEach { task ->
                    reminderScheduler.cancelAll(task.id)
                    geofenceCoordinator.deactivate(task.id)
                }
                try {
                    database.withTransaction {
                        logDao.deleteAll()
                        placeDao.deleteAll()
                        categoryDao.deleteAll()
                        taskDao.deleteAll()
                        categoryDao.insertAll(snapshot.categories)
                        taskDao.insertAll(importedTasks)
                        placeDao.insertAll(snapshot.places)
                    }
                    preferencesRepository.restoreReminderPreferences(snapshot.reminderPreferences)
                } catch (exception: Exception) {
                    database.withTransaction {
                        placeDao.deleteAll()
                        categoryDao.deleteAll()
                        taskDao.deleteAll()
                        categoryDao.insertAll(previousCategories)
                        taskDao.insertAll(previousTasks)
                        placeDao.insertAll(previousPlaces)
                    }
                    preferencesRepository.restoreReminderPreferences(previousPreferences)
                    restoreSchedulesAfterFailedImport()
                    throw exception
                }

                importedTasks.forEach(reminderScheduler::syncDueReminder)
                geofenceCoordinator.reconcileAll(force = true)
                BackupOperationSummary(importedTasks.size, snapshot.places.size)
            } catch (exception: BackupOperationException) {
                throw exception
            } catch (exception: BackupCodecException) {
                throw exception.toOperationException()
            } catch (exception: IOException) {
                throw BackupOperationException(BackupOperationFailure.STORAGE_ERROR, exception)
            } catch (exception: Exception) {
                throw BackupOperationException(BackupOperationFailure.INVALID_FILE, exception)
            } finally {
                data?.fill(0)
                password.fill('\u0000')
            }
        }

    private suspend fun restoreSchedulesAfterFailedImport() {
        taskDao.getAllForBackup().forEach(reminderScheduler::syncDueReminder)
        geofenceCoordinator.reconcileAll(force = true)
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > BackupCodec.MAX_BACKUP_FILE_BYTES) {
                throw BackupOperationException(BackupOperationFailure.INVALID_FILE)
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun TaskEntity.forRestore(): TaskEntity = copy(
        geofenceStatus = if (shouldMonitor) {
            GeofenceStatus.PENDING.name
        } else {
            GeofenceStatus.DISABLED.name
        },
        geofenceStatusDetails = null,
        geofenceRegisteredAt = null,
        lastNotifiedAt = null,
        lastNotifiedTransition = null,
    )

    private fun BackupCodecException.toOperationException() = BackupOperationException(
        failure = when (failure) {
            BackupCodecFailure.INVALID_PASSWORD -> BackupOperationFailure.WRONG_PASSWORD
            BackupCodecFailure.INVALID_FILE -> BackupOperationFailure.INVALID_FILE
            BackupCodecFailure.UNSUPPORTED_VERSION -> BackupOperationFailure.UNSUPPORTED_VERSION
        },
        cause = this,
    )
}
