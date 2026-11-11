package com.dailytracker.app

import android.app.Application
import com.dailytracker.app.alarm.AlarmScheduler
import com.dailytracker.app.data.local.DailyTrackerDatabase
import com.dailytracker.app.data.preferences.SettingsManager
import com.dailytracker.app.data.repository.TaskRepository
import com.dailytracker.app.notification.TaskNotificationManager
import com.dailytracker.app.worker.AlarmReconciliationWorker

class AppContainer(context: Application) {
    val database: DailyTrackerDatabase by lazy { DailyTrackerDatabase.getInstance(context) }
    val settingsManager: SettingsManager by lazy { SettingsManager(context) }
    val notificationManager: TaskNotificationManager by lazy { TaskNotificationManager(context) }
    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(context) }
    val taskRepository: TaskRepository by lazy {
        TaskRepository(
            appContext = context,
            taskDao = database.taskDao(),
            alarmScheduler = alarmScheduler,
            settingsManager = settingsManager,
            notificationManager = notificationManager
        )
    }
}

class DailyTrackerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationManager.createNotificationChannels()
        AlarmReconciliationWorker.enqueueImmediateAndPeriodic(this)
    }
}
