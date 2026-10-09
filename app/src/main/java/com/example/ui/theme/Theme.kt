package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PinterestRedLight,
    onPrimary = Color.White,
    primaryContainer = PinterestRedDark,
    onPrimaryContainer = Color.White,
    secondary = AccentTeal,
    onSecondary = Color.Black,
    tertiary = AccentGold,
    background = DarkBackground,
    onBackground = Color(0xFFF0F0F5),
    surface = DarkSurface,
    onSurface = Color(0xFFF0F0F5),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFC8C8DC)
)

private val LightColorScheme = lightColorScheme(
    primary = PinterestRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADA),
    onPrimaryContainer = PinterestRedDark,
    secondary = AccentTeal,
    onSecondary = Color.White,
    tertiary = AccentGold,
    background = LightBackground,
    onBackground = Color(0xFF1B1B26),
    surface = LightSurface,
    onSurface = Color(0xFF1B1B26),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF454558)
)

@Composable
fun PinZipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to keep signature red branding consistent
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
