package ru.pavel.locationtasks.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.BuildConfig
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.location.LocationPermissionState
import kotlin.math.roundToInt

private enum class TaskMapMode {
    MAP,
    NEARBY,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskMapScreen(
    onClose: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onCreateTaskAt: (Double, Double) -> Unit,
    viewModel: TaskMapViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsState()
    val activeTasks = remember(tasks) { activeMapTasks(tasks) }
    val clusters = remember(activeTasks) { clusterMapTasks(activeTasks) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val fusedLocationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }
    var permissions by remember { mutableStateOf(LocationPermissionState.from(context)) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var locating by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(TaskMapMode.MAP) }
    var selectedCluster by remember { mutableStateOf<TaskMapCluster?>(null) }
    var routeTaskIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showRoute by remember { mutableStateOf(false) }

    val fetchCurrentLocation = {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locating = true
            @Suppress("MissingPermission")
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token,
            ).addOnCompleteListener { result ->
                locating = false
                val location = if (result.isSuccessful) result.result else null
                if (location == null) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.current_location_unavailable),
                        )
                    }
                } else {
                    currentLocation = GeoPoint(location.latitude, location.longitude)
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissions = LocationPermissionState.from(context)
        if (permissions.preciseLocation) fetchCurrentLocation()
    }
    val requestCurrentLocation = {
        if (permissions.preciseLocation) {
            fetchCurrentLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = LocationPermissionState.from(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (permissions.preciseLocation) fetchCurrentLocation()
    }

    val nearbyTasks = remember(activeTasks, currentLocation) {
        currentLocation?.let { sortNearbyTasks(activeTasks, it) }.orEmpty()
    }
    val orderedRoute = remember(activeTasks, routeTaskIds, currentLocation) {
        currentLocation?.let { location ->
            optimizeTaskRoute(activeTasks.filter { it.id in routeTaskIds }, location)
        }.orEmpty()
    }
    val alongTheWay = remember(activeTasks, currentLocation, orderedRoute, showRoute) {
        if (showRoute) {
            currentLocation?.let { tasksAlongRoute(activeTasks, it, orderedRoute) }.orEmpty()
        } else {
            emptyList()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.task_map_title)) },
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
                .padding(padding),
        ) {
            MapModeSelector(
                mode = mode,
                taskCount = activeTasks.size,
                onModeSelected = { selected ->
                    mode = selected
                    if (selected == TaskMapMode.NEARBY && currentLocation == null) {
                        requestCurrentLocation()
                    }
                },
            )
            when (mode) {
                TaskMapMode.MAP -> MapOverview(
                    clusters = clusters,
                    currentLocation = currentLocation,
                    route = orderedRoute.takeIf { showRoute }.orEmpty(),
                    alongTheWay = alongTheWay,
                    selectedCluster = selectedCluster,
                    permissions = permissions,
                    onRequestLocation = requestCurrentLocation,
                    onClusterClick = { selectedCluster = it },
                    onOpenTask = onOpenTask,
                    onCreateTaskAt = onCreateTaskAt,
                    onClearRoute = {
                        showRoute = false
                        routeTaskIds = emptySet()
                    },
                    modifier = Modifier.weight(1f),
                )
                TaskMapMode.NEARBY -> NearbyTasks(
                    tasks = nearbyTasks,
                    currentLocation = currentLocation,
                    permissions = permissions,
                    locating = locating,
                    routeTaskIds = routeTaskIds,
                    alongTheWayIds = alongTheWay.mapTo(mutableSetOf(), TaskEntity::id),
                    onRequestLocation = requestCurrentLocation,
                    onOpenTask = onOpenTask,
                    onToggleRouteTask = { task ->
                        if (task.id in routeTaskIds) {
                            routeTaskIds -= task.id
                            showRoute = false
                        } else if (routeTaskIds.size >= MAX_ROUTE_TASKS) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.route_task_limit, MAX_ROUTE_TASKS),
                                )
                            }
                        } else {
                            routeTaskIds += task.id
                            showRoute = false
                        }
                    },
                    onClearRoute = {
                        routeTaskIds = emptySet()
                        showRoute = false
                    },
                    onBuildRoute = {
                        showRoute = true
                        selectedCluster = null
                        mode = TaskMapMode.MAP
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MapModeSelector(
    mode: TaskMapMode,
    taskCount: Int,
    onModeSelected: (TaskMapMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = mode == TaskMapMode.MAP,
            onClick = { onModeSelected(TaskMapMode.MAP) },
            label = { Text(stringResource(R.string.map_mode_all, taskCount)) },
            leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) },
        )
        FilterChip(
            selected = mode == TaskMapMode.NEARBY,
            onClick = { onModeSelected(TaskMapMode.NEARBY) },
            label = { Text(stringResource(R.string.map_mode_nearby)) },
            leadingIcon = { Icon(Icons.Default.NearMe, contentDescription = null) },
        )
    }
}

@Composable
private fun MapOverview(
    clusters: List<TaskMapCluster>,
    currentLocation: GeoPoint?,
    route: List<TaskEntity>,
    alongTheWay: List<TaskEntity>,
    selectedCluster: TaskMapCluster?,
    permissions: LocationPermissionState,
    onRequestLocation: () -> Unit,
    onClusterClick: (TaskMapCluster) -> Unit,
    onOpenTask: (Long) -> Unit,
    onCreateTaskAt: (Double, Double) -> Unit,
    onClearRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (BuildConfig.MAPKIT_API_KEY_PRESENT) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                YandexTasksMap(
                    clusters = clusters,
                    currentLocation = currentLocation,
                    routePoints = route.map(TaskEntity::requireGeoPoint),
                    showUserLocation = permissions.preciseLocation,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    onClusterClick = onClusterClick,
                    onLongClick = onCreateTaskAt,
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.AddLocationAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(R.string.general_map_long_press_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                SmallFloatingActionButton(
                    onClick = onRequestLocation,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = stringResource(R.string.use_my_location),
                    )
                }
            }
        } else {
            MissingMapCard(
                onRequestLocation = onRequestLocation,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
            )
        }

        when {
            route.isNotEmpty() && currentLocation != null -> RouteSummaryCard(
                route = route,
                alongTheWay = alongTheWay,
                currentLocation = currentLocation,
                onOpenTask = onOpenTask,
                onClearRoute = onClearRoute,
            )
            selectedCluster != null -> ClusterSummaryCard(
                cluster = selectedCluster,
                onOpenTask = onOpenTask,
            )
            clusters.isEmpty() -> Text(
                text = stringResource(R.string.map_empty_tasks),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            else -> Text(
                text = stringResource(R.string.map_marker_summary, clusters.size),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun MissingMapCard(
    onRequestLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.map_api_key_missing))
            OutlinedButton(onClick = onRequestLocation) {
                Icon(Icons.Default.MyLocation, contentDescription = null)
                Text(
                    stringResource(R.string.use_my_location),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ClusterSummaryCard(
    cluster: TaskMapCluster,
    onOpenTask: (Long) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.tasks_at_place, cluster.count),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            cluster.tasks.take(MAX_CLUSTER_PREVIEW_TASKS).forEach { task ->
                TextButton(onClick = { onOpenTask(task.id) }) {
                    Text(task.title, modifier = Modifier.fillMaxWidth())
                }
            }
            if (cluster.count > MAX_CLUSTER_PREVIEW_TASKS) {
                Text(
                    stringResource(
                        R.string.more_tasks_at_place,
                        cluster.count - MAX_CLUSTER_PREVIEW_TASKS,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RouteSummaryCard(
    route: List<TaskEntity>,
    alongTheWay: List<TaskEntity>,
    currentLocation: GeoPoint,
    onOpenTask: (Long) -> Unit,
    onClearRoute: () -> Unit,
) {
    val distance = routeDistanceMeters(currentLocation, route)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null)
                Text(
                    text = stringResource(
                        R.string.route_summary,
                        route.size,
                        formatDistance(distance),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                )
                TextButton(onClick = onClearRoute) {
                    Text(stringResource(R.string.common_reset))
                }
            }
            Text(
                stringResource(R.string.route_straight_line_notice),
                style = MaterialTheme.typography.bodySmall,
            )
            route.forEachIndexed { index, task ->
                TextButton(onClick = { onOpenTask(task.id) }) {
                    Text("${index + 1}. ${task.title}", modifier = Modifier.fillMaxWidth())
                }
            }
            if (alongTheWay.isNotEmpty()) {
                Text(
                    stringResource(R.string.along_the_way_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    alongTheWay.take(MAX_ALONG_ROUTE_PREVIEW).joinToString { it.title },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun NearbyTasks(
    tasks: List<TaskEntity>,
    currentLocation: GeoPoint?,
    permissions: LocationPermissionState,
    locating: Boolean,
    routeTaskIds: Set<Long>,
    alongTheWayIds: Set<Long>,
    onRequestLocation: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onToggleRouteTask: (TaskEntity) -> Unit,
    onClearRoute: () -> Unit,
    onBuildRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (currentLocation == null) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Card(modifier = Modifier.padding(20.dp)) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(48.dp))
                    Text(
                        if (permissions.preciseLocation) {
                            stringResource(R.string.current_location_unavailable)
                        } else {
                            stringResource(R.string.nearby_location_explanation)
                        },
                    )
                    Button(onClick = onRequestLocation, enabled = !locating) {
                        Text(
                            if (locating) {
                                stringResource(R.string.location_loading)
                            } else {
                                stringResource(R.string.allow_location)
                            },
                        )
                    }
                }
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.route_selected_count, routeTaskIds.size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.route_selection_hint, MAX_ROUTE_TASKS),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (routeTaskIds.isNotEmpty()) {
                    TextButton(onClick = onClearRoute) {
                        Text(stringResource(R.string.common_reset))
                    }
                }
                FilledTonalButton(
                    onClick = onBuildRoute,
                    enabled = routeTaskIds.isNotEmpty(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null)
                    Text(
                        stringResource(R.string.build_route),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.map_empty_tasks))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tasks, key = TaskEntity::id) { task ->
                    NearbyTaskCard(
                        task = task,
                        distanceMeters = distanceMeters(task.requireGeoPoint(), currentLocation),
                        selectedForRoute = task.id in routeTaskIds,
                        alongTheWay = task.id in alongTheWayIds,
                        onOpenTask = { onOpenTask(task.id) },
                        onToggleRoute = { onToggleRouteTask(task) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyTaskCard(
    task: TaskEntity,
    distanceMeters: Double,
    selectedForRoute: Boolean,
    alongTheWay: Boolean,
    onOpenTask: () -> Unit,
    onToggleRoute: () -> Unit,
) {
    Card(
        onClick = onOpenTask,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selectedForRoute,
                onCheckedChange = { onToggleRoute() },
            )
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                task.address?.takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                if (alongTheWay) {
                    Text(
                        stringResource(R.string.along_the_way_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                formatDistance(distanceMeters),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun formatDistance(distanceMeters: Double): String = if (distanceMeters < 1_000) {
    stringResource(R.string.task_distance_meters, distanceMeters.roundToInt())
} else {
    stringResource(R.string.task_distance_kilometers, distanceMeters / 1_000.0)
}

private const val MAX_CLUSTER_PREVIEW_TASKS = 3
private const val MAX_ALONG_ROUTE_PREVIEW = 3
