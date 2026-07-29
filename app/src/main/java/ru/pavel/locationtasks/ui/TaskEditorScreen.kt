package ru.pavel.locationtasks.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.pavel.locationtasks.data.GeofenceStatus
import ru.pavel.locationtasks.data.GeofenceTransitionMode
import ru.pavel.locationtasks.data.TaskPriority
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.location.BackgroundExecutionState
import ru.pavel.locationtasks.location.LocationPermissionState
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    onClose: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val recentPlaces by viewModel.recentPlaces.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showDueTimePicker by remember { mutableStateOf(false) }
    var showWindowStartPicker by remember { mutableStateOf(false) }
    var showWindowEndPicker by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { onClose() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isExisting) R.string.edit_task_title else R.string.new_task_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (state.isExisting) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.task_title_label)) },
                    singleLine = true,
                    isError = state.validationMessageRes == R.string.validation_title_required,
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.task_description_label)) },
                    minLines = 3,
                    maxLines = 7,
                )

                Text(
                    stringResource(R.string.priority_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TaskPriority.entries.forEach { priority ->
                        FilterChip(
                            selected = state.priority == priority,
                            onClick = { viewModel.setPriority(priority) },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    stringResource(
                                        when (priority) {
                                            TaskPriority.LOW -> R.string.priority_low
                                            TaskPriority.NORMAL -> R.string.priority_normal
                                            TaskPriority.HIGH -> R.string.priority_high
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            state.dueAt?.let(::formatEditorDate)
                                ?: stringResource(R.string.task_set_due_date),
                        )
                    }
                    state.dueAt?.let { dueAt ->
                        OutlinedButton(onClick = { showDueTimePicker = true }) {
                            Text(formatTime(instantMinutesOfDay(dueAt)))
                        }
                        TextButton(onClick = { viewModel.setDueAt(null) }) {
                            Text(stringResource(R.string.common_reset))
                        }
                    }
                }
                Text(
                    stringResource(R.string.due_reminder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()
                Text(
                    stringResource(R.string.location_reminder_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                if (state.hasLocation) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.size(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        state.address.ifBlank {
                                            stringResource(R.string.selected_point)
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.location_summary,
                                            formatCoordinate(state.latitude),
                                            formatCoordinate(state.longitude),
                                            state.radiusMeters.toInt(),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { showLocationPicker = true },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.EditLocation, contentDescription = null)
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(R.string.common_edit))
                                }
                                OutlinedButton(onClick = viewModel::clearLocation) {
                                    Icon(
                                        Icons.Default.LocationOff,
                                        contentDescription = stringResource(R.string.remove_location),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showLocationPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.choose_location))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.geofence_title),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.geofence_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.geofenceEnabled,
                        onCheckedChange = viewModel::setGeofenceEnabled,
                        enabled = state.hasLocation,
                    )
                }

                if (state.geofenceEnabled) {
                    Text(
                        stringResource(R.string.transition_mode_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GeofenceTransitionMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.transitionMode == mode,
                                onClick = { viewModel.setTransitionMode(mode) },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        stringResource(
                                            when (mode) {
                                                GeofenceTransitionMode.ENTER ->
                                                    R.string.transition_enter
                                                GeofenceTransitionMode.EXIT ->
                                                    R.string.transition_exit
                                                GeofenceTransitionMode.BOTH ->
                                                    R.string.transition_both
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.task_cooldown_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        listOf<Int?>(null, 60, 240, 720, 1_440).forEach { minutes ->
                            FilterChip(
                                selected = state.notificationCooldownMinutes == minutes,
                                onClick = {
                                    viewModel.setNotificationCooldownMinutes(minutes)
                                },
                                label = {
                                    Text(
                                        if (minutes == null) {
                                            stringResource(R.string.cooldown_default)
                                        } else {
                                            stringResource(R.string.cooldown_hours, minutes / 60)
                                        },
                                    )
                                },
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.allowed_days_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            val bit = 1 shl (day.value - 1)
                            FilterChip(
                                selected = state.allowedDaysMask and bit != 0,
                                onClick = { viewModel.toggleAllowedDay(bit) },
                                label = { Text(stringResource(dayShortLabel(day))) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.reminder_window_title))
                            Text(
                                stringResource(R.string.reminder_window_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.reminderWindowStartMinutes != null,
                            onCheckedChange = viewModel::setReminderWindowEnabled,
                        )
                    }
                    state.reminderWindowStartMinutes?.let { startMinutes ->
                        state.reminderWindowEndMinutes?.let { endMinutes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { showWindowStartPicker = true },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.reminder_window_from,
                                            formatTime(startMinutes),
                                        ),
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showWindowEndPicker = true },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.reminder_window_to,
                                            formatTime(endMinutes),
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    PermissionCard(
                        geofenceStatus = state.geofenceStatus,
                        geofenceStatusDetails = state.geofenceStatusDetails,
                    )
                }

                if (state.isExisting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.task_completed_label),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.isCompleted,
                            onCheckedChange = viewModel::setCompleted,
                        )
                    }
                }

                state.validationMessageRes?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !state.isSaving,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.common_save))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.dueAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let(viewModel::setDueDate)
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.common_done)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    state.dueAt?.takeIf { showDueTimePicker }?.let { dueAt ->
        ReminderTimePickerDialog(
            initialMinutes = instantMinutesOfDay(dueAt),
            onDismiss = { showDueTimePicker = false },
            onConfirm = {
                viewModel.setDueTime(it)
                showDueTimePicker = false
            },
        )
    }

    if (showWindowStartPicker) {
        ReminderTimePickerDialog(
            initialMinutes = state.reminderWindowStartMinutes
                ?: ru.pavel.locationtasks.notifications.ReminderSchedule
                    .DEFAULT_WINDOW_START_MINUTES,
            onDismiss = { showWindowStartPicker = false },
            onConfirm = {
                viewModel.setReminderWindowStart(it)
                showWindowStartPicker = false
            },
        )
    }

    if (showWindowEndPicker) {
        ReminderTimePickerDialog(
            initialMinutes = state.reminderWindowEndMinutes
                ?: ru.pavel.locationtasks.notifications.ReminderSchedule
                    .DEFAULT_WINDOW_END_MINUTES,
            onDismiss = { showWindowEndPicker = false },
            onConfirm = {
                viewModel.setReminderWindowEnd(it)
                showWindowEndPicker = false
            },
        )
    }

    if (showLocationPicker) {
        LocationPickerDialog(
            initialLatitude = state.latitude,
            initialLongitude = state.longitude,
            initialAddress = state.address,
            initialRadius = state.radiusMeters,
            savedPlaces = savedPlaces,
            recentPlaces = recentPlaces,
            onSearch = viewModel::searchLocation,
            onReverse = viewModel::reverseLocation,
            onSavePlace = viewModel::savePlace,
            onDismiss = { showLocationPicker = false },
            onConfirm = { latitude, longitude, address, radius ->
                viewModel.setRadius(radius)
                viewModel.setLocation(latitude, longitude, address)
                showLocationPicker = false
            },
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_task_title)) },
            text = { Text(stringResource(R.string.delete_task_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::delete) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun PermissionCard(
    geofenceStatus: GeofenceStatus,
    geofenceStatusDetails: String?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissions by remember { mutableStateOf(LocationPermissionState.from(context)) }
    var backgroundState by remember { mutableStateOf(BackgroundExecutionState.from(context)) }
    var showBackgroundDisclosure by remember { mutableStateOf(false) }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions = LocationPermissionState.from(context) }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissions = LocationPermissionState.from(context) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissions = LocationPermissionState.from(context) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = LocationPermissionState.from(context)
                backgroundState = BackgroundExecutionState.from(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.geofence_state_title),
                fontWeight = FontWeight.SemiBold,
            )
            GeofenceStateRow(
                status = when {
                    !permissions.canRegisterGeofences || !permissions.notifications ->
                        GeofenceStatus.MISSING_PERMISSION
                    else -> geofenceStatus
                },
                details = geofenceStatusDetails,
            )
            HorizontalDivider()
            Text(stringResource(R.string.permissions_title), fontWeight = FontWeight.SemiBold)
            PermissionRow(
                stringResource(R.string.permission_precise_location),
                permissions.preciseLocation,
            )
            PermissionRow(
                stringResource(R.string.permission_background_location),
                permissions.backgroundLocation,
            )
            PermissionRow(
                stringResource(R.string.permission_notifications),
                permissions.notifications,
            )

            when {
                !permissions.preciseLocation -> Button(
                    onClick = {
                        foregroundLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.allow_location)) }

                !permissions.backgroundLocation -> Button(
                    onClick = { showBackgroundDisclosure = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.allow_background_work)) }

                !permissions.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    Button(
                        onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.allow_notifications)) }

                else -> Text(
                    stringResource(R.string.geofence_ready),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (permissions.canRegisterGeofences && backgroundState.mayDelayGeofences) {
                Text(
                    if (backgroundState.backgroundRestricted) {
                        stringResource(R.string.background_restricted_warning)
                    } else {
                        stringResource(R.string.battery_optimization_warning)
                    },
                    color = Color(0xFF9A6700),
                    style = MaterialTheme.typography.bodySmall,
                )
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
                    Text(stringResource(R.string.configure_battery_optimization))
                }
            }
        }
    }

    if (showBackgroundDisclosure) {
        AlertDialog(
            onDismissRequest = { showBackgroundDisclosure = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.background_location_title)) },
            text = {
                Text(
                    stringResource(R.string.background_location_disclosure),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackgroundDisclosure = false
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                },
                            )
                        }
                    },
                ) { Text(stringResource(R.string.common_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundDisclosure = false }) {
                    Text(stringResource(R.string.common_not_now))
                }
            },
        )
    }
}

@Composable
private fun GeofenceStateRow(
    status: GeofenceStatus,
    details: String?,
) {
    val (labelRes, color) = when (status) {
        GeofenceStatus.ACTIVE ->
            R.string.geofence_state_active to MaterialTheme.colorScheme.primary
        GeofenceStatus.PENDING ->
            R.string.geofence_state_pending to MaterialTheme.colorScheme.onSurfaceVariant
        GeofenceStatus.MISSING_PERMISSION ->
            R.string.geofence_state_missing_permissions to MaterialTheme.colorScheme.error
        GeofenceStatus.LIMIT_REACHED ->
            R.string.geofence_state_limit to MaterialTheme.colorScheme.error
        GeofenceStatus.ERROR ->
            R.string.geofence_state_error to MaterialTheme.colorScheme.error
        GeofenceStatus.DISABLED ->
            R.string.geofence_state_disabled to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(labelRes), color = color, style = MaterialTheme.typography.bodyMedium)
        if (status == GeofenceStatus.ERROR && !details.isNullOrBlank()) {
            Text(
                localizedGeofenceDetails(details),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun localizedGeofenceDetails(details: String): String = when {
    details == "INVALID_TASK" -> stringResource(R.string.geofence_invalid_task)
    details.startsWith("RETRY_SCHEDULED|") -> stringResource(
        R.string.geofence_retry_scheduled_detail,
        details.substringAfter('|'),
    )
    else -> details
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (granted) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (granted) MaterialTheme.colorScheme.primary else Color(0xFF9A6700),
        )
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatEditorDate(timestamp: Long): String = DateTimeFormatter
    .ofPattern("dd.MM.yyyy")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(timestamp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_time)) },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) {
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

private fun instantMinutesOfDay(timestamp: Long): Int {
    val time = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
    return time.hour * 60 + time.minute
}

private fun formatTime(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

private fun dayShortLabel(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.day_mon
    DayOfWeek.TUESDAY -> R.string.day_tue
    DayOfWeek.WEDNESDAY -> R.string.day_wed
    DayOfWeek.THURSDAY -> R.string.day_thu
    DayOfWeek.FRIDAY -> R.string.day_fri
    DayOfWeek.SATURDAY -> R.string.day_sat
    DayOfWeek.SUNDAY -> R.string.day_sun
}

private fun formatCoordinate(value: Double?): String =
    value?.let { "%.5f".format(it) } ?: "—"
