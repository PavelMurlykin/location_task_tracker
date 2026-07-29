package ru.pavel.locationtasks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.data.GeofenceStatus
import ru.pavel.locationtasks.data.PlaceEntity
import ru.pavel.locationtasks.data.PlaceRepository
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.data.TaskRepository
import ru.pavel.locationtasks.data.TaskCompletionOutcome
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val placeRepository: PlaceRepository,
) : ViewModel() {
    private val undoOperations = mutableMapOf<Long, UndoOperation>()
    private var nextUndoToken = 1L
    private val _events = Channel<TaskListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val tasks: StateFlow<List<TaskEntity>> = repository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val savedPlaces: StateFlow<List<PlaceEntity>> = placeRepository.savedPlaces.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun quickCreate(title: String, place: PlaceEntity?) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) return
        viewModelScope.launch {
            repository.save(
                TaskEntity(
                    title = normalizedTitle,
                    latitude = place?.latitude,
                    longitude = place?.longitude,
                    address = place?.address,
                    geofenceRadiusMeters =
                        place?.radiusMeters ?: TaskEntity.DEFAULT_RADIUS_METERS,
                    geofenceEnabled = place != null,
                    geofenceStatus = if (place != null) {
                        GeofenceStatus.PENDING.name
                    } else {
                        GeofenceStatus.DISABLED.name
                    },
                ),
            )
            if (place != null) {
                placeRepository.recordUsed(
                    address = place.address,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    radiusMeters = place.radiusMeters,
                )
            }
            _events.send(
                TaskListEvent(
                    messageRes = R.string.quick_task_created,
                    messageArgs = listOf(normalizedTitle),
                ),
            )
        }
    }

    fun setCompleted(task: TaskEntity, completed: Boolean) {
        if (task.isCompleted == completed) return
        viewModelScope.launch {
            val outcome = repository.setCompleted(task, completed)
            val token = rememberUndo(
                if (outcome == TaskCompletionOutcome.RESCHEDULED) {
                    UndoOperation.RestoreTask(task)
                } else {
                    UndoOperation.SetCompleted(task.id, task.isCompleted)
                },
            )
            _events.send(
                TaskListEvent(
                    messageRes = when {
                        outcome == TaskCompletionOutcome.RESCHEDULED ->
                            R.string.task_recurrence_rescheduled_message
                        completed -> R.string.task_completed_message
                        else -> R.string.task_reopened_message
                    },
                    messageArgs = listOf(task.title),
                    undoToken = token,
                ),
            )
        }
    }

    fun setArchived(task: TaskEntity, archived: Boolean) {
        if (task.isArchived == archived) return
        viewModelScope.launch {
            repository.setArchived(task, archived)
            val token = rememberUndo(UndoOperation.SetArchived(task.id, task.isArchived))
            _events.send(
                TaskListEvent(
                    messageRes = if (archived) {
                        R.string.task_archived_message
                    } else {
                        R.string.task_unarchived_message
                    },
                    messageArgs = listOf(task.title),
                    undoToken = token,
                ),
            )
        }
    }

    fun delete(task: TaskEntity) {
        viewModelScope.launch {
            repository.delete(task)
            val token = rememberUndo(UndoOperation.RestoreDeleted(task))
            _events.send(
                TaskListEvent(
                    messageRes = R.string.task_deleted_message,
                    messageArgs = listOf(task.title),
                    undoToken = token,
                ),
            )
        }
    }

    fun snooze(task: TaskEntity, nowMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val snoozedUntil = calculateSnoozedDueAt(task.dueAt, nowMillis)
            repository.setDueAt(task.id, snoozedUntil)
            val token = rememberUndo(UndoOperation.RestoreDueAt(task.id, task.dueAt))
            _events.send(
                TaskListEvent(
                    messageRes = R.string.task_snoozed_message,
                    messageArgs = listOf(task.title),
                    undoToken = token,
                ),
            )
        }
    }

    fun undo(token: Long) {
        val operation = undoOperations.remove(token) ?: return
        viewModelScope.launch {
            when (operation) {
                is UndoOperation.RestoreDeleted -> repository.restore(operation.task)
                is UndoOperation.RestoreTask -> repository.save(operation.task)
                is UndoOperation.RestoreDueAt ->
                    repository.setDueAt(operation.taskId, operation.previousDueAt)
                is UndoOperation.SetCompleted ->
                    repository.setCompleted(operation.taskId, operation.previousCompleted)
                is UndoOperation.SetArchived ->
                    repository.getById(operation.taskId)?.let {
                        repository.setArchived(it, operation.previousArchived)
                    }
            }
        }
    }

    fun discardUndo(token: Long) {
        undoOperations.remove(token)
    }

    private fun rememberUndo(operation: UndoOperation): Long {
        val token = nextUndoToken++
        undoOperations[token] = operation
        return token
    }
}

data class TaskListEvent(
    @param:StringRes val messageRes: Int,
    val messageArgs: List<Any> = emptyList(),
    val undoToken: Long? = null,
)

private sealed interface UndoOperation {
    data class RestoreDeleted(val task: TaskEntity) : UndoOperation
    data class RestoreTask(val task: TaskEntity) : UndoOperation
    data class RestoreDueAt(val taskId: Long, val previousDueAt: Long?) : UndoOperation
    data class SetCompleted(val taskId: Long, val previousCompleted: Boolean) : UndoOperation
    data class SetArchived(val taskId: Long, val previousArchived: Boolean) : UndoOperation
}

internal fun calculateSnoozedDueAt(
    dueAt: Long?,
    nowMillis: Long,
    zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): Long {
    val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val dueDate = dueAt?.let {
        java.time.Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate()
    }
    val baseDate = dueDate?.takeIf { !it.isBefore(today) } ?: today
    return baseDate.plusDays(1)
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()
}

data class ParsedVoiceTask(
    val title: String,
    val placeId: Long?,
)

internal fun parseVoiceTask(
    spokenText: String,
    savedPlaces: List<PlaceEntity>,
): ParsedVoiceTask {
    val text = spokenText.trim()
    val lowercaseText = text.lowercase()
    savedPlaces
        .filter { !it.name.isNullOrBlank() }
        .sortedByDescending { it.name.orEmpty().length }
        .forEach { place ->
            val name = place.name.orEmpty().trim().lowercase()
            val markers = voicePlaceNameVariants(name).flatMap { variant ->
                listOf(
                    " рядом с $variant",
                    " возле $variant",
                    " около $variant",
                    " у $variant",
                    " near $variant",
                    " at $variant",
                )
            }
            val marker = markers.firstOrNull(lowercaseText::endsWith)
            if (marker != null) {
                val title = text.dropLast(marker.length).trim().trimEnd(',', '.', ';', ':')
                if (title.isNotEmpty()) return ParsedVoiceTask(title, place.id)
            }
        }
    return ParsedVoiceTask(text, null)
}

private fun voicePlaceNameVariants(name: String): Set<String> = buildSet {
    add(name)
    add(
        when (name) {
            "дом" -> "дома"
            "работа" -> "работы"
            "магазин" -> "магазина"
            "родители" -> "родителей"
            else -> when {
                name.endsWith("а") -> name.dropLast(1) + "ы"
                name.endsWith("я") -> name.dropLast(1) + "и"
                name.endsWith("ь") -> name.dropLast(1) + "я"
                name.lastOrNull()?.isLetter() == true -> name + "а"
                else -> name
            }
        },
    )
}
