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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import ru.pavel.locationtasks.BuildConfig
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.data.PlaceEntity
import ru.pavel.locationtasks.location.ResolvedLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    initialAddress: String,
    initialRadius: Float,
    savedPlaces: List<PlaceEntity>,
    recentPlaces: List<PlaceEntity>,
    onSearch: (String, (List<ResolvedLocation>) -> Unit) -> Unit,
    onReverse: (Double, Double, (String?) -> Unit) -> Unit,
    onSavePlace: (String, Double, Double, String, Float) -> Unit,
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
    var searchResults by remember { mutableStateOf<List<ResolvedLocation>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSavePlaceDialog by remember { mutableStateOf(false) }
    var placeName by remember { mutableStateOf("") }
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
            context.getString(R.string.location_permission_granted_retry)
        } else {
            context.getString(R.string.precise_location_required)
        }
    }

    fun applyResolvedLocation(result: ResolvedLocation) {
        latitude = result.latitude
        longitude = result.longitude
        latitudeText = result.latitude.toString()
        longitudeText = result.longitude.toString()
        address = result.address
        searchResults = emptyList()
        errorMessage = null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.location_picker_title)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_close),
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
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PlaceSuggestionRow(
                        title = stringResource(R.string.saved_places_title),
                        places = savedPlaces,
                        onSelected = { place ->
                            applyResolvedLocation(
                                ResolvedLocation(
                                    latitude = place.latitude,
                                    longitude = place.longitude,
                                    address = place.address,
                                ),
                            )
                            radius = place.radiusMeters
                        },
                    )
                    PlaceSuggestionRow(
                        title = stringResource(R.string.recent_places_title),
                        places = recentPlaces,
                        onSelected = { place ->
                            applyResolvedLocation(
                                ResolvedLocation(
                                    latitude = place.latitude,
                                    longitude = place.longitude,
                                    address = place.address,
                                ),
                            )
                            radius = place.radiusMeters
                        },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.search_address_hint)) },
                            singleLine = true,
                        )
                        IconButton(
                            onClick = {
                                isSearching = true
                                onSearch(searchQuery) { results ->
                                    isSearching = false
                                    searchResults = results
                                    if (results.isEmpty()) {
                                        errorMessage = context.getString(R.string.address_not_found)
                                    } else {
                                        errorMessage = null
                                    }
                                }
                            },
                            enabled = searchQuery.isNotBlank() && !isSearching,
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.common_search),
                                )
                            }
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(
                                items = searchResults,
                                key = { "${it.latitude}:${it.longitude}:${it.address}" },
                            ) { result ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { applyResolvedLocation(result) },
                                ) {
                                    Text(
                                        result.address,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }

                    if (BuildConfig.MAPKIT_API_KEY_PRESENT) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            YandexLocationMap(
                                modifier = Modifier.fillMaxSize(),
                                latitude = latitude,
                                longitude = longitude,
                                radius = radius,
                                showUserLocation = hasFinePermission,
                                primaryColor = MaterialTheme.colorScheme.primary,
                                onLongClick = { selectedLatitude, selectedLongitude ->
                                    latitude = selectedLatitude
                                    longitude = selectedLongitude
                                    latitudeText = selectedLatitude.toString()
                                    longitudeText = selectedLongitude.toString()
                                    onReverse(selectedLatitude, selectedLongitude) { resolved ->
                                        address = resolved.orEmpty()
                                    }
                                },
                            )
                            OpenInYandexMapsButton(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.map_long_press_hint),
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
                                    stringResource(R.string.map_api_key_missing),
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
                                            errorMessage = context.getString(
                                                R.string.current_position_unavailable,
                                            )
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
                        Text(stringResource(R.string.use_my_location))
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
                            label = { Text(stringResource(R.string.latitude_label)) },
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
                            label = { Text(stringResource(R.string.longitude_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.place_name_or_address)) },
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = {
                            placeName = ""
                            showSavePlaceDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.save_place_template))
                    }
                    Text(stringResource(R.string.radius_value, radius.toInt()))
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
                                errorMessage = context.getString(R.string.invalid_coordinates)
                            } else {
                                onConfirm(validLatitude, validLongitude, address, radius)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text(stringResource(R.string.choose_this_location))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showSavePlaceDialog) {
        AlertDialog(
            onDismissRequest = { showSavePlaceDialog = false },
            title = { Text(stringResource(R.string.save_place_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = placeName,
                        onValueChange = { placeName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.place_template_name)) },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            R.string.place_home,
                            R.string.place_work,
                            R.string.place_shop,
                            R.string.place_parents,
                        ).forEach { label ->
                            val suggestion = stringResource(label)
                            SuggestionChip(
                                onClick = { placeName = suggestion },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val validLatitude = latitudeText.replace(',', '.').toDoubleOrNull()
                        val validLongitude = longitudeText.replace(',', '.').toDoubleOrNull()
                        if (placeName.isNotBlank() &&
                            validLatitude != null &&
                            validLongitude != null
                        ) {
                            onSavePlace(
                                placeName,
                                validLatitude,
                                validLongitude,
                                address,
                                radius,
                            )
                            errorMessage = context.getString(R.string.place_saved)
                            showSavePlaceDialog = false
                        }
                    },
                    enabled = placeName.isNotBlank(),
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePlaceDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaceSuggestionRow(
    title: String,
    places: List<PlaceEntity>,
    onSelected: (PlaceEntity) -> Unit,
) {
    if (places.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            places.forEach { place ->
                SuggestionChip(
                    onClick = { onSelected(place) },
                    label = { Text(place.displayName, maxLines = 1) },
                )
            }
        }
    }
}

private const val DEFAULT_LATITUDE = 55.7558
private const val DEFAULT_LONGITUDE = 37.6173
