package com.example.xs.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = InstagramBlue,
    secondary = InstagramSecondaryText,
    tertiary = InstagramBlue,
    background = InstagramBlack,
    surface = InstagramBlack,
    onPrimary = InstagramWhite,
    onSecondary = InstagramWhite,
    onBackground = InstagramWhite,
    onSurface = InstagramWhite
)

private val LightColorScheme = lightColorScheme(
    primary = InstagramBlue,
    secondary = InstagramSecondaryText,
    tertiary = InstagramBlue,
    background = InstagramWhite,
    surface = InstagramWhite,
    onPrimary = InstagramWhite,
    onSecondary = InstagramBlack,
    onBackground = InstagramBlack,
    onSurface = InstagramBlack
)

@Composable
fun XsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
