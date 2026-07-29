package ru.pavel.locationtasks.location

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

data class BackgroundExecutionState(
    val batteryOptimizationsIgnored: Boolean,
    val backgroundRestricted: Boolean,
) {
    val mayDelayGeofences: Boolean
        get() = !batteryOptimizationsIgnored || backgroundRestricted

    companion object {
        fun from(context: Context): BackgroundExecutionState {
            val powerManager = context.getSystemService(PowerManager::class.java)
            val activityManager = context.getSystemService(ActivityManager::class.java)
            return BackgroundExecutionState(
                batteryOptimizationsIgnored = powerManager
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true,
                backgroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    activityManager?.isBackgroundRestricted == true,
            )
        }
    }
}
