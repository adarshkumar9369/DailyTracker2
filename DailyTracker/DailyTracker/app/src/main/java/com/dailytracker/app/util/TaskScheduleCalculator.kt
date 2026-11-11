package com.dailytracker.app.util

import com.dailytracker.app.data.local.entity.TaskEntity
import com.dailytracker.app.data.local.entity.TaskHistoryEntity
import com.dailytracker.app.data.model.RecurrenceType
import com.dailytracker.app.data.model.SnoozeStatus
import com.dailytracker.app.data.model.TaskCompletionStatus
import com.dailytracker.app.data.model.TaskWithInstanceStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TaskScheduleCalculator {

    private val isoDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parseWeekdays(csv: String): Set<Int> {
        if (csv.isBlank()) return emptySet()
        return csv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .toSet()
    }

    fun serializeWeekdays(days: Set<Int>): String =
        days.filter { it in 1..7 }.sorted().joinToString(",")

    fun doesTaskApplyOnDate(task: TaskEntity, date: LocalDate): Boolean {
        val baseDate = runCatching { LocalDate.parse(task.scheduledDate, isoDateFormatter) }
            .getOrDefault(date)

        return when (task.recurrenceType) {
            RecurrenceType.ONE_TIME -> date == baseDate
            RecurrenceType.DAILY -> !date.isBefore(baseDate)
            RecurrenceType.SPECIFIC_DAYS -> {
                if (date.isBefore(baseDate)) false
                else {
                    val days = parseWeekdays(task.selectedWeekdays)
                    days.contains(date.dayOfWeek.value)
                }
            }
        }
    }

    fun resolveTaskStatusForDate(
        task: TaskEntity,
        date: LocalDate,
        historyForDate: Map<Long, TaskHistoryEntity>,
        now: ZonedDateTime = ZonedDateTime.now()
    ): TaskCompletionStatus {
        val dateStr = date.format(isoDateFormatter)
        val history = historyForDate[task.id]
        if (history != null && history.status == TaskCompletionStatus.COMPLETED) {
            return TaskCompletionStatus.COMPLETED
        }
        if (task.recurrenceType == RecurrenceType.ONE_TIME && task.completionStatus == TaskCompletionStatus.COMPLETED) {
            return TaskCompletionStatus.COMPLETED
        }
        if (task.lastCompletedDate == dateStr) {
            return TaskCompletionStatus.COMPLETED
        }

        val scheduledDateTime = ZonedDateTime.of(
            date,
            LocalTime.of(task.alarmHour, task.alarmMinute),
            now.zone
        )

        return when {
            date.isBefore(now.toLocalDate()) -> TaskCompletionStatus.MISSED
            date.isAfter(now.toLocalDate()) -> TaskCompletionStatus.PENDING
            else -> {
                if (now.isAfter(scheduledDateTime.plusMinutes(1))) {
                    TaskCompletionStatus.MISSED
                } else {
                    TaskCompletionStatus.PENDING
                }
            }
        }
    }

    fun buildTaskInstanceForDate(
        task: TaskEntity,
        date: LocalDate,
        historyForDate: Map<Long, TaskHistoryEntity>,
        now: ZonedDateTime = ZonedDateTime.now()
    ): TaskWithInstanceStatus {
        val dateStr = date.format(isoDateFormatter)
        val history = historyForDate[task.id]
        val status = resolveTaskStatusForDate(task, date, historyForDate, now)
        val completionTs = history?.completedAtTimestamp
            ?: if (status == TaskCompletionStatus.COMPLETED) task.completionTimestamp else null

        val isCurrentActiveCycle = task.activeCycleDate == null || task.activeCycleDate == dateStr
        val instanceSnooze = if (status == TaskCompletionStatus.COMPLETED || !isCurrentActiveCycle) {
            SnoozeStatus.NONE
        } else {
            task.snoozeStatus
        }

        return TaskWithInstanceStatus(
            taskId = task.id,
            title = task.title,
            description = task.description,
            scheduledDateStr = dateStr,
            baseScheduledDateStr = task.scheduledDate,
            alarmHour = task.alarmHour,
            alarmMinute = task.alarmMinute,
            recurrenceType = task.recurrenceType,
            selectedWeekdays = parseWeekdays(task.selectedWeekdays),
            alarmEnabled = task.alarmEnabled,
            status = status,
            completionTimestamp = completionTs,
            snoozeStatus = instanceSnooze,
            snoozedUntilTimestamp = if (instanceSnooze == SnoozeStatus.SNOOZED) task.snoozedUntilTimestamp else null,
            nextTriggerTimestamp = task.nextTriggerTimestamp,
            repeatCount = if (isCurrentActiveCycle) task.repeatCount else 0
        )
    }

    fun computeNextTriggerMillis(
        task: TaskEntity,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        repeatIntervalMinutes: Int = 5
    ): Long? {
        if (!task.alarmEnabled) return null

        val nowZdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val today = nowZdt.toLocalDate()
        val todayStr = today.format(isoDateFormatter)

        if (task.recurrenceType == RecurrenceType.ONE_TIME &&
            task.completionStatus == TaskCompletionStatus.COMPLETED
        ) {
            return null
        }

        if (task.snoozeStatus == SnoozeStatus.SNOOZED && task.snoozedUntilTimestamp != null) {
            val activeCycle = task.activeCycleDate ?: todayStr
            if (task.lastCompletedDate != activeCycle) {
                return if (task.snoozedUntilTimestamp > nowMillis + 2_000L) {
                    task.snoozedUntilTimestamp
                } else {
                    nowMillis + 5_000L
                }
            }
        }

        if (task.snoozeStatus == SnoozeStatus.RINGING) {
            val activeCycle = task.activeCycleDate ?: todayStr
            if (task.lastCompletedDate != activeCycle) {
                val intervalMs = (repeatIntervalMinutes.coerceIn(1, 60)) * 60_000L
                val candidate = task.nextTriggerTimestamp ?: (nowMillis + intervalMs)
                return if (candidate > nowMillis + 2_000L) candidate else (nowMillis + intervalMs)
            }
        }

        val alarmTime = LocalTime.of(task.alarmHour, task.alarmMinute)
        val baseDate = runCatching { LocalDate.parse(task.scheduledDate, isoDateFormatter) }
            .getOrDefault(today)

        when (task.recurrenceType) {
            RecurrenceType.ONE_TIME -> {
                val targetZdt = ZonedDateTime.of(baseDate, alarmTime, zoneId)
                val targetMillis = targetZdt.toInstant().toEpochMilli()
                return if (targetMillis > nowMillis) {
                    targetMillis
                } else {
                    if (baseDate == today && task.lastCompletedDate != todayStr) {
                        nowMillis + (repeatIntervalMinutes.coerceIn(1, 60) * 60_000L)
                    } else {
                        null
                    }
                }
            }

            RecurrenceType.DAILY -> {
                var checkDate = if (today.isBefore(baseDate)) baseDate else today
                if (checkDate == today && task.lastCompletedDate != todayStr) {
                    val todayTrigger = ZonedDateTime.of(today, alarmTime, zoneId).toInstant().toEpochMilli()
                    return if (todayTrigger > nowMillis) {
                        todayTrigger
                    } else {
                        nowMillis + (repeatIntervalMinutes.coerceIn(1, 60) * 60_000L)
                    }
                }
                if (checkDate == today && task.lastCompletedDate == todayStr) {
                    checkDate = today.plusDays(1)
                }
                return ZonedDateTime.of(checkDate, alarmTime, zoneId).toInstant().toEpochMilli()
            }

            RecurrenceType.SPECIFIC_DAYS -> {
                val weekdays = parseWeekdays(task.selectedWeekdays)
                if (weekdays.isEmpty()) return null

                val checkDate = if (today.isBefore(baseDate)) baseDate else today
                for (offset in 0..14) {
                    val candidateDate = checkDate.plusDays(offset.toLong())
                    if (weekdays.contains(candidateDate.dayOfWeek.value)) {
                        val candidateMillis = ZonedDateTime.of(candidateDate, alarmTime, zoneId)
                            .toInstant()
                            .toEpochMilli()
                        if (candidateDate == today) {
                            if (task.lastCompletedDate != todayStr) {
                                return if (candidateMillis > nowMillis) {
                                    candidateMillis
                                } else {
                                    nowMillis + (repeatIntervalMinutes.coerceIn(1, 60) * 60_000L)
                                }
                            }
                        } else if (candidateMillis > nowMillis) {
                            return candidateMillis
                        }
                    }
                }
                return null
            }
        }
    }

    fun formatTime(hour: Int, minute: Int, use24Hour: Boolean): String {
        val localTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val pattern = if (use24Hour) "HH:mm" else "hh:mm a"
        return localTime.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }

    fun formatReadableDate(dateStr: String): String {
        return runCatching {
            val d = LocalDate.parse(dateStr, isoDateFormatter)
            d.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US))
        }.getOrDefault(dateStr)
    }
}
