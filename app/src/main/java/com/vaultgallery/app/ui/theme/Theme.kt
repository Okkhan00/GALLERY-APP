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
import androidx.compose.runtime.staticCompositionLocalOf
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
    surfaceContainerLowest = Color(0xFF070A09), surfaceContainerLow = Color(0xFF0D1311), surfaceContainer = Color(0xFF111815),
    surfaceContainerHigh = Color(0xFF16201C), surfaceContainerHighest = Color(0xFF1C2823),
)

/** True-black variant for OLED panels: only the surfaces change, every other colour role stays identical to Dark. */
private val AmoledScheme = DarkScheme.copy(
    background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF101613),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF050706), surfaceContainer = Color(0xFF0A0E0C),
    surfaceContainerHigh = Color(0xFF101613), surfaceContainerHighest = Color(0xFF161E1A),
    outline = Color(0xFF33423A),
)

private val LightScheme = lightColorScheme(
    primary = Emerald, onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEDD), onPrimaryContainer = Color(0xFF0C1310),
    secondary = Color(0xFF8A6D00), onSecondary = Color.White,
    background = Color(0xFFF4F7F5), onBackground = Color(0xFF0C1310),
    surface = Color.White, onSurface = Color(0xFF0C1310),
    surfaceVariant = Color(0xFFEEF3F0), onSurfaceVariant = Color(0xFF3F534A),
    outline = Color(0xFFAEBBB4), error = Color(0xFFB3261E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F9F7), surfaceContainer = Color(0xFFEEF3F0),
    surfaceContainerHigh = Color(0xFFE8EEEA), surfaceContainerHighest = Color(0xFFE2E9E5),
)

/** Subtle motion is skipped when the user turned animations off or picked Performance / Battery saver. */
val LocalAnimations = staticCompositionLocalOf { true }

/** mode: 0 system, 1 dark, 2 light, 3 AMOLED black */
@Composable
fun VaultTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1, 3 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (mode == 3) AmoledScheme else if (dark) DarkScheme else LightScheme, content = content)
}

fun isDarkMode(mode: Int, systemDark: Boolean) = when (mode) { 1, 3 -> true; 2 -> false; else -> systemDark }

/** Frosted "glass" surface with the gold hairline from the web design. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(14.dp)): Modifier =
    this.clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        .border(1.dp, Gold.copy(alpha = 0.18f), shape)
