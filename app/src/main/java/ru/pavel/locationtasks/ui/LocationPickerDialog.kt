package ru.pavel.locationtasks.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import ru.pavel.locationtasks.BuildConfig
import ru.pavel.locationtasks.location.ResolvedLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    initialAddress: String,
    initialRadius: Float,
    onSearch: (String, (ResolvedLocation?) -> Unit) -> Unit,
    onReverse: (Double, Double, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double, String, Float) -> Unit,
) {
    val context = LocalContext.current
    var latitude by remember { mutableDoubleStateOf(initialLatitude ?: DEFAULT_LATITUDE) }
    var longitude by remember { mutableDoubleStateOf(initialLongitude ?: DEFAULT_LONGITUDE) }
    var latitudeText by remember { mutableStateOf(initialLatitude?.toString() ?: DEFAULT_LATITUDE.toString()) }
    var longitudeText by remember { mutableStateOf(initialLongitude?.toString() ?: DEFAULT_LONGITUDE.toString()) }
    var address by remember { mutableStateOf(initialAddress) }
    var radius by remember { mutableFloatStateOf(initialRadius) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasFinePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasFinePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        errorMessage = if (hasFinePermission) {
            "Разрешение получено — нажмите кнопку ещё раз"
        } else {
            "Без точной геопозиции текущую точку определить нельзя"
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 14f)
    }

    fun applyResolvedLocation(result: ResolvedLocation) {
        latitude = result.latitude
        longitude = result.longitude
        latitudeText = result.latitude.toString()
        longitudeText = result.longitude.toString()
        address = result.address
        errorMessage = null
    }

    LaunchedEffect(latitude, longitude) {
        if (BuildConfig.MAPS_API_KEY_PRESENT) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(LatLng(latitude, longitude)),
                400,
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Место задачи") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Найти адрес") },
                            singleLine = true,
                        )
                        IconButton(
                            onClick = {
                                isSearching = true
                                onSearch(searchQuery) { result ->
                                    isSearching = false
                                    if (result == null) {
                                        errorMessage = "Адрес не найден"
                                    } else {
                                        applyResolvedLocation(result)
                                    }
                                }
                            },
                            enabled = searchQuery.isNotBlank() && !isSearching,
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Найти")
                            }
                        }
                    }

                    if (BuildConfig.MAPS_API_KEY_PRESENT) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState,
                                properties = MapProperties(isMyLocationEnabled = hasFinePermission),
                                onMapLongClick = { point ->
                                    latitude = point.latitude
                                    longitude = point.longitude
                                    latitudeText = point.latitude.toString()
                                    longitudeText = point.longitude.toString()
                                    onReverse(point.latitude, point.longitude) { resolved ->
                                        address = resolved.orEmpty()
                                    }
                                },
                            ) {
                                val point = LatLng(latitude, longitude)
                                Marker(
                                    state = rememberUpdatedMarkerState(point),
                                    title = address.ifBlank { "Место задачи" },
                                )
                                Circle(
                                    center = point,
                                    radius = radius.toDouble(),
                                    fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    strokeColor = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2f,
                                )
                            }
                        }
                        Text(
                            "Нажмите и удерживайте карту, чтобы поставить метку",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null)
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    "Для карты добавьте MAPS_API_KEY в local.properties. Координаты можно указать вручную или получить с устройства.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }

                    OutlinedButton(
                        onClick = {
                            if (!hasFinePermission) {
                                foregroundPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ),
                                )
                            } else {
                                @Suppress("MissingPermission")
                                LocationServices.getFusedLocationProviderClient(context)
                                    .getCurrentLocation(
                                        Priority.PRIORITY_HIGH_ACCURACY,
                                        CancellationTokenSource().token,
                                    )
                                    .addOnSuccessListener { location ->
                                        if (location == null) {
                                            errorMessage = "Не удалось получить текущую позицию"
                                        } else {
                                            latitude = location.latitude
                                            longitude = location.longitude
                                            latitudeText = location.latitude.toString()
                                            longitudeText = location.longitude.toString()
                                            onReverse(location.latitude, location.longitude) {
                                                address = it.orEmpty()
                                            }
                                        }
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Использовать моё местоположение")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latitudeText,
                            onValueChange = { value ->
                                latitudeText = value
                                value.replace(',', '.').toDoubleOrNull()
                                    ?.takeIf { it in -90.0..90.0 }
                                    ?.let { latitude = it }
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Широта") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = longitudeText,
                            onValueChange = { value ->
                                longitudeText = value
                                value.replace(',', '.').toDoubleOrNull()
                                    ?.takeIf { it in -180.0..180.0 }
                                    ?.let { longitude = it }
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("Долгота") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название или адрес места") },
                        singleLine = true,
                    )
                    Text("Радиус: ${radius.toInt()} м")
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = 100f..1_000f,
                        steps = 8,
                    )
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            val validLatitude = latitudeText.replace(',', '.').toDoubleOrNull()
                            val validLongitude = longitudeText.replace(',', '.').toDoubleOrNull()
                            if (validLatitude == null || validLatitude !in -90.0..90.0 ||
                                validLongitude == null || validLongitude !in -180.0..180.0
                            ) {
                                errorMessage = "Проверьте широту и долготу"
                            } else {
                                onConfirm(validLatitude, validLongitude, address, radius)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text("Выбрать это место")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private const val DEFAULT_LATITUDE = 55.7558
private const val DEFAULT_LONGITUDE = 37.6173
