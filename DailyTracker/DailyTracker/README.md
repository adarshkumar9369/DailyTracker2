# Daily Tracker — Android Application (Kotlin + Jetpack Compose + Room + AlarmManager)

## 1. Overview & Architecture

**Daily Tracker** is a native, offline-first Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, **Room Database**, **Preferences DataStore**, **Exact AlarmManager**, **Foreground Service (`mediaPlayback|specialUse`)**, and **WorkManager**.

### Clean Architecture Layers

* **UI Layer (`ui/`)**:

  * `MainActivity.kt`: Single-activity host with Material 3 `NavigationBar` (`Today`, `Tasks`, `History`, `Settings`), runtime permission launchers, and lifecycle observers.

  * `TodayScreen.kt`: Displays current day/date, daily completion percentage, linear progress bar, 4 metric cards (`Total`, `Completed`, `Pending`, `Missed`), upcoming task & next alarm card, active ringing alarm banner, and `Quick Add Task` FAB.

  * `TasksScreen.kt`: Create, edit, delete, toggle individual alarms, snooze, mark completed, and filter tasks across `All`, `Pending`, `Completed`, and `Missed`.

  * `HistoryScreen.kt`: Weekly (Last 7 Days) and Monthly (Last 30 Days) completion reports, completed vs. pending/missed statistics, 14-day interactive calendar strip, and full `DatePickerDialog` history browser.

  * `SettingsScreen.kt`: Alarm tone selection (`Default System Alarm`, `System Ringtone`, `Urgent Notification Beep`), volume stream guidance with direct shortcut to system sound settings, vibration toggle, default snooze duration (`5`, `10`, `15` min), persistent uncompleted repeat interval (`2`, `5`, `10`, `15` min), `12/24-hour` clock toggle, theme selector (`Light`, `Dark`, `System`), and live permission status diagnostics.

  * `AlarmActivity.kt`: Full-screen lock-screen wakeup activity (`setShowWhenLocked(true)`, `setTurnScreenOn(true)`, `FLAG_KEEP_SCREEN_ON`) presenting the task title, description, repeat counter, **Mark Task Completed (Stop Alarm)** button, and **5m / 10m / 15m Snooze** buttons.

* **ViewModel Layer (`ui/viewmodel/DailyTrackerViewModel.kt`)**:

  * Combines reactive `Flow` streams from Room (`tasks` + `task_history`), `SettingsManager` (DataStore), and `AlarmService.activeAlarmFlow` with a 30-second clock tick so `Pending -> Missed` transitions update automatically without manual refresh.

* **Repository Layer (`data/repository/TaskRepository.kt`)**:

  * Single source of truth coordinating Room persistence, historical completion snapshots, exact alarm scheduling, repeat scheduling, snooze management, and boot/time-change reconciliation.

* **Room Database Layer (`data/local/`)**:

  * `TaskEntity.kt`: Stores task ID, title, description, scheduled date (`YYYY-MM-DD`), alarm hour/minute, recurrence type (`ONE_TIME`, `DAILY`, `SPECIFIC_DAYS`), selected weekdays (`1..7`), completion status, completion timestamp, last completed date, alarm enabled flag, snooze status (`NONE`, `SNOOZED`, `RINGING`), snoozed-until timestamp, next trigger timestamp, active cycle date, and repeat count.

  * `TaskHistoryEntity.kt`: Preserves per-date completion and missed logs across days (`taskId + dateStr` unique index) so recurring tasks retain complete historical accuracy.

* **Persistent Alarm & Background System (`alarm/`, `receiver/`, `worker/`, `notification/`)**:

  * `AlarmScheduler.kt`: Uses `AlarmManager.setAlarmClock()` when `canScheduleExactAlarms()` is granted (waking the device from Doze and granting foreground start privileges) and gracefully falls back to `setAndAllowWhileIdle()` if exact alarm permission is denied. Uses deterministic `PendingIntent` request codes (`task.id.toInt()`) with `FLAG_UPDATE_CURRENT` so duplicate alarms for the same task can never be created.

  * `AlarmService.kt`: Foreground service playing looping audio over `AudioAttributes.USAGE_ALARM` (automatically boosting muted alarm streams to 70% if zero) with waveform vibration and a partial `WakeLock`. Immediately upon triggering, `onAlarmTriggeredAndScheduleRepeat()` schedules the next repeat in `AlarmManager` (`now + repeatIntervalMinutes`).

  * `AlarmActionReceiver.kt`: Handles `ACTION_COMPLETE_TASK`, `ACTION_SNOOZE_ALARM`, and `ACTION_NOTIFICATION_DISMISSED`. Swiping/dismissing the notification (`deleteIntent`) **never** marks the task as completed; it stops the current 90-second audio burst while keeping the next repeat alarm active in `AlarmManager` and posting a persistent reminder banner.

  * `SystemBroadcastReceiver.kt` & `AlarmReconciliationWorker.kt`: Listens for `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED`, and `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` to reconcile all alarms and backfill missed task history.

## 2. Android Background Execution Limitations & OS Compatibility

1. **Android 12+ (API 31+) Exact Alarms (`SCHEDULE_EXACT_ALARM`)**:

   * Starting in Android 12, apps targeting API 31+ must hold user-granted `Alarms & reminders` access to call `setAlarmClock()` / `setExactAndAllowWhileIdle()`.

   * If the user revokes or denies this permission, `AlarmScheduler` catches the check via `alarmManager.canScheduleExactAlarms()` and falls back to `setAndAllowWhileIdle()`, while the **Settings** screen highlights the missing permission with a one-tap **Configure** button (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`).

2. **Android 13+ (API 33+) Runtime Notifications (`POST_NOTIFICATIONS`)**:

   * Notification banners require explicit runtime approval. Even if notification permission is denied, `AlarmService` still executes the scheduled wakeup and plays the loud alarm stream, and users can grant notification permission directly from Settings.

3. **Android 14+ (API 34+) Full-Screen Intent Restrictions (`USE_FULL_SCREEN_INTENT`)**:

   * On Android 14+, full-screen lockscreen intents require explicit permission (`NotificationManager.canUseFullScreenIntent()`). `SettingsScreen` detects this state and provides a direct launcher (`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`). When the screen is unlocked, Android displays the alarm as a high-priority heads-up notification with **Complete Task** and **Snooze** actions plus an in-app live banner.

4. **Force-Stop & OEM Battery Savers**:

   * Standard Android behavior cancels all `AlarmManager` alarms if the user explicitly clicks **Force Stop** in system settings until the app is opened again. Upon reopening, `DailyTrackerViewModel` and `DailyTrackerApp` immediately run `reconcileAllAlarmsAndHistory()` to restore all pending and repeating alarms.

   * Aggressive OEM battery savers (Xiaomi MIUI, Samsung OneUI, Oppo ColorOS, Vivo) may restrict background wakeups unless the app is marked **Unrestricted**. The **Settings** screen includes a one-tap **Allow Unrestricted Battery** button (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).

## 3. Build & Installation Instructions

### Prerequisites

* **Android Studio Ladybug (2024.2.1+)** or newer (or command-line **Android SDK Platform 35** + **JDK 17**).

### Step 1: Extract the Project

Unzip `DailyTracker_Android_Project.zip` into a local directory:

```
unzip DailyTracker_Android_Project.zip -d DailyTracker
cd DailyTracker

```

### Step 2: Open in Android Studio & Run Unit Tests

1. Open **Android Studio** -> **Open** -> select the `DailyTracker` folder.

2. Let Gradle sync download the Android SDK 35 & Jetpack Compose dependencies.

3. Run the unit test suite verifying alarm scheduling, repeating alarms, snooze intervals, recurrence rules, and timezone shifts:

```
./gradlew testDebugUnitTest

```

### Step 3: Build & Install the Debug APK for Testing

Connect your Android phone (with **USB Debugging** enabled in Developer Options) or start an Android Emulator:

```
# Build Debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk

# Install directly to connected phone via ADB:
adb install -r app/build/outputs/apk/debug/app-debug.apk

```

## 4. Release-Ready Signed APK Instructions

### Step 1: Generate a Production Keystore

Run `keytool` (included with JDK 17) to create your private signing key:

```
keytool -genkey -v \
  -keystore daily-tracker-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias daily_tracker_key

```

### Step 2: Build the Signed Release APK

`app/build.gradle.kts` is pre-configured to read your signing credentials from environment variables so secrets are never hardcoded in source control:

```
export KEYSTORE_PATH="/absolute/path/to/daily-tracker-release.jks"
export KEYSTORE_PASSWORD="your_keystore_password"
export KEY_ALIAS="daily_tracker_key"
export KEY_PASSWORD="your_key_password"

./gradlew assembleRelease

```

The signed, R8-minified production APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

Install it on your personal phone:

```
adb install -r app/build/outputs/apk/release/app-release.apk

```

## 5. End-to-End Verification & Testing Checklist

| Scenario | How It Is Handled & Verified | 
| ----- | ----- | 
| **1. Creating & Editing Tasks** | `TaskRepository.saveTask()` validates fields, cancels any prior alarm `PendingIntent` (`task.id.toInt()`), stops any active ringing for that task, saves to Room, and schedules the new exact timestamp. | 
| **2. Alarm Triggering at Correct Time** | `AlarmScheduler` registers `AlarmManager.setAlarmClock()` with `RTC_WAKEUP`, waking the device at the exact minute/second and starting `AlarmService`. | 
| **3. Repeating Alarm Until Completion** | As soon as `AlarmService` starts, `onAlarmTriggeredAndScheduleRepeat()` increments `repeatCount`, sets `SnoozeStatus.RINGING`, and schedules the next alarm at `now + repeatIntervalMinutes`. | 
| **4. Snooze Functionality (5 / 10 / 15m)** | Tapping 5m, 10m, or 15m in `AlarmActivity`, notification, or task card stops audio playback and schedules `snoozedUntilTimestamp = now + snoozeMinutes * 60_000L`. | 
| **5. Completing Task While Ringing** | Tapping **Mark Task Completed** immediately stops `AlarmService`, cancels notifications and `AlarmManager` entries, writes `COMPLETED` to `task_history`, and schedules the next recurring day if applicable. | 
| **6. Dismissing Notification != Completion** | Swiping away the notification triggers `ACTION_NOTIFICATION_DISMISSED`, which never marks the task completed, leaves the next repeat alarm scheduled in `AlarmManager`, and posts a warning banner. | 
| **7. Rebooting the Phone** | `SystemBroadcastReceiver` captures `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` and invokes `reconcileAllAlarmsAndHistory()` to restore all alarms from Room. | 
| **8. Force-Closing & Reopening** | `DailyTrackerApp.onCreate()` and `DailyTrackerViewModel.init` run `reconcileAllAlarmsAndHistory()`, immediately re-registering any missed or upcoming alarms. | 
| **9. Recurring Tasks Across Days** | `TaskScheduleCalculator` evaluates `ONE_TIME`, `DAILY`, and `SPECIFIC_DAYS` against each `LocalDate`, while `TaskHistoryEntity` stores independent daily completion records. | 
| **10. Permissions Denied Fallback** | `AlarmScheduler` checks `canScheduleExactAlarms()` and falls back to `setAndAllowWhileIdle()` without crashing; `SettingsScreen` provides one-tap permission resolution buttons. | 
| **11. Timezone & Clock Changes** | `SystemBroadcastReceiver` listens for `TIME_SET`, `TIMEZONE_CHANGED`, and `DATE_CHANGED`, recalculating epoch timestamps in the new `ZoneId.systemDefault()`. |

## 6. Automated GitHub Actions APK Build (`.github/workflows/build-apk.yml`)

The project includes a pre-configured GitHub Actions workflow at `.github/workflows/build-apk.yml` (included both at the archive root and inside `DailyTracker/.github/workflows/build-apk.yml`) and full Gradle Wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, and `gradle/wrapper/gradle-wrapper.jar`).

### How It Works
1. Push the extracted repository (either the `DailyTracker` folder at the repo root or its contents directly at the repo root) to GitHub.
2. On every push, pull request, or manual **Run workflow** (`workflow_dispatch`) trigger in the **Actions** tab:
   - Sets up **Temurin JDK 17**, **Android SDK Platform 35**, and **Gradle 8.9** via the wrapper.
   - Automatically detects whether the project root is `DailyTracker/` or `.`.
   - Runs `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`.
   - Uploads `app-debug.apk` (`DailyTracker/app/build/outputs/apk/debug/app-debug.apk`) as a downloadable workflow artifact named **`app-debug`**.
