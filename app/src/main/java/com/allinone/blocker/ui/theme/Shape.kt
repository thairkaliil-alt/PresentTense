package com.allinone.blocker.ui.theme

// ═══════════════════════════════════════════════════════════════════════════
// Shape.kt  —  AllinOneBlocker M3 Shape scale
//
// Uses the standard Material 3 corner radius defaults exactly.
// Do not invent custom values — always pick the nearest M3 size.
//
// USAGE GUIDE (what M3 components use each size by default)
//   extraSmall (4dp)   → Tooltip, SnackBar corner start
//   small      (8dp)   → Chip, TextField, small Button
//   medium     (12dp)  → Card, Dialog, FAB (standard)
//   large      (16dp)  → NavigationDrawer sheet, large cards
//   extraLarge (28dp)  → BottomSheet, extended FAB, big overlay panels
//
// In this app specifically:
//   extraSmall → pill badges, tiny accent bars
//   small      → avatar clips, icon containers, small interactive buttons
//   medium     → most cards, toggle cards, stat cards  ← most common
//   large      → session type cards, schedule cards, list item cards
//   extraLarge → big header cards (lockdown session card, radial dialog)
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val BlockerShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // M3 default — pill badge, tiny bar
    small      = RoundedCornerShape(8.dp),   // M3 default — chip, avatar clip, small button
    medium     = RoundedCornerShape(12.dp),  // M3 default — most cards
    large      = RoundedCornerShape(16.dp),  // M3 default — list cards, schedule rows
    extraLarge = RoundedCornerShape(28.dp),  // M3 default — big panels, bottom sheet style
)
