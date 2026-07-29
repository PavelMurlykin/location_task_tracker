package ru.pavel.locationtasks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.location.GeofenceCoordinator
import ru.pavel.locationtasks.ui.LocationTasksApp
import ru.pavel.locationtasks.ui.theme.LocationTasksTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var geofenceCoordinator: GeofenceCoordinator
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

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            geofenceCoordinator.reconcileAll()
        }
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
