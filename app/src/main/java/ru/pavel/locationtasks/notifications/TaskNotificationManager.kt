package ru.pavel.locationtasks.notifications

import android.Manifest
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
import ru.pavel.locationtasks.data.TaskEntity
import javax.inject.Inject
import javax.inject.Singleton

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

    fun showNearbyTask(task: TaskEntity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return false
        if (context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
            ?.importance == NotificationManager.IMPORTANCE_NONE
        ) return false

        val contentIntent = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TASK_ID, task.id)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val completeIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            Intent(context, TaskActionReceiver::class.java).apply {
                action = TaskActionReceiver.ACTION_COMPLETE
                putExtra(TaskActionReceiver.EXTRA_TASK_ID, task.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val place = task.address?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.notification_selected_place)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(context.getString(R.string.notification_nearby, place))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        context.getString(
                            R.string.notification_nearby_details,
                            task.title,
                            place,
                        ),
                    ),
            )
            .setContentIntent(contentIntent)
            .addAction(
                0,
                context.getString(R.string.notification_complete_action),
                completeIntent,
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notificationManager.notify(task.id.hashCode(), notification)
        return true
    }

    companion object {
        const val CHANNEL_ID = "nearby_tasks"
    }
}
