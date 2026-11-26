package com.momentline.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.momentline.android.data.AppAppearance

private val LightColors = lightColorScheme(
    primary = Color(0xFF275F55),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0EBE4),
    onPrimaryContainer = Color(0xFF173F38),
    secondary = Color(0xFF9A682F),
    background = Color(0xFFF4F3EE),
    onBackground = Color(0xFF252C29),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF252C29),
    surfaceVariant = Color(0xFFEAECE5),
    onSurfaceVariant = Color(0xFF5F6A64),
    outline = Color(0xFFD9DDD4),
    error = Color(0xFFA13E3E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC7B3),
    onPrimary = Color(0xFF14231E),
    primaryContainer = Color(0xFF2D443B),
    onPrimaryContainer = Color(0xFFEDF0E9),
    secondary = Color(0xFFDFAD73),
    background = Color(0xFF171C1A),
    onBackground = Color(0xFFEDF0E9),
    surface = Color(0xFF222926),
    onSurface = Color(0xFFEDF0E9),
    surfaceVariant = Color(0xFF2B332F),
    onSurfaceVariant = Color(0xFFB0BBB3),
    outline = Color(0xFF3B4540),
    error = Color(0xFFF0A1A1)
)

@Composable
fun MomentTheme(appearance: AppAppearance, content: @Composable () -> Unit) {
    val dark = when (appearance) {
        AppAppearance.SYSTEM -> isSystemInDarkTheme()
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}