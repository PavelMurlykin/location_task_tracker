package ru.pavel.locationtasks.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepository @Inject constructor(
    private val placeDao: PlaceDao,
) {
    val savedPlaces: Flow<List<PlaceEntity>> = placeDao.observeSaved()
    val recentPlaces: Flow<List<PlaceEntity>> = placeDao.observeRecent(8)

    suspend fun savePlace(
        name: String,
        address: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        now: Long = System.currentTimeMillis(),
    ) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty())
        val existingByName = placeDao.getByName(normalizedName)
        val existingByCoordinates = placeDao.getByCoordinates(latitude, longitude)
        if (existingByName != null &&
            existingByCoordinates != null &&
            existingByName.id != existingByCoordinates.id
        ) {
            placeDao.deleteById(existingByCoordinates.id)
        }
        val existing = existingByName ?: existingByCoordinates
        val place = PlaceEntity(
            id = existing?.id ?: 0,
            name = normalizedName,
            address = address.trim(),
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            lastUsedAt = now,
            createdAt = existing?.createdAt ?: now,
        )
        if (place.id == 0L) {
            val insertedId = placeDao.insert(place)
            if (insertedId < 0) {
                (placeDao.getByName(normalizedName)
                    ?: placeDao.getByCoordinates(latitude, longitude))
                    ?.let { placeDao.update(place.copy(id = it.id, createdAt = it.createdAt)) }
            }
        } else {
            placeDao.update(place)
        }
    }

    suspend fun recordUsed(
        address: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
        now: Long = System.currentTimeMillis(),
    ) {
        val existing = placeDao.getByCoordinates(latitude, longitude)
        val place = PlaceEntity(
            id = existing?.id ?: 0,
            name = existing?.name,
            address = address.trim(),
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            lastUsedAt = now,
            createdAt = existing?.createdAt ?: now,
        )
        if (place.id == 0L) {
            val insertedId = placeDao.insert(place)
            if (insertedId < 0) {
                placeDao.getByCoordinates(latitude, longitude)
                    ?.let { placeDao.update(place.copy(id = it.id, createdAt = it.createdAt)) }
            }
        } else {
            placeDao.update(place)
        }
    }
}
