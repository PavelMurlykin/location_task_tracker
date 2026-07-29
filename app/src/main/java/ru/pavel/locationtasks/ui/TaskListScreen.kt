package ru.pavel.locationtasks.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
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
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.data.GeofenceStatus
import ru.pavel.locationtasks.data.PlaceEntity
import ru.pavel.locationtasks.data.TaskCategory
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.data.TaskPriority
import ru.pavel.locationtasks.data.TaskRecurrence
import ru.pavel.locationtasks.location.LocationPermissionState
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onCreateTask: (String) -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsState()
    val savedPlaces by viewModel.savedPlaces.collectAsState()
    var criteria by remember { mutableStateOf(TaskListCriteria()) }
    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showQuickCreate by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissions by remember { mutableStateOf(LocationPermissionState.from(context)) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val fetchCurrentLocation = {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            @Suppress("MissingPermission")
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token,
            ).addOnSuccessListener { location ->
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
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissions = LocationPermissionState.from(context)
        if (permissions.preciseLocation) {
            fetchCurrentLocation()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.distance_permission_required),
                )
            }
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

    LaunchedEffect(viewModel, context) {
        viewModel.events.collect { event ->
            val message = context.getString(
                event.messageRes,
                *event.messageArgs.toTypedArray(),
            )
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = event.undoToken?.let { context.getString(R.string.common_undo) },
                withDismissAction = event.undoToken != null,
            )
            event.undoToken?.let { token ->
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undo(token)
                } else {
                    viewModel.discardUndo(token)
                }
            }
        }
    }

    val visibleTasks = remember(tasks, criteria, currentLocation) {
        filterAndSortTasks(
            tasks = tasks,
            criteria = criteria,
            currentLocation = currentLocation,
        )
    }
    val availableCategories = remember(tasks) {
        tasks.asSequence()
            .map(TaskEntity::resolvedCategory)
            .filter { it != TaskCategory.NONE }
            .distinct()
            .sortedBy { it.ordinal }
            .toList()
    }
    val selectSort: (TaskSort) -> Unit = { sort ->
        criteria = criteria.copy(sort = sort)
        sortMenuExpanded = false
        if (sort == TaskSort.DISTANCE && currentLocation == null) {
            if (permissions.preciseLocation) {
                fetchCurrentLocation()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ),
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort_tasks),
                            )
                        }
                        SortMenu(
                            expanded = sortMenuExpanded,
                            selectedSort = criteria.sort,
                            onDismiss = { sortMenuExpanded = false },
                            onSelect = selectSort,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuickCreate = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.new_task_content_description),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchField(
                query = criteria.query,
                onQueryChange = { criteria = criteria.copy(query = it) },
            )
            SectionSelector(
                selected = criteria.section,
                activeCount = tasks.count { !it.isCompleted && !it.isArchived },
                completedCount = tasks.count { it.isCompleted && !it.isArchived },
                archivedCount = tasks.count(TaskEntity::isArchived),
                onSelected = { criteria = criteria.copy(section = it) },
            )
            CategorySelector(
                categories = availableCategories,
                selected = criteria.category,
                onSelected = { criteria = criteria.copy(category = it) },
            )
            FilterAndSortRow(
                selectedFilter = criteria.quickFilter,
                selectedSort = criteria.sort,
                onFilterSelected = { criteria = criteria.copy(quickFilter = it) },
                onOpenSort = { sortMenuExpanded = true },
            )

            if (visibleTasks.isEmpty()) {
                EmptyTasks(
                    section = criteria.section,
                    hasCriteria = criteria.query.isNotBlank() ||
                        criteria.quickFilter != null ||
                        criteria.category != null,
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visibleTasks, key = TaskEntity::id) { task ->
                        SwipeActionTaskCard(
                            task = task,
                            permissions = permissions,
                            currentLocation = currentLocation,
                            onClick = { onOpenTask(task.id) },
                            onCompletedChange = { viewModel.setCompleted(task, it) },
                            onSnooze = { viewModel.snooze(task) },
                            onArchivedChange = { viewModel.setArchived(task, it) },
                            onDelete = { viewModel.delete(task) },
                        )
                    }
                }
            }
        }
    }

    if (showQuickCreate) {
        QuickCreateDialog(
            savedPlaces = savedPlaces,
            onDismiss = { showQuickCreate = false },
            onCreate = { title, place ->
                viewModel.quickCreate(title, place)
                showQuickCreate = false
            },
            onOpenEditor = { title ->
                showQuickCreate = false
                onCreateTask(title)
            },
        )
    }
}

@Composable
private fun QuickCreateDialog(
    savedPlaces: List<PlaceEntity>,
    onDismiss: () -> Unit,
    onCreate: (String, PlaceEntity?) -> Unit,
    onOpenEditor: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var selectedPlaceId by remember(savedPlaces) {
        mutableStateOf(savedPlaces.firstOrNull()?.id)
    }
    var voiceError by remember { mutableStateOf(false) }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (spokenText.isNotBlank()) {
                val parsed = parseVoiceTask(spokenText, savedPlaces)
                title = parsed.title
                parsed.placeId?.let { selectedPlaceId = it }
                voiceError = false
            }
        }
    }
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        voiceError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.task_title_label)) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                voiceError = !runCatching {
                                    voiceLauncher.launch(voiceIntent)
                                }.isSuccess
                            },
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = stringResource(R.string.voice_input),
                            )
                        }
                    },
                    singleLine = true,
                )
                if (savedPlaces.isEmpty()) {
                    Text(
                        stringResource(R.string.quick_create_no_saved_places),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.quick_create_place),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        savedPlaces.forEach { place ->
                            FilterChip(
                                selected = selectedPlaceId == place.id,
                                onClick = {
                                    selectedPlaceId =
                                        if (selectedPlaceId == place.id) null else place.id
                                },
                                label = { Text(place.displayName) },
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.voice_input_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (voiceError) {
                    Text(
                        stringResource(R.string.voice_input_unavailable),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(
                    onClick = { onOpenEditor(title) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.quick_create_more_options))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        title,
                        savedPlaces.firstOrNull { it.id == selectedPlaceId },
                    )
                },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        },
        placeholder = { Text(stringResource(R.string.task_search_hint)) },
        singleLine = true,
    )
}

@Composable
private fun SectionSelector(
    selected: TaskSection,
    activeCount: Int,
    completedCount: Int,
    archivedCount: Int,
    onSelected: (TaskSection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == TaskSection.ACTIVE,
            onClick = { onSelected(TaskSection.ACTIVE) },
            label = { Text(stringResource(R.string.task_section_active, activeCount)) },
        )
        FilterChip(
            selected = selected == TaskSection.COMPLETED,
            onClick = { onSelected(TaskSection.COMPLETED) },
            label = { Text(stringResource(R.string.task_section_completed, completedCount)) },
        )
        FilterChip(
            selected = selected == TaskSection.ARCHIVED,
            onClick = { onSelected(TaskSection.ARCHIVED) },
            label = { Text(stringResource(R.string.task_section_archived, archivedCount)) },
        )
    }
}

@Composable
private fun CategorySelector(
    categories: List<TaskCategory>,
    selected: TaskCategory?,
    onSelected: (TaskCategory?) -> Unit,
) {
    if (categories.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text(stringResource(R.string.category_all)) },
        )
        categories.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelected(category) },
                label = { Text(stringResource(category.labelRes())) },
            )
        }
    }
}

@Composable
private fun FilterAndSortRow(
    selectedFilter: TaskQuickFilter?,
    selectedSort: TaskSort,
    onFilterSelected: (TaskQuickFilter?) -> Unit,
    onOpenSort: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterSelected(null) },
            label = { Text(stringResource(R.string.filter_all)) },
        )
        TaskQuickFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
        FilterChip(
            selected = true,
            onClick = onOpenSort,
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
            label = { Text(stringResource(selectedSort.labelRes())) },
        )
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    selectedSort: TaskSort,
    onDismiss: () -> Unit,
    onSelect: (TaskSort) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        TaskSort.entries.forEach { sort ->
            DropdownMenuItem(
                text = { Text(stringResource(sort.labelRes())) },
                onClick = { onSelect(sort) },
                leadingIcon = {
                    if (selectedSort == sort) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
            )
        }
    }
}

@Composable
private fun EmptyTasks(
    section: TaskSection,
    hasCriteria: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when {
                    hasCriteria -> stringResource(R.string.empty_filtered_tasks)
                    section == TaskSection.ACTIVE -> stringResource(R.string.empty_active_tasks)
                    section == TaskSection.COMPLETED ->
                        stringResource(R.string.empty_completed_tasks)
                    else -> stringResource(R.string.empty_archived_tasks)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (!hasCriteria && section == TaskSection.ACTIVE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.empty_active_tasks_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwipeActionTaskCard(
    task: TaskEntity,
    permissions: LocationPermissionState,
    currentLocation: GeoPoint?,
    onClick: () -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    onSnooze: () -> Unit,
    onArchivedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val leftActionWidth = 84.dp
    val rightActionWidth = if (task.isArchived) 84.dp else 168.dp
    val maxRightPx = with(density) { leftActionWidth.toPx() }
    val maxLeftPx = with(density) { -rightActionWidth.toPx() }
    val settleThresholdPx = with(density) { 36.dp.toPx() }
    var offsetX by remember(task.id) { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        Row(
            modifier = Modifier
                .matchParentSize(),
        ) {
            SwipeAction(
                modifier = Modifier
                    .width(leftActionWidth)
                    .fillMaxHeight(),
                label = when {
                    task.isArchived -> stringResource(R.string.swipe_unarchive)
                    task.isCompleted -> stringResource(R.string.swipe_reopen)
                    else -> stringResource(R.string.swipe_complete)
                },
                icon = if (task.isArchived) Icons.Default.Unarchive else Icons.Default.Check,
                color = MaterialTheme.colorScheme.primary,
                onClick = {
                    offsetX = 0f
                    if (task.isArchived) {
                        onArchivedChange(false)
                    } else {
                        onCompletedChange(!task.isCompleted)
                    }
                },
            )
            Spacer(Modifier.weight(1f))
            if (!task.isCompleted && !task.isArchived) {
                SwipeAction(
                    modifier = Modifier.width(84.dp).fillMaxHeight(),
                    label = stringResource(R.string.swipe_snooze),
                    icon = Icons.Default.Schedule,
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = {
                        offsetX = 0f
                        onSnooze()
                    },
                )
            } else if (task.isCompleted && !task.isArchived) {
                SwipeAction(
                    modifier = Modifier.width(84.dp).fillMaxHeight(),
                    label = stringResource(R.string.swipe_archive),
                    icon = Icons.Default.Archive,
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = {
                        offsetX = 0f
                        onArchivedChange(true)
                    },
                )
            }
            SwipeAction(
                modifier = Modifier.width(84.dp).fillMaxHeight(),
                label = stringResource(R.string.swipe_delete),
                icon = Icons.Default.Delete,
                color = MaterialTheme.colorScheme.error,
                onClick = {
                    offsetX = 0f
                    onDelete()
                },
            )
        }

        TaskCard(
            task = task,
            permissions = permissions,
            currentLocation = currentLocation,
            onClick = {
                if (offsetX == 0f) onClick() else offsetX = 0f
            },
            onCompletedChange = onCompletedChange,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(task.id, maxLeftPx, maxRightPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = when {
                                offsetX > settleThresholdPx -> maxRightPx
                                offsetX < -settleThresholdPx -> maxLeftPx
                                else -> 0f
                            }
                        },
                        onDragCancel = { offsetX = 0f },
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(maxLeftPx, maxRightPx)
                    }
                },
        )
    }
}

@Composable
private fun SwipeAction(
    modifier: Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    permissions: LocationPermissionState,
    currentLocation: GeoPoint?,
    onClick: () -> Unit,
    onCompletedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (task.isArchived) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Checkbox(
                    checked = task.isCompleted,
                    onCheckedChange = onCompletedChange,
                )
            }
            Column(modifier = Modifier.weight(1f).padding(top = 2.dp, end = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    )
                    PriorityLabel(task.resolvedPriority)
                }
                if (task.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                    )
                }
                val checklist = task.checklistItems
                if (checklist.isNotEmpty()) {
                    val completedItems = checklist.count { it.isCompleted }
                    Spacer(Modifier.height(7.dp))
                    LinearProgressIndicator(
                        progress = { completedItems.toFloat() / checklist.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(
                            R.string.checklist_progress,
                            completedItems,
                            checklist.size,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val organizationLabels = buildList {
                    if (task.resolvedCategory != TaskCategory.NONE) {
                        add(stringResource(task.resolvedCategory.labelRes()))
                    }
                    task.tagNames.forEach { add("#$it") }
                    if (task.resolvedRecurrence != TaskRecurrence.NONE) {
                        add(stringResource(task.resolvedRecurrence.labelRes()))
                    }
                }
                if (organizationLabels.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        organizationLabels.forEach { label ->
                            Text(
                                label,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                task.dueAt?.let {
                    Spacer(Modifier.height(6.dp))
                    DueDateLabel(it)
                }
                if (task.hasLocation) {
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (task.geofenceEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(Modifier.size(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.address?.takeIf(String::isNotBlank)
                                    ?: "${"%.5f".format(task.latitude)}, ${"%.5f".format(task.longitude)}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                            )
                            val distance = distanceMeters(task, currentLocation)
                            val detail = buildList {
                                add(stringResource(R.string.task_radius, task.geofenceRadiusMeters.toInt()))
                                if (distance != null) add(formatDistance(distance))
                            }.joinToString(" · ")
                            Text(
                                detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (task.geofenceEnabled && !task.isCompleted) {
                    Spacer(Modifier.height(6.dp))
                    GeofenceStatusRow(task, permissions)
                }
            }
        }
    }
}

@Composable
private fun PriorityLabel(priority: TaskPriority) {
    val color = when (priority) {
        TaskPriority.HIGH -> MaterialTheme.colorScheme.error
        TaskPriority.NORMAL -> MaterialTheme.colorScheme.primary
        TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = stringResource(priority.labelRes()),
        modifier = Modifier
            .padding(start = 8.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
private fun DueDateLabel(timestamp: Long) {
    val zoneId = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zoneId).toLocalDate()
    val dueDate = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
    val formattedDate = formatDate(timestamp)
    val isOverdue = dueDate.isBefore(today)
    Text(
        text = when {
            isOverdue -> stringResource(R.string.task_overdue, formattedDate)
            dueDate == today -> stringResource(R.string.task_due_today)
            else -> stringResource(R.string.task_due_date, formattedDate)
        },
        style = MaterialTheme.typography.labelMedium,
        color = when {
            isOverdue -> MaterialTheme.colorScheme.error
            dueDate == today -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun GeofenceStatusRow(
    task: TaskEntity,
    permissions: LocationPermissionState,
) {
    val status = if (!permissions.canRegisterGeofences || !permissions.notifications) {
        GeofenceStatus.MISSING_PERMISSION
    } else {
        task.resolvedGeofenceStatus
    }
    val color = when (status) {
        GeofenceStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        GeofenceStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (status == GeofenceStatus.ACTIVE) {
                Icons.Default.LocationOn
            } else {
                Icons.Default.Warning
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Spacer(Modifier.size(5.dp))
        Text(
            stringResource(status.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun formatDistance(distance: Double): String =
    if (distance < 1_000) {
        stringResource(R.string.task_distance_meters, distance.roundToInt())
    } else {
        stringResource(R.string.task_distance_kilometers, distance / 1_000)
    }

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(java.util.Date(timestamp))

private fun TaskQuickFilter.labelRes(): Int = when (this) {
    TaskQuickFilter.OVERDUE -> R.string.filter_overdue
    TaskQuickFilter.TODAY -> R.string.filter_today
    TaskQuickFilter.GEOFENCE -> R.string.filter_geofence
    TaskQuickFilter.WITHOUT_LOCATION -> R.string.filter_without_location
}

private fun TaskSort.labelRes(): Int = when (this) {
    TaskSort.DUE_DATE -> R.string.sort_due_date
    TaskSort.DISTANCE -> R.string.sort_distance
    TaskSort.CREATED_AT -> R.string.sort_created_at
    TaskSort.PRIORITY -> R.string.sort_priority
}

private fun TaskPriority.labelRes(): Int = when (this) {
    TaskPriority.LOW -> R.string.priority_low
    TaskPriority.NORMAL -> R.string.priority_normal
    TaskPriority.HIGH -> R.string.priority_high
}

private fun TaskCategory.labelRes(): Int = when (this) {
    TaskCategory.NONE -> R.string.category_none
    TaskCategory.SHOPPING -> R.string.category_shopping
    TaskCategory.WORK -> R.string.category_work
    TaskCategory.HOME -> R.string.category_home
}

private fun TaskRecurrence.labelRes(): Int = when (this) {
    TaskRecurrence.NONE -> R.string.recurrence_none
    TaskRecurrence.DAILY -> R.string.recurrence_daily
    TaskRecurrence.WEEKLY -> R.string.recurrence_weekly
    TaskRecurrence.MONTHLY -> R.string.recurrence_monthly
}

private fun GeofenceStatus.labelRes(): Int = when (this) {
    GeofenceStatus.ACTIVE -> R.string.geofence_active
    GeofenceStatus.PENDING -> R.string.geofence_pending
    GeofenceStatus.MISSING_PERMISSION -> R.string.geofence_missing_permission
    GeofenceStatus.LIMIT_REACHED -> R.string.geofence_limit_reached
    GeofenceStatus.ERROR -> R.string.geofence_registration_error
    GeofenceStatus.DISABLED -> R.string.geofence_disabled
}
