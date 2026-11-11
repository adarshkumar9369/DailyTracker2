package com.dailytracker.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailytracker.app.DailyTrackerApp
import com.dailytracker.app.alarm.ActiveAlarmState
import com.dailytracker.app.alarm.AlarmService
import com.dailytracker.app.data.local.entity.TaskEntity
import com.dailytracker.app.data.local.entity.TaskHistoryEntity
import com.dailytracker.app.data.model.AlarmToneOption
import com.dailytracker.app.data.model.AppSettings
import com.dailytracker.app.data.model.AppThemeMode
import com.dailytracker.app.data.model.RecurrenceType
import com.dailytracker.app.data.model.TaskCompletionStatus
import com.dailytracker.app.data.model.TaskWithInstanceStatus
import com.dailytracker.app.data.preferences.SettingsManager
import com.dailytracker.app.data.repository.TaskRepository
import com.dailytracker.app.util.PermissionHelper
import com.dailytracker.app.util.SystemPermissionStatus
import com.dailytracker.app.util.TaskScheduleCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class TaskFilterTab(val label: String) {
    ALL("All"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    MISSED("Missed")
}

data class PeriodReportStats(
    val periodLabel: String,
    val totalScheduled: Int,
    val completedCount: Int,
    val pendingCount: Int,
    val missedCount: Int,
    val completionPercentage: Int
)

data class DashboardSummary(
    val dateFormatted: String,
    val dayOfWeekFormatted: String,
    val totalTasksToday: Int,
    val completedTasksToday: Int,
    val pendingTasksToday: Int,
    val missedTasksToday: Int,
    val completionPercentage: Int,
    val upcomingTask: TaskWithInstanceStatus?,
    val todayInstances: List<TaskWithInstanceStatus>
)

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val dashboard: DashboardSummary = DashboardSummary(
        dateFormatted = "",
        dayOfWeekFormatted = "",
        totalTasksToday = 0,
        completedTasksToday = 0,
        pendingTasksToday = 0,
        missedTasksToday = 0,
        completionPercentage = 0,
        upcomingTask = null,
        todayInstances = emptyList()
    ),
    val allTasksRaw: List<TaskEntity> = emptyList(),
    val filteredTaskInstances: List<TaskWithInstanceStatus> = emptyList(),
    val selectedTaskFilter: TaskFilterTab = TaskFilterTab.ALL,
    val selectedHistoryDate: LocalDate = LocalDate.now(),
    val selectedDateInstances: List<TaskWithInstanceStatus> = emptyList(),
    val weeklyReport: PeriodReportStats = PeriodReportStats("Last 7 Days", 0, 0, 0, 0, 0),
    val monthlyReport: PeriodReportStats = PeriodReportStats("Last 30 Days", 0, 0, 0, 0, 0),
    val activeRingingAlarm: ActiveAlarmState? = null,
    val permissions: SystemPermissionStatus = SystemPermissionStatus(
        notificationsGranted = true,
        exactAlarmGranted = true,
        fullScreenIntentGranted = true,
        batteryOptimizationIgnored = true
    )
)

class DailyTrackerViewModel(
    private val appContext: Context,
    private val repository: TaskRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val selectedFilterFlow = MutableStateFlow(TaskFilterTab.ALL)
    private val selectedHistoryDateFlow = MutableStateFlow(LocalDate.now())
    private val clockTickFlow = MutableStateFlow(System.currentTimeMillis())
    private val permissionsFlow = MutableStateFlow(PermissionHelper.checkPermissions(appContext))

    init {
        viewModelScope.launch {
            repository.reconcileAllAlarmsAndHistory()
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                clockTickFlow.value = System.currentTimeMillis()
            }
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        repository.allTasksFlow,
        repository.allHistoryFlow,
        settingsManager.settingsFlow,
        selectedFilterFlow,
        selectedHistoryDateFlow,
        clockTickFlow,
        AlarmService.activeAlarmFlow,
        permissionsFlow
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val tasks = args[0] as List<TaskEntity>
        @Suppress("UNCHECKED_CAST")
        val historyList = args[1] as List<TaskHistoryEntity>
        val settings = args[2] as AppSettings
        val filterTab = args[3] as TaskFilterTab
        val historyDate = args[4] as LocalDate
        val activeAlarm = args[6] as ActiveAlarmState?
        val perms = args[7] as SystemPermissionStatus

        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val todayStr = today.format(isoFormatter)

        val historyByDate: Map<String, Map<Long, TaskHistoryEntity>> =
            historyList.groupBy { it.dateStr }
                .mapValues { (_, entries) -> entries.associateBy { it.taskId } }

        val todayHistoryMap = historyByDate[todayStr].orEmpty()

        val todayTasks = tasks
            .filter { TaskScheduleCalculator.doesTaskApplyOnDate(it, today) }
            .map { TaskScheduleCalculator.buildTaskInstanceForDate(it, today, todayHistoryMap, now) }
            .sortedWith(compareBy({ it.alarmHour }, { it.alarmMinute }, { it.taskId }))

        val totalToday = todayTasks.size
        val completedToday = todayTasks.count { it.status == TaskCompletionStatus.COMPLETED }
        val pendingToday = todayTasks.count { it.status == TaskCompletionStatus.PENDING }
        val missedToday = todayTasks.count { it.status == TaskCompletionStatus.MISSED }
        val completionPct = if (totalToday > 0) ((completedToday * 100f) / totalToday).toInt() else 0

        val upcomingTask = todayTasks
            .filter { it.status != TaskCompletionStatus.COMPLETED && it.alarmEnabled }
            .minByOrNull { it.nextTriggerTimestamp ?: Long.MAX_VALUE }
            ?: tasks
                .filter { it.alarmEnabled && it.nextTriggerTimestamp != null }
                .minByOrNull { it.nextTriggerTimestamp ?: Long.MAX_VALUE }
                ?.let { nextEntity ->
                    TaskScheduleCalculator.buildTaskInstanceForDate(
                        nextEntity,
                        today,
                        todayHistoryMap,
                        now
                    )
                }

        val dashboard = DashboardSummary(
            dateFormatted = today.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
            dayOfWeekFormatted = today.format(DateTimeFormatter.ofPattern("EEEE")),
            totalTasksToday = totalToday,
            completedTasksToday = completedToday,
            pendingTasksToday = pendingToday,
            missedTasksToday = missedToday,
            completionPercentage = completionPct,
            upcomingTask = upcomingTask,
            todayInstances = todayTasks
        )

        val allTaskInstances = tasks.map { task ->
            val refDate = if (TaskScheduleCalculator.doesTaskApplyOnDate(task, today)) {
                today
            } else {
                runCatching { LocalDate.parse(task.scheduledDate, isoFormatter) }.getOrDefault(today)
            }
            val refDateStr = refDate.format(isoFormatter)
            TaskScheduleCalculator.buildTaskInstanceForDate(
                task = task,
                date = refDate,
                historyForDate = historyByDate[refDateStr].orEmpty(),
                now = now
            )
        }

        val filteredInstances = when (filterTab) {
            TaskFilterTab.ALL -> allTaskInstances
            TaskFilterTab.PENDING -> allTaskInstances.filter { it.status == TaskCompletionStatus.PENDING }
            TaskFilterTab.COMPLETED -> allTaskInstances.filter { it.status == TaskCompletionStatus.COMPLETED }
            TaskFilterTab.MISSED -> allTaskInstances.filter { it.status == TaskCompletionStatus.MISSED }
        }

        val historyDateStr = historyDate.format(isoFormatter)
        val selectedDateHistoryMap = historyByDate[historyDateStr].orEmpty()
        val selectedDateInstances = tasks
            .filter { TaskScheduleCalculator.doesTaskApplyOnDate(it, historyDate) }
            .map { TaskScheduleCalculator.buildTaskInstanceForDate(it, historyDate, selectedDateHistoryMap, now) }

        val weeklyStats = computePeriodStats(
            label = "Weekly Report (Last 7 Days)",
            daysCount = 7,
            today = today,
            tasks = tasks,
            historyByDate = historyByDate,
            now = now
        )
        val monthlyStats = computePeriodStats(
            label = "Monthly Report (Last 30 Days)",
            daysCount = 30,
            today = today,
            tasks = tasks,
            historyByDate = historyByDate,
            now = now
        )

        MainUiState(
            settings = settings,
            dashboard = dashboard,
            allTasksRaw = tasks,
            filteredTaskInstances = filteredInstances,
            selectedTaskFilter = filterTab,
            selectedHistoryDate = historyDate,
            selectedDateInstances = selectedDateInstances,
            weeklyReport = weeklyStats,
            monthlyReport = monthlyStats,
            activeRingingAlarm = activeAlarm,
            permissions = perms
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState()
    )

    private fun computePeriodStats(
        label: String,
        daysCount: Int,
        today: LocalDate,
        tasks: List<TaskEntity>,
        historyByDate: Map<String, Map<Long, TaskHistoryEntity>>,
        now: ZonedDateTime
    ): PeriodReportStats {
        var total = 0
        var completed = 0
        var pending = 0
        var missed = 0

        for (offset in 0 until daysCount) {
            val date = today.minusDays(offset.toLong())
            val dateStr = date.format(isoFormatter)
            val dayHistory = historyByDate[dateStr].orEmpty()

            for (task in tasks) {
                if (TaskScheduleCalculator.doesTaskApplyOnDate(task, date)) {
                    total++
                    when (TaskScheduleCalculator.resolveTaskStatusForDate(task, date, dayHistory, now)) {
                        TaskCompletionStatus.COMPLETED -> completed++
                        TaskCompletionStatus.PENDING -> pending++
                        TaskCompletionStatus.MISSED -> missed++
                    }
                }
            }
        }

        val pct = if (total > 0) ((completed * 100f) / total).toInt() else 0
        return PeriodReportStats(
            periodLabel = label,
            totalScheduled = total,
            completedCount = completed,
            pendingCount = pending,
            missedCount = missed,
            completionPercentage = pct
        )
    }

    fun refreshPermissions() {
        permissionsFlow.value = PermissionHelper.checkPermissions(appContext)
    }

    fun selectTaskFilter(tab: TaskFilterTab) {
        selectedFilterFlow.value = tab
    }

    fun selectHistoryDate(date: LocalDate) {
        selectedHistoryDateFlow.value = date
    }

    fun saveTask(
        existingId: Long?,
        title: String,
        description: String,
        scheduledDateStr: String,
        alarmHour: Int,
        alarmMinute: Int,
        recurrenceType: RecurrenceType,
        selectedWeekdays: Set<Int>,
        alarmEnabled: Boolean
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.saveTask(
                existingId = existingId,
                title = title,
                description = description,
                scheduledDateStr = scheduledDateStr,
                alarmHour = alarmHour,
                alarmMinute = alarmMinute,
                recurrenceType = recurrenceType,
                selectedWeekdays = selectedWeekdays,
                alarmEnabled = alarmEnabled
            )
            clockTickFlow.value = System.currentTimeMillis()
        }
    }

    fun toggleTaskCompletion(task: TaskWithInstanceStatus) {
        viewModelScope.launch {
            if (task.status == TaskCompletionStatus.COMPLETED) {
                repository.markTaskUncompleted(task.taskId, task.scheduledDateStr)
            } else {
                repository.markTaskCompleted(task.taskId, task.scheduledDateStr)
            }
            clockTickFlow.value = System.currentTimeMillis()
        }
    }

    fun completeRingingTask(taskId: Long, cycleDateStr: String) {
        viewModelScope.launch {
            repository.markTaskCompleted(
                taskId = taskId,
                targetDateStr = cycleDateStr.ifBlank { LocalDate.now().format(isoFormatter) }
            )
            clockTickFlow.value = System.currentTimeMillis()
        }
    }

    fun snoozeTask(taskId: Long, minutes: Int, cycleDateStr: String? = null) {
        viewModelScope.launch {
            repository.snoozeTaskAlarm(taskId, minutes, cycleDateStr)
            clockTickFlow.value = System.currentTimeMillis()
        }
    }

    fun toggleAlarmEnabled(taskId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setTaskAlarmEnabled(taskId, enabled)
            clockTickFlow.value = System.currentTimeMillis()
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
            clockTickFlow.value = System.currentTimeMillis()
        }
    }

    fun updateAlarmSound(option: AlarmToneOption) {
        viewModelScope.launch { settingsManager.updateAlarmSound(option) }
    }

    fun updateVibration(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateVibration(enabled) }
    }

    fun updateDefaultSnooze(minutes: Int) {
        viewModelScope.launch { settingsManager.updateDefaultSnoozeMinutes(minutes) }
    }

    fun updateRepeatInterval(minutes: Int) {
        viewModelScope.launch {
            settingsManager.updateRepeatIntervalMinutes(minutes)
            repository.reconcileAllAlarmsAndHistory()
        }
    }

    fun updateUse24HourFormat(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateUse24HourFormat(enabled) }
    }

    fun updateThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsManager.updateThemeMode(mode) }
    }

    companion object {
        fun provideFactory(app: DailyTrackerApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DailyTrackerViewModel(
                        appContext = app.applicationContext,
                        repository = app.container.taskRepository,
                        settingsManager = app.container.settingsManager
                    ) as T
                }
            }
    }
}
