package com.dailytracker.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailytracker.app.DailyTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class AlarmActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COMPLETE_TASK = "com.dailytracker.app.ACTION_COMPLETE_TASK"
        const val ACTION_SNOOZE_ALARM = "com.dailytracker.app.ACTION_SNOOZE_ALARM"
        const val ACTION_NOTIFICATION_DISMISSED = "com.dailytracker.app.ACTION_NOTIFICATION_DISMISSED"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_CYCLE_DATE = "extra_cycle_date"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) return

        val cycleDate = intent.getStringExtra(EXTRA_CYCLE_DATE)
            ?.takeIf { it.isNotBlank() }
            ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        val pendingResult = goAsync()
        val app = context.applicationContext as DailyTrackerApp
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE_TASK -> {
                        app.container.taskRepository.markTaskCompleted(
                            taskId = taskId,
                            targetDateStr = cycleDate
                        )
                    }

                    ACTION_SNOOZE_ALARM -> {
                        val defaultSnooze = app.container.settingsManager.getSettingsOnce().defaultSnoozeMinutes
                        val snoozeMins = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, defaultSnooze)
                        app.container.taskRepository.snoozeTaskAlarm(
                            taskId = taskId,
                            snoozeMinutes = snoozeMins,
                            cycleDateStr = cycleDate
                        )
                    }

                    ACTION_NOTIFICATION_DISMISSED -> {
                        AlarmService.stopAlarmIfMatching(context.applicationContext, taskId)
                        val task = app.container.taskRepository.getTaskById(taskId)
                        val settings = app.container.settingsManager.getSettingsOnce()
                        if (task != null && task.lastCompletedDate != cycleDate) {
                            app.container.notificationManager.showPersistentUncompletedWarning(
                                taskId = task.id,
                                taskTitle = task.title,
                                nextRepeatMinutes = settings.repeatIntervalMinutes
                            )
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
