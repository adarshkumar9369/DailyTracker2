package com.dailytracker.app.data.local

import androidx.room.TypeConverter
import com.dailytracker.app.data.model.RecurrenceType
import com.dailytracker.app.data.model.SnoozeStatus
import com.dailytracker.app.data.model.TaskCompletionStatus

class Converters {
    @TypeConverter
    fun fromRecurrenceType(value: RecurrenceType): String = value.name

    @TypeConverter
    fun toRecurrenceType(value: String): RecurrenceType =
        runCatching { RecurrenceType.valueOf(value) }.getOrDefault(RecurrenceType.ONE_TIME)

    @TypeConverter
    fun fromCompletionStatus(value: TaskCompletionStatus): String = value.name

    @TypeConverter
    fun toCompletionStatus(value: String): TaskCompletionStatus =
        runCatching { TaskCompletionStatus.valueOf(value) }.getOrDefault(TaskCompletionStatus.PENDING)

    @TypeConverter
    fun fromSnoozeStatus(value: SnoozeStatus): String = value.name

    @TypeConverter
    fun toSnoozeStatus(value: String): SnoozeStatus =
        runCatching { SnoozeStatus.valueOf(value) }.getOrDefault(SnoozeStatus.NONE)
}
