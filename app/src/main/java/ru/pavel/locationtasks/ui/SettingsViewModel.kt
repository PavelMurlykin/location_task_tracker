package ru.pavel.locationtasks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.data.UserPreferencesRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UserPreferencesRepository,
) : ViewModel() {
    val cooldownHours: StateFlow<Int> = repository.notificationCooldownHours.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserPreferencesRepository.DEFAULT_COOLDOWN_HOURS,
    )

    fun setCooldownHours(hours: Int) {
        viewModelScope.launch { repository.setNotificationCooldownHours(hours) }
    }
}
