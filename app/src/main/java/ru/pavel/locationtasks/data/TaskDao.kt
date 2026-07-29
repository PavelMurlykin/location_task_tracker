package ru.pavel.locationtasks.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueAt IS NULL, dueAt ASC, updatedAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query(
        """
        SELECT * FROM tasks
        WHERE geofenceEnabled = 1
          AND isCompleted = 0
          AND latitude IS NOT NULL
          AND longitude IS NOT NULL
        ORDER BY dueAt IS NULL, dueAt ASC, updatedAt DESC, id ASC
        """,
    )
    suspend fun getTasksToMonitor(): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE isCompleted = 0
          AND dueAt IS NOT NULL
        """,
    )
    suspend fun getTasksWithDueReminders(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = :completed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, updatedAt: Long)

    @Query(
        """
        UPDATE tasks
        SET lastNotifiedAt = :notifiedAt,
            lastNotifiedTransition = :transition
        WHERE id = :id
        """,
    )
    suspend fun setLastNotifiedAt(id: Long, notifiedAt: Long, transition: String?)

    @Query(
        """
        UPDATE tasks
        SET lastNotifiedAt = NULL,
            lastNotifiedTransition = NULL
        WHERE id = :id
        """,
    )
    suspend fun clearLastNotified(id: Long)

    @Query(
        """
        UPDATE tasks
        SET snoozedUntil = :snoozedUntil,
            skipUntilNextVisit = :skipUntilNextVisit
        WHERE id = :id
        """,
    )
    suspend fun setSnoozeState(
        id: Long,
        snoozedUntil: Long?,
        skipUntilNextVisit: Boolean,
    )

    @Query(
        """
        UPDATE tasks
        SET geofenceStatus = :status,
            geofenceStatusDetails = :details,
            geofenceRegisteredAt = :registeredAt
        WHERE id = :id
        """,
    )
    suspend fun setGeofenceStatus(
        id: Long,
        status: String,
        details: String?,
        registeredAt: Long?,
    )
}
