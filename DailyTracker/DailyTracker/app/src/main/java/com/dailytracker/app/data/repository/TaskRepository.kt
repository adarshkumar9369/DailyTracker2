package com.dailytracker.app.data.repository

import android.content.Context
import com.dailytracker.app.alarm.AlarmScheduler
import com.dailytracker.app.alarm.AlarmService
import com.dailytracker.app.data.local.dao.TaskDao
import com.dailytracker.app.data.local.entity.TaskEntity
import com.dailytracker.app.data.local.entity.TaskHistoryEntity
import com.dailytracker.app.data.model.RecurrenceType
import com.dailytracker.app.data.model.SnoozeStatus
import com.dailytracker.app.data.model.TaskCompletionStatus
import com.dailytracker.app.data.preferences.SettingsManager
import com.dailytracker.app.notification.TaskNotificationManager
import com.dailytracker.app.util.TaskScheduleCalculator
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskRepository(
    private val appContext: Context,
    private val taskDao: TaskDao,
    private val alarmScheduler: AlarmScheduler,
    private val settingsManager: SettingsManager,
    private val notificationManager: TaskNotificationManager
) {
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val allTasksFlow: Flow<List<TaskEntity>> = taskDao.observeAllTasks()
    val allHistoryFlow: Flow<List<TaskHistoryEntity>> = taskDao.observeAllHistory()

    fun observeHistoryBetween(startDateStr: String, endDateStr: String): Flow<List<TaskHistoryEntity>> =
        taskDao.observeHistoryBetweenDates(startDateStr, endDateStr)

    suspend fun getTaskById(taskId: Long): TaskEntity? = taskDao.getTaskById(taskId)

    suspend fun saveTask(
        existingId: Long? = null,
        title: String,
        description: String,
        scheduledDateStr: String,
        alarmHour: Int,
        alarmMinute: Int,
        recurrenceType: RecurrenceType,
        selectedWeekdays: Set<Int>,
        alarmEnabled: Boolean
    ): Long {
        val nowMillis = System.currentTimeMillis()
        val settings = settingsManager.getSettingsOnce()
        val weekdaysCsv = TaskScheduleCalculator.serializeWeekdays(selectedWeekdays)

        val existing = if (existingId != null && existingId > 0L) taskDao.getTaskById(existingId) else null

        if (existing != null) {
            alarmScheduler.cancelTaskAlarm(existing.id)
            AlarmService.stopAlarmIfMatching(appContext, existing.id)
            notificationManager.cancelTaskNotifications(existing.id)
        }

        val draftEntity = TaskEntity(
            id = existing?.id ?: 0L,
            title = title.trim(),
            description = description.trim(),
            scheduledDate = scheduledDateStr,
            alarmHour = alarmHour.coerceIn(0, 23),
            alarmMinute = alarmMinute.coerceIn(0, 59),
            recurrenceType = recurrenceType,
            selectedWeekdays = weekdaysCsv,
            completionStatus = TaskCompletionStatus.PENDING,
            completionTimestamp = null,
            lastCompletedDate = existing?.lastCompletedDate,
            alarmEnabled = alarmEnabled,
            snoozeStatus = SnoozeStatus.NONE,
            snoozedUntilTimestamp = null,
            nextTriggerTimestamp = null,
            activeCycleDate = null,
            repeatCount = 0,
            createdAt = existing?.createdAt ?: nowMillis,
            updatedAt = nowMillis
        )

        val nextTrigger = TaskScheduleCalculator.computeNextTriggerMillis(
            task = draftEntity,
            nowMillis = nowMillis,
            repeatIntervalMinutes = settings.repeatIntervalMinutes
        )

        val entityToSave = draftEntity.copy(nextTriggerTimestamp = nextTrigger)
        val savedId = if (existing == null) {
            taskDao.insertTask(entityToSave)
        } else {
            taskDao.updateTask(entityToSave)
            existing.id
        }

        val finalEntity = entityToSave.copy(id = savedId)
        if (finalEntity.alarmEnabled && nextTrigger != null) {
            val cycleDate = Instant.ofEpochMilli(nextTrigger)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(isoFormatter)
            alarmScheduler.scheduleTaskAlarm(finalEntity, nextTrigger, cycleDate)
        } else {
            alarmScheduler.cancelTaskAlarm(savedId)
        }

        return savedId
    }

    suspend fun setTaskAlarmEnabled(taskId: Long, enabled: Boolean) {
        val task = taskDao.getTaskById(taskId) ?: return
        val nowMillis = System.currentTimeMillis()
        val settings = settingsManager.getSettingsOnce()

        if (!enabled) {
            alarmScheduler.cancelTaskAlarm(taskId)
            AlarmService.stopAlarmIfMatching(appContext, taskId)
            notificationManager.cancelTaskNotifications(taskId)
            taskDao.updateTask(
                task.copy(
                    alarmEnabled = false,
                    snoozeStatus = SnoozeStatus.NONE,
                    snoozedUntilTimestamp = null,
                    nextTriggerTimestamp = null,
                    repeatCount = 0,
                    updatedAt = nowMillis
                )
            )
        } else {
            val updatedDraft = task.copy(
                alarmEnabled = true,
                snoozeStatus = SnoozeStatus.NONE,
                snoozedUntilTimestamp = null,
                repeatCount = 0,
                updatedAt = nowMillis
            )
            val nextTrigger = TaskScheduleCalculator.computeNextTriggerMillis(
                task = updatedDraft,
                nowMillis = nowMillis,
                repeatIntervalMinutes = settings.repeatIntervalMinutes
            )
            val finalTask = updatedDraft.copy(nextTriggerTimestamp = nextTrigger)
            taskDao.updateTask(finalTask)
            if (nextTrigger != null) {
                val cycleDate = Instant.ofEpochMilli(nextTrigger)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(isoFormatter)
                alarmScheduler.scheduleTaskAlarm(finalTask, nextTrigger, cycleDate)
            }
        }
    }

    suspend fun markTaskCompleted(
        taskId: Long,
        targetDateStr: String = LocalDate.now().format(isoFormatter)
    ) {
        val task = taskDao.getTaskById(taskId) ?: return
        val nowMillis = System.currentTimeMillis()
        val settings = settingsManager.getSettingsOnce()

        AlarmService.stopAlarmIfMatching(appContext, taskId)
        notificationManager.cancelTaskNotifications(taskId)
        alarmScheduler.cancelTaskAlarm(taskId)

        val existingHistory = taskDao.getHistoryEntry(taskId, targetDateStr)
        val historyEntry = TaskHistoryEntity(
            historyId = existingHistory?.historyId ?: 0L,
            taskId = task.id,
            taskTitleSnapshot = task.title,
            dateStr = targetDateStr,
            scheduledHour = task.alarmHour,
            scheduledMinute = task.alarmMinute,
            status = TaskCompletionStatus.COMPLETED,
            completedAtTimestamp = nowMillis
        )
        taskDao.upsertHistoryEntry(historyEntry)

        val newCompletionStatus = if (task.recurrenceType == RecurrenceType.ONE_TIME) {
            TaskCompletionStatus.COMPLETED
        } else {
            TaskCompletionStatus.PENDING
        }

        val updatedDraft = task.copy(
            completionStatus = newCompletionStatus,
            completionTimestamp = nowMillis,
            lastCompletedDate = targetDateStr,
            snoozeStatus = SnoozeStatus.NONE,
            snoozedUntilTimestamp = null,
            activeCycleDate = null,
            repeatCount = 0,
            updatedAt = nowMillis
        )

        val nextTrigger = TaskScheduleCalculator.computeNextTriggerMillis(
            task = updatedDraft,
            nowMillis = nowMillis,
            repeatIntervalMinutes = settings.repeatIntervalMinutes
        )

        val finalTask = updatedDraft.copy(nextTriggerTimestamp = nextTrigger)
        taskDao.updateTask(finalTask)

        if (finalTask.alarmEnabled && nextTrigger != null) {
            val nextCycleDate = Instant.ofEpochMilli(nextTrigger)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(isoFormatter)
            alarmScheduler.scheduleTaskAlarm(finalTask, nextTrigger, nextCycleDate)
        }
    }

    suspend fun markTaskUncompleted(
        taskId: Long,
        targetDateStr: String = LocalDate.now().format(isoFormatter)
    ) {
        val task = taskDao.getTaskById(taskId) ?: return
        val nowMillis = System.currentTimeMillis()
        val settings = settingsManager.getSettingsOnce()

        taskDao.deleteHistoryEntry(taskId, targetDateStr)

        val updatedDraft = task.copy(
            completionStatus = TaskCompletionStatus.PENDING,
            completionTimestamp = null,
            lastCompletedDate = if (task.lastCompletedDate == targetDateStr) null else task.lastCompletedDate,
            snoozeStatus = SnoozeStatus.NONE,
            snoozedUntilTimestamp = null,
            updatedAt = nowMillis
        )

        val nextTrigger = TaskScheduleCalculator.computeNextTriggerMillis(
            task = updatedDraft,
            nowMillis = nowMillis,
            repeatIntervalMinutes = settings.repeatIntervalMinutes
        )
        val finalTask = updatedDraft.copy(nextTriggerTimestamp = nextTrigger)
        taskDao.updateTask(finalTask)

        if (finalTask.alarmEnabled && nextTrigger != null) {
            val cycleDate = Instant.ofEpochMilli(nextTrigger)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(isoFormatter)
            alarmScheduler.scheduleTaskAlarm(finalTask, nextTrigger, cycleDate)
        }
    }

    suspend fun snoozeTaskAlarm(
        taskId: Long,
        snoozeMinutes: Int,
        cycleDateStr: String? = null
    ) {
        val task = taskDao.getTaskById(taskId) ?: return
        val nowMillis = System.currentTimeMillis()
        val validMinutes = snoozeMinutes.coerceIn(1, 60)
        val snoozeUntil = nowMillis + (validMinutes * 60_000L)
        val effectiveCycleDate = cycleDateStr
            ?: task.activeCycleDate
            ?: LocalDate.now().format(isoFormatter)

        AlarmService.stopAlarmIfMatching(appContext, taskId)
        notificationManager.cancelTaskNotifications(taskId)

        val updatedTask = task.copy(
            snoozeStatus = SnoozeStatus.SNOOZED,
            snoozedUntilTimestamp = snoozeUntil,
            nextTriggerTimestamp = snoozeUntil,
            activeCycleDate = effectiveCycleDate,
            updatedAt = nowMillis
        )
        taskDao.updateTask(updatedTask)
        alarmScheduler.scheduleTaskAlarm(updatedTask, snoozeUntil, effectiveCycleDate)
    }

    suspend fun onAlarmTriggeredAndScheduleRepeat(
        taskId: Long,
        cycleDateStr: String?
    ): TaskEntity? {
        val task = taskDao.getTaskById(taskId) ?: return null
        val todayStr = LocalDate.now().format(isoFormatter)
        val effectiveCycleDate = cycleDateStr?.takeIf { it.isNotBlank() }
            ?: task.activeCycleDate
            ?: todayStr

        if (task.lastCompletedDate == effectiveCycleDate ||
            (task.recurrenceType == RecurrenceType.ONE_TIME && task.completionStatus == TaskCompletionStatus.COMPLETED)
        ) {
            alarmScheduler.cancelTaskAlarm(taskId)
            return null
        }

        val settings = settingsManager.getSettingsOnce()
        val nowMillis = System.currentTimeMillis()
        val nextRepeatMillis = nowMillis + (settings.repeatIntervalMinutes.coerceIn(1, 60) * 60_000L)

        val existingHistory = taskDao.getHistoryEntry(taskId, effectiveCycleDate)
        if (existingHistory == null) {
            taskDao.upsertHistoryEntry(
                TaskHistoryEntity(
                    taskId = task.id,
                    taskTitleSnapshot = task.title,
                    dateStr = effectiveCycleDate,
                    scheduledHour = task.alarmHour,
                    scheduledMinute = task.alarmMinute,
                    status = TaskCompletionStatus.MISSED,
                    completedAtTimestamp = null
                )
            )
        }

        val updatedTask = task.copy(
            snoozeStatus = SnoozeStatus.RINGING,
            snoozedUntilTimestamp = null,
            nextTriggerTimestamp = nextRepeatMillis,
            activeCycleDate = effectiveCycleDate,
            repeatCount = task.repeatCount + 1,
            updatedAt = nowMillis
        )
        taskDao.updateTask(updatedTask)

        alarmScheduler.scheduleTaskAlarm(updatedTask, nextRepeatMillis, effectiveCycleDate)
        return updatedTask
    }

    suspend fun deleteTask(taskId: Long) {
        alarmScheduler.cancelTaskAlarm(taskId)
        AlarmService.stopAlarmIfMatching(appContext, taskId)
        notificationManager.cancelTaskNotifications(taskId)
        taskDao.deleteTaskById(taskId)
    }

    suspend fun reconcileAllAlarmsAndHistory() {
        val allTasks = taskDao.getAllTasksOnce()
        val settings = settingsManager.getSettingsOnce()
        val nowMillis = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()

        for (task in allTasks) {
            for (daysAgo in 1..7) {
                val pastDate = today.minusDays(daysAgo.toLong())
                val pastDateStr = pastDate.format(isoFormatter)
                if (TaskScheduleCalculator.doesTaskApplyOnDate(task, pastDate)) {
                    val existingEntry = taskDao.getHistoryEntry(task.id, pastDateStr)
                    if (existingEntry == null) {
                        val wasCompleted = task.lastCompletedDate == pastDateStr ||
                                (task.recurrenceType == RecurrenceType.ONE_TIME &&
                                        task.completionStatus == TaskCompletionStatus.COMPLETED)
                        taskDao.upsertHistoryEntry(
                            TaskHistoryEntity(
                                taskId = task.id,
                                taskTitleSnapshot = task.title,
                                dateStr = pastDateStr,
                                scheduledHour = task.alarmHour,
                                scheduledMinute = task.alarmMinute,
                                status = if (wasCompleted) TaskCompletionStatus.COMPLETED else TaskCompletionStatus.MISSED,
                                completedAtTimestamp = if (wasCompleted) task.completionTimestamp else null
                            )
                        )
                    }
                }
            }

            if (!task.alarmEnabled) {
                alarmScheduler.cancelTaskAlarm(task.id)
                continue
            }

            val nextTrigger = TaskScheduleCalculator.computeNextTriggerMillis(
                task = task,
                nowMillis = nowMillis,
                zoneId = zoneId,
                repeatIntervalMinutes = settings.repeatIntervalMinutes
            )

            if (nextTrigger != null) {
                val cycleDate = Instant.ofEpochMilli(nextTrigger)
                    .atZone(zoneId)
                    .toLocalDate()
                    .format(isoFormatter)
                if (task.nextTriggerTimestamp != nextTrigger) {
                    taskDao.updateTask(
                        task.copy(
                            nextTriggerTimestamp = nextTrigger,
                            activeCycleDate = task.activeCycleDate ?: cycleDate
                        )
                    )
                }
                alarmScheduler.scheduleTaskAlarm(task, nextTrigger, task.activeCycleDate ?: cycleDate)
            } else {
                alarmScheduler.cancelTaskAlarm(task.id)
            }
        }
    }
}
