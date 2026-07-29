package ru.pavel.locationtasks.location

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidGeofencePermissionSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : GeofencePermissionSource {
    override fun current(): LocationPermissionState = LocationPermissionState.from(context)
}
