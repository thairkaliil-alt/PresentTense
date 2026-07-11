package com.allinone.blocker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary

// ─────────────────────────────────────────────────────────────────────────────
// OnboardingScreen.kt
//
// PLAIN-ENGLISH SUMMARY:
// The very first thing a brand-new user sees, shown once (see
// OnboardingPreference.kt). Instead of dumping a wall of Android permission
// dialogs on someone with zero context — or worse, silently doing nothing
// until they happen to find the Permissions screen buried in Settings —
// this walks through each permission ONE AT A TIME:
//   1. A plain-English card explains what it's for and why Present Tense
//      needs it.
//   2. One button jumps straight to the exact right toggle on the phone —
//      no hunting through Settings menus themselves.
//   3. The screen watches for the user coming back (onResume) and instantly
//      shows a green checkmark once it's granted.
//
// The three permissions blocking can't work without (Accessibility, Display
// over other apps, Usage Access) are marked "Required" and have to actually
// be granted before continuing. Everything else is marked "Recommended" and
// can be skipped with one tap — nothing here is a one-time-only offer, since
// every one of these can also be revisited later from Settings → Permissions
// (PermissionsScreen.kt), which shares the exact same Permissions.kt helpers
// this screen uses.
//
// Location permission is deliberately NOT part of this walkthrough. That's
// on purpose — Location Lock only asks for it contextually, at the moment
// the user actually adds a location zone in Strict Mode, so nobody has to
// hand over their location just to finish onboarding.
// ─────────────────────────────────────────────────────────────────────────────

private enum class StepBadge { REQUIRED, RECOMMENDED }

private data class PermissionStep(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val badge: StepBadge,
    val granted: Boolean,
    val onGrant: () -> Unit
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current

    // Re-check every permission each time the user comes back to this screen
    // from a system Settings page — same "refresh on resume" pattern already
    // used by PermissionsScreen.kt and StrictModeSettingsScreen.kt.
    var refreshKey by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Notifications needs an actual runtime system dialog on Android 13+,
    // not just a settings deep link — same pattern as PermissionsScreen.kt.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Permissions.openNotificationSettings(context)
        }
    }

    val steps = remember(refreshKey) {
        listOf(
            PermissionStep(
                icon = Icons.Filled.Accessibility,
                title = "Accessibility Service",
                body = "This is the permission that actually makes blocking work — it's how " +
                    "Present Tense sees which app you just opened, so it can step in the " +
                    "instant it's one you've blocked. Android calls this \"Accessibility\" " +
                    "because it was originally built for screen readers, but every blocker " +
                    "app uses the same door to watch for app switches.",
                badge   = StepBadge.REQUIRED,
                granted = Permissions.hasAccessibility(context),
                onGrant = { Permissions.openAccessibilitySettings(context) }
            ),
            PermissionStep(
                icon = Icons.Filled.Layers,
                title = "Display over other apps",
                body = "Lets Present Tense draw the actual \"blocked\" screen on top of an " +
                    "app you're trying to open, instead of just closing it and leaving you " +
                    "guessing why.",
                badge   = StepBadge.REQUIRED,
                granted = Permissions.hasOverlay(context),
                onGrant = { Permissions.openOverlaySettings(context) }
            ),
            PermissionStep(
                icon = Icons.Filled.BarChart,
                title = "Usage Access",
                body = "Lets Present Tense read how long you've spent in each app, so daily " +
                    "limits, stats, and streaks have real numbers to work with.",
                badge   = StepBadge.REQUIRED,
                granted = Permissions.hasUsageAccess(context),
                onGrant = { Permissions.openUsageAccessSettings(context) }
            ),
            PermissionStep(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                body = "Lets Present Tense show you block reminders, your daily summary, and " +
                    "the \"blocking active\" status notification. Blocking itself still works " +
                    "without this — you just won't see those.",
                badge   = StepBadge.RECOMMENDED,
                granted = Permissions.hasNotifications(context),
                onGrant = { requestNotifications() }
            ),
            PermissionStep(
                icon = Icons.Filled.AdminPanelSettings,
                title = "Uninstall protection",
                body = "Closes the easiest way around a block: just uninstalling the app. " +
                    "With this on, Present Tense has to be deactivated as a device admin " +
                    "first — and that's itself blocked while a lockdown session is running.",
                badge   = StepBadge.RECOMMENDED,
                granted = Permissions.hasDeviceAdmin(context),
                onGrant = { Permissions.requestDeviceAdmin(context) }
            ),
            PermissionStep(
                icon = Icons.Filled.Home,
                title = "Default Home app",
                body = "Makes Present Tense open first when you press Home during lockdown, " +
                    "instead of your normal launcher flashing up before being corrected. " +
                    "Closes the \"press Home and it rolls away\" gap.",
                badge   = StepBadge.RECOMMENDED,
                granted = Permissions.isDefaultHomeApp(context),
                onGrant = { Permissions.openHomeAppSettings(context) }
            ),
            PermissionStep(
                icon = Icons.Filled.BatteryChargingFull,
                title = "Background battery use",
                body = "Tells Android's battery manager not to restrict Present Tense in the " +
                    "background, so it's less likely to get killed and briefly stop " +
                    "enforcing a block.",
                badge   = StepBadge.RECOMMENDED,
                granted = Permissions.hasBatteryOptimizationExemption(context),
                onGrant = { Permissions.requestBatteryOptimizationExemption(context) }
            ),
            PermissionStep(
                icon = Icons.Filled.Bolt,
                title = "Manufacturer battery settings",
                body = "Some phone brands (Xiaomi, Huawei, OPPO, Vivo, OnePlus, Samsung) run " +
                    "their own separate battery manager on top of Android's, with its own " +
                    "\"autostart\" toggle. Android won't tell us whether it's on, so there's " +
                    "no checkmark for this one — just tap through and allow Present Tense to " +
                    "run in the background if your phone shows a screen like this.",
                badge   = StepBadge.RECOMMENDED,
                granted = false, // no public API to detect this — see Permissions.kt
                onGrant = { Permissions.openBackgroundAutostartSettings(context) }
            )
        )
    }

    // Page 0 = welcome card, pages 1..steps.size = one card per permission,
    // last page = done card.
    val totalPages = steps.size + 2
    var page by remember { mutableIntStateOf(0) }

    fun finish() {
        OnboardingPreference.setComplete(context)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgScreen)
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar: progress dots + skip ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(totalPages) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i <= page) AccentBlue else CardSurface)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextTertiary,
                    modifier = Modifier.clickable { finish() }
                )
            }

            // ── Middle: the current card ─────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    page == 0 -> WelcomeCard()
                    page == totalPages - 1 -> DoneCard(steps = steps)
                    else -> PermissionStepCard(step = steps[page - 1])
                }
            }

            // ── Bottom bar: back / continue ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page > 0) {
                    TextButton(onClick = { page-- }) {
                        Text("Back", color = TextSecondary)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                when {
                    page == 0 -> {
                        Button(
                            onClick = { page++ },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                "Let's go",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    page == totalPages - 1 -> {
                        Button(
                            onClick = { finish() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                "Finish",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> {
                        val step = steps[page - 1]
                        // A required step can't be skipped — Continue stays
                        // disabled until Present Tense actually detects the
                        // grant. A recommended step can always be skipped.
                        val canContinue = step.granted || step.badge == StepBadge.RECOMMENDED
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!step.granted) {
                                FilledTonalButton(
                                    onClick = step.onGrant,
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Text("Open settings", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Button(
                                onClick = { page++ },
                                enabled = canContinue,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text(
                                    when {
                                        step.granted -> "Continue"
                                        step.badge == StepBadge.RECOMMENDED -> "Skip for now"
                                        else -> "Continue"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard() {
    Icon(
        Icons.Filled.Shield,
        contentDescription = null,
        tint = AccentBlue,
        modifier = Modifier.size(72.dp)
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "Let's get Present Tense set up",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Blocking apps and websites relies on a handful of phone permissions. We'll go " +
            "through them one at a time, explain what each one actually does, and take you " +
            "straight to the right toggle — no hunting through Settings menus yourself.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Everything runs locally on your phone — nothing you grant here sends data anywhere.",
        style = MaterialTheme.typography.bodySmall,
        color = TextTertiary,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun DoneCard(steps: List<PermissionStep>) {
    val grantedCount = steps.count { it.granted }
    Icon(
        Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = AccentTeal,
        modifier = Modifier.size(72.dp)
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "You're set up",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "$grantedCount of ${steps.size} permissions granted. Anything you skipped can be " +
            "turned on any time from Settings → Permissions — nothing here was a one-time offer.",
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PermissionStepCard(step: PermissionStep) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(
                if (step.granted) AccentTeal.copy(alpha = 0.15f) else AccentBlue.copy(alpha = 0.15f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (step.granted) Icons.Filled.CheckCircle else step.icon,
            contentDescription = null,
            tint = if (step.granted) AccentTeal else AccentBlue,
            modifier = Modifier.size(44.dp)
        )
    }
    Spacer(Modifier.height(20.dp))

    val (badgeText, badgeColor) = when {
        step.granted -> "Granted" to AccentTeal
        step.badge == StepBadge.REQUIRED -> "Required" to AccentAmber
        else -> "Recommended" to TextTertiary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            badgeText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = badgeColor
        )
    }
    Spacer(Modifier.height(16.dp))

    Text(
        step.title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Text(
        step.body,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))
}
