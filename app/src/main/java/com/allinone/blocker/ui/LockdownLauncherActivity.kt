package com.allinone.blocker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.LockdownDecision
import com.allinone.blocker.data.LockdownEngine
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.BlockerTheme
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * The full-phone lockdown "home screen". While a lockdown is active this is the
 * only screen the user can reach: a black launcher showing the whitelisted apps
 * as a grid. Tapping one launches it normally; pressing back does nothing; and
 * the AccessibilityService bounces the user straight back here the moment they
 * try to open anything that isn't whitelisted.
 *
 * Together with [AppBlockerAccessibilityService] this turns the device into a
 * single inescapable screen — the Digital-Detox effect — without needing
 * Device Owner / ADB. The trade-off vs. true Device Owner kiosk mode is that
 * this relies on the accessibility service staying enabled.
 *
 * When no lockdown is active this activity is harmless: it immediately hands
 * off to [MainActivity], so it can safely be registered as a HOME launcher.
 */
class LockdownLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BlockerRepository.isInitialized) BlockerRepository.init(applicationContext)

        // Edge-to-edge, immersive: hide the status & nav bars so the lockdown
        // screen reads as one uninterrupted surface. Swiping reveals them only
        // transiently — they can't be used to escape because the accessibility
        // service bounces any non-whitelisted app straight back here.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Swallow back — there is no "leaving" the lockdown screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* intentionally nothing */ }
        })

        setContent {
            BlockerTheme(darkTheme = true) {
                LockdownLauncherScreen(
                    onLaunchApp = ::launchApp,
                    onExitToApp = ::exitToApp,
                    onRequestEnd = ::requestEndLockdown
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If lockdown ended while we were backgrounded, don't trap the user here.
        if (!isLockdownActive()) exitToApp()
    }

    private fun isLockdownActive(): Boolean = LockdownEngine.evaluate(
        manualLockUntil = BlockerRepository.manualLockUntil.value,
        schedules = BlockerRepository.schedules.value
    ).active

    private fun launchApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    private fun exitToApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
        finish()
    }

    /**
     * The sanctioned early-exit. Routes through the strict-mode gate: if strict
     * friction is configured this hands off to MainActivity's unlock challenge
     * (which suppresses the lockdown bounce while pending); otherwise it ends the
     * session immediately. Either way we open the app so the user sees the result.
     */
    private fun requestEndLockdown() {
        com.allinone.blocker.data.StrictModeGate.guard { BlockerRepository.endManualLock() }
        exitToApp()
    }

    companion object {
        /** Brings the lockdown launcher to the front (used when a session starts
         *  and by the accessibility service when corralling the user). */
        fun launch(context: Context) {
            val intent = Intent(context, LockdownLauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }
}

/** An app shown on the lockdown launcher grid. */
private data class LauncherApp(val packageName: String, val label: String)

@Composable
private fun LockdownLauncherScreen(
    onLaunchApp: (String) -> Unit,
    onExitToApp: () -> Unit,
    onRequestEnd: () -> Unit
) {
    val context = LocalContext.current
    val whitelist by BlockerRepository.whitelist.collectAsState()
    val manualUntil by BlockerRepository.manualLockUntil.collectAsState()
    val schedules by BlockerRepository.schedules.collectAsState()
    val breakUntil by BlockerRepository.breakUntil.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedTicker { now = it }

    val decision = remember(manualUntil, schedules, breakUntil, now) {
        LockdownEngine.evaluate(manualUntil, schedules, now, breakUntil)
    }

    // Build the visible app list: phone + messages (always exempt) followed by
    // the user's whitelist, de-duplicated and labelled.
    val apps = remember(whitelist) { buildLauncherApps(context, whitelist) }
    val breaksRemaining = BlockerRepository.breaksRemaining()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDarkest)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))

            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(14.dp))

            // Countdown / status
            val target = decision.endsAtMillis
            if (target in 1 until Long.MAX_VALUE) {
                val remainingSec = ((target - now) / 1000L).coerceAtLeast(0)
                Text(
                    formatLockCountdown(remainingSec),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text("remaining", style = MaterialTheme.typography.labelMedium, color = TextMuted)
            } else {
                Text(
                    decision.reason.ifBlank { "Locked down" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Stay focused. Only your allowed apps are available.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            if (apps.isEmpty()) {
                Text(
                    "No apps whitelisted.\nPhone and Messages still work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 40.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        LauncherAppIcon(app = app, onClick = { onLaunchApp(app.packageName) })
                    }
                }
            }

            // Emergency break — the only sanctioned escape valve, if any remain.
            if (!decision.onBreak && breaksRemaining > 0) {
                OutlinedButton(
                    onClick = {
                        if (BlockerRepository.startEmergencyBreak()) onExitToApp()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Emergency break ($breaksRemaining left)", color = AccentTeal)
                }
            } else {
                Spacer(Modifier.height(24.dp))
            }

            // Sanctioned early-exit (goes through the strict-mode gate).
            androidx.compose.material3.TextButton(
                onClick = onRequestEnd,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text("End lockdown", color = AccentRed, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun LauncherAppIcon(app: LauncherApp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconOrLetter(packageName = app.packageName, label = app.label)
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Drives a 1-second clock for the countdown without leaking a coroutine; the
 * caller just receives the latest millis.
 */
@Composable
private fun LaunchedTicker(onTick: (Long) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            onTick(System.currentTimeMillis())
            delay(1_000)
        }
    }
}

private fun buildLauncherApps(context: Context, whitelist: Set<String>): List<LauncherApp> {
    val pm = context.packageManager
    val ordered = LinkedHashSet<String>()

    // Always-available comms first.
    runCatching {
        (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
    }.getOrNull()?.let { ordered.add(it) }
    runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let { ordered.add(it) }

    ordered.addAll(whitelist)

    return ordered
        // Only keep things that can actually be launched.
        .filter { pm.getLaunchIntentForPackage(it) != null }
        .map { LauncherApp(it, InstalledApps.labelFor(context, it)) }
}

private fun formatLockCountdown(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
