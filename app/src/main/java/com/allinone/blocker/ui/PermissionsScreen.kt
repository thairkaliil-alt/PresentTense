package com.allinone.blocker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
                "Grant these three permissions so the blocker can detect and cover apps. " +
                    "Everything runs locally — no data leaves your device.",
                style = MaterialTheme.typography.bodyMedium
            )

            PermissionRow(
                title       = "Accessibility Service",
                description = "Detects which app is open so blocks can be enforced.",
                granted     = hasAccessibility,
                onGrant     = { Permissions.openAccessibilitySettings(context) }
            )
            PermissionRow(
                title       = "Display over other apps",
                description = "Shows the block overlay on top of blocked apps.",
                granted     = hasOverlay,
                onGrant     = { Permissions.openOverlaySettings(context) }
            )
            PermissionRow(
                title       = "Usage Access",
                description = "Tracks per-app screen time for daily limits and stats.",
                granted     = hasUsage,
                onGrant     = { Permissions.openUsageAccessSettings(context) }
            )

            Spacer(Modifier.height(4.dp))
            Text(
                "Extra protection (optional, but recommended for Strict Mode)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Not required for basic blocking, but closes the easiest way around a block: " +
                    "just uninstalling the app.",
                style = MaterialTheme.typography.bodyMedium
            )
            PermissionRow(
                title       = "Notifications",
                description = "Lets Present Tense show you block reminders, your daily " +
                    "summary, and the \"blocking active\" status notification. Blocking " +
                    "itself still works without this — you just won't see those.",
                granted     = hasNotifications,
                onGrant     = { requestNotifications() }
            )
            PermissionRow(
                title       = "Uninstall protection",
                description = "Prevents Present Tense from being uninstalled without first " +
                    "turning this off in Settings — which is itself blocked while a lockdown " +
                    "session is running.",
                granted     = hasDeviceAdmin,
                onGrant     = { Permissions.requestDeviceAdmin(context) }
            )
            PermissionRow(
                title       = "Default Home app",
                description = "Makes Present Tense open first when you press Home during " +
                    "lockdown, instead of your normal launcher flashing up before being " +
                    "corrected. This closes the \"press Home and it rolls away\" gap.",
                granted     = hasDefaultHome,
                onGrant     = { Permissions.openHomeAppSettings(context) }
            )
            PermissionRow(
                title       = "Background battery use",
                description = "Tells Android's battery manager not to restrict Present Tense " +
                    "in the background. Without this, the phone can occasionally kill the " +
                    "app's process, which delays lockdown protection kicking back in until " +
                    "the next backstop check.",
                granted     = hasBatteryExempt,
                onGrant     = { Permissions.requestBatteryOptimizationExemption(context) }
            )

            Spacer(Modifier.height(4.dp))
            Text(
                "Manufacturer battery settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            AutostartRow(
                onOpen = { Permissions.openBackgroundAutostartSettings(context) }
            )
        }
    }
}

@Composable
private fun AutostartRow(onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.large   // 16dp — M3 token
    ) {
        Column(Modifier.padding(16.dp)) {
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
            Spacer(Modifier.height(6.dp))
            Text(
                "Some phone brands (Xiaomi, Huawei, OPPO, Vivo, OnePlus, Samsung) have their " +
                    "own separate battery manager on top of Android's, with its own toggle for " +
                    "this. Android doesn't let apps check whether it's on, so there's no " +
                    "\"granted\" status here — just tap through and allow Present Tense to run " +
                    "in the background if your phone shows a screen like this.",
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
