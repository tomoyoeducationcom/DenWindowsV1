package com.denwindows.v1.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class DenDesktopColors(
    val isDark: Boolean,
    val desktopBackground: Color,
    val windowSurface: Color,
    val secondarySurface: Color,
    val glassSurface: Color,
    val taskbarSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val accent: Color,
    val accentContainer: Color,
    val success: Color = DenPalette.Success,
    val warning: Color = DenPalette.Warning,
    val error: Color = DenPalette.Error
)

val LocalDenColors = staticCompositionLocalOf {
    DenDesktopColors(
        isDark = true,
        desktopBackground = DenPalette.DarkBackground,
        windowSurface = DenPalette.DarkSurface,
        secondarySurface = DenPalette.DarkSecondarySurface,
        glassSurface = DenPalette.DarkGlassSurface,
        taskbarSurface = DenPalette.DarkTaskbarSurface,
        textPrimary = DenPalette.DarkTextPrimary,
        textSecondary = DenPalette.DarkTextSecondary,
        border = DenPalette.DarkBorder,
        accent = AccentOption.COBALT.color,
        accentContainer = AccentOption.COBALT.containerDark
    )
}

private val DenShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun DenWindowsTheme(
    isDarkTheme: Boolean = true,
    accentOption: AccentOption = AccentOption.COBALT,
    content: @Composable () -> Unit
) {
    val denColors = if (isDarkTheme) {
        DenDesktopColors(
            isDark = true,
            desktopBackground = DenPalette.DarkBackground,
            windowSurface = DenPalette.DarkSurface,
            secondarySurface = DenPalette.DarkSecondarySurface,
            glassSurface = DenPalette.DarkGlassSurface,
            taskbarSurface = DenPalette.DarkTaskbarSurface,
            textPrimary = DenPalette.DarkTextPrimary,
            textSecondary = DenPalette.DarkTextSecondary,
            border = DenPalette.DarkBorder,
            accent = accentOption.color,
            accentContainer = accentOption.containerDark
        )
    } else {
        DenDesktopColors(
            isDark = false,
            desktopBackground = DenPalette.LightBackground,
            windowSurface = DenPalette.LightSurface,
            secondarySurface = DenPalette.LightSecondarySurface,
            glassSurface = DenPalette.LightGlassSurface,
            taskbarSurface = DenPalette.LightTaskbarSurface,
            textPrimary = DenPalette.LightTextPrimary,
            textSecondary = DenPalette.LightTextSecondary,
            border = DenPalette.LightBorder,
            accent = accentOption.color,
            accentContainer = accentOption.containerLight
        )
    }

    val materialScheme = if (isDarkTheme) {
        darkColorScheme(
            primary = accentOption.color,
            onPrimary = Color.White,
            primaryContainer = accentOption.containerDark,
            onPrimaryContainer = Color.White,
            secondary = DenPalette.DarkSecondarySurface,
            onSecondary = DenPalette.DarkTextPrimary,
            background = DenPalette.DarkBackground,
            onBackground = DenPalette.DarkTextPrimary,
            surface = DenPalette.DarkSurface,
            onSurface = DenPalette.DarkTextPrimary,
            surfaceVariant = DenPalette.DarkSecondarySurface,
            onSurfaceVariant = DenPalette.DarkTextSecondary,
            outline = DenPalette.DarkBorder,
            error = DenPalette.Error
        )
    } else {
        lightColorScheme(
            primary = accentOption.color,
            onPrimary = Color.White,
            primaryContainer = accentOption.containerLight,
            onPrimaryContainer = DenPalette.LightTextPrimary,
            secondary = DenPalette.LightSecondarySurface,
            onSecondary = DenPalette.LightTextPrimary,
            background = DenPalette.LightBackground,
            onBackground = DenPalette.LightTextPrimary,
            surface = DenPalette.LightSurface,
            onSurface = DenPalette.LightTextPrimary,
            surfaceVariant = DenPalette.LightSecondarySurface,
            onSurfaceVariant = DenPalette.LightTextSecondary,
            outline = DenPalette.LightBorder,
            error = DenPalette.Error
        )
    }

    CompositionLocalProvider(LocalDenColors provides denColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = DenTypography,
            shapes = DenShapes,
            content = content
        )
    }
}
