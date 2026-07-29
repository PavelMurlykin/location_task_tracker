package ru.pavel.locationtasks.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.pavel.locationtasks.testing.FakePlaceDao

class PlaceRepositoryTest {
    @Test
    fun `used address appears in recent history`() = runBlocking {
        val dao = FakePlaceDao()
        val repository = PlaceRepository(dao)

        repository.recordUsed(
            address = "Тверская улица",
            latitude = 55.76,
            longitude = 37.61,
            radiusMeters = 250f,
            now = 100L,
        )

        assertEquals("Тверская улица", repository.recentPlaces.first().single().address)
    }

    @Test
    fun `saving recent address converts it to named place without duplicate`() = runBlocking {
        val dao = FakePlaceDao(
            listOf(
                PlaceEntity(
                    id = 7,
                    address = "Домашний адрес",
                    latitude = 55.7,
                    longitude = 37.5,
                ),
            ),
        )
        val repository = PlaceRepository(dao)

        repository.savePlace(
            name = "Дом",
            address = "Домашний адрес",
            latitude = 55.7,
            longitude = 37.5,
            radiusMeters = 300f,
            now = 200L,
        )

        assertEquals(1, dao.snapshot().size)
        assertEquals("Дом", repository.savedPlaces.first().single().name)
        assertTrue(repository.recentPlaces.first().isEmpty())
    }
}
