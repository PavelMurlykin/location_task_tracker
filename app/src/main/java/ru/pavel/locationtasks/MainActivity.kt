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
import ru.pavel.locationtasks.ui.extractSharedTaskTitle
import ru.pavel.locationtasks.ui.theme.LocationTasksTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var geofenceCoordinator: GeofenceCoordinator
    private val requestedTaskId = MutableStateFlow<Long?>(null)
    private val sharedTaskTitle = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIntent(intent)
        setContent {
            val taskId by requestedTaskId.collectAsState()
            val sharedTitle by sharedTaskTitle.collectAsState()
            LocationTasksTheme {
                LocationTasksApp(
                    requestedTaskId = taskId,
                    onTaskRequestConsumed = { requestedTaskId.value = null },
                    sharedTaskTitle = sharedTitle,
                    onSharedTaskConsumed = { sharedTaskTitle.value = null },
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
        if (intent?.action == Intent.ACTION_SEND &&
            intent.type?.startsWith("text/") == true
        ) {
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            sharedTaskTitle.value = extractSharedTaskTitle(subject, sharedText)
        } else {
            sharedTaskTitle.value = null
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "open_task_id"
    }
}
