package com.app.check_inpets.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WarmTerracotta,
    onPrimary = Color.White,
    secondary = SkyAccent,
    onSecondary = Color.White,
    tertiary = SageGreen,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = Color(0xFF332A24),
    onSurfaceVariant = Color(0xFFD0C2B8)
)

private val LightColorScheme = lightColorScheme(
    primary = WarmTerracotta,
    onPrimary = Color.White,
    secondary = SkyAccent,
    onSecondary = Color.White,
    tertiary = SageGreen,
    background = SoftCream,
    onBackground = DeepEspresso,
    surface = Color.White,
    onSurface = DeepEspresso,
    surfaceVariant = SoftSand,
    onSurfaceVariant = MutedTaupe
)

@Composable
fun CheckinPetsTheme(
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
