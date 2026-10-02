package com.vaultgallery.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val Jade = Color(0xFF34D399)
val Emerald = Color(0xFF0F5E44)
val Emerald2 = Color(0xFF1A8F66)
val Gold = Color(0xFFD4AF37)
val Gold2 = Color(0xFFF4D97A)
val Danger = Color(0xFFE0625A)

private val DarkScheme = darkColorScheme(
    primary = Jade, onPrimary = Color(0xFF04130D),
    primaryContainer = Emerald, onPrimaryContainer = Color(0xFFEAF4EE),
    secondary = Gold, onSecondary = Color(0xFF1B1500),
    background = Color(0xFF070A09), onBackground = Color(0xFFEAF4EE),
    surface = Color(0xFF0D1311), onSurface = Color(0xFFEAF4EE),
    surfaceVariant = Color(0xFF16201C), onSurfaceVariant = Color(0xFFB8CCC2),
    outline = Color(0xFF3A4A42), error = Danger,
)

private val LightScheme = lightColorScheme(
    primary = Emerald, onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEDD), onPrimaryContainer = Color(0xFF0C1310),
    secondary = Color(0xFF8A6D00), onSecondary = Color.White,
    background = Color(0xFFF4F7F5), onBackground = Color(0xFF0C1310),
    surface = Color.White, onSurface = Color(0xFF0C1310),
    surfaceVariant = Color(0xFFEEF3F0), onSurfaceVariant = Color(0xFF3F534A),
    outline = Color(0xFFAEBBB4), error = Color(0xFFB3261E),
)

/** mode: 0 system, 1 dark, 2 light */
@Composable
fun VaultTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

fun isDarkMode(mode: Int, systemDark: Boolean) = when (mode) { 1 -> true; 2 -> false; else -> systemDark }

/** Frosted "glass" surface with the gold hairline from the web design. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(14.dp)): Modifier =
    this.clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        .border(1.dp, Gold.copy(alpha = 0.18f), shape)
