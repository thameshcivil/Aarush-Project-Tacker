package com.aarush.cpm.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/*
 * "Site ledger" design system.
 *
 * Direction taken from Inspo references for technical work tools (LlamaIndex, Smartsheet,
 * Morgen — consensus: light paper surface, grotesk sans, one warm accent): a warm off-white
 * paper, near-black slate ink, hairline 1dp rules instead of shadows, small uppercase
 * monospaced labels over large tabular figures, ink-black rectangular primary buttons, and
 * a single safety-orange accent reserved for progress, selection and attention. A blueprint
 * blue is the only second hue — used strictly for "planned/baseline" vs orange "actual".
 */

// ---- Light tokens
val Paper = Color(0xFFF5F3EE)
val Sheet = Color(0xFFFFFFFF)
val Ink = Color(0xFF1A1D26)
val InkMuted = Color(0xFF5C6270)
val Hairline = Color(0xFFDDD9CF)
val SafetyOrange = Color(0xFFE2620F)
val SafetyOrangeTint = Color(0xFFFCE7D8)
val Blueprint = Color(0xFF2D6CB5)
val BlueprintTint = Color(0xFFDDE8F6)
val SiteGreen = Color(0xFF2E7D4F)
val SiteGreenTint = Color(0xFFDCEFE3)
val AlertRed = Color(0xFFC0392B)
val AlertRedTint = Color(0xFFF8DEDA)
val AmberWarn = Color(0xFFB7791F)

// ---- Dark tokens (charcoal with the same warm accent)
private val Charcoal = Color(0xFF15171C)
private val CharcoalSheet = Color(0xFF1E2128)
private val CharcoalRaised = Color(0xFF262A33)
private val DarkInk = Color(0xFFECEAE4)
private val DarkInkMuted = Color(0xFFA3A8B4)
private val DarkHairline = Color(0xFF353A45)

/** Semantic colours that Material's scheme has no slot for. */
@Immutable
data class SiteColors(
    val accent: Color,
    val accentTint: Color,
    val planned: Color,
    val plannedTint: Color,
    val good: Color,
    val goodTint: Color,
    val bad: Color,
    val badTint: Color,
    val warn: Color,
    val hairline: Color,
    val muted: Color,
    val sheet: Color
)

private val LightSite = SiteColors(SafetyOrange, SafetyOrangeTint, Blueprint, BlueprintTint, SiteGreen, SiteGreenTint,
    AlertRed, AlertRedTint, AmberWarn, Hairline, InkMuted, Sheet)
private val DarkSite = SiteColors(Color(0xFFF07A2E), Color(0xFF3A2516), Color(0xFF6FA3E0), Color(0xFF1B2A3D),
    Color(0xFF5BBE85), Color(0xFF173025), Color(0xFFF0796B), Color(0xFF3B1E1B), Color(0xFFE0A84A), DarkHairline, DarkInkMuted, CharcoalSheet)

val LocalSiteColors = staticCompositionLocalOf { LightSite }

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = SafetyOrangeTint,
    onPrimaryContainer = Color(0xFF6B2B03),
    secondary = SafetyOrange,
    onSecondary = Color.White,
    secondaryContainer = SafetyOrangeTint,
    onSecondaryContainer = Color(0xFF6B2B03),
    tertiary = Blueprint,
    onTertiary = Color.White,
    tertiaryContainer = BlueprintTint,
    onTertiaryContainer = Color(0xFF0E2C52),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDEAE3),
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Sheet,
    surfaceContainerLow = Sheet,
    surfaceContainer = Sheet,
    surfaceContainerHigh = Sheet,
    surfaceContainerHighest = Color(0xFFF0EDE6),
    outline = Color(0xFFB9B4A8),
    outlineVariant = Hairline,
    error = AlertRed,
    errorContainer = AlertRedTint
)

private val DarkColors = darkColorScheme(
    primary = DarkInk,
    onPrimary = Charcoal,
    primaryContainer = Color(0xFF3A2516),
    onPrimaryContainer = Color(0xFFFFD2B3),
    secondary = Color(0xFFF07A2E),
    onSecondary = Charcoal,
    secondaryContainer = Color(0xFF3A2516),
    onSecondaryContainer = Color(0xFFFFD2B3),
    tertiary = Color(0xFF6FA3E0),
    onTertiary = Charcoal,
    background = Charcoal,
    onBackground = DarkInk,
    surface = Charcoal,
    onSurface = DarkInk,
    surfaceVariant = CharcoalRaised,
    onSurfaceVariant = DarkInkMuted,
    surfaceContainerLowest = CharcoalSheet,
    surfaceContainerLow = CharcoalSheet,
    surfaceContainer = CharcoalSheet,
    surfaceContainerHigh = CharcoalRaised,
    surfaceContainerHighest = CharcoalRaised,
    outline = Color(0xFF59606E),
    outlineVariant = DarkHairline,
    error = Color(0xFFF0796B)
)

private val Grotesk = FontFamily.SansSerif
/** Labels: small, uppercase, monospaced — the "spec sheet" voice from the references. */
val LabelMono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, letterSpacing = 0.8.sp)
/** Big figures: tight, heavy, tabular so columns of numbers line up. */
val FigureStyle = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum")

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
        headlineMedium = t.headlineMedium.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        titleLarge = t.titleLarge.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleMedium = t.titleMedium.copy(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
        titleSmall = t.titleSmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontFamily = Grotesk),
        bodyMedium = t.bodyMedium.copy(fontFamily = Grotesk, fontFeatureSettings = "tnum"),
        bodySmall = t.bodySmall.copy(fontFamily = Grotesk, fontFeatureSettings = "tnum"),
        labelLarge = t.labelLarge.copy(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelMedium = t.labelMedium.copy(fontFamily = Grotesk, fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.Medium)
    )
}

/** Squarer than Material's default — a drafting-table feel, not a pill-shaped consumer app. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun AarushCPMTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: content draws behind the bars; just pick legible status icons.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSiteColors provides if (darkTheme) DarkSite else LightSite) {
        MaterialTheme(colorScheme = colorScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** Shortcut: `SiteTheme.colors.accent` etc. */
object SiteTheme {
    val colors: SiteColors
        @Composable get() = LocalSiteColors.current
}
