package com.dailytracker.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dailytracker.app.MainActivity
import com.dailytracker.app.R
import com.dailytracker.app.alarm.AlarmActionReceiver
import com.dailytracker.app.ui.alarm.AlarmActivity

class TaskNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID_ALARM = "daily_tracker_persistent_alarm_channel"
        const val CHANNEL_ID_STATUS = "daily_tracker_status_channel"
        const val NOTIFICATION_ID_BASE = 20000
        const val REMINDER_NOTIFICATION_ID_BASE = 50000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                context.getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_alarm_desc)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
                setSound(null, null)
            }

            val statusChannel = NotificationChannel(
                CHANNEL_ID_STATUS,
                context.getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_reminder_desc)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(statusChannel)
        }
    }

    fun buildAlarmForegroundNotification(
        taskId: Long,
        taskTitle: String,
        taskDescription: String,
        cycleDateStr: String,
        defaultSnoozeMinutes: Int,
        repeatIntervalMinutes: Int,
        repeatCount: Int
    ): Notification {
        createNotificationChannels()

        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmActivity.EXTRA_TASK_ID, taskId)
            putExtra(AlarmActivity.EXTRA_TASK_TITLE, taskTitle)
            putExtra(AlarmActivity.EXTRA_TASK_DESC, taskDescription)
            putExtra(AlarmActivity.EXTRA_CYCLE_DATE, cycleDateStr)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completeIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_COMPLETE_TASK
            putExtra(AlarmActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(AlarmActionReceiver.EXTRA_CYCLE_DATE, cycleDateStr)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 1).toInt(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_SNOOZE_ALARM
            putExtra(AlarmActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(AlarmActionReceiver.EXTRA_SNOOZE_MINUTES, defaultSnoozeMinutes)
            putExtra(AlarmActionReceiver.EXTRA_CYCLE_DATE, cycleDateStr)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 2).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = AlarmActionReceiver.ACTION_NOTIFICATION_DISMISSED
            putExtra(AlarmActionReceiver.EXTRA_TASK_ID, taskId)
            putExtra(AlarmActionReceiver.EXTRA_CYCLE_DATE, cycleDateStr)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (taskId * 10 + 3).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = buildString {
            if (taskDescription.isNotBlank()) {
                append(taskDescription).append(" • ")
            }
            append("Repeats every $repeatIntervalMinutes min until marked Completed")
            if (repeatCount > 0) {
                append(" (Repeat #$repeatCount)")
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Task Due: $taskTitle")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(0, "Complete Task", completePendingIntent)
            .addAction(0, "Snooze (${defaultSnoozeMinutes}m)", snoozePendingIntent)
            .build()
    }

    fun showPersistentUncompletedWarning(
        taskId: Long,
        taskTitle: String,
        nextRepeatMinutes: Int
    ) {
        createNotificationChannels()
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            (taskId + 9000).toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_STATUS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Still Pending: $taskTitle")
            .setContentText("Dismissing does not complete this task. Alarm will ring again in $nextRepeatMinutes min.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            notificationManager.notify((REMINDER_NOTIFICATION_ID_BASE + taskId).toInt(), notification)
        }
    }

    fun cancelTaskNotifications(taskId: Long) {
        notificationManager.cancel((NOTIFICATION_ID_BASE + taskId).toInt())
        notificationManager.cancel((REMINDER_NOTIFICATION_ID_BASE + taskId).toInt())
    }
}
