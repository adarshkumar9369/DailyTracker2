package com.dailytracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.dailytracker.app.data.model.AppThemeMode

private val LightColors = lightColorScheme(
    primary = AccentPrimary,
    onPrimary = PureWhite,
    primaryContainer = AccentPrimaryContainer,
    onPrimaryContainer = OnAccentPrimaryContainer,
    secondary = CharcoalText,
    onSecondary = PureWhite,
    background = OffWhiteBackground,
    onBackground = InkBlack,
    surface = CardLightSurface,
    onSurface = InkBlack,
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = MutedGreyText,
    outline = SubtleGreyBorder,
    error = StatusMissedRed,
    onError = PureWhite
)

private val DarkColors = darkColorScheme(
    primary = DarkAccentPrimary,
    onPrimary = PureWhite,
    primaryContainer = DarkAccentContainer,
    onPrimaryContainer = DarkPrimaryText,
    secondary = DarkPrimaryText,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkSurface,
    onSurface = DarkPrimaryText,
    surfaceVariant = Color(0xFF18181B),
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkBorder,
    error = Color(0xFFEF4444),
    onError = PureWhite
)

@Composable
fun DailyTrackerTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
