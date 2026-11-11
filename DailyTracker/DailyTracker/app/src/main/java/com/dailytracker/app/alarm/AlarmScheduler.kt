package com.dailytracker.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dailytracker.app.MainActivity
import com.dailytracker.app.data.local.entity.TaskEntity

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleTaskAlarm(
        task: TaskEntity,
        triggerAtMillis: Long,
        cycleDateStr: String
    ) {
        if (!task.alarmEnabled) {
            cancelTaskAlarm(task.id)
            return
        }

        val pendingIntent = buildTriggerPendingIntent(
            taskId = task.id,
            taskTitle = task.title,
            cycleDateStr = cycleDateStr,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canScheduleExact) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (se: SecurityException) {
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }

    fun cancelTaskAlarm(taskId: Long) {
        val existingPendingIntent = buildTriggerPendingIntent(
            taskId = taskId,
            taskTitle = "",
            cycleDateStr = "",
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existingPendingIntent != null) {
            runCatching {
                alarmManager.cancel(existingPendingIntent)
                existingPendingIntent.cancel()
            }
        }
    }

    private fun buildTriggerPendingIntent(
        taskId: Long,
        taskTitle: String,
        cycleDateStr: String,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            putExtra(AlarmReceiver.EXTRA_TASK_TITLE, taskTitle)
            putExtra(AlarmReceiver.EXTRA_CYCLE_DATE, cycleDateStr)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            flags
        )
    }
}
