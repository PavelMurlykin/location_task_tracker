package ru.pavel.locationtasks.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.pavel.locationtasks.data.GeofenceLogEntity
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.data.UserPreferencesRepository
import ru.pavel.locationtasks.location.BackgroundExecutionState
import ru.pavel.locationtasks.location.LocationPermissionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val cooldownHours by viewModel.cooldownHours.collectAsStateWithLifecycle()
    val geofenceLogs by viewModel.geofenceLogs.collectAsStateWithLifecycle()
    val isCheckingGeofences by viewModel.isCheckingGeofences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissions by remember { mutableStateOf(LocationPermissionState.from(context)) }
    var backgroundState by remember { mutableStateOf(BackgroundExecutionState.from(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = LocationPermissionState.from(context)
                backgroundState = BackgroundExecutionState.from(context)
                viewModel.checkGeofences()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.repeat_notifications_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.repeat_notifications_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserPreferencesRepository.ALLOWED_COOLDOWNS.sorted().forEach { hours ->
                    FilterChip(
                        selected = cooldownHours == hours,
                        onClick = { viewModel.setCooldownHours(hours) },
                        label = { Text(stringResource(R.string.cooldown_hours, hours)) },
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.geofence_reliability_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    SettingStatus(
                        stringResource(R.string.permission_precise_location),
                        permissions.preciseLocation,
                    )
                    SettingStatus(
                        stringResource(R.string.permission_background_location),
                        permissions.backgroundLocation,
                    )
                    SettingStatus(
                        stringResource(R.string.permission_notifications),
                        permissions.notifications,
                    )
                    SettingStatus(
                        stringResource(R.string.background_work_status),
                        !backgroundState.backgroundRestricted,
                        enabledText = stringResource(R.string.status_not_restricted),
                        disabledText = stringResource(R.string.status_restricted),
                    )
                    SettingStatus(
                        stringResource(R.string.battery_saving_status),
                        backgroundState.batteryOptimizationsIgnored,
                        enabledText = stringResource(R.string.status_unrestricted),
                        disabledText = stringResource(R.string.status_may_delay),
                    )
                    if (backgroundState.mayDelayGeofences) {
                        Text(
                            stringResource(R.string.system_may_delay_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                },
                            )
                        },
                    ) {
                        Text(stringResource(R.string.open_app_settings))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    }
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                                )
                            }.onFailure {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    },
                                )
                            }
                        },
                    ) {
                        Text(stringResource(R.string.open_battery_settings))
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    }
                    FilledTonalButton(
                        onClick = viewModel::checkGeofences,
                        enabled = !isCheckingGeofences,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (isCheckingGeofences) {
                                stringResource(R.string.checking_geofences)
                            } else {
                                stringResource(R.string.check_geofences)
                            },
                        )
                    }
                }
            }

            Text(
                stringResource(R.string.recent_geofence_events),
                style = MaterialTheme.typography.titleMedium,
            )
            if (geofenceLogs.isEmpty()) {
                Text(
                    stringResource(R.string.no_geofence_events),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                geofenceLogs.forEach { entry ->
                    GeofenceLogCard(entry)
                }
            }

            Text(
                stringResource(R.string.local_storage_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenPrivacy) {
                Text(stringResource(R.string.privacy_policy))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SettingStatus(
    label: String,
    enabled: Boolean,
    enabledText: String = stringResource(R.string.status_allowed),
    disabledText: String = stringResource(R.string.status_not_allowed),
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            if (enabled) enabledText else disabledText,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun GeofenceLogCard(entry: GeofenceLogEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(entry.taskTitle, style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(logOutcomeLabelRes(entry)),
                style = MaterialTheme.typography.bodyMedium,
                color = when (entry.outcome) {
                    GeofenceLogEntity.OUTCOME_ACTIVE,
                    GeofenceLogEntity.OUTCOME_NOTIFIED,
                    -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                },
            )
            entry.details?.takeIf(String::isNotBlank)?.let {
                Text(
                    localizedLogDetails(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                LOG_DATE_FORMAT.format(Instant.ofEpochMilli(entry.occurredAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun localizedLogDetails(details: String): String = when {
    details == "INVALID_TASK" -> stringResource(R.string.geofence_invalid_task)
    details.startsWith("RETRY_SCHEDULED|") -> stringResource(
        R.string.geofence_retry_scheduled_detail,
        details.substringAfter('|'),
    )
    details.startsWith("MISSING_") -> stringResource(R.string.log_missing_permissions)
    else -> details
}

private fun logOutcomeLabelRes(entry: GeofenceLogEntity): Int = when (entry.outcome) {
    GeofenceLogEntity.OUTCOME_ACTIVE ->
        if (entry.event == GeofenceLogEntity.EVENT_RESTORE) {
            R.string.log_geofence_restored
        } else {
            R.string.log_geofence_registered
        }
    GeofenceLogEntity.OUTCOME_ERROR -> R.string.log_registration_error
    GeofenceLogEntity.OUTCOME_MISSING_PERMISSION -> R.string.log_missing_permissions
    GeofenceLogEntity.OUTCOME_LIMIT_REACHED -> R.string.log_limit_reached
    GeofenceLogEntity.OUTCOME_NOTIFIED -> R.string.log_notification_shown
    GeofenceLogEntity.OUTCOME_COOLDOWN -> R.string.log_cooldown
    GeofenceLogEntity.OUTCOME_NOTIFICATIONS_BLOCKED ->
        R.string.log_notifications_blocked
    GeofenceLogEntity.OUTCOME_TASK_INACTIVE -> R.string.log_task_inactive
    else -> R.string.log_registration_error
}

private val LOG_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter
    .ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())
