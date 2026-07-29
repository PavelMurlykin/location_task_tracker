package ru.pavel.locationtasks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.data.GeofenceLogDao
import ru.pavel.locationtasks.data.GeofenceLogEntity
import ru.pavel.locationtasks.data.ReminderPreferences
import ru.pavel.locationtasks.data.UserPreferencesRepository
import ru.pavel.locationtasks.location.GeofenceCoordinator
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UserPreferencesRepository,
    logDao: GeofenceLogDao,
    private val geofenceCoordinator: GeofenceCoordinator,
) : ViewModel() {
    val reminderPreferences: StateFlow<ReminderPreferences> =
        repository.reminderPreferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReminderPreferences(),
    )
    val geofenceLogs: StateFlow<List<GeofenceLogEntity>> = logDao.observeRecent(20).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    private val _isCheckingGeofences = MutableStateFlow(false)
    val isCheckingGeofences: StateFlow<Boolean> = _isCheckingGeofences.asStateFlow()

    fun setCooldownHours(hours: Int) {
        viewModelScope.launch { repository.setNotificationCooldownHours(hours) }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setQuietHoursEnabled(enabled) }
    }

    fun setQuietHours(startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch { repository.setQuietHours(startMinutes, endMinutes) }
    }

    fun checkGeofences() {
        if (_isCheckingGeofences.value) return
        viewModelScope.launch {
            _isCheckingGeofences.value = true
            try {
                geofenceCoordinator.reconcileAll()
            } finally {
                _isCheckingGeofences.value = false
            }
        }
    }
}
