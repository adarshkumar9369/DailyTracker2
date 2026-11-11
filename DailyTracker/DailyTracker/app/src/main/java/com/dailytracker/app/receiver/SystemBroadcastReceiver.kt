package com.dailytracker.app.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailytracker.app.DailyTrackerApp
import com.dailytracker.app.worker.AlarmReconciliationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SystemBroadcastReceiver : BroadcastReceiver() {

    private val supportedActions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_LOCKED_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_DATE_CHANGED,
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
    )

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in supportedActions) return

        val pendingResult = goAsync()
        val app = context.applicationContext as DailyTrackerApp
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                app.container.taskRepository.reconcileAllAlarmsAndHistory()
                AlarmReconciliationWorker.enqueueImmediateAndPeriodic(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
