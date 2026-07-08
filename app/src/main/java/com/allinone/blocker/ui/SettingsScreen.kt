package com.allinone.blocker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary

// ─────────────────────────────────────────────────────────────────────────────
// SettingsScreen.kt
//
// PLAIN-ENGLISH SUMMARY:
// This is the actual Settings page the user sees. It's split into 3 sections:
//   1. Permissions    — one row showing how many permissions are granted
//                        (e.g. "3/4 granted"), tap to open the full
//                        Permissions screen (PermissionsScreen.kt)
//   2. Notifications  — 3 on/off switches, saved permanently via DataStore
//   3. About & Support — app version, Rate, Feedback, Privacy Policy, Terms
//
// All colors/text here use MaterialTheme + this app's existing theme tokens
// (TextPrimary, CardSurface, etc.), the same ones every other screen in this
// app uses — so when the user flips the dark/light switch on the Home
// screen, this screen automatically matches, with zero extra code needed
// here.
//
// [refreshKey] works exactly like it does on PermissionsScreen.kt: every
// time the user returns to this screen (e.g. after granting a permission in
// Android Settings and pressing back), MainActivity bumps this number, which
// makes the permission checks below re-run and show the up-to-date status.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    refreshKey: Int,
    onBack: () -> Unit,
    onPermissions: () -> Unit
) {
    val viewModel: SettingsViewModel = viewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgScreen,
                    scrolledContainerColor = BgScreen
                )
            )
        },
        containerColor = BgScreen
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── 1. PERMISSIONS SECTION ───────────────────────────────────────
            // A single row that opens the dedicated Permissions screen, instead
            // of duplicating the full permission list here. That screen is the
            // one place that lists every permission the app needs (now and any
            // added later) — which also makes it the one place to lock behind
            // Lockdown/Strict Mode later, instead of two.
            SectionHeader(title = "Permissions")
            PermissionsSummaryRow(refreshKey = refreshKey, onClick = onPermissions)

            Spacer(Modifier.height(8.dp))

            // ── 2. NOTIFICATIONS SECTION ─────────────────────────────────────
            SectionHeader(title = "Notifications")
            NotificationsSection(viewModel = viewModel)

            Spacer(Modifier.height(8.dp))

            // ── 3. ABOUT & SUPPORT SECTION ───────────────────────────────────
            SectionHeader(title = "About & Support")
            AboutSection(viewModel = viewModel)

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION HEADER — small reusable label used above each group of cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = AccentBlue,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. PERMISSIONS SECTION
// One row, showing how many of the permissions are granted right now, that
// opens PermissionsScreen.kt — the single dedicated screen with the full
// list (and any new permission added down the line). No permission-checking
// logic lives here anymore; it all lives in Permissions.kt, used by both this
// row and PermissionsScreen.kt.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionsSummaryRow(refreshKey: Int, onClick: () -> Unit) {
    val context = LocalContext.current

    // remember(refreshKey) re-checks each permission only when refreshKey
    // changes (i.e. when the user comes back to this screen), not on every
    // recomposition — same pattern PermissionsScreen.kt already uses.
    val checks = remember(refreshKey) {
        listOf(
            Permissions.hasAccessibility(context),
            Permissions.hasUsageAccess(context),
            Permissions.hasOverlay(context),
            Permissions.hasDeviceAdmin(context)
        )
    }
    val grantedCount = checks.count { it }
    val allGranted = grantedCount == checks.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        SettingsRow(
            icon = Icons.Filled.Security,
            iconTint = if (allGranted) AccentTeal else AccentRed,
            title = "Permissions",
            subtitle = "What the app can access on your device.",
            onClick = onClick,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$grantedCount/${checks.size} granted",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (allGranted) AccentTeal else AccentRed
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. NOTIFICATIONS SECTION
// Three on/off switches. Every flip is saved instantly and permanently via
// SettingsViewModel -> SettingsPreferences (DataStore).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationsSection(viewModel: SettingsViewModel) {
    val blockReminders by viewModel.blockRemindersEnabled.collectAsState()
    val dailySummary   by viewModel.dailySummaryEnabled.collectAsState()
    val vibration      by viewModel.vibrationEnabled.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            ToggleRow(
                icon = Icons.Filled.Shield,
                title = "Block Reminders",
                subtitle = "Get notified when a block stops you from opening an app.",
                checked = blockReminders,
                accentColor = AccentBlue,
                onCheckedChange = viewModel::setBlockRemindersEnabled
            )
            ItemDivider()
            ToggleRow(
                icon = Icons.Filled.QueryStats,
                title = "Daily Usage Summary",
                subtitle = "Get a daily notification summarizing your screen time.",
                checked = dailySummary,
                accentColor = AccentTeal,
                onCheckedChange = viewModel::setDailySummaryEnabled
            )
            ItemDivider()
            ToggleRow(
                icon = Icons.Filled.Vibration,
                title = "Vibration",
                subtitle = "Vibrate the phone when a notification arrives.",
                checked = vibration,
                accentColor = AccentBlue,
                onCheckedChange = viewModel::setVibrationEnabled
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsRow(
        icon = icon,
        iconTint = if (checked) accentColor else TextTertiary,
        title = title,
        subtitle = subtitle,
        // The whole row is also tappable, toggling the switch — a slightly
        // bigger, easier-to-hit target than the switch alone.
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor    = Color.White,
                    checkedTrackColor    = accentColor,
                    checkedBorderColor   = accentColor,
                    uncheckedThumbColor  = TextTertiary,
                    uncheckedTrackColor  = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = TextTertiary
                )
            )
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. ABOUT & SUPPORT SECTION
// All four links below (Rate, Feedback, Privacy, Terms) currently point at
// PLACEHOLDER addresses/URLs set in SettingsViewModel.kt — search that file
// for "PLACEHOLDER" to find exactly what to swap once you have the real
// Play Store link, support email, and policy pages.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AboutSection(viewModel: SettingsViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            // App version — informational only, not tappable (no chevron, no onClick).
            SettingsRow(
                icon = Icons.Filled.Info,
                iconTint = TextTertiary,
                title = "App Version",
                subtitle = "AllinOneBlocker v${viewModel.appVersionName}",
                onClick = null,
                trailing = null
            )
            ItemDivider()
            SettingsRow(
                icon = Icons.Filled.StarRate,
                iconTint = AccentBlue,
                title = "Rate the App",
                subtitle = "Enjoying the app? Leave a rating on the Play Store.",
                onClick = viewModel::openPlayStoreListing,
                trailing = { ChevronTrailing() }
            )
            ItemDivider()
            SettingsRow(
                icon = Icons.Filled.RateReview,
                iconTint = AccentTeal,
                title = "Send Feedback",
                subtitle = "Found a bug or have an idea? Email us directly.",
                onClick = viewModel::openFeedbackEmail,
                trailing = { ChevronTrailing() }
            )
            ItemDivider()
            SettingsRow(
                icon = Icons.Filled.PictureAsPdf,
                iconTint = TextTertiary,
                title = "Privacy Policy",
                subtitle = "How your data is handled.",
                onClick = viewModel::openPrivacyPolicy,
                trailing = { ChevronTrailing() }
            )
            ItemDivider()
            SettingsRow(
                icon = Icons.Filled.Email,
                iconTint = TextTertiary,
                title = "Terms of Service",
                subtitle = "The rules for using this app.",
                onClick = viewModel::openTermsOfService,
                trailing = { ChevronTrailing() }
            )
        }
    }
}

@Composable
private fun ChevronTrailing() {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = TextTertiary,
        modifier = Modifier.size(20.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED ROW BUILDING BLOCK
// Every item in every section (permissions, toggles, about links) is built
// from this one row layout, so spacing/icon size/text style stays identical
// everywhere. [onClick] being null makes the row non-interactive (used for
// the "App Version" info row, which isn't tappable). Passing a real onClick
// to Modifier.clickable() gives every tappable row Android's standard
// ripple/highlight animation automatically — no extra animation code needed.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
        .padding(horizontal = 16.dp, vertical = 14.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun ItemDivider() {
    HorizontalDivider(
        color = TextTertiary.copy(alpha = 0.12f),
        modifier = Modifier.padding(start = 52.dp)
    )
}
