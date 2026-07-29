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
import ru.pavel.locationtasks.data.TaskEntity
import ru.pavel.locationtasks.data.TaskRepository
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: TaskRepository,
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

    fun setCompleted(task: TaskEntity, completed: Boolean) {
        if (task.isCompleted == completed) return
        viewModelScope.launch {
            repository.setCompleted(task, completed)
            val token = rememberUndo(UndoOperation.SetCompleted(task.id, task.isCompleted))
            _events.send(
                TaskListEvent(
                    messageRes = if (completed) {
                        R.string.task_completed_message
                    } else {
                        R.string.task_reopened_message
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
                is UndoOperation.RestoreDueAt ->
                    repository.setDueAt(operation.taskId, operation.previousDueAt)
                is UndoOperation.SetCompleted ->
                    repository.setCompleted(operation.taskId, operation.previousCompleted)
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
    data class RestoreDueAt(val taskId: Long, val previousDueAt: Long?) : UndoOperation
    data class SetCompleted(val taskId: Long, val previousCompleted: Boolean) : UndoOperation
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
