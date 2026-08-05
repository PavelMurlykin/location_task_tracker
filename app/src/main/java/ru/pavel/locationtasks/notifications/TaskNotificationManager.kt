package ru.pavel.locationtasks.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.pavel.locationtasks.MainActivity
import ru.pavel.locationtasks.R
import ru.pavel.locationtasks.data.GeofenceTransition
import ru.pavel.locationtasks.data.TaskEntity
import javax.inject.Inject
import javax.inject.Singleton

enum class ReminderKind {
    LOCATION,
    DUE,
}

@Singleton
class TaskNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun showLocationTask(
        task: TaskEntity,
        transition: GeofenceTransition,
    ): Boolean {
        val place = task.address?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.notification_selected_place)
        val text = when (transition) {
            GeofenceTransition.ENTER ->
                context.getString(R.string.notification_nearby, place)
            GeofenceTransition.EXIT ->
                context.getString(R.string.notification_left_place, place)
        }
        val details = when (transition) {
            GeofenceTransition.ENTER -> context.getString(
                R.string.notification_nearby_details,
                task.title,
                place,
            )
            GeofenceTransition.EXIT -> context.getString(
                R.string.notification_left_place_details,
                task.title,
                place,
            )
        }
        return showTaskNotification(
            task = task,
            kind = ReminderKind.LOCATION,
            transition = transition,
            contentText = text,
            details = details,
        )
    }

    fun showDueTask(task: TaskEntity): Boolean = showTaskNotification(
        task = task,
        kind = ReminderKind.DUE,
        transition = null,
        contentText = context.getString(R.string.notification_due),
        details = context.getString(R.string.notification_due_details, task.title),
    )

    @SuppressLint("MissingPermission")
    private fun showTaskNotification(
        task: TaskEntity,
        kind: ReminderKind,
        transition: GeofenceTransition?,
        contentText: String,
        details: String,
    ): Boolean {
        if (!canShowNotifications()) return false

        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode(task.id, ACTION_CODE_OPEN),
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TASK_ID, task.id)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(contentIntent)
            .addAction(
                0,
                context.getString(R.string.notification_snooze_15),
                actionIntent(task.id, TaskActionReceiver.ACTION_SNOOZE_15, kind, transition),
            )
            .addAction(
                0,
                context.getString(R.string.notification_snooze_60),
                actionIntent(task.id, TaskActionReceiver.ACTION_SNOOZE_60, kind, transition),
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification())
            .apply {
                if (kind == ReminderKind.LOCATION &&
                    transition == GeofenceTransition.ENTER
                ) {
                    addAction(
                        0,
                        context.getString(R.string.notification_next_visit),
                        actionIntent(
                            task.id,
                            TaskActionReceiver.ACTION_NEXT_VISIT,
                            kind,
                            transition,
                        ),
                    )
                } else {
                    addAction(
                        0,
                        context.getString(R.string.notification_complete_action),
                        actionIntent(
                            task.id,
                            TaskActionReceiver.ACTION_COMPLETE,
                            kind,
                            transition,
                        ),
                    )
                }
            }
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(task.id), notification)
        }.isSuccess
    }

    private fun publicNotification() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(R.string.notification_hidden_details))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()

    private fun actionIntent(
        taskId: Long,
        action: String,
        kind: ReminderKind,
        transition: GeofenceTransition?,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(taskId, action.hashCode()),
        Intent(context, TaskActionReceiver::class.java).apply {
            this.action = action
            putExtra(TaskActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskActionReceiver.EXTRA_REMINDER_KIND, kind.name)
            transition?.let {
                putExtra(TaskActionReceiver.EXTRA_TRANSITION, it.name)
            }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canShowNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return false
        return context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE
    }

    companion object {
        const val CHANNEL_ID = "nearby_tasks"
        private const val ACTION_CODE_OPEN = 1

        fun notificationId(taskId: Long): Int = taskId.hashCode()
        private fun requestCode(taskId: Long, actionCode: Int): Int =
            31 * taskId.hashCode() + actionCode
    }
}
