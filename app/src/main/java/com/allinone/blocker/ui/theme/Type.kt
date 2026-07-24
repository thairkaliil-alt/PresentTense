package com.allinone.blocker.ui.theme

// ═══════════════════════════════════════════════════════════════════════════
// Type.kt  —  AllinOneBlocker M3 Typography scale
//
// Follows the official Material 3 type scale exactly (Display, Headline,
// Title, Body, Label) — no invented sizes.  The scale uses the system
// default font (Roboto on most Android devices) so there's no font download
// required and text renders crisply at all densities.
//
// USAGE GUIDE
//   displaySmall   → big countdown numbers (lockdown timer, screen time total)
//   displayMedium  → hero numbers (only used sparingly, e.g. lockdown panel)
//   headlineMedium → section hero titles, rarely used
//   titleLarge     → top-bar titles, app name in HomeScreen
//   titleMedium    → card section headers, screen sub-headings
//   titleSmall     → card primary text, row item names
//   bodyLarge      → longer explanatory paragraphs
//   bodyMedium     → standard body copy, descriptions
//   bodySmall      → subtitles, secondary info, captions
//   labelLarge     → button text, status banner title
//   labelMedium    → chip labels, tab labels, badge text
//   labelSmall     → tiny metadata, time labels in charts, day abbreviations
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.allinone.blocker.R

val BlockerTypography = Typography(
    // ── Display — for large hero numbers (timer countdowns, totals) ──
    displayLarge  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),

    // ── Headline — section hero text ──
    headlineLarge  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),

    // ── Title — card headers, screen titles, item names ──
    titleLarge  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),

    // ── Body — paragraphs, descriptions, row content ──
    bodyLarge   = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall   = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),

    // ── Label — buttons, chips, tabs, captions ──
    labelLarge  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

// ─────────────────────────────────────────────────────────────────────────────
// NUMERAL FONT — a scoped, deliberate exception to "system font everywhere"
//
// BlockerTypography above is untouched — every Text() in the app still gets
// the system font (Roboto) at the stock M3 scale, exactly as documented up
// top. This one extra FontFamily exists ONLY for the Lockdown screen's
// duration readout and countdown ring — "the two numbers people will
// actually stare at" (design pass, "typography pass"). It's deliberately
// kept OUTSIDE the Typography() scale so it can never silently leak into
// body text or button labels somewhere else.
//
// Font: Space Grotesk (SIL Open Font License 1.1 — google/fonts repo,
// ofl/spacegrotesk). Chosen for two concrete reasons, not just look:
//   • Its numerals are genuinely distinct/geometric — not a system-font
//     lookalike, which is the whole point of a "numeral typeface swap."
//   • Its digits are TABULAR (fixed-width), so a countdown ticking over —
//     "9:59" → "10:00" — never jitters sideways as digit count changes,
//     something a proportional font (Roboto included) doesn't guarantee.
//
// It ships as ONE variable-font file (res/font/space_grotesk_variable.ttf)
// rather than several static weight files, locked to a single fixed weight
// via FontVariation so it always renders one considered, consistent look
// rather than whatever weight happens to be requested at the call site.
// Variable fonts need API 26+ (Android O) — this app's own minSdk is
// already 26, so every supported device qualifies and no fallback font is
// needed.
@OptIn(ExperimentalTextApi::class)
val NumeralFontFamily = FontFamily(
    Font(
        resId             = R.font.space_grotesk_variable,
        weight            = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    )
)
