package ru.pavel.locationtasks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.receiveAsFlow
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.analytics.ProductMetrics
import ru.pavel.locationtasks.analytics.ProductMetricsRepository
import ru.pavel.locationtasks.data.GeofenceLogDao
import ru.pavel.locationtasks.data.GeofenceLogEntity
import ru.pavel.locationtasks.data.ReminderPreferences
import ru.pavel.locationtasks.data.SecurityPreferences
import ru.pavel.locationtasks.data.ProductPreferences
import ru.pavel.locationtasks.data.AppThemeMode
import ru.pavel.locationtasks.data.UserPreferencesRepository
import ru.pavel.locationtasks.data.backup.BackupOperationException
import ru.pavel.locationtasks.data.backup.BackupOperationFailure
import ru.pavel.locationtasks.data.backup.DataBackupRepository
import ru.pavel.locationtasks.location.GeofenceCoordinator
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: UserPreferencesRepository,
    logDao: GeofenceLogDao,
    private val geofenceCoordinator: GeofenceCoordinator,
    private val backupRepository: DataBackupRepository,
    metricsRepository: ProductMetricsRepository,
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
    val securityPreferences: StateFlow<SecurityPreferences> =
        repository.securityPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SecurityPreferences(),
        )
    val productPreferences: StateFlow<ProductPreferences> =
        repository.productPreferences.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProductPreferences(),
        )
    val productMetrics: StateFlow<ProductMetrics> = metricsRepository.metrics.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ProductMetrics(),
    )
    private val _isCheckingGeofences = MutableStateFlow(false)
    val isCheckingGeofences: StateFlow<Boolean> = _isCheckingGeofences.asStateFlow()
    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun setCooldownHours(hours: Int) {
        viewModelScope.launch { repository.setNotificationCooldownHours(hours) }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setQuietHoursEnabled(enabled) }
    }

    fun setQuietHours(startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch { repository.setQuietHours(startMinutes, endMinutes) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAppLockEnabled(enabled) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setAnalyticsConsent(consent: Boolean) {
        viewModelScope.launch { repository.setAnalyticsConsent(consent) }
    }

    fun exportBackup(uri: Uri, password: String) {
        runBackupOperation(
            operation = { backupRepository.exportTo(uri, password.toCharArray()) },
            successMessage = R.string.backup_export_success,
        )
    }

    fun importBackup(uri: Uri, password: String) {
        runBackupOperation(
            operation = { backupRepository.importFrom(uri, password.toCharArray()) },
            successMessage = R.string.backup_import_success,
        )
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

    private fun runBackupOperation(
        operation: suspend () -> Any,
        @StringRes successMessage: Int,
    ) {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                operation()
                _events.send(SettingsEvent(successMessage))
            } catch (exception: BackupOperationException) {
                _events.send(
                    SettingsEvent(
                        when (exception.failure) {
                            BackupOperationFailure.WRONG_PASSWORD ->
                                R.string.backup_error_password
                            BackupOperationFailure.UNSUPPORTED_VERSION ->
                                R.string.backup_error_version
                            BackupOperationFailure.INVALID_FILE -> R.string.backup_error_invalid
                            BackupOperationFailure.STORAGE_ERROR -> R.string.backup_error_storage
                        },
                    ),
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }
}

data class SettingsEvent(@param:StringRes val messageRes: Int)
