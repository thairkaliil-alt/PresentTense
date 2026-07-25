package com.allinone.blocker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Permissions screen, organized into three clear tiers so it's obvious what
 * actually matters:
 *
 *   1. REQUIRED     — blocking is fundamentally broken without these.
 *   2. RECOMMENDED   — not needed for basic blocking, but each one closes a
 *                      specific way around a block (this is what Strict Mode
 *                      leans on).
 *   3. OPTIONAL      — quality-of-life only. Blocking works fine without it.
 *
 * Every permission here maps to a real, currently-used feature — nothing is
 * requested "just in case." (Location access for Location Lock is requested
 * contextually from the Strict Mode screen instead of here, since it's tied
 * to one specific optional feature rather than the app as a whole.)
 */
private enum class PermissionTier(val label: String) {
    REQUIRED("REQUIRED"),
    RECOMMENDED("RECOMMENDED"),
    OPTIONAL("OPTIONAL")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(refreshKey: Int, onBack: () -> Unit) {
    val context          = LocalContext.current
    val hasAccessibility = remember(refreshKey) { Permissions.hasAccessibility(context) }
    val hasOverlay       = remember(refreshKey) { Permissions.hasOverlay(context) }
    val hasUsage         = remember(refreshKey) { Permissions.hasUsageAccess(context) }
    val hasDeviceAdmin   = remember(refreshKey) { Permissions.hasDeviceAdmin(context) }
    val hasDefaultHome   = remember(refreshKey) { Permissions.isDefaultHomeApp(context) }
    val hasBatteryExempt = remember(refreshKey) { Permissions.hasBatteryOptimizationExemption(context) }
    val hasFullScreenIntent = remember(refreshKey) { Permissions.hasFullScreenIntentPermission(context) }

    // A local, throwaway counter used only to force a re-read of
    // Permissions.hasNotifications() right after the system dialog below
    // closes — [refreshKey] only changes when the whole screen is revisited
    // (e.g. coming back from Settings), which wouldn't otherwise happen for
    // an in-app dialog like this one.
    var notificationsRefresh by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val hasNotifications = remember(refreshKey, notificationsRefresh) {
        Permissions.hasNotifications(context)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsRefresh++ }

    // On Android 13+ there's a real system dialog to show (POST_NOTIFICATIONS).
    // Below that, Android has no runtime dialog for notifications at all — the
    // only way to change it is the app's own notification settings page.
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Permissions.openNotificationSettings(context)
        }
    }

    val requiredGrantedCount = listOf(hasAccessibility, hasOverlay, hasUsage).count { it }
    val allRequiredGranted   = requiredGrantedCount == 3

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Present Tense needs a few permissions to work. They're grouped below by " +
                    "how important each one is — start from the top.",
                style = MaterialTheme.typography.bodyMedium
            )

            RequiredStatusBanner(
                grantedCount = requiredGrantedCount,
                allGranted   = allRequiredGranted
            )

            // ── TIER 1: REQUIRED ────────────────────────────────────────────
            TierSectionHeader(
                tier        = PermissionTier.REQUIRED,
                title       = "Required",
                subtitle    = "Present Tense can't detect apps or show a block screen at " +
                    "all without these three. Everything runs locally — no data leaves " +
                    "your device."
            )
            PermissionRow(
                tier        = PermissionTier.REQUIRED,
                title       = "Accessibility Service",
                description = "Detects which app is open so blocks can be enforced.",
                granted     = hasAccessibility,
                onGrant     = { Permissions.openAccessibilitySettings(context) }
            )
            PermissionRow(
                tier        = PermissionTier.REQUIRED,
                title       = "Display over other apps",
                description = "Shows the block overlay on top of blocked apps.",
                granted     = hasOverlay,
                onGrant     = { Permissions.openOverlaySettings(context) }
            )
            PermissionRow(
                tier        = PermissionTier.REQUIRED,
                title       = "Usage Access",
                description = "Tracks per-app screen time for daily limits and stats, and " +
                    "acts as a backup way to detect the current app if the phone kills " +
                    "the main detection service in the background.",
                granted     = hasUsage,
                onGrant     = { Permissions.openUsageAccessSettings(context) }
            )

            // ── TIER 2: RECOMMENDED ─────────────────────────────────────────
            TierSectionHeader(
                tier        = PermissionTier.RECOMMENDED,
                title       = "Recommended",
                subtitle    = "Not required for basic blocking — but each one closes a " +
                    "specific way around a block. Turn these on for Strict Mode to " +
                    "actually be strict."
            )
            PermissionRow(
                tier        = PermissionTier.RECOMMENDED,
                title       = "Uninstall protection",
                description = "Prevents Present Tense from being uninstalled without first " +
                    "turning this off in Settings — which is itself blocked while a lockdown " +
                    "session is running.",
                granted     = hasDeviceAdmin,
                onGrant     = { Permissions.requestDeviceAdmin(context) }
            )
            PermissionRow(
                tier        = PermissionTier.RECOMMENDED,
                title       = "Default Home app",
                description = "Makes Present Tense open first when you press Home during " +
                    "lockdown, instead of your normal launcher flashing up before being " +
                    "corrected. This closes the \"press Home and it rolls away\" gap.",
                granted     = hasDefaultHome,
                onGrant     = { Permissions.openHomeAppSettings(context) }
            )
            PermissionRow(
                tier        = PermissionTier.RECOMMENDED,
                title       = "Full-screen alarm",
                description = "Lets a ringing Strict Alarm take over the screen on its own, " +
                    "the same way the system Clock app's alarm does — instead of sitting as " +
                    "a plain notification you have to go find and tap. Android 14+ only; " +
                    "older phones already have this by default.",
                granted     = hasFullScreenIntent,
                onGrant     = { Permissions.openFullScreenIntentSettings(context) }
            )
            PermissionRow(
                tier        = PermissionTier.RECOMMENDED,
                title       = "Background battery use",
                description = "Tells Android's battery manager not to restrict Present Tense " +
                    "in the background. Without this, the phone can occasionally kill the " +
                    "app's process, which delays lockdown protection kicking back in until " +
                    "the next backstop check.",
                granted     = hasBatteryExempt,
                onGrant     = { Permissions.requestBatteryOptimizationExemption(context) }
            )
            Text(
                "Some phone brands (Xiaomi, Huawei, OPPO, Vivo, OnePlus, Samsung) also have " +
                    "their own separate battery manager on top of Android's:",
                style = MaterialTheme.typography.bodySmall
            )
            AutostartRow(
                onOpen = { Permissions.openBackgroundAutostartSettings(context) }
            )

            // ── TIER 3: OPTIONAL ────────────────────────────────────────────
            TierSectionHeader(
                tier        = PermissionTier.OPTIONAL,
                title       = "Optional",
                subtitle    = "Quality-of-life only. Blocking still works fine without this."
            )
            PermissionRow(
                tier        = PermissionTier.OPTIONAL,
                title       = "Notifications",
                description = "Lets Present Tense show you block reminders, your daily " +
                    "summary, and the \"blocking active\" status notification. Blocking " +
                    "itself still works without this — you just won't see those.",
                granted     = hasNotifications,
                onGrant     = { requestNotifications() }
            )
        }
    }
}

/** Colors for a tier's badge — kept separate from the granted/not-granted
 *  colors (tertiary/error) used inside each row so the two signals never get
 *  visually confused with each other. */
@Composable
private fun tierBadgeColors(tier: PermissionTier): Pair<Color, Color> = when (tier) {
    PermissionTier.REQUIRED ->
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    PermissionTier.RECOMMENDED ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    PermissionTier.OPTIONAL ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

/** Small dot + count banner at the top: "3 of 3 required permissions granted"
 *  (or however many are still missing), so the most important status is
 *  visible without reading every card below. */
@Composable
private fun RequiredStatusBanner(grantedCount: Int, allGranted: Boolean) {
    val containerColor = if (allGranted) MaterialTheme.colorScheme.tertiaryContainer
                          else MaterialTheme.colorScheme.errorContainer
    val contentColor    = if (allGranted) MaterialTheme.colorScheme.onTertiaryContainer
                          else MaterialTheme.colorScheme.onErrorContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.large,
        colors   = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (allGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (allGranted) "All 3 required permissions granted"
                else "$grantedCount of 3 required permissions granted — basic blocking " +
                    "won't fully work until these are done",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

/** Section header for a tier: a small colored dot (matching that tier's
 *  badge color) plus a title and one-line explanation of what the tier
 *  means, so the grouping logic is obvious even to a non-technical reader. */
@Composable
private fun TierSectionHeader(tier: PermissionTier, title: String, subtitle: String) {
    val (badgeColor, _) = tierBadgeColors(tier)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(badgeColor, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
}

/** The small "REQUIRED" / "RECOMMENDED" / "OPTIONAL" pill shown on each card. */
@Composable
private fun TierBadge(tier: PermissionTier) {
    val (containerColor, contentColor) = tierBadgeColors(tier)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Text(
            tier.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun AutostartRow(onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.large   // 16dp — M3 token
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "  Autostart / \"allow background activity\"",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TierBadge(PermissionTier.RECOMMENDED)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Android doesn't let apps check whether this is on, so there's no " +
                    "\"granted\" status here — just tap through and allow Present Tense to " +
                    "run in the background if your phone shows a screen like this.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onOpen,
                shape   = MaterialTheme.shapes.large  // 16dp — M3 token
            ) {
                Text("Open settings", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PermissionRow(
    tier: PermissionTier,
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    // Use theme colors throughout — no hardcoded hex
    val iconTint = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.large   // 16dp — M3 token
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = iconTint
                    )
                    Text(
                        "  $title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TierBadge(tier)
            }
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            if (granted) {
                Text(
                    "Granted",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
            } else {
                // FilledTonalButton — softer than filled, correct for a secondary action
                FilledTonalButton(
                    onClick = onGrant,
                    shape   = MaterialTheme.shapes.large  // 16dp — M3 token
                ) {
                    Text("Open settings", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
