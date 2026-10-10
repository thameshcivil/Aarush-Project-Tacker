package com.aarush.cpm.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/*
 * "Aarush" brand theme — derived from the company logo (maroon-to-crimson "A", metallic gold
 * swoosh and house, "Makes You Comfort" script tagline).
 *
 * Direction checked against Inspo references for warm, premium brands: Nordiska Museet
 * (ivory paper, deep burgundy, bold caps headings marked by a short coloured rule), Louis
 * Poulsen (metallic gold used sparingly for luxury), Daptone (maroon on cream).
 *
 *  - Paper: warm ivory; sheets: near-white with warm hairlines (no shadows).
 *  - Maroon is the STRUCTURE colour: primary buttons, FABs, wordmark, headings rule.
 *  - Gold is the ACCENT: progress, the active tab, highlights, the brand rule. Used
 *    sparingly, as in the logo.
 *  - Planned/baseline is a dark NEUTRAL (outline or tick) against gold "actual"; status
 *    colours (green good, red bad = delayed / over budget, orange warn) are kept distinct.
 *
 * Colour roles (redesign rule, keep to these):
 *    maroon  -> brand identity and primary actions only
 *    gold    -> actual / achieved progress only
 *    planned -> baseline / planned marks only (dark neutral)
 *    bad     -> delayed or over-budget status only
 *
 * Shape rule: tags 4dp, buttons / inputs / chips / menus 6dp, cards / dialogs / sheets 8dp,
 * progress bars 2dp. Every button passes shapes.small so none fall back to M3's pill shape.
 */

// ---- Brand
val AarushMaroon = Color(0xFF7A1712)
val AarushMaroonDeep = Color(0xFF4E0D0A)
val AarushCrimson = Color(0xFFB3261E)
val AarushGold = Color(0xFFB8862B)        // darker metallic gold — legible on ivory
val AarushGoldBright = Color(0xFFE2B84A)  // highlight gold (logo house)
val AarushGoldTint = Color(0xFFF5E9CF)

// ---- Light neutrals
private val Ivory = Color(0xFFFAF5EC)
private val Sheet = Color(0xFFFFFDF9)
private val Ink = Color(0xFF2A1714)
private val InkMuted = Color(0xFF735F59)
private val Hairline = Color(0xFFE7DBC7)

// ---- Dark neutrals (maroon-black with the same gold)
private val Night = Color(0xFF170C0B)
private val NightSheet = Color(0xFF221412)
private val NightRaised = Color(0xFF2E1B18)
private val NightInk = Color(0xFFF3E9DC)
private val NightMuted = Color(0xFFB9A79E)
private val NightHairline = Color(0xFF41292A)

/** Semantic colours that Material's scheme has no slot for. */
@Immutable
data class SiteColors(
    /** Progress fills, active tab, highlights — brand gold. */
    val accent: Color,
    val accentTint: Color,
    /** Dark gold for TEXT on accentTint (bright gold text there would be illegible). */
    val accentText: Color,
    /** Planned / baseline marks: a dark neutral, so it never competes with brand maroon or
     *  with red "delayed / over budget". */
    val planned: Color,
    val plannedTint: Color,
    val good: Color,
    val goodTint: Color,
    val bad: Color,
    val badTint: Color,
    val warn: Color,
    val hairline: Color,
    val muted: Color,
    val sheet: Color,
    /** Brand colours for wordmark, rules and headers. */
    val maroon: Color,
    val gold: Color
)

private val LightSite = SiteColors(
    accent = AarushGold, accentTint = AarushGoldTint, accentText = Color(0xFF6E4E10),
    planned = Color(0xFF3D3431), plannedTint = Color(0xFFECE6DF),
    // Status shades darkened (same hues) so text meets WCAG AA 4.5:1 on its pill tint and on
    // cards: good 5.4:1 on tint, bad 5.3:1, warn 5.3:1 on sheet. Brand maroon/gold unchanged.
    good = Color(0xFF276B44), goodTint = Color(0xFFDDEFE3),
    bad = Color(0xFFB71C1C), badTint = Color(0xFFFBE0DE),
    warn = Color(0xFFA85310),
    hairline = Hairline, muted = InkMuted, sheet = Sheet,
    maroon = AarushMaroon, gold = AarushGold
)
private val DarkSite = SiteColors(
    accent = AarushGoldBright, accentTint = Color(0xFF3A2B12), accentText = Color(0xFFF2CF77),
    planned = Color(0xFFD8CCC4), plannedTint = Color(0xFF2E2624),
    good = Color(0xFF5BBE85), goodTint = Color(0xFF173025),
    bad = Color(0xFFFF6B60), badTint = Color(0xFF3F1A18),
    warn = Color(0xFFF0A050),
    hairline = NightHairline, muted = NightMuted, sheet = NightSheet,
    maroon = Color(0xFFE07A6E), gold = AarushGoldBright
)

val LocalSiteColors = staticCompositionLocalOf { LightSite }

private val LightColors = lightColorScheme(
    primary = AarushMaroon,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3DEDA),
    onPrimaryContainer = AarushMaroonDeep,
    secondary = AarushGold,
    onSecondary = Color.White,
    secondaryContainer = AarushGoldTint,
    onSecondaryContainer = Color(0xFF4A3305),
    tertiary = AarushCrimson,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFADBD7),
    onTertiaryContainer = AarushMaroonDeep,
    background = Ivory,
    onBackground = Ink,
    surface = Ivory,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1E8DA),
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Sheet,
    surfaceContainerLow = Sheet,
    surfaceContainer = Sheet,
    surfaceContainerHigh = Sheet,
    surfaceContainerHighest = Color(0xFFF3EBDD),
    outline = Color(0xFFBFAE97),
    outlineVariant = Hairline,
    error = Color(0xFFB71C1C),
    errorContainer = Color(0xFFFBE0DE)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE9A79F),
    onPrimary = AarushMaroonDeep,
    primaryContainer = AarushMaroon,
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = AarushGoldBright,
    onSecondary = Color(0xFF3A2805),
    secondaryContainer = Color(0xFF3A2B12),
    onSecondaryContainer = Color(0xFFFFE3A8),
    tertiary = Color(0xFFFF8A7E),
    onTertiary = AarushMaroonDeep,
    background = Night,
    onBackground = NightInk,
    surface = Night,
    onSurface = NightInk,
    surfaceVariant = NightRaised,
    onSurfaceVariant = NightMuted,
    surfaceContainerLowest = NightSheet,
    surfaceContainerLow = NightSheet,
    surfaceContainer = NightSheet,
    surfaceContainerHigh = NightRaised,
    surfaceContainerHighest = NightRaised,
    outline = Color(0xFF6E5550),
    outlineVariant = NightHairline,
    error = Color(0xFFFF6B60)
)

private val Grotesk = FontFamily.SansSerif
/** Labels: small, uppercase, monospaced — the "spec sheet" voice for figures. */
val LabelMono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, letterSpacing = 0.8.sp)
/** Big figures: tight, heavy, tabular so columns of numbers line up. */
val FigureStyle = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum")
/** The "AARUSH" wordmark: heavy, wide-tracked capitals like the logo lettering. */
val WordmarkStyle = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 3.sp)
/** "Makes You Comfort" — the logo's script tagline. */
val TaglineStyle = TextStyle(fontFamily = FontFamily.Cursive, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium, fontSize = 17.sp)

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
        labelLarge = t.labelLarge.copy(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
        labelMedium = t.labelMedium.copy(fontFamily = Grotesk, fontWeight = FontWeight.Medium),
        labelSmall = t.labelSmall.copy(fontFamily = Grotesk, fontWeight = FontWeight.Medium)
    )
}

/** Squared, architectural corners echoing the logo's "A". One scale for the whole app:
 *  inputs, menus, snackbars, chips and buttons 6dp; cards, FABs, dialogs and sheets 8dp.
 *  Tags (StatusPill) use [TagShape]; progress bars use [RailShape]. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp)
)
val TagShape = RoundedCornerShape(4.dp)
val RailShape = RoundedCornerShape(2.dp)

@Composable
fun AarushCPMTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Safe cast: in the app this is always the Activity; preview / screenshot renderers
            // host the view elsewhere, and there is no status bar to tint there.
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalSiteColors provides if (darkTheme) DarkSite else LightSite) {
        MaterialTheme(colorScheme = colorScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** Shortcut: `SiteTheme.colors.accent` etc. */
object SiteTheme {
    val colors: SiteColors
        @Composable get() = LocalSiteColors.current
}
