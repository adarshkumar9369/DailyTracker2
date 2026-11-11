package com.dailytracker.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailytracker.app.DailyTrackerApp
import java.util.concurrent.TimeUnit

class AlarmReconciliationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as DailyTrackerApp
            app.container.taskRepository.reconcileAllAlarmsAndHistory()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_WORK = "daily_tracker_periodic_alarm_reconciliation"
        private const val UNIQUE_IMMEDIATE_WORK = "daily_tracker_immediate_alarm_reconciliation"

        fun enqueueImmediateAndPeriodic(context: Context) {
            val workManager = WorkManager.getInstance(context)

            val immediateRequest = OneTimeWorkRequestBuilder<AlarmReconciliationWorker>().build()
            workManager.enqueueUniqueWork(
                UNIQUE_IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                immediateRequest
            )

            val periodicRequest = PeriodicWorkRequestBuilder<AlarmReconciliationWorker>(
                6, TimeUnit.HOURS
            ).build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }
}
