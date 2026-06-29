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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
