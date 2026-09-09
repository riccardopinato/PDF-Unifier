package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SleekDarkGreenPrimary,
    onPrimary = SleekDarkGreenOnPrimary,
    primaryContainer = SleekDarkGreenPrimaryContainer,
    onPrimaryContainer = SleekDarkGreenOnPrimaryContainer,
    secondary = SleekGreenSecondaryContainer,
    onSecondary = SleekGreenOnSecondaryContainer,
    background = SleekDarkBackground,
    onBackground = SleekDarkOnSurface,
    surface = SleekDarkSurface,
    onSurface = SleekDarkOnSurface,
    surfaceVariant = SleekDarkSurfaceVariant,
    onSurfaceVariant = SleekDarkOnSurfaceVariant,
    outline = SleekOutline,
    outlineVariant = SleekDarkSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = SleekGreenPrimary,
    onPrimary = SleekGreenOnPrimary,
    primaryContainer = SleekGreenPrimaryContainer,
    onPrimaryContainer = SleekGreenOnPrimaryContainer,
    secondary = SleekGreenSecondary,
    onSecondary = SleekGreenOnSecondary,
    secondaryContainer = SleekGreenSecondaryContainer,
    onSecondaryContainer = SleekGreenOnSecondaryContainer,
    background = SleekBackground,
    onBackground = SleekOnBackground,
    surface = SleekSurface,
    onSurface = SleekOnSurface,
    surfaceVariant = SleekSurfaceVariant,
    onSurfaceVariant = SleekOnSurfaceVariant,
    outline = SleekOutline,
    outlineVariant = SleekOutlineVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
