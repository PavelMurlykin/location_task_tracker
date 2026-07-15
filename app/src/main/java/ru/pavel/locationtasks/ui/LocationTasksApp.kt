package ru.pavel.locationtasks.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private object Routes {
    const val TASKS = "tasks"
    const val TASK = "task/{taskId}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"

    fun task(taskId: Long) = "task/$taskId"
}

@Composable
fun LocationTasksApp(
    requestedTaskId: Long?,
    onTaskRequestConsumed: () -> Unit,
) {
    val navController = rememberNavController()

    LaunchedEffect(requestedTaskId) {
        requestedTaskId?.let { taskId ->
            navController.navigate(Routes.task(taskId)) { launchSingleTop = true }
            onTaskRequestConsumed()
        }
    }

    NavHost(navController = navController, startDestination = Routes.TASKS) {
        composable(Routes.TASKS) {
            TaskListScreen(
                onCreateTask = { navController.navigate(Routes.task(0)) },
                onOpenTask = { navController.navigate(Routes.task(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.TASK,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) {
            TaskEditorScreen(onClose = navController::popBackStack)
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
