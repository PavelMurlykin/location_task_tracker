package ru.pavel.locationtasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.data.PlaceEntity
import ru.pavel.locationtasks.data.PlaceRepository
import ru.pavel.locationtasks.data.ChecklistCodec
import ru.pavel.locationtasks.data.ChecklistItem
import ru.pavel.locationtasks.data.CategoryEntity
import ru.pavel.locationtasks.data.CategoryRepository
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.data.TaskRepository
import ru.pavel.locationtasks.data.GeofenceStatus
import ru.pavel.locationtasks.data.GeofenceTransitionMode
import ru.pavel.locationtasks.data.TaskPriority
import ru.pavel.locationtasks.data.TaskRecurrence
import ru.pavel.locationtasks.data.encodeTags
import ru.pavel.locationtasks.data.parseTags
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.location.LocationResolver
import ru.pavel.locationtasks.location.ResolvedLocation
import ru.pavel.locationtasks.notifications.ReminderSchedule
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

data class TaskEditorState(
    val isLoading: Boolean = true,
    val isExisting: Boolean = false,
    val title: String = "",
    val description: String = "",
    val dueAt: Long? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val categoryId: String? = null,
    val tagsInput: String = "",
    val checklist: List<ChecklistItem> = emptyList(),
    val recurrence: TaskRecurrence = TaskRecurrence.NONE,
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String = "",
    val radiusMeters: Float = TaskEntity.DEFAULT_RADIUS_METERS,
    val geofenceEnabled: Boolean = false,
    val transitionMode: GeofenceTransitionMode = GeofenceTransitionMode.ENTER,
    val notificationCooldownMinutes: Int? = null,
    val allowedDaysMask: Int = ReminderSchedule.ALL_DAYS_MASK,
    val reminderWindowStartMinutes: Int? = null,
    val reminderWindowEndMinutes: Int? = null,
    val geofenceStatus: GeofenceStatus = GeofenceStatus.DISABLED,
    val geofenceStatusDetails: String? = null,
    @param:StringRes val validationMessageRes: Int? = null,
    val isSaving: Boolean = false,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object Deleted : EditorEvent
    data class Duplicated(val taskId: Long) : EditorEvent
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    private val placeRepository: PlaceRepository,
    categoryRepository: CategoryRepository,
    private val locationResolver: LocationResolver,
) : ViewModel() {
    private val taskId = savedStateHandle.get<Long>("taskId") ?: 0L
    private val initialTitle = savedStateHandle.get<String>("initialTitle").orEmpty()
    private val initialLatitude = savedStateHandle.get<String>("initialLatitude")
        ?.toDoubleOrNull()
        ?.takeIf { it in -90.0..90.0 }
    private val initialLongitude = savedStateHandle.get<String>("initialLongitude")
        ?.toDoubleOrNull()
        ?.takeIf { it in -180.0..180.0 }
    private var originalTask: TaskEntity? = null
    private val _state = MutableStateFlow(TaskEditorState())
    val state: StateFlow<TaskEditorState> = _state.asStateFlow()
    val savedPlaces: StateFlow<List<PlaceEntity>> = placeRepository.savedPlaces.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val recentPlaces: StateFlow<List<PlaceEntity>> = placeRepository.recentPlaces.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.categories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            originalTask = if (taskId > 0) repository.getById(taskId) else null
            val task = originalTask
            _state.value = if (task == null) {
                val hasInitialLocation = initialLatitude != null && initialLongitude != null
                TaskEditorState(
                    isLoading = false,
                    title = initialTitle,
                    latitude = initialLatitude,
                    longitude = initialLongitude,
                    geofenceEnabled = hasInitialLocation,
                    geofenceStatus = if (hasInitialLocation) {
                        GeofenceStatus.PENDING
                    } else {
                        GeofenceStatus.DISABLED
                    },
                )
            } else {
                TaskEditorState(
                    isLoading = false,
                    isExisting = true,
                    title = task.title,
                    description = task.description,
                    dueAt = task.dueAt,
                    priority = task.resolvedPriority,
                    categoryId = task.category.takeUnless {
                        it == CategoryEntity.NO_CATEGORY_ID
                    },
                    tagsInput = task.tagNames.joinToString(", "),
                    checklist = task.checklistItems,
                    recurrence = task.resolvedRecurrence,
                    isCompleted = task.isCompleted,
                    isArchived = task.isArchived,
                    latitude = task.latitude,
                    longitude = task.longitude,
                    address = task.address.orEmpty(),
                    radiusMeters = task.geofenceRadiusMeters,
                    geofenceEnabled = task.geofenceEnabled,
                    transitionMode = task.resolvedTransitionMode,
                    notificationCooldownMinutes = task.notificationCooldownMinutes,
                    allowedDaysMask = task.allowedDaysMask,
                    reminderWindowStartMinutes = task.reminderWindowStartMinutes,
                    reminderWindowEndMinutes = task.reminderWindowEndMinutes,
                    geofenceStatus = task.resolvedGeofenceStatus,
                    geofenceStatusDetails = task.geofenceStatusDetails,
                )
            }
            if (task == null && initialLatitude != null && initialLongitude != null) {
                locationResolver.reverse(initialLatitude, initialLongitude)?.let { address ->
                    update {
                        if (latitude == initialLatitude && longitude == initialLongitude) {
                            copy(address = address)
                        } else {
                            this
                        }
                    }
                }
            }
        }
        if (taskId > 0) {
            viewModelScope.launch {
                repository.observeById(taskId).collect { latestTask ->
                    latestTask ?: return@collect
                    originalTask = originalTask?.copy(
                        geofenceStatus = latestTask.geofenceStatus,
                        geofenceStatusDetails = latestTask.geofenceStatusDetails,
                        geofenceRegisteredAt = latestTask.geofenceRegisteredAt,
                    ) ?: latestTask
                    update {
                        copy(
                            geofenceStatus = latestTask.resolvedGeofenceStatus,
                            geofenceStatusDetails = latestTask.geofenceStatusDetails,
                        )
                    }
                }
            }
        }
    }

    fun setTitle(value: String) = update { copy(title = value, validationMessageRes = null) }
    fun setDescription(value: String) = update { copy(description = value) }
    fun setDueAt(value: Long?) = update { copy(dueAt = value) }
    fun setDueDate(selectedDateMillis: Long) = update {
        val selectedDate = Instant.ofEpochMilli(selectedDateMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        val time = dueAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime()
        } ?: LocalTime.of(DEFAULT_DUE_HOUR, 0)
        copy(dueAt = selectedDate.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    fun setDueTime(minutes: Int) = update {
        val date = dueAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
        } ?: return@update this
        val time = LocalTime.of(minutes / 60, minutes % 60)
        copy(dueAt = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }
    fun setPriority(value: TaskPriority) = update { copy(priority = value) }
    fun setCategory(value: String?) = update { copy(categoryId = value) }
    fun setTagsInput(value: String) = update { copy(tagsInput = value) }
    fun setRecurrence(value: TaskRecurrence) = update { copy(recurrence = value) }
    fun addChecklistItem(title: String) {
        val normalized = title.trim()
        if (normalized.isEmpty()) return
        update { copy(checklist = checklist + ChecklistItem(title = normalized)) }
    }
    fun setChecklistItemCompleted(id: String, completed: Boolean) = update {
        copy(
            checklist = checklist.map {
                if (it.id == id) it.copy(isCompleted = completed) else it
            },
        )
    }
    fun removeChecklistItem(id: String) = update {
        copy(checklist = checklist.filterNot { it.id == id })
    }
    fun setCompleted(value: Boolean) = update { copy(isCompleted = value) }
    fun setTransitionMode(value: GeofenceTransitionMode) =
        update { copy(transitionMode = value) }
    fun setNotificationCooldownMinutes(value: Int?) =
        update { copy(notificationCooldownMinutes = value) }
    fun toggleAllowedDay(dayBit: Int) = update {
        val nextMask = allowedDaysMask xor dayBit
        if (nextMask == 0) this else copy(allowedDaysMask = nextMask)
    }
    fun setReminderWindowEnabled(enabled: Boolean) = update {
        copy(
            reminderWindowStartMinutes = if (enabled) {
                reminderWindowStartMinutes ?: ReminderSchedule.DEFAULT_WINDOW_START_MINUTES
            } else {
                null
            },
            reminderWindowEndMinutes = if (enabled) {
                reminderWindowEndMinutes ?: ReminderSchedule.DEFAULT_WINDOW_END_MINUTES
            } else {
                null
            },
        )
    }
    fun setReminderWindowStart(minutes: Int) =
        update { copy(reminderWindowStartMinutes = minutes) }
    fun setReminderWindowEnd(minutes: Int) =
        update { copy(reminderWindowEndMinutes = minutes) }
    fun setGeofenceEnabled(value: Boolean) = update {
        copy(
            geofenceEnabled = value,
            geofenceStatus = if (value) GeofenceStatus.PENDING else GeofenceStatus.DISABLED,
            geofenceStatusDetails = null,
        )
    }
    fun setRadius(value: Float) = update { copy(radiusMeters = value.coerceIn(100f, 1_000f)) }

    fun setLocation(latitude: Double, longitude: Double, address: String) = update {
        copy(
            latitude = latitude,
            longitude = longitude,
            address = address,
            geofenceEnabled = true,
            geofenceStatus = GeofenceStatus.PENDING,
            geofenceStatusDetails = null,
            validationMessageRes = null,
        )
    }

    fun clearLocation() = update {
        copy(
            latitude = null,
            longitude = null,
            address = "",
            geofenceEnabled = false,
            geofenceStatus = GeofenceStatus.DISABLED,
            geofenceStatusDetails = null,
        )
    }

    fun searchLocation(query: String, onResult: (List<ResolvedLocation>) -> Unit) {
        viewModelScope.launch { onResult(locationResolver.search(query)) }
    }

    fun reverseLocation(latitude: Double, longitude: Double, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(locationResolver.reverse(latitude, longitude)) }
    }

    fun savePlace(
        name: String,
        latitude: Double,
        longitude: Double,
        address: String,
        radius: Float,
    ) {
        viewModelScope.launch {
            placeRepository.savePlace(name, address, latitude, longitude, radius)
        }
    }

    fun save() {
        val current = _state.value
        if (current.title.isBlank()) {
            update { copy(validationMessageRes = R.string.validation_title_required) }
            return
        }
        if (current.geofenceEnabled && !current.hasLocation) {
            update { copy(validationMessageRes = R.string.validation_location_required) }
            return
        }
        viewModelScope.launch {
            update { copy(isSaving = true, validationMessageRes = null) }
            val base = originalTask ?: TaskEntity(title = current.title.trim())
            val geofenceChanged = originalTask?.let { task ->
                task.latitude != current.latitude ||
                    task.longitude != current.longitude ||
                    task.geofenceRadiusMeters != current.radiusMeters ||
                    task.geofenceEnabled != current.geofenceEnabled ||
                    task.resolvedTransitionMode != current.transitionMode
            } ?: true
            val reminderChanged = originalTask?.let { task ->
                geofenceChanged ||
                    task.dueAt != current.dueAt ||
                    task.notificationCooldownMinutes != current.notificationCooldownMinutes ||
                    task.allowedDaysMask != current.allowedDaysMask ||
                    task.reminderWindowStartMinutes != current.reminderWindowStartMinutes ||
                    task.reminderWindowEndMinutes != current.reminderWindowEndMinutes ||
                    task.resolvedRecurrence != current.recurrence
            } ?: true
            val shouldAdvanceRecurrence = current.isCompleted &&
                current.recurrence != TaskRecurrence.NONE
            val taskToSave = base.copy(
                title = current.title.trim(),
                description = current.description.trim(),
                dueAt = current.dueAt,
                priority = current.priority.name,
                category = current.categoryId ?: CategoryEntity.NO_CATEGORY_ID,
                tags = encodeTags(parseTags(current.tagsInput)),
                checklistData = ChecklistCodec.encode(current.checklist),
                recurrence = current.recurrence.name,
                isCompleted = current.isCompleted && !shouldAdvanceRecurrence,
                latitude = current.latitude,
                longitude = current.longitude,
                address = current.address.trim().takeIf(String::isNotEmpty),
                geofenceRadiusMeters = current.radiusMeters,
                geofenceEnabled = current.geofenceEnabled,
                geofenceTransitionMode = current.transitionMode.name,
                notificationCooldownMinutes = current.notificationCooldownMinutes,
                allowedDaysMask = current.allowedDaysMask,
                reminderWindowStartMinutes = current.reminderWindowStartMinutes,
                reminderWindowEndMinutes = current.reminderWindowEndMinutes,
                snoozedUntil = if (reminderChanged) null else base.snoozedUntil,
                skipUntilNextVisit =
                    if (reminderChanged) false else base.skipUntilNextVisit,
                lastNotifiedAt = if (reminderChanged) null else base.lastNotifiedAt,
                lastNotifiedTransition =
                    if (reminderChanged) null else base.lastNotifiedTransition,
                geofenceStatus = when {
                    !current.geofenceEnabled -> GeofenceStatus.DISABLED.name
                    geofenceChanged -> GeofenceStatus.PENDING.name
                    else -> base.geofenceStatus
                },
                geofenceStatusDetails = if (geofenceChanged) null else base.geofenceStatusDetails,
                geofenceRegisteredAt = if (geofenceChanged) null else base.geofenceRegisteredAt,
            )
            val savedTaskId = repository.save(taskToSave)
            if (shouldAdvanceRecurrence) {
                repository.setCompleted(taskToSave.copy(id = savedTaskId), completed = true)
            }
            if (current.hasLocation) {
                placeRepository.recordUsed(
                    address = current.address,
                    latitude = requireNotNull(current.latitude),
                    longitude = requireNotNull(current.longitude),
                    radiusMeters = current.radiusMeters,
                )
            }
            _events.send(EditorEvent.Saved)
        }
    }

    fun delete() {
        val task = originalTask ?: return
        viewModelScope.launch {
            repository.delete(task)
            _events.send(EditorEvent.Deleted)
        }
    }

    fun duplicate() {
        val task = originalTask ?: return
        if (_state.value.isSaving) return
        viewModelScope.launch {
            update { copy(isSaving = true) }
            val duplicatedTaskId = repository.duplicate(task)
            _events.send(EditorEvent.Duplicated(duplicatedTaskId))
        }
    }

    private inline fun update(transform: TaskEditorState.() -> TaskEditorState) {
        _state.value = _state.value.transform()
    }

    companion object {
        private const val DEFAULT_DUE_HOUR = 9
    }
}
