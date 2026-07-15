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
        """,
    )
    suspend fun getTasksToMonitor(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("UPDATE tasks SET isCompleted = :completed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, updatedAt: Long)

    @Query("UPDATE tasks SET lastNotifiedAt = :notifiedAt WHERE id = :id")
    suspend fun setLastNotifiedAt(id: Long, notifiedAt: Long)
}
