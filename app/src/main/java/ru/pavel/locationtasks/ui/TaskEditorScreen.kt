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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.pavel.locationtasks.location.LocationPermissionState
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
    var showDatePicker by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { onClose() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isExisting) "Задача" else "Новая задача") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (state.isExisting) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
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
                    label = { Text("Название") },
                    singleLine = true,
                    isError = state.validationMessage?.contains("название", ignoreCase = true) == true,
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Описание") },
                    minLines = 3,
                    maxLines = 7,
                )

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
                        Text(state.dueAt?.let(::formatEditorDate) ?: "Указать срок")
                    }
                    if (state.dueAt != null) {
                        TextButton(onClick = { viewModel.setDueAt(null) }) { Text("Сбросить") }
                    }
                }

                HorizontalDivider()
                Text(
                    "Напоминание по месту",
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
                                        state.address.ifBlank { "Выбранная точка" },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        "${formatCoordinate(state.latitude)}, ${formatCoordinate(state.longitude)} · ${state.radiusMeters.toInt()} м",
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
                                    Text("Изменить")
                                }
                                OutlinedButton(onClick = viewModel::clearLocation) {
                                    Icon(Icons.Default.LocationOff, contentDescription = "Удалить место")
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
                        Text("Выбрать место")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Геонапоминание", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Сообщить, когда вы окажетесь рядом",
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
                    PermissionCard()
                }

                if (state.isExisting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Задача выполнена", modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.isCompleted,
                            onCheckedChange = viewModel::setCompleted,
                        )
                    }
                }

                state.validationMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
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
                        Text("Сохранить")
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
                        viewModel.setDueAt(datePickerState.selectedDateMillis)
                        showDatePicker = false
                    },
                ) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showLocationPicker) {
        LocationPickerDialog(
            initialLatitude = state.latitude,
            initialLongitude = state.longitude,
            initialAddress = state.address,
            initialRadius = state.radiusMeters,
            onSearch = viewModel::searchLocation,
            onReverse = viewModel::reverseLocation,
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
            title = { Text("Удалить задачу?") },
            text = { Text("Задача и её геонапоминание будут удалены.") },
            confirmButton = {
                TextButton(onClick = viewModel::delete) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissions by remember { mutableStateOf(LocationPermissionState.from(context)) }
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
            Text("Разрешения", fontWeight = FontWeight.SemiBold)
            PermissionRow("Точная геопозиция", permissions.preciseLocation)
            PermissionRow("Геопозиция в фоне", permissions.backgroundLocation)
            PermissionRow("Уведомления", permissions.notifications)

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
                ) { Text("Разрешить геопозицию") }

                !permissions.backgroundLocation -> Button(
                    onClick = { showBackgroundDisclosure = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Разрешить работу в фоне") }

                !permissions.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    Button(
                        onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Разрешить уведомления") }

                else -> Text(
                    "Геонапоминание готово к работе",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showBackgroundDisclosure) {
        AlertDialog(
            onDismissRequest = { showBackgroundDisclosure = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Фоновая геопозиция") },
            text = {
                Text(
                    "Приложение использует данные о местоположении в фоне, в том числе когда приложение закрыто или не используется, чтобы определить вход в место задачи и показать напоминание. Данные остаются на устройстве и не передаются третьим лицам.",
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
                ) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundDisclosure = false }) { Text("Не сейчас") }
            },
        )
    }
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

private fun formatCoordinate(value: Double?): String =
    value?.let { "%.5f".format(it) } ?: "—"
