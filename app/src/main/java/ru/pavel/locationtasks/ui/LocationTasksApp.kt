package ru.pavel.locationtasks.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object Routes {
    const val TASKS = "tasks"
    const val TASK = "task/{taskId}?initialTitle={initialTitle}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun task(taskId: Long) = "task/$taskId"
    fun newTask(initialTitle: String) = "task/0?initialTitle=${Uri.encode(initialTitle)}"
}

@Composable
fun LocationTasksApp(
    requestedTaskId: Long?,
    onTaskRequestConsumed: () -> Unit,
    sharedTaskTitle: String?,
    onSharedTaskConsumed: () -> Unit,
) {
    val navController = rememberNavController()

    LaunchedEffect(requestedTaskId) {
        requestedTaskId?.let { taskId ->
            navController.navigate(Routes.task(taskId)) { launchSingleTop = true }
            onTaskRequestConsumed()
        }
    }
    LaunchedEffect(sharedTaskTitle) {
        sharedTaskTitle?.let { title ->
            navController.navigate(Routes.newTask(title)) { launchSingleTop = true }
            onSharedTaskConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.TASKS) {
        composable(Routes.TASKS) {
            TaskListScreen(
                onCreateTask = { initialTitle ->
                    navController.navigate(
                        if (initialTitle.isBlank()) {
                            Routes.task(0)
                        } else {
                            Routes.newTask(initialTitle)
                        },
                    )
                },
                onOpenTask = { navController.navigate(Routes.task(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.TASK,
            arguments = listOf(
                navArgument("taskId") { type = NavType.LongType },
                navArgument("initialTitle") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            TaskEditorScreen(
                onClose = navController::popBackStack,
                onOpenTask = { taskId ->
                    val currentDestinationId = navController.currentDestination?.id
                    navController.navigate(Routes.task(taskId)) {
                        currentDestinationId?.let {
                            popUpTo(it) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onClose = navController::popBackStack,
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }
        composable(Routes.PRIVACY) {
            PrivacyPolicyScreen(onClose = navController::popBackStack)
        }
    }
}
