package com.b2tech.fimirtsp.ui.theme

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

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F3B7C),
    onPrimary = Color.White,
    secondary = Color(0xFF1F6FD8),
    tertiary = Color(0xFF10A37F),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFB00020),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7DB1FF),
    onPrimary = Color(0xFF002F6C),
    secondary = Color(0xFF9BC1FF),
    tertiary = Color(0xFF3ADAB4),
    background = Color(0xFF0F172A),
    surface = Color(0xFF111827),
)

@Composable
fun FimiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
