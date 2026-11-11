package com.dailytracker.app

import com.dailytracker.app.data.local.entity.TaskEntity
import com.dailytracker.app.data.local.entity.TaskHistoryEntity
import com.dailytracker.app.data.model.RecurrenceType
import com.dailytracker.app.data.model.SnoozeStatus
import com.dailytracker.app.data.model.TaskCompletionStatus
import com.dailytracker.app.util.TaskScheduleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskScheduleCalculatorTest {

    private val zoneId = ZoneId.of("UTC")

    @Test
    fun oneTimeTask_triggersAtExactScheduledTime() {
        val date = LocalDate.of(2026, 11, 10)
        val now = ZonedDateTime.of(date, LocalTime.of(9, 0), zoneId)
        val task = TaskEntity(
            id = 1L,
            title = "Submit Tax Filing",
            scheduledDate = "2026-11-10",
            alarmHour = 10,
            alarmMinute = 30,
            recurrenceType = RecurrenceType.ONE_TIME
        )

        val nextMillis = TaskScheduleCalculator.computeNextTriggerMillis(
            task = task,
            nowMillis = now.toInstant().toEpochMilli(),
            zoneId = zoneId,
            repeatIntervalMinutes = 5
        )
        val expectedMillis = ZonedDateTime.of(date, LocalTime.of(10, 30), zoneId).toInstant().toEpochMilli()
        assertEquals(expectedMillis, nextMillis)
    }

    @Test
    fun uncompletedTask_repeatsAtIntervalUntilCompleted() {
        val date = LocalDate.of(2026, 11, 10)
        val now = ZonedDateTime.of(date, LocalTime.of(10, 35), zoneId)
        val nowMillis = now.toInstant().toEpochMilli()

        val ringingTask = TaskEntity(
            id = 2L,
            title = "Take Morning Medication",
            scheduledDate = "2026-11-10",
            alarmHour = 10,
            alarmMinute = 30,
            recurrenceType = RecurrenceType.DAILY,
            snoozeStatus = SnoozeStatus.RINGING,
            activeCycleDate = "2026-11-10",
            repeatCount = 1
        )

        val nextMillis = TaskScheduleCalculator.computeNextTriggerMillis(
            task = ringingTask,
            nowMillis = nowMillis,
            zoneId = zoneId,
            repeatIntervalMinutes = 5
        )
        assertEquals(nowMillis + 5 * 60_000L, nextMillis)
    }

    @Test
    fun snoozedTask_firesAfterSelectedSnoozeDuration_5_10_15() {
        val date = LocalDate.of(2026, 11, 10)
        val nowMillis = ZonedDateTime.of(date, LocalTime.of(14, 0), zoneId).toInstant().toEpochMilli()
        val snoozedUntil = nowMillis + 15 * 60_000L

        val snoozedTask = TaskEntity(
            id = 3L,
            title = "Hydration Check",
            scheduledDate = "2026-11-10",
            alarmHour = 13,
            alarmMinute = 50,
            recurrenceType = RecurrenceType.DAILY,
            snoozeStatus = SnoozeStatus.SNOOZED,
            snoozedUntilTimestamp = snoozedUntil,
            activeCycleDate = "2026-11-10"
        )

        val nextMillis = TaskScheduleCalculator.computeNextTriggerMillis(
            task = snoozedTask,
            nowMillis = nowMillis,
            zoneId = zoneId,
            repeatIntervalMinutes = 5
        )
        assertEquals(snoozedUntil, nextMillis)
    }

    @Test
    fun completingDailyTaskToday_stopsRepeatsAndSchedulesTomorrow() {
        val today = LocalDate.of(2026, 11, 10)
        val nowMillis = ZonedDateTime.of(today, LocalTime.of(8, 15), zoneId).toInstant().toEpochMilli()

        val completedTodayTask = TaskEntity(
            id = 4L,
            title = "Daily Standup Prep",
            scheduledDate = "2026-11-01",
            alarmHour = 8,
            alarmMinute = 0,
            recurrenceType = RecurrenceType.DAILY,
            lastCompletedDate = "2026-11-10",
            snoozeStatus = SnoozeStatus.NONE
        )

        val nextMillis = TaskScheduleCalculator.computeNextTriggerMillis(
            task = completedTodayTask,
            nowMillis = nowMillis,
            zoneId = zoneId,
            repeatIntervalMinutes = 5
        )
        val expectedTomorrow = ZonedDateTime.of(today.plusDays(1), LocalTime.of(8, 0), zoneId)
            .toInstant()
            .toEpochMilli()
        assertEquals(expectedTomorrow, nextMillis)
    }

    @Test
    fun completingOneTimeTask_cancelsFutureAlarms() {
        val today = LocalDate.of(2026, 11, 10)
        val nowMillis = ZonedDateTime.of(today, LocalTime.of(12, 0), zoneId).toInstant().toEpochMilli()

        val completedOneTime = TaskEntity(
            id = 5L,
            title = "Pay Electricity Bill",
            scheduledDate = "2026-11-10",
            alarmHour = 11,
            alarmMinute = 0,
            recurrenceType = RecurrenceType.ONE_TIME,
            completionStatus = TaskCompletionStatus.COMPLETED,
            lastCompletedDate = "2026-11-10"
        )

        val nextMillis = TaskScheduleCalculator.computeNextTriggerMillis(
            task = completedOneTime,
            nowMillis = nowMillis,
            zoneId = zoneId,
            repeatIntervalMinutes = 5
        )
        assertNull(nextMillis)
    }

    @Test
    fun specificWeekdaysRecurrence_matchesOnlySelectedDays() {
        val tuesday = LocalDate.of(2026, 11, 10)
        val wednesday = LocalDate.of(2026, 11, 11)
        val thursday = LocalDate.of(2026, 11, 12)

        val task = TaskEntity(
            id = 6L,
            title = "Gym Workout",
            scheduledDate = "2026-11-01",
            alarmHour = 18,
            alarmMinute = 0,
            recurrenceType = RecurrenceType.SPECIFIC_DAYS,
            selectedWeekdays = "2,4"
        )

        assertTrue(TaskScheduleCalculator.doesTaskApplyOnDate(task, tuesday))
        assertFalse(TaskScheduleCalculator.doesTaskApplyOnDate(task, wednesday))
        assertTrue(TaskScheduleCalculator.doesTaskApplyOnDate(task, thursday))
    }

    @Test
    fun statusResolution_distinguishesPendingCompletedAndMissed() {
        val today = LocalDate.of(2026, 11, 10)
        val now = ZonedDateTime.of(today, LocalTime.of(15, 0), zoneId)

        val pastDueTask = TaskEntity(
            id = 7L,
            title = "Morning Reading",
            scheduledDate = "2026-11-10",
            alarmHour = 9,
            alarmMinute = 0,
            recurrenceType = RecurrenceType.DAILY
        )

        val upcomingTask = TaskEntity(
            id = 8L,
            title = "Evening Journal",
            scheduledDate = "2026-11-10",
            alarmHour = 21,
            alarmMinute = 0,
            recurrenceType = RecurrenceType.DAILY
        )

        assertEquals(
            TaskCompletionStatus.MISSED,
            TaskScheduleCalculator.resolveTaskStatusForDate(pastDueTask, today, emptyMap(), now)
        )
        assertEquals(
            TaskCompletionStatus.PENDING,
            TaskScheduleCalculator.resolveTaskStatusForDate(upcomingTask, today, emptyMap(), now)
        )

        val completedHistory = mapOf(
            7L to TaskHistoryEntity(
                taskId = 7L,
                taskTitleSnapshot = "Morning Reading",
                dateStr = "2026-11-10",
                scheduledHour = 9,
                scheduledMinute = 0,
                status = TaskCompletionStatus.COMPLETED,
                completedAtTimestamp = now.toInstant().toEpochMilli()
            )
        )
        assertEquals(
            TaskCompletionStatus.COMPLETED,
            TaskScheduleCalculator.resolveTaskStatusForDate(pastDueTask, today, completedHistory, now)
        )
    }

    @Test
    fun timezoneAndClockChange_recalculatesAccurateEpochMillis() {
        val date = LocalDate.of(2026, 11, 10)
        val task = TaskEntity(
            id = 9L,
            title = "Evening Sync",
            scheduledDate = "2026-11-10",
            alarmHour = 20,
            alarmMinute = 0,
            recurrenceType = RecurrenceType.ONE_TIME
        )

        val nyZone = ZoneId.of("America/New_York")
        val tokyoZone = ZoneId.of("Asia/Tokyo")

        val nyNow = ZonedDateTime.of(date, LocalTime.of(10, 0), nyZone).toInstant().toEpochMilli()
        val tokyoNow = ZonedDateTime.of(date, LocalTime.of(10, 0), tokyoZone).toInstant().toEpochMilli()

        val nyTrigger = TaskScheduleCalculator.computeNextTriggerMillis(task, nyNow, nyZone, 5)
        val tokyoTrigger = TaskScheduleCalculator.computeNextTriggerMillis(task, tokyoNow, tokyoZone, 5)

        assertNotNull(nyTrigger)
        assertNotNull(tokyoTrigger)
        assertTrue(nyTrigger != tokyoTrigger)
    }
}
