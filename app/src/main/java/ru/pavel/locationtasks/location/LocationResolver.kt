package ru.pavel.locationtasks.location

import android.content.Context
import android.location.Geocoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
)

@Singleton
class LocationResolver @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val geocoder = Geocoder(context, Locale.getDefault())

    suspend fun search(query: String): List<ResolvedLocation> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !Geocoder.isPresent()) return@withContext emptyList()
        @Suppress("DEPRECATION")
        runCatching { geocoder.getFromLocationName(query, MAX_SEARCH_RESULTS).orEmpty() }
            .getOrNull()
            .orEmpty()
            .map { address ->
                ResolvedLocation(
                    latitude = address.latitude,
                    longitude = address.longitude,
                    address = address.getAddressLine(0) ?: query,
                )
            }
            .distinctBy { Triple(it.latitude, it.longitude, it.address) }
    }

    suspend fun reverse(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }
                .getOrNull()
                ?.getAddressLine(0)
        }

    companion object {
        private const val MAX_SEARCH_RESULTS = 5
    }
}
