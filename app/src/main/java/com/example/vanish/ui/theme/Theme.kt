package com.example.vanish.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    onPrimaryContainer = TealOnPrimaryContainer,
    secondaryContainer = TealSecondaryContainer,
    onSecondaryContainer = TealOnSecondaryContainer,
    surface = TealSurface,
    onSurface = TealOnSurface,
    onSurfaceVariant = TealOnSurfaceVariant,
    outline = TealOutline,
    outlineVariant = TealOutlineVariant,
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = TealOnPrimaryDark,
    primaryContainer = TealPrimaryContainerDark,
    onPrimaryContainer = TealOnPrimaryContainerDark,
    secondaryContainer = TealSecondaryContainerDark,
    onSecondaryContainer = TealOnSecondaryContainerDark,
    surface = TealSurfaceDark,
    onSurface = TealOnSurfaceDark,
    onSurfaceVariant = TealOnSurfaceVariantDark,
    outline = TealOutlineDark,
    outlineVariant = TealOutlineVariantDark,
)

@Composable
fun VanishTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
