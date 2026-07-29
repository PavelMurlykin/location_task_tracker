package ru.pavel.locationtasks.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places WHERE name IS NOT NULL ORDER BY lastUsedAt DESC, name ASC")
    fun observeSaved(): Flow<List<PlaceEntity>>

    @Query(
        """
        SELECT * FROM places
        WHERE name IS NULL
        ORDER BY lastUsedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): PlaceEntity?

    @Query(
        """
        SELECT * FROM places
        WHERE latitude = :latitude AND longitude = :longitude
        LIMIT 1
        """,
    )
    suspend fun getByCoordinates(latitude: Double, longitude: Double): PlaceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(place: PlaceEntity): Long

    @Update
    suspend fun update(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deleteById(id: Long)
}
