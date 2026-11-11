package com.dailytracker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailytracker.app.data.model.AlarmToneOption
import com.dailytracker.app.data.model.AppSettings
import com.dailytracker.app.data.model.AppThemeMode
import com.dailytracker.app.ui.theme.StatusCompletedGreen
import com.dailytracker.app.ui.theme.StatusMissedRed
import com.dailytracker.app.util.PermissionHelper
import com.dailytracker.app.util.SystemPermissionStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    permissions: SystemPermissionStatus,
    onUpdateSound: (AlarmToneOption) -> Unit,
    onUpdateVibration: (Boolean) -> Unit,
    onUpdateSnooze: (Int) -> Unit,
    onUpdateRepeatInterval: (Int) -> Unit,
    onUpdate24Hour: (Boolean) -> Unit,
    onUpdateTheme: (AppThemeMode) -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Configure persistent alarm behavior, permissions, and display",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsCard(title = "Alarm Audio & Vibration") {
                Text(
                    text = "Alarm Sound Selection",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AlarmToneOption.entries.forEach { option ->
                        FilterChip(
                            selected = settings.alarmSound == option,
                            onClick = { onUpdateSound(option) },
                            label = { Text(option.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vibration During Alarm",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Pulse device vibrator continuously while alarm rings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.vibrationEnabled,
                        onCheckedChange = onUpdateVibration
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Alarm Volume Guidance: Daily Tracker plays alarms over Android's dedicated Alarm audio stream (USAGE_ALARM) and automatically boosts muted alarm streams to 70% when triggered. Ensure your hardware Alarm volume is audible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching { context.startActivity(PermissionHelper.buildSoundSettingsIntent()) }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open System Sound & Volume Settings")
                }
            }
        }

        item {
            SettingsCard(title = "Snooze & Persistent Repeat Timing") {
                Text(
                    text = "Default Snooze Duration",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15).forEach { mins ->
                        FilterChip(
                            selected = settings.defaultSnoozeMinutes == mins,
                            onClick = { onUpdateSnooze(mins) },
                            label = { Text("$mins min") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Uncompleted Task Repeat Interval",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "How often the alarm automatically rings again until the task is marked Completed:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2, 5, 10, 15).forEach { mins ->
                        FilterChip(
                            selected = settings.repeatIntervalMinutes == mins,
                            onClick = { onUpdateRepeatInterval(mins) },
                            label = { Text("Every $mins min") }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Clock & Appearance") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "24-Hour Clock Format",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (settings.use24HourFormat) "Displaying 14:30 format" else "Displaying 02:30 PM format",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.use24HourFormat,
                        onCheckedChange = onUpdate24Hour
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "App Theme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onUpdateTheme(mode) },
                            label = { Text(mode.displayName) }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(title = "Permissions & Background Reliability") {
                PermissionRow(
                    title = "Notifications (POST_NOTIFICATIONS)",
                    granted = permissions.notificationsGranted,
                    actionLabel = "Grant",
                    onAction = onRequestNotificationPermission
                )
                Spacer(modifier = Modifier.height(10.dp))
                PermissionRow(
                    title = "Exact Alarms (SCHEDULE_EXACT_ALARM)",
                    granted = permissions.exactAlarmGranted,
                    actionLabel = "Configure",
                    onAction = {
                        runCatching { context.startActivity(PermissionHelper.buildExactAlarmSettingsIntent(context)) }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                PermissionRow(
                    title = "Full-Screen Lockscreen Alarm Intent",
                    granted = permissions.fullScreenIntentGranted,
                    actionLabel = "Configure",
                    onAction = {
                        runCatching { context.startActivity(PermissionHelper.buildFullScreenIntentSettingsIntent(context)) }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                PermissionRow(
                    title = "Unrestricted Battery / Doze Exemption",
                    granted = permissions.batteryOptimizationIgnored,
                    actionLabel = "Allow",
                    onAction = {
                        runCatching { context.startActivity(PermissionHelper.buildBatteryOptimizationIntent(context)) }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Android Background Execution Note: On Android 12+ (API 31+), exact alarms require 'Alarms & reminders' permission. On Android 14+ (API 34+), full-screen lockscreen popups require 'Full-screen notifications' access. Some OEMs (Xiaomi MIUI, Samsung OneUI, Oppo/Vivo) apply aggressive battery killing on force-stopped apps—enabling 'Unrestricted Battery' ensures alarms fire reliably overnight.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (granted) "Active & Granted" else "Permission Required",
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) StatusCompletedGreen else StatusMissedRed,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (!granted) {
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}
