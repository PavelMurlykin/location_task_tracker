package ru.pavel.locationtasks.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import ru.pavel.locationtasks.data.GeofenceLogEntity
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.data.UserPreferencesRepository
import ru.pavel.locationtasks.location.BackgroundExecutionState
import ru.pavel.locationtasks.location.LocationPermissionState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.data.backup.BackupCodec
import ru.pavel.locationtasks.BuildConfig
import ru.pavel.locationtasks.analytics.ProductMetrics
import ru.pavel.locationtasks.data.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val reminderPreferences by viewModel.reminderPreferences.collectAsStateWithLifecycle()
    val geofenceLogs by viewModel.geofenceLogs.collectAsStateWithLifecycle()
    val isCheckingGeofences by viewModel.isCheckingGeofences.collectAsStateWithLifecycle()
    val securityPreferences by viewModel.securityPreferences.collectAsStateWithLifecycle()
    val productPreferences by viewModel.productPreferences.collectAsStateWithLifecycle()
    val productMetrics by viewModel.productMetrics.collectAsStateWithLifecycle()
    val isBackupBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissions by remember { mutableStateOf(LocationPermissionState.from(context)) }
    var backgroundState by remember { mutableStateOf(BackgroundExecutionState.from(context)) }
    var showQuietStartPicker by remember { mutableStateOf(false) }
    var showQuietEndPicker by remember { mutableStateOf(false) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
    ) { uri ->
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri != null && password != null) viewModel.exportBackup(uri, password)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        pendingImportUri = uri
    }

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

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(context.getString(event.messageRes))
        }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.appearance_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.appearance_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = productPreferences.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(stringResource(mode.labelRes())) },
                            )
                        }
                    }
                }
            }

            Card(
                onClick = onOpenCategories,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            stringResource(R.string.categories_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.categories_settings_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.categories_open),
                    )
                }
            }

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
                        selected = reminderPreferences.notificationCooldownHours == hours,
                        onClick = { viewModel.setCooldownHours(hours) },
                        label = { Text(stringResource(R.string.cooldown_hours, hours)) },
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) { },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.quiet_hours_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.quiet_hours_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = reminderPreferences.quietHoursEnabled,
                            onCheckedChange = viewModel::setQuietHoursEnabled,
                        )
                    }
                    if (reminderPreferences.quietHoursEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { showQuietStartPicker = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.quiet_hours_from,
                                        formatSettingsTime(
                                            reminderPreferences.quietHoursStartMinutes,
                                        ),
                                    ),
                                )
                            }
                            OutlinedButton(
                                onClick = { showQuietEndPicker = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.quiet_hours_to,
                                        formatSettingsTime(
                                            reminderPreferences.quietHoursEndMinutes,
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) { },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .weight(1f),
                        ) {
                            Text(
                                stringResource(R.string.data_protection_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.app_lock_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = securityPreferences.appLockEnabled,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    viewModel.setAppLockEnabled(false)
                                } else {
                                    val activity = context as? FragmentActivity
                                    if (activity == null || !canUseDeviceAuthentication(context)) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.device_lock_required),
                                            )
                                        }
                                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                    } else {
                                        requestDeviceAuthentication(activity) {
                                            viewModel.setAppLockEnabled(true)
                                        }
                                    }
                                }
                            },
                        )
                    }
                    Text(
                        stringResource(R.string.backup_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isBackupBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(
                        onClick = { showExportPasswordDialog = true },
                        enabled = !isBackupBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null)
                        Text(
                            stringResource(R.string.backup_export),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "*/*"))
                        },
                        enabled = !isBackupBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Text(
                            stringResource(R.string.backup_import),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
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

            AnalyticsCard(
                consent = productPreferences.analyticsConsent,
                metrics = productMetrics,
                onConsentChange = viewModel::setAnalyticsConsent,
            )

            Text(
                stringResource(R.string.local_storage_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenPrivacy) {
                Text(stringResource(R.string.privacy_policy))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            }
            YandexMapsTermsLink()
        }
    }

    if (showQuietStartPicker) {
        SettingsTimePickerDialog(
            initialMinutes = reminderPreferences.quietHoursStartMinutes,
            onDismiss = { showQuietStartPicker = false },
            onConfirm = {
                viewModel.setQuietHours(it, reminderPreferences.quietHoursEndMinutes)
                showQuietStartPicker = false
            },
        )
    }
    if (showQuietEndPicker) {
        SettingsTimePickerDialog(
            initialMinutes = reminderPreferences.quietHoursEndMinutes,
            onDismiss = { showQuietEndPicker = false },
            onConfirm = {
                viewModel.setQuietHours(reminderPreferences.quietHoursStartMinutes, it)
                showQuietEndPicker = false
            },
        )
    }
    if (showExportPasswordDialog) {
        BackupPasswordDialog(
            restore = false,
            onDismiss = { showExportPasswordDialog = false },
            onConfirm = { password ->
                showExportPasswordDialog = false
                pendingExportPassword = password
                exportLauncher.launch("nearby-tasks-${LocalDate.now()}.$BACKUP_EXTENSION")
            },
        )
    }
    pendingImportUri?.let { uri ->
        BackupPasswordDialog(
            restore = true,
            onDismiss = { pendingImportUri = null },
            onConfirm = { password ->
                pendingImportUri = null
                viewModel.importBackup(uri, password)
            },
        )
    }
}

@Composable
private fun AnalyticsCard(
    consent: Boolean,
    metrics: ProductMetrics,
    onConsentChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.analytics_consent_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.analytics_settings_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = consent, onCheckedChange = onConsentChange)
            }
            Text(
                stringResource(
                    if (BuildConfig.SENTRY_CONFIGURED || BuildConfig.POSTHOG_CONFIGURED) {
                        R.string.analytics_provider_configured
                    } else {
                        R.string.analytics_local_only
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (consent) {
                MetricRow(
                    stringResource(R.string.metric_geofence_registration),
                    formatMetricRate(
                        metrics.geofenceRegistrationSuccessRate,
                        metrics.geofenceRegistrationSuccesses,
                        metrics.geofenceRegistrationAttempts,
                    ),
                )
                MetricRow(
                    stringResource(R.string.metric_reminder_delivery),
                    formatMetricRate(
                        metrics.reminderDeliveryRate,
                        metrics.remindersDelivered,
                        metrics.geofenceTriggers,
                    ),
                )
                MetricRow(
                    stringResource(R.string.metric_notification_completion),
                    formatMetricRate(
                        metrics.notificationCompletionRate,
                        metrics.notificationCompletions,
                        metrics.notificationsShown,
                    ),
                )
                MetricRow(
                    stringResource(R.string.metric_retention),
                    stringResource(
                        R.string.metric_retention_value,
                        pluralStringResource(
                            R.plurals.metric_active_days,
                            metrics.activeDays,
                            metrics.activeDays,
                        ),
                        if (metrics.retainedDayOne) stringResource(R.string.common_yes) else stringResource(R.string.common_no),
                        if (metrics.retainedDaySeven) stringResource(R.string.common_yes) else stringResource(R.string.common_no),
                    ),
                )
            }
            Text(
                stringResource(R.string.analytics_no_personal_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}

private fun AppThemeMode.labelRes(): Int = when (this) {
    AppThemeMode.SYSTEM -> R.string.theme_system
    AppThemeMode.LIGHT -> R.string.theme_light
    AppThemeMode.DARK -> R.string.theme_dark
}

private fun formatMetricRate(rate: Double?, value: Int, total: Int): String =
    if (rate == null) "—" else "%.0f%% · %d/%d".format(rate * 100, value, total)

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
                    GeofenceLogEntity.OUTCOME_DEFERRED,
                    GeofenceLogEntity.OUTCOME_NEXT_VISIT,
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
    details == "ENTER" -> stringResource(R.string.transition_enter)
    details == "EXIT" -> stringResource(R.string.transition_exit)
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
    GeofenceLogEntity.OUTCOME_DEFERRED -> R.string.log_reminder_deferred
    GeofenceLogEntity.OUTCOME_NEXT_VISIT -> R.string.log_next_visit
    GeofenceLogEntity.OUTCOME_NOTIFICATIONS_BLOCKED ->
        R.string.log_notifications_blocked
    GeofenceLogEntity.OUTCOME_TASK_INACTIVE -> R.string.log_task_inactive
    else -> R.string.log_registration_error
}

private val LOG_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter
    .ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.common_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private fun formatSettingsTime(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
private fun BackupPasswordDialog(
    restore: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showValidation by remember { mutableStateOf(false) }
    val valid = password.length >= BackupCodec.MIN_PASSWORD_LENGTH &&
        (restore || password == confirmation)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (restore) R.string.backup_restore_title else R.string.backup_password_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        if (restore) {
                            R.string.backup_restore_warning
                        } else {
                            R.string.backup_password_explanation
                        },
                    ),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        showValidation = false
                    },
                    label = { Text(stringResource(R.string.backup_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = showValidation && password.length < BackupCodec.MIN_PASSWORD_LENGTH,
                )
                if (!restore) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = {
                            confirmation = it
                            showValidation = false
                        },
                        label = { Text(stringResource(R.string.backup_password_confirm)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = showValidation && password != confirmation,
                    )
                }
                if (showValidation && !valid) {
                    Text(
                        stringResource(
                            if (password.length < BackupCodec.MIN_PASSWORD_LENGTH) {
                                R.string.backup_password_too_short
                            } else {
                                R.string.backup_passwords_mismatch
                            },
                            BackupCodec.MIN_PASSWORD_LENGTH,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid) onConfirm(password) else showValidation = true
                },
            ) {
                Text(
                    stringResource(
                        if (restore) R.string.backup_restore_confirm else R.string.backup_export,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private const val BACKUP_MIME_TYPE = "application/octet-stream"
private const val BACKUP_EXTENSION = "ltbackup"
