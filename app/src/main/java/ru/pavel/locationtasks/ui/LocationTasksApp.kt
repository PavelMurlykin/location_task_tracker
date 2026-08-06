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
    const val TASK = "task/{taskId}?initialTitle={initialTitle}" +
        "&initialLatitude={initialLatitude}&initialLongitude={initialLongitude}"
    const val MAP = "map"
    const val SETTINGS = "settings"
    const val CATEGORIES = "categories"
    const val PRIVACY = "privacy"

    fun task(taskId: Long) = "task/$taskId"
    fun newTask(initialTitle: String) = "task/0?initialTitle=${Uri.encode(initialTitle)}"
    fun newTaskAt(latitude: Double, longitude: Double) =
        "task/0?initialLatitude=$latitude&initialLongitude=$longitude"
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
                onOpenMap = { navController.navigate(Routes.MAP) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.MAP) {
            TaskMapScreen(
                onClose = navController::popBackStack,
                onOpenTask = { navController.navigate(Routes.task(it)) },
                onCreateTaskAt = { latitude, longitude ->
                    navController.navigate(Routes.newTaskAt(latitude, longitude))
                },
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
                navArgument("initialLatitude") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("initialLongitude") {
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
                onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }
        composable(Routes.CATEGORIES) {
            CategoryManagementScreen(onClose = navController::popBackStack)
        }
        composable(Routes.PRIVACY) {
            PrivacyPolicyScreen(onClose = navController::popBackStack)
        }
    }
}
