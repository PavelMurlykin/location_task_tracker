package ru.pavel.locationtasks.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

data class LocationPermissionState(
    val preciseLocation: Boolean,
    val backgroundLocation: Boolean,
    val notifications: Boolean,
) {
    val canRegisterGeofences: Boolean
        get() = preciseLocation && backgroundLocation

    companion object {
        fun from(context: Context): LocationPermissionState {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
            val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

            return LocationPermissionState(fine, background, notifications)
        }
    }
}
