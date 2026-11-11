package com.dailytracker.app.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dailytracker.app.data.model.RecurrenceType
import com.dailytracker.app.data.model.TaskWithInstanceStatus
import com.dailytracker.app.util.TaskScheduleCalculator
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorDialog(
    initialTask: TaskWithInstanceStatus? = null,
    use24HourFormat: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long?,
        title: String,
        description: String,
        scheduledDateStr: String,
        alarmHour: Int,
        alarmMinute: Int,
        recurrenceType: RecurrenceType,
        selectedWeekdays: Set<Int>,
        alarmEnabled: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val defaultTime = LocalTime.now().plusMinutes(10)
    var title by remember { mutableStateOf(initialTask?.title ?: "") }
    var description by remember { mutableStateOf(initialTask?.description ?: "") }
    var scheduledDateStr by remember {
        mutableStateOf(initialTask?.baseScheduledDateStr ?: LocalDate.now().format(isoFormatter))
    }
    var alarmHour by remember { mutableIntStateOf(initialTask?.alarmHour ?: defaultTime.hour) }
    var alarmMinute by remember { mutableIntStateOf(initialTask?.alarmMinute ?: defaultTime.minute) }
    var recurrenceType by remember {
        mutableStateOf(initialTask?.recurrenceType ?: RecurrenceType.DAILY)
    }
    var selectedWeekdays by remember {
        mutableStateOf(
            initialTask?.selectedWeekdays?.takeIf { it.isNotEmpty() }
                ?: setOf(LocalDate.now().dayOfWeek.value)
        )
    }
    var alarmEnabled by remember { mutableStateOf(initialTask?.alarmEnabled ?: true) }
    var titleError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (initialTask == null) "New Daily Task" else "Edit Task",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotBlank()) titleError = false
                    },
                    label = { Text("Task Title *") },
                    placeholder = { Text("e.g., Morning Medication, Review Sprint Goals") },
                    isError = titleError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (titleError) {
                    Text(
                        text = "Please enter a task title",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Optional Description / Notes") },
                    placeholder = { Text("Add specific checklist details or instructions...") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Schedule & Alarm Time",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val parsed = runCatching { LocalDate.parse(scheduledDateStr, isoFormatter) }
                                .getOrDefault(LocalDate.now())
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val picked = LocalDate.of(year, month + 1, dayOfMonth)
                                    scheduledDateStr = picked.format(isoFormatter)
                                },
                                parsed.year,
                                parsed.monthValue - 1,
                                parsed.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = scheduledDateStr)
                    }

                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    alarmHour = hourOfDay
                                    alarmMinute = minute
                                },
                                alarmHour,
                                alarmMinute,
                                use24HourFormat
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = TaskScheduleCalculator.formatTime(
                                alarmHour,
                                alarmMinute,
                                use24HourFormat
                            ),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Recurrence Pattern",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RecurrenceType.entries.forEach { type ->
                        FilterChip(
                            selected = recurrenceType == type,
                            onClick = { recurrenceType = type },
                            label = { Text(type.displayName) }
                        )
                    }
                }

                if (recurrenceType == RecurrenceType.SPECIFIC_DAYS) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Select Weekdays",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val daysList = listOf(
                        1 to "Mon",
                        2 to "Tue",
                        3 to "Wed",
                        4 to "Thu",
                        5 to "Fri",
                        6 to "Sat",
                        7 to "Sun"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        daysList.forEach { (dayNum, label) ->
                            val isSelected = selectedWeekdays.contains(dayNum)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedWeekdays = if (isSelected && selectedWeekdays.size > 1) {
                                        selectedWeekdays - dayNum
                                    } else {
                                        selectedWeekdays + dayNum
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { alarmEnabled = !alarmEnabled },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Persistent Repeating Alarm",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Repeats automatically until task is marked Completed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = alarmEnabled,
                            onCheckedChange = { alarmEnabled = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                titleError = true
                                return@Button
                            }
                            onSave(
                                initialTask?.taskId,
                                title,
                                description,
                                scheduledDateStr,
                                alarmHour,
                                alarmMinute,
                                recurrenceType,
                                selectedWeekdays,
                                alarmEnabled
                            )
                        }
                    ) {
                        Text(if (initialTask == null) "Create Task" else "Save Changes")
                    }
                }
            }
        }
    }
}
