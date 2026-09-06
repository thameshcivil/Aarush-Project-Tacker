package com.aarush.cpm.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Construction-management palette: safety-orange accent on slate/charcoal neutrals.
val ConstructionOrange = Color(0xFFE8622C)
val ConstructionOrangeDark = Color(0xFFB94A1E)
val Slate900 = Color(0xFF1C1F26)
val Slate700 = Color(0xFF3A3F4B)
val SlateSurface = Color(0xFFF5F6F8)
val SuccessGreen = Color(0xFF2E7D32)
val WarningAmber = Color(0xFFEF6C00)
val DangerRed = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = ConstructionOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = ConstructionOrangeDark,
    secondary = Slate700,
    onSecondary = Color.White,
    background = SlateSurface,
    surface = Color.White,
    onSurface = Slate900,
    error = DangerRed
)

private val DarkColors = darkColorScheme(
    primary = ConstructionOrange,
    onPrimary = Color.Black,
    background = Slate900,
    surface = Slate700,
    onSurface = Color.White,
    error = Color(0xFFFF6B6B)
)

@Composable
fun AarushCPMTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
}
