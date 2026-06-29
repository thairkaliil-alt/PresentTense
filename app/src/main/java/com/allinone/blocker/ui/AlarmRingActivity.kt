package com.allinone.blocker.ui

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.allinone.blocker.service.AlarmRingingService
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.BlockerTheme
import kotlin.random.Random

/**
 * The full-screen "your strict alarm is ringing" experience. This is a
 * separate Activity (not part of the normal MainActivity navigation)
 * because it needs to:
 *   - show up even when the phone is locked / asleep
 *   - turn the screen on by itself
 *   - have no way to back out of without solving the puzzle
 *
 * v1 dismiss method: a simple math puzzle, hardcoded. Swapping this for the
 * full Strict Mode challenge picker (PIN / cooldown / pledge / math) is a
 * follow-up — see UnlockChallengeScreen.kt for the existing pattern this
 * will eventually plug into.
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        blockBackButton()

        setContent {
            BlockerTheme(darkTheme = true) {
                AlarmRingScreen(onDismissed = { dismiss() })
            }
        }
    }

    // Block the back button — "strict" means no escaping without solving
    // the puzzle. Home button still works at the OS level (we can't block
    // that), but the ringing keeps going since the service is independent
    // of this Activity's lifecycle.
    private fun blockBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally does nothing.
            }
        })
    }

    /**
     * Makes this screen appear ON TOP of the lock screen and turns the
     * display on — exactly what the system Clock app's alarm screen does.
     * Without this, the alarm would ring but the user would have to unlock
     * their phone first to see WHY, which defeats the point of an alarm.
     */
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

    /** Stops the ringtone/vibration and closes this screen. */
    private fun dismiss() {
        AlarmRingingService.activeInstance?.stopRinging()
        finish()
    }
}

@Composable
private fun AlarmRingScreen(onDismissed: () -> Unit) {
    var a by remember { mutableStateOf(Random.nextInt(12, 40)) }
    var b by remember { mutableStateOf(Random.nextInt(12, 40)) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = BgScreen) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "\u23F0",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Wake up!",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Solve this to turn off the alarm",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(32.dp))
            Text(
                "$a + $b = ?",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit); error = false },
                label = { Text("Your answer") },
                singleLine = true,
                isError = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
            if (error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Not quite — try the new one",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(
                onClick = {
                    if (input.toIntOrNull() == a + b) {
                        onDismissed()
                    } else {
                        error = true
                        input = ""
                        a = Random.nextInt(12, 40)
                        b = Random.nextInt(12, 40)
                    }
                },
                enabled = input.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Turn off alarm", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
