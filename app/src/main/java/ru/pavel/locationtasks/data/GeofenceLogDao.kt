package ru.pavel.locationtasks.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceLogDao {
    @Query(
        """
        SELECT * FROM geofence_logs
        ORDER BY occurredAt DESC, id DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<GeofenceLogEntity>>

    @Insert
    suspend fun insert(entry: GeofenceLogEntity): Long

    @Query(
        """
        DELETE FROM geofence_logs
        WHERE id NOT IN (
            SELECT id FROM geofence_logs
            ORDER BY occurredAt DESC, id DESC
            LIMIT :entriesToKeep
        )
        """,
    )
    suspend fun trimToSize(entriesToKeep: Int)

    @Transaction
    suspend fun record(entry: GeofenceLogEntity) {
        insert(entry)
        trimToSize(GeofenceLogEntity.MAX_ENTRIES)
    }
}
