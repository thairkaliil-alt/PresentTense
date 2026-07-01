package com.allinone.blocker.ui.theme

// ═══════════════════════════════════════════════════════════════════════════
// Theme.kt  —  AllinOneBlocker Material 3 theme
//
// PALETTE REFERENCE (seed values — do not use directly in screens)
//   Light  primary       : #0F2A43    Dark  primary       : #5C9CC4
//   Light  primary-light : #3B6E91    Dark  primary-dim   : #3B6E91
//   Light  background    : #F7F6F3    Dark  background    : #15171A
//   Light  surface       : #E7E5E0    Dark  surface       : #222529
//   Light  error         : #D9663B    Dark  error         : #E4895F
//
// HOW TO USE COLORS IN SCREENS
//   ✅  MaterialTheme.colorScheme.primary          ← always do this
//   ✅  AccentBlue / AccentTeal / AccentRed / AccentAmber  ← semantic accents
//   ❌  Color(0xFF5C9CC4)                          ← never hard-code hex
//
// TOKENS SAVED FOR FUTURE REFERENCE
//   See colors.xml for the full XML-side token dictionary.
//   The Compose equivalents are the named val's below.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.allinone.blocker.ui.motion.LocalReducedHaptics
import com.allinone.blocker.ui.motion.LocalReducedMotion

// ─────────────────────────────────────────────────────────────────────────────
// LIGHT MODE COLOR TOKENS
// ─────────────────────────────────────────────────────────────────────────────

val LightPrimary             = Color(0xFF0F2A43)
val LightOnPrimary           = Color(0xFFFFFFFF)
val LightPrimaryContainer    = Color(0xFFC8E0F4)
val LightOnPrimaryContainer  = Color(0xFF001829)

val LightSecondary            = Color(0xFF4A6178)
val LightOnSecondary          = Color(0xFFFFFFFF)
val LightSecondaryContainer   = Color(0xFFCDE3F5)
val LightOnSecondaryContainer = Color(0xFF071E2E)

val LightTertiary             = Color(0xFF2D6E6A)
val LightOnTertiary           = Color(0xFFFFFFFF)
val LightTertiaryContainer    = Color(0xFFB0EDE8)
val LightOnTertiaryContainer  = Color(0xFF00201F)

val LightError                = Color(0xFFD9663B)
val LightOnError              = Color(0xFFFFFFFF)
val LightErrorContainer       = Color(0xFFFFDAD2)
val LightOnErrorContainer     = Color(0xFF3B0D00)

val LightBackground           = Color(0xFFF7F6F3)
val LightOnBackground         = Color(0xFF191C1E)
val LightSurface              = Color(0xFFE7E5E0)
val LightOnSurface            = Color(0xFF191C1E)
val LightSurfaceVariant       = Color(0xFFD8DCE0)
val LightOnSurfaceVariant     = Color(0xFF41484D)

val LightOutline              = Color(0xFF71787D)
val LightOutlineVariant       = Color(0xFFC1C7CC)
val LightInverseSurface       = Color(0xFF2E3133)
val LightInverseOnSurface     = Color(0xFFEFF1F3)
val LightInversePrimary       = Color(0xFF8BBFD8)
val LightScrim                = Color(0xFF000000)

// ─────────────────────────────────────────────────────────────────────────────
// DARK MODE COLOR TOKENS
// ─────────────────────────────────────────────────────────────────────────────

val DarkPrimary               = Color(0xFF5C9CC4)
val DarkOnPrimary             = Color(0xFF00304E)
val DarkPrimaryContainer      = Color(0xFF06466D)
val DarkOnPrimaryContainer    = Color(0xFFC8E0F4)

val DarkSecondary             = Color(0xFFA0BFCF)
val DarkOnSecondary           = Color(0xFF1B3445)
val DarkSecondaryContainer    = Color(0xFF324A5C)
val DarkOnSecondaryContainer  = Color(0xFFCDE3F5)

val DarkTertiary              = Color(0xFF94D1CC)
val DarkOnTertiary            = Color(0xFF003735)
val DarkTertiaryContainer     = Color(0xFF1B524F)
val DarkOnTertiaryContainer   = Color(0xFFB0EDE8)

val DarkError                 = Color(0xFFE4895F)
val DarkOnError               = Color(0xFF5C1900)
val DarkErrorContainer        = Color(0xFF7C3015)
val DarkOnErrorContainer      = Color(0xFFFFDAD2)

val DarkBackground            = Color(0xFF15171A)
val DarkOnBackground          = Color(0xFFE2E2E5)
val DarkSurface               = Color(0xFF222529)
val DarkOnSurface             = Color(0xFFE2E2E5)
val DarkSurfaceVariant        = Color(0xFF2C3036)
val DarkOnSurfaceVariant      = Color(0xFFC1C7CC)

val DarkOutline               = Color(0xFF8B9198)
val DarkOutlineVariant        = Color(0xFF41484D)
val DarkInverseSurface        = Color(0xFFE2E2E5)
val DarkInverseOnSurface      = Color(0xFF2E3133)
val DarkInversePrimary        = Color(0xFF0F2A43)
val DarkScrim                 = Color(0xFF000000)

// ─────────────────────────────────────────────────────────────────────────────
// SEMANTIC ACCENT COLORS
// These are the vivid, role-specific colors used throughout the app.
// Use these directly by name in composables; they work on both light and dark.
//
//  AccentBlue   → primary interactive, blocking-active, stat highlights
//  AccentTeal   → success, whitelist, active/good states
//  AccentRed    → danger, blocked, reels kill-switch, error accents
//  AccentAmber  → streaks, screen-time warnings
// ─────────────────────────────────────────────────────────────────────────────

val AccentBlue     = Color(0xFF5C9CC4)   // #5C9CC4 — calm, focused, trustworthy
val AccentBlueSoft = Color(0xFFB0D4EA)   // lighter text/label on blue backgrounds
val AccentBlueContainer = Color(0xFF06466D) // container/chip fill behind AccentBlue

val AccentTeal     = Color(0xFF2DB8A0)   // #2DB8A0 — active, good, whitelisted
val AccentTealSoft = Color(0xFFA8E8DC)   // lighter text/label on teal backgrounds
val AccentTealContainer = Color(0xFF1B524F)

val AccentRed      = Color(0xFFE4895F)   // #E4895F — danger, blocked, coral-warm
val AccentRedSoft  = Color(0xFFFFDAD2)   // lighter text/label on red backgrounds
val AccentRedContainer  = Color(0xFF7C3015)

val AccentAmber    = Color(0xFFF4A33C)   // #F4A33C — streaks, highlights, warnings

val AccentGreen    = Color(0xFF4CAF82)   // #4CAF82 — health, gym, morning energy
val AccentGreenSoft = Color(0xFFA8E6C8)  // lighter text/label on green backgrounds
val AccentGreenContainer = Color(0xFF1A5C3A) // container/chip fill behind AccentGreen

val AccentPurple   = Color(0xFF9B7FD4)   // #9B7FD4 — calm, deep work, focus
val AccentPurpleSoft = Color(0xFFD4C4F0) // lighter text/label on purple backgrounds
val AccentPurpleContainer = Color(0xFF3D2670) // container/chip fill behind AccentPurple

// A fixed (non-theme-reactive) neutral grey, for the rare spot — like a
// top-level data list — that needs a plain Color constant and can't call a
// @Composable property. Visually matches the old always-dark TextMuted tone.
val NeutralGreyFixed = Color(0xFF6B7590)

// ─────────────────────────────────────────────────────────────────────────────
// THEME-AWARE TEXT & SURFACE HELPERS
//
// These used to be fixed colors that were always dark — which is why toggling
// Dark Mode used to barely change anything on most screens. Now each one is a
// small @Composable property that looks at LocalIsDarkTheme (set by
// BlockerTheme below) and returns the right tone for whichever mode is
// currently active. Every screen keeps using them exactly the same way
// (e.g. `color = TextPrimary`) — nothing else needs to change.
//
// Prefer MaterialTheme.colorScheme.onSurface / onBackground for brand-new
// code where possible; these exist for the composables that were already
// built around a single light/dark text tone.
// ─────────────────────────────────────────────────────────────────────────────

internal val LocalIsDarkTheme = staticCompositionLocalOf { true }

// Dark-mode tones (unchanged from before)
private val TextPrimaryDark   = Color(0xFFFFFFFF)
private val TextSecondaryDark = Color(0xFFC4CBDA)
private val TextTertiaryDark  = Color(0xFF8A97B0)
private val TextMutedDark     = Color(0xFF6B7590)

// Light-mode tones — dark ink on a light background, same relative contrast
// steps as the dark-mode set above.
private val TextPrimaryLight   = LightOnBackground       // #191C1E — near-black ink
private val TextSecondaryLight = LightOnSurfaceVariant    // #41484D — medium grey-blue
private val TextTertiaryLight  = LightOutline             // #71787D — muted grey
private val TextMutedLight     = Color(0xFF9298A0)        // lightest step, still readable

val TextPrimary: Color
    @Composable get() = if (LocalIsDarkTheme.current) TextPrimaryDark else TextPrimaryLight

val TextSecondary: Color
    @Composable get() = if (LocalIsDarkTheme.current) TextSecondaryDark else TextSecondaryLight

val TextTertiary: Color
    @Composable get() = if (LocalIsDarkTheme.current) TextTertiaryDark else TextTertiaryLight

val TextMuted: Color
    @Composable get() = if (LocalIsDarkTheme.current) TextMutedDark else TextMutedLight

// Background / surface aliases — same idea. BgDarkest is the slightly deeper
// layer used for things like the bottom nav bar; it now has its own light
// counterpart instead of always being near-black.
private val BgScreenDark   = Color(0xFF13171F)    // was #15171A — subtle blue shift
private val BgDarkestDark  = Color(0xFF0E1219)    // was #0F1117 — matches the shift
private val CardSurfaceDark    = Color(0xFF1C2333) // was #222529 — blue-grey tint
private val CardSurfaceAltDark = Color(0xFF232A3A) // was #2C3036 — same family

private val BgScreenLight   = LightBackground      // #F7F6F3 — warm off-white
private val BgDarkestLight  = Color(0xFFEDEBE6)    // one shade deeper than the screen bg
private val CardSurfaceLight    = LightSurface         // #E7E5E0
private val CardSurfaceAltLight = LightSurfaceVariant  // #D8DCE0

val BgScreen: Color
    @Composable get() = if (LocalIsDarkTheme.current) BgScreenDark else BgScreenLight

val BgDarkest: Color
    @Composable get() = if (LocalIsDarkTheme.current) BgDarkestDark else BgDarkestLight

val CardSurface: Color
    @Composable get() = if (LocalIsDarkTheme.current) CardSurfaceDark else CardSurfaceLight

val CardSurfaceAlt: Color
    @Composable get() = if (LocalIsDarkTheme.current) CardSurfaceAltDark else CardSurfaceAltLight

// ─────────────────────────────────────────────────────────────────────────────
// COLOR SCHEMES
// ─────────────────────────────────────────────────────────────────────────────

private val BlockerLightColors = lightColorScheme(
    primary                = LightPrimary,
    onPrimary              = LightOnPrimary,
    primaryContainer       = LightPrimaryContainer,
    onPrimaryContainer     = LightOnPrimaryContainer,
    secondary              = LightSecondary,
    onSecondary            = LightOnSecondary,
    secondaryContainer     = LightSecondaryContainer,
    onSecondaryContainer   = LightOnSecondaryContainer,
    tertiary               = LightTertiary,
    onTertiary             = LightOnTertiary,
    tertiaryContainer      = LightTertiaryContainer,
    onTertiaryContainer    = LightOnTertiaryContainer,
    error                  = LightError,
    onError                = LightOnError,
    errorContainer         = LightErrorContainer,
    onErrorContainer       = LightOnErrorContainer,
    background             = LightBackground,
    onBackground           = LightOnBackground,
    surface                = LightSurface,
    onSurface              = LightOnSurface,
    surfaceVariant         = LightSurfaceVariant,
    onSurfaceVariant       = LightOnSurfaceVariant,
    outline                = LightOutline,
    outlineVariant         = LightOutlineVariant,
    inverseSurface         = LightInverseSurface,
    inverseOnSurface       = LightInverseOnSurface,
    inversePrimary         = LightInversePrimary,
    scrim                  = LightScrim,
)

private val BlockerDarkColors = darkColorScheme(
    primary                = DarkPrimary,
    onPrimary              = DarkOnPrimary,
    primaryContainer       = DarkPrimaryContainer,
    onPrimaryContainer     = DarkOnPrimaryContainer,
    secondary              = DarkSecondary,
    onSecondary            = DarkOnSecondary,
    secondaryContainer     = DarkSecondaryContainer,
    onSecondaryContainer   = DarkOnSecondaryContainer,
    tertiary               = DarkTertiary,
    onTertiary             = DarkOnTertiary,
    tertiaryContainer      = DarkTertiaryContainer,
    onTertiaryContainer    = DarkOnTertiaryContainer,
    error                  = DarkError,
    onError                = DarkOnError,
    errorContainer         = DarkErrorContainer,
    onErrorContainer       = DarkOnErrorContainer,
    background             = DarkBackground,
    onBackground           = DarkOnBackground,
    surface                = DarkSurface,
    onSurface              = DarkOnSurface,
    surfaceVariant         = DarkSurfaceVariant,
    onSurfaceVariant       = DarkOnSurfaceVariant,
    outline                = DarkOutline,
    outlineVariant         = DarkOutlineVariant,
    inverseSurface         = DarkInverseSurface,
    inverseOnSurface       = DarkInverseOnSurface,
    inversePrimary         = DarkInversePrimary,
    scrim                  = DarkScrim,
)

// ─────────────────────────────────────────────────────────────────────────────
// THEME ENTRY POINT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BlockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BlockerDarkColors else BlockerLightColors

    // Honour the OS "Remove animations" accessibility setting app-wide. When the
    // user has dialled the animator duration scale to 0, every motion component
    // collapses to an instant / cross-fade-only state. Read once per context.
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    // Same idea, but for the OS "Touch feedback" / "vibrate on tap" setting
    // (Settings > Sound & vibration on stock Android). When the user has
    // switched that off, every haptic tick in the app should stay silent —
    // see Haptics.kt for where this gets read.
    val reducedHaptics = remember(context) {
        android.provider.Settings.System.getInt(
            context.contentResolver,
            android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1
        ) == 0
    }

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalReducedMotion provides reducedMotion,
        LocalReducedHaptics provides reducedHaptics
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = BlockerTypography,
            shapes      = BlockerShapes,
            content     = content
        )
    }
}
