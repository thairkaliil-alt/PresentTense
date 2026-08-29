package com.allinone.blocker.ui

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.service.AccessibilityOffAlarmService
import com.allinone.blocker.ui.theme.BlockerTheme

/**
 * Third protection layer — THE INSTANT OFF-ALARM.
 *
 * Shown the instant AccessibilityWatchdog notices Present Tense's
 * Accessibility permission was switched off in Android Settings (see
 * AccessibilityWatchdog.checkForSilentDisable). Deliberately a NOTICE, not
 * a challenge: Android doesn't let any third-party app actually prevent
 * that switch from being flipped, so this doesn't try to. What it does
 * instead is make the moment impossible to quietly miss — full screen,
 * shows over the lock screen, sound + vibration (AccessibilityOffAlarmService)
 * — with exactly ONE way off this screen: turn Accessibility back on.
 *
 * This screen only ever shows while the Ultra-Strict Layer is turned on
 * (see AccessibilityWatchdog.checkForSilentDisable — the whole alarm is
 * gated behind that toggle). So on purpose, unlike a plain notice, there is
 * no "not now" / dismiss button here, and Back does nothing — same
 * reasoning AlarmRingActivity already uses for Strict Alarm's ring screen:
 * something meant to hold the line in a moment of temptation shouldn't
 * ship with a one-tap way past itself. That's not a missing exit, either —
 * Ultra-Strict Layer already has its own real one (the wait + password +
 * written-pledge disable flow elsewhere in the app), so this screen
 * doesn't need a second, looser one bolted on too.
 *
 * Tapping "Turn it back on" sends the user to Android's Accessibility
 * settings but deliberately does NOT close this screen by itself — only
 * Accessibility actually being back on does that. Closing happens two
 * ways, belt-and-braces:
 *  1. [AccessibilityWatchdog.recordEnabled] pushes the close the moment
 *     Android confirms the service reconnected — works even while this
 *     screen is backgrounded (the user is off in Settings right now).
 *  2. [onResume] double-checks the same thing directly, in case this
 *     screen happens to become visible again just ahead of that push.
 */
class AccessibilityOffAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeInstance = this
        showOverLockScreen()
        blockBackButton()

        setContent {
            BlockerTheme(darkTheme = true) {
                AccessibilityOffAlarmScreen(
                    onTurnBackOn = {
                        AccessibilityOffAlarmService.activeInstance?.stopRinging()
                        Permissions.openAccessibilitySettings(this)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // See class doc, closing path 2/2. Cheap single settings read, and
        // only ever actually does anything on the specific "came back here
        // and it's genuinely on now" case.
        if (Permissions.hasAccessibility(this)) {
            finishBecauseAccessibilityIsOn()
        }
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    private fun finishBecauseAccessibilityIsOn() {
        AccessibilityOffAlarmService.activeInstance?.stopRinging()
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun blockBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally blocked — same reasoning as AlarmRingActivity:
                // the only way off this screen is actually turning
                // Accessibility back on, not an accidental Back press.
            }
        })
    }

    companion object {
        // In-memory only, on purpose — same pattern as
        // AccessibilityOffAlarmService.activeInstance right below this
        // screen. Lets AccessibilityWatchdog.recordEnabled close this
        // exact screen the instant Accessibility is confirmed back on,
        // even while it's sitting backgrounded behind Android's own
        // Settings app.
        @Volatile private var activeInstance: AccessibilityOffAlarmActivity? = null

        /**
         * Closes this screen if it's currently showing (or backgrounded
         * but still alive) — no-op otherwise. Safe to call from any
         * thread, any time; hops to the main thread itself since some
         * callers (AccessibilityWatchdogService's periodic self-check)
         * run on a background thread.
         */
        fun closeIfShowing() {
            val instance = activeInstance ?: return
            Handler(Looper.getMainLooper()).post {
                instance.finishBecauseAccessibilityIsOn()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COLORS — a warning red/amber family, deliberately distinct from Strict
// Alarm's purple, so the two intrusive full-screen states never feel
// interchangeable even at a glance.
// ─────────────────────────────────────────────────────────────────────────────

private val WarnBg        = Color(0xFF1A0F0F)
private val WarnAccent    = Color(0xFFE4895F)
private val WarnTextPri   = Color(0xFFF5EFEC)
private val WarnTextSec   = Color(0xFFC9B8B0)

@Composable
private fun AccessibilityOffAlarmScreen(
    onTurnBackOn: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = WarnBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            PulsingWarningIcon()

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Protection just turned off",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = WarnTextPri,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "You disabled Present Tense's Accessibility permission in Settings, so blocking has stopped. Today's streak was marked broken.",
                style = MaterialTheme.typography.bodyMedium,
                color = WarnTextSec,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "This screen stays up until it's back on — that's the only way past it.",
                style = MaterialTheme.typography.bodySmall,
                color = WarnTextSec,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onTurnBackOn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarnAccent,
                    contentColor = Color.White
                )
            ) {
                Text(
                    "Turn it back on",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PulsingWarningIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "warnPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warnScale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(84.dp)
            .clip(CircleShape)
            .background(WarnAccent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "\u26A0\uFE0F", fontSize = 38.sp)
    }
}
