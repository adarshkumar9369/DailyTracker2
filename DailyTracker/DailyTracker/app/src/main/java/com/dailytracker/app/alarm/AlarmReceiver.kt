package com.dailytracker.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.dailytracker.app.ACTION_TRIGGER_ALARM"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_CYCLE_DATE = "extra_cycle_date"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_ALARM) return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) return
        val cycleDate = intent.getStringExtra(EXTRA_CYCLE_DATE)

        AlarmService.startAlarm(
            context = context.applicationContext,
            taskId = taskId,
            cycleDateStr = cycleDate
        )
    }
}
