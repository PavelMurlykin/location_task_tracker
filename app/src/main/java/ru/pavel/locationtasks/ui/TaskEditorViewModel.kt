package ru.pavel.locationtasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.data.TaskRepository
import ru.pavel.locationtasks.location.LocationResolver
import ru.pavel.locationtasks.location.ResolvedLocation
import javax.inject.Inject

data class TaskEditorState(
    val isLoading: Boolean = true,
    val isExisting: Boolean = false,
    val title: String = "",
    val description: String = "",
    val dueAt: Long? = null,
    val isCompleted: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String = "",
    val radiusMeters: Float = TaskEntity.DEFAULT_RADIUS_METERS,
    val geofenceEnabled: Boolean = false,
    val validationMessage: String? = null,
    val isSaving: Boolean = false,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object Deleted : EditorEvent
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    private val locationResolver: LocationResolver,
) : ViewModel() {
    private val taskId = savedStateHandle.get<Long>("taskId") ?: 0L
    private var originalTask: TaskEntity? = null
    private val _state = MutableStateFlow(TaskEditorState())
    val state: StateFlow<TaskEditorState> = _state.asStateFlow()
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            originalTask = if (taskId > 0) repository.getById(taskId) else null
            val task = originalTask
            _state.value = if (task == null) {
                TaskEditorState(isLoading = false)
            } else {
                TaskEditorState(
                    isLoading = false,
                    isExisting = true,
                    title = task.title,
                    description = task.description,
                    dueAt = task.dueAt,
                    isCompleted = task.isCompleted,
                    latitude = task.latitude,
                    longitude = task.longitude,
                    address = task.address.orEmpty(),
                    radiusMeters = task.geofenceRadiusMeters,
                    geofenceEnabled = task.geofenceEnabled,
                )
            }
        }
    }

    fun setTitle(value: String) = update { copy(title = value, validationMessage = null) }
    fun setDescription(value: String) = update { copy(description = value) }
    fun setDueAt(value: Long?) = update { copy(dueAt = value) }
    fun setCompleted(value: Boolean) = update { copy(isCompleted = value) }
    fun setGeofenceEnabled(value: Boolean) = update { copy(geofenceEnabled = value) }
    fun setRadius(value: Float) = update { copy(radiusMeters = value.coerceIn(100f, 1_000f)) }

    fun setLocation(latitude: Double, longitude: Double, address: String) = update {
        copy(
            latitude = latitude,
            longitude = longitude,
            address = address,
            geofenceEnabled = true,
            validationMessage = null,
        )
    }

    fun clearLocation() = update {
        copy(latitude = null, longitude = null, address = "", geofenceEnabled = false)
    }

    fun searchLocation(query: String, onResult: (ResolvedLocation?) -> Unit) {
        viewModelScope.launch { onResult(locationResolver.search(query)) }
    }

    fun reverseLocation(latitude: Double, longitude: Double, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(locationResolver.reverse(latitude, longitude)) }
    }

    fun save() {
        val current = _state.value
        if (current.title.isBlank()) {
            update { copy(validationMessage = "Введите название задачи") }
            return
        }
        if (current.geofenceEnabled && !current.hasLocation) {
            update { copy(validationMessage = "Укажите место для геонапоминания") }
            return
        }
        viewModelScope.launch {
            update { copy(isSaving = true, validationMessage = null) }
            val base = originalTask ?: TaskEntity(title = current.title.trim())
            val geofenceChanged = originalTask?.let { task ->
                task.latitude != current.latitude ||
                    task.longitude != current.longitude ||
                    task.geofenceRadiusMeters != current.radiusMeters ||
                    task.geofenceEnabled != current.geofenceEnabled
            } ?: true
            repository.save(
                base.copy(
                    title = current.title.trim(),
                    description = current.description.trim(),
                    dueAt = current.dueAt,
                    isCompleted = current.isCompleted,
                    latitude = current.latitude,
                    longitude = current.longitude,
                    address = current.address.trim().takeIf(String::isNotEmpty),
                    geofenceRadiusMeters = current.radiusMeters,
                    geofenceEnabled = current.geofenceEnabled,
                    lastNotifiedAt = if (geofenceChanged) null else base.lastNotifiedAt,
                ),
            )
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

    private inline fun update(transform: TaskEditorState.() -> TaskEditorState) {
        _state.value = _state.value.transform()
    }
}
