package com.dailytracker.app.data.model

enum class RecurrenceType(val displayName: String) {
    ONE_TIME("One-time"),
    DAILY("Every Day"),
    SPECIFIC_DAYS("Specific Weekdays")
}

enum class TaskCompletionStatus(val displayName: String) {
    PENDING("Pending"),
    COMPLETED("Completed"),
    MISSED("Missed")
}

enum class SnoozeStatus(val displayName: String) {
    NONE("Not Snoozed"),
    SNOOZED("Snoozed"),
    RINGING("Ringing / Repeating")
}

enum class AppThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class AlarmToneOption(val displayName: String) {
    DEFAULT_ALARM("Default System Alarm"),
    RINGTONE("System Ringtone"),
    NOTIFICATION_LOUD("Urgent Notification Beep")
}

data class AppSettings(
    val alarmSound: AlarmToneOption = AlarmToneOption.DEFAULT_ALARM,
    val vibrationEnabled: Boolean = true,
    val defaultSnoozeMinutes: Int = 10,
    val repeatIntervalMinutes: Int = 5,
    val use24HourFormat: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT
)

data class TaskWithInstanceStatus(
    val taskId: Long,
    val title: String,
    val description: String,
    val scheduledDateStr: String,
    val baseScheduledDateStr: String,
    val alarmHour: Int,
    val alarmMinute: Int,
    val recurrenceType: RecurrenceType,
    val selectedWeekdays: Set<Int>,
    val alarmEnabled: Boolean,
    val status: TaskCompletionStatus,
    val completionTimestamp: Long?,
    val snoozeStatus: SnoozeStatus,
    val snoozedUntilTimestamp: Long?,
    val nextTriggerTimestamp: Long?,
    val repeatCount: Int
)
