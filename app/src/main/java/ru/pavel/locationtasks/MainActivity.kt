package ru.pavel.locationtasks

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.pavel.locationtasks.location.GeofenceCoordinator
import ru.pavel.locationtasks.data.UserPreferencesRepository
import ru.pavel.locationtasks.ui.AppLockLoadingScreen
import ru.pavel.locationtasks.ui.AppLockScreen
import ru.pavel.locationtasks.ui.canUseDeviceAuthentication
import ru.pavel.locationtasks.ui.requestDeviceAuthentication
import ru.pavel.locationtasks.ui.LocationTasksApp
import ru.pavel.locationtasks.ui.extractSharedTaskTitle
import ru.pavel.locationtasks.ui.theme.LocationTasksTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var geofenceCoordinator: GeofenceCoordinator
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    private val requestedTaskId = MutableStateFlow<Long?>(null)
    private val sharedTaskTitle = MutableStateFlow<String?>(null)
    private val appLockEnabled = MutableStateFlow<Boolean?>(null)
    private val appUnlocked = MutableStateFlow(false)
    private var stoppedAtElapsedRealtime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIntent(intent)
        lifecycleScope.launch {
            preferencesRepository.securityPreferences.collect { preferences ->
                val previous = appLockEnabled.value
                if (!preferences.appLockEnabled || previous == false) {
                    appUnlocked.value = true
                }
                appLockEnabled.value = preferences.appLockEnabled
                if (preferences.appLockEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent {
            val taskId by requestedTaskId.collectAsState()
            val sharedTitle by sharedTaskTitle.collectAsState()
            val lockEnabled by appLockEnabled.collectAsState()
            val unlocked by appUnlocked.collectAsState()
            LocationTasksTheme {
                when {
                    lockEnabled == null -> AppLockLoadingScreen()
                    lockEnabled == true && !unlocked -> AppLockScreen(::requestDeviceUnlock)
                    else -> LocationTasksApp(
                        requestedTaskId = taskId,
                        onTaskRequestConsumed = { requestedTaskId.value = null },
                        sharedTaskTitle = sharedTitle,
                        onSharedTaskConsumed = { sharedTaskTitle.value = null },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (appLockEnabled.value == true &&
            stoppedAtElapsedRealtime > 0 &&
            SystemClock.elapsedRealtime() - stoppedAtElapsedRealtime >= LOCK_TIMEOUT_MILLIS
        ) {
            appUnlocked.value = false
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

    override fun onStop() {
        stoppedAtElapsedRealtime = SystemClock.elapsedRealtime()
        super.onStop()
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

    private fun requestDeviceUnlock() {
        if (!canUseDeviceAuthentication(this)) {
            lifecycleScope.launch { preferencesRepository.setAppLockEnabled(false) }
            appUnlocked.value = true
            return
        }
        requestDeviceAuthentication(this) {
            appUnlocked.value = true
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "open_task_id"
        private const val LOCK_TIMEOUT_MILLIS = 30_000L
    }
}
