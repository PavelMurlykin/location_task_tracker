package ru.pavel.locationtasks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import ru.pavel.locationtasks.ui.LocationTasksApp
import ru.pavel.locationtasks.ui.theme.LocationTasksTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestedTaskId = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIntent(intent)
        setContent {
            val taskId by requestedTaskId.collectAsState()
            LocationTasksTheme {
                LocationTasksApp(
                    requestedTaskId = taskId,
                    onTaskRequestConsumed = { requestedTaskId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        requestedTaskId.value = intent
            ?.getLongExtra(EXTRA_TASK_ID, -1L)
            ?.takeIf { it > 0 }
    }

    companion object {
        const val EXTRA_TASK_ID = "open_task_id"
    }
}
