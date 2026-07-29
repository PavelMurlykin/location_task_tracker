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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.pavel.locationtasks.data.GeofenceLogEntity
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
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
            Text("Повторные уведомления", style = MaterialTheme.typography.titleMedium)
            Text(
                "Не напоминать об одной задаче повторно в течение:",
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
                        label = { Text("$hours ч") },
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Надёжность геонапоминаний", style = MaterialTheme.typography.titleMedium)
                    SettingStatus("Точная геопозиция", permissions.preciseLocation)
                    SettingStatus("Геопозиция в фоне", permissions.backgroundLocation)
                    SettingStatus("Уведомления", permissions.notifications)
                    SettingStatus(
                        "Фоновая работа",
                        !backgroundState.backgroundRestricted,
                        enabledText = "Не ограничена",
                        disabledText = "Ограничена",
                    )
                    SettingStatus(
                        "Энергосбережение",
                        backgroundState.batteryOptimizationsIgnored,
                        enabledText = "Без ограничений",
                        disabledText = "Может задерживать",
                    )
                    if (backgroundState.mayDelayGeofences) {
                        Text(
                            "Android или оболочка устройства может задерживать события геозон. " +
                                "Разрешите приложению фоновую работу без ограничений.",
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
                        Text("Открыть настройки приложения")
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
                        Text("Открыть настройки энергосбережения")
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    }
                    FilledTonalButton(
                        onClick = viewModel::checkGeofences,
                        enabled = !isCheckingGeofences,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (isCheckingGeofences) {
                                "Проверяем геозоны…"
                            } else {
                                "Проверить геозоны"
                            },
                        )
                    }
                }
            }

            Text("Последние события геозон", style = MaterialTheme.typography.titleMedium)
            if (geofenceLogs.isEmpty()) {
                Text(
                    "Событий пока нет. Здесь появятся результаты регистрации и срабатываний.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                geofenceLogs.forEach { entry ->
                    GeofenceLogCard(entry)
                }
            }

            Text(
                "Координаты и задачи хранятся только на этом устройстве.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenPrivacy) {
                Text("Политика конфиденциальности")
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SettingStatus(
    label: String,
    enabled: Boolean,
    enabledText: String = "Разрешено",
    disabledText: String = "Не разрешено",
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
                logOutcomeLabel(entry),
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
                    it,
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

private fun logOutcomeLabel(entry: GeofenceLogEntity): String = when (entry.outcome) {
    GeofenceLogEntity.OUTCOME_ACTIVE ->
        if (entry.event == GeofenceLogEntity.EVENT_RESTORE) {
            "Геозона восстановлена"
        } else {
            "Геозона зарегистрирована"
        }
    GeofenceLogEntity.OUTCOME_ERROR -> "Ошибка регистрации"
    GeofenceLogEntity.OUTCOME_MISSING_PERMISSION -> "Нет необходимых разрешений"
    GeofenceLogEntity.OUTCOME_LIMIT_REACHED -> "Не зарегистрирована из-за лимита"
    GeofenceLogEntity.OUTCOME_NOTIFIED -> "Вход в геозону — уведомление показано"
    GeofenceLogEntity.OUTCOME_COOLDOWN -> "Вход в геозону — действует интервал повтора"
    GeofenceLogEntity.OUTCOME_NOTIFICATIONS_BLOCKED ->
        "Вход в геозону — уведомления запрещены"
    GeofenceLogEntity.OUTCOME_TASK_INACTIVE -> "Вход в геозону — задача уже неактивна"
    else -> entry.outcome
}

private val LOG_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter
    .ofPattern("dd.MM.yyyy HH:mm")
    .withZone(ZoneId.systemDefault())
