package com.pamurlykin.locationtasks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.pamurlykin.locationtasks.data.TaskEntity
import com.pamurlykin.locationtasks.data.TaskRepository
import javax.inject.Inject

@HiltViewModel
class TaskMapViewModel @Inject constructor(
    repository: TaskRepository,
) : ViewModel() {
    val tasks: StateFlow<List<TaskEntity>> = repository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
}
