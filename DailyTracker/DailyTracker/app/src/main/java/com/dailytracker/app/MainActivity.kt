package com.dailytracker.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailytracker.app.data.model.TaskWithInstanceStatus
import com.dailytracker.app.ui.components.TaskEditorDialog
import com.dailytracker.app.ui.screens.HistoryScreen
import com.dailytracker.app.ui.screens.SettingsScreen
import com.dailytracker.app.ui.screens.TasksScreen
import com.dailytracker.app.ui.screens.TodayScreen
import com.dailytracker.app.ui.theme.DailyTrackerTheme
import com.dailytracker.app.ui.viewmodel.DailyTrackerViewModel

enum class BottomNavTab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Outlined.Today),
    TASKS("Tasks", Icons.Outlined.CheckBox),
    HISTORY("History", Icons.Outlined.Analytics),
    SETTINGS("Settings", Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DailyTrackerApp

        setContent {
            val viewModel: DailyTrackerViewModel = viewModel(
                factory = DailyTrackerViewModel.provideFactory(app)
            )
            val uiState by viewModel.uiState.collectAsState()

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {
                viewModel.refreshPermissions()
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshPermissions()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            DailyTrackerTheme(themeMode = uiState.settings.themeMode) {
                MainAppScaffold(
                    uiState = uiState,
                    viewModel = viewModel,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MainAppScaffold(
    uiState: com.dailytracker.app.ui.viewmodel.MainUiState,
    viewModel: DailyTrackerViewModel,
    onRequestNotificationPermission: () -> Unit
) {
    var currentTab by remember { mutableStateOf(BottomNavTab.TODAY) }
    var showTaskDialog by remember { mutableStateOf(false) }
    var taskBeingEdited by remember { mutableStateOf<TaskWithInstanceStatus?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                BottomNavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                BottomNavTab.TODAY -> {
                    TodayScreen(
                        dashboard = uiState.dashboard,
                        use24Hour = uiState.settings.use24HourFormat,
                        activeRingingAlarm = uiState.activeRingingAlarm,
                        onQuickAddTask = {
                            taskBeingEdited = null
                            showTaskDialog = true
                        },
                        onToggleComplete = { viewModel.toggleTaskCompletion(it) },
                        onToggleAlarm = { id, enabled -> viewModel.toggleAlarmEnabled(id, enabled) },
                        onSnoozeTask = { id, mins -> viewModel.snoozeTask(id, mins) },
                        onEditTask = { task ->
                            taskBeingEdited = task
                            showTaskDialog = true
                        },
                        onDeleteTask = { id -> viewModel.deleteTask(id) },
                        onCompleteRingingAlarm = { id, cycleDate ->
                            viewModel.completeRingingTask(id, cycleDate)
                        }
                    )
                }

                BottomNavTab.TASKS -> {
                    TasksScreen(
                        tasks = uiState.filteredTaskInstances,
                        selectedFilter = uiState.selectedTaskFilter,
                        use24Hour = uiState.settings.use24HourFormat,
                        onSelectFilter = { viewModel.selectTaskFilter(it) },
                        onAddTaskClick = {
                            taskBeingEdited = null
                            showTaskDialog = true
                        },
                        onToggleComplete = { viewModel.toggleTaskCompletion(it) },
                        onToggleAlarm = { id, enabled -> viewModel.toggleAlarmEnabled(id, enabled) },
                        onSnoozeTask = { id, mins -> viewModel.snoozeTask(id, mins) },
                        onEditTask = { task ->
                            taskBeingEdited = task
                            showTaskDialog = true
                        },
                        onDeleteTask = { id -> viewModel.deleteTask(id) }
                    )
                }

                BottomNavTab.HISTORY -> {
                    HistoryScreen(
                        selectedDate = uiState.selectedHistoryDate,
                        selectedDateInstances = uiState.selectedDateInstances,
                        weeklyReport = uiState.weeklyReport,
                        monthlyReport = uiState.monthlyReport,
                        use24Hour = uiState.settings.use24HourFormat,
                        onSelectDate = { viewModel.selectHistoryDate(it) },
                        onToggleComplete = { viewModel.toggleTaskCompletion(it) }
                    )
                }

                BottomNavTab.SETTINGS -> {
                    SettingsScreen(
                        settings = uiState.settings,
                        permissions = uiState.permissions,
                        onUpdateSound = { viewModel.updateAlarmSound(it) },
                        onUpdateVibration = { viewModel.updateVibration(it) },
                        onUpdateSnooze = { viewModel.updateDefaultSnooze(it) },
                        onUpdateRepeatInterval = { viewModel.updateRepeatInterval(it) },
                        onUpdate24Hour = { viewModel.updateUse24HourFormat(it) },
                        onUpdateTheme = { viewModel.updateThemeMode(it) },
                        onRequestNotificationPermission = onRequestNotificationPermission
                    )
                }
            }
        }
    }

    if (showTaskDialog) {
        TaskEditorDialog(
            initialTask = taskBeingEdited,
            use24HourFormat = uiState.settings.use24HourFormat,
            onDismiss = {
                showTaskDialog = false
                taskBeingEdited = null
            },
            onSave = { existingId, title, desc, dateStr, hour, min, recurrence, weekdays, alarmOn ->
                viewModel.saveTask(
                    existingId = existingId,
                    title = title,
                    description = desc,
                    scheduledDateStr = dateStr,
                    alarmHour = hour,
                    alarmMinute = min,
                    recurrenceType = recurrence,
                    selectedWeekdays = weekdays,
                    alarmEnabled = alarmOn
                )
                showTaskDialog = false
                taskBeingEdited = null
            }
        )
    }
}
