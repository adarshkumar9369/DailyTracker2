package com.dailytracker.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dailytracker.app.data.model.AlarmToneOption
import com.dailytracker.app.data.model.AppSettings
import com.dailytracker.app.data.model.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "daily_tracker_settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val KEY_ALARM_SOUND = stringPreferencesKey("alarm_sound")
        private val KEY_VIBRATION = booleanPreferencesKey("vibration_enabled")
        private val KEY_SNOOZE_MINS = intPreferencesKey("default_snooze_minutes")
        private val KEY_REPEAT_MINS = intPreferencesKey("repeat_interval_minutes")
        private val KEY_USE_24H = booleanPreferencesKey("use_24_hour_format")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val toneName = prefs[KEY_ALARM_SOUND] ?: AlarmToneOption.DEFAULT_ALARM.name
            val themeName = prefs[KEY_THEME_MODE] ?: AppThemeMode.LIGHT.name

            AppSettings(
                alarmSound = runCatching { AlarmToneOption.valueOf(toneName) }
                    .getOrDefault(AlarmToneOption.DEFAULT_ALARM),
                vibrationEnabled = prefs[KEY_VIBRATION] ?: true,
                defaultSnoozeMinutes = (prefs[KEY_SNOOZE_MINS] ?: 10).coerceIn(5, 15),
                repeatIntervalMinutes = (prefs[KEY_REPEAT_MINS] ?: 5).coerceIn(1, 30),
                use24HourFormat = prefs[KEY_USE_24H] ?: false,
                themeMode = runCatching { AppThemeMode.valueOf(themeName) }
                    .getOrDefault(AppThemeMode.LIGHT)
            )
        }

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun updateAlarmSound(option: AlarmToneOption) {
        context.dataStore.edit { it[KEY_ALARM_SOUND] = option.name }
    }

    suspend fun updateVibration(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VIBRATION] = enabled }
    }

    suspend fun updateDefaultSnoozeMinutes(minutes: Int) {
        val valid = if (minutes in listOf(5, 10, 15)) minutes else 10
        context.dataStore.edit { it[KEY_SNOOZE_MINS] = valid }
    }

    suspend fun updateRepeatIntervalMinutes(minutes: Int) {
        val valid = minutes.coerceIn(1, 30)
        context.dataStore.edit { it[KEY_REPEAT_MINS] = valid }
    }

    suspend fun updateUse24HourFormat(use24Hour: Boolean) {
        context.dataStore.edit { it[KEY_USE_24H] = use24Hour }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }
}
