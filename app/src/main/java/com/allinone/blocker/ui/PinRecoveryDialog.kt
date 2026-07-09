package com.allinone.blocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.PinHasher
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.CardSurfaceAlt
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * "Forgot PIN?" recovery flow. Deliberately NOT instant — see
 * [StrictModeGate.PIN_RESET_DELAY_MS]: an instant reset would make the PIN
 * meaningless as a friction (you'd just tap "forgot it" whenever it was in
 * your way), but no recovery path at all would mean a genuine memory slip
 * locks you out permanently. This is the middle ground: a mandatory 1-hour
 * wait, then a normal "set a new PIN" step.
 *
 * Three states, shown automatically based on [BlockerRepository.strictMode]:
 *  1. No reset requested yet → explain the wait, offer to start it.
 *  2. Reset requested, still waiting → live countdown + option to cancel.
 *  3. Wait is over → enter and confirm a new 6-digit PIN.
 */
@Composable
fun PinRecoveryDialog(onDismiss: () -> Unit, onNewPinSaved: () -> Unit) {
    val config by BlockerRepository.strictMode.collectAsState()
    var remainingMs by remember { mutableStateOf(StrictModeGate.pinResetRemainingMs(config)) }

    // Ticks once a second so the countdown actually counts down instead of
    // sitting frozen at whatever value it had when the dialog opened.
    LaunchedEffect(config.pinResetRequestedAt) {
        while (true) {
            remainingMs = StrictModeGate.pinResetRemainingMs(config)
            if (remainingMs <= 0L) break
            delay(1000)
        }
    }

    when {
        config.pinResetRequestedAt <= 0L -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = CardSurfaceAlt,
                title = { Text("Forgot your PIN?", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Text(
                        "For this to actually protect you from yourself, resetting it can't be instant. " +
                            "Tap below to start a 1-hour wait — after that, you'll be able to set a new PIN.",
                        style = MaterialTheme.typography.bodySmall, color = TextTertiary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { StrictModeGate.requestPinReset() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("Start 1-hour wait", fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
            )
        }

        remainingMs > 0L -> {
            val totalMinutes = (remainingMs / 60000L).toInt()
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            val label = when {
                h > 0 && m > 0 -> "${h}h ${m}m"
                h > 0          -> "${h}h"
                else           -> "${m + 1}m" // round up so it never reads "0m" with time still left
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = CardSurfaceAlt,
                title = { Text("Hang tight", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Text(
                        "You can set a new PIN in $label. Your current PIN still works normally until then — " +
                            "this is just here in case you genuinely don't remember it.",
                        style = MaterialTheme.typography.bodySmall, color = TextTertiary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("OK", fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { StrictModeGate.cancelPinReset() }) {
                        Text("Cancel reset request", color = TextMuted)
                    }
                }
            )
        }

        else -> {
            // The wait is over — let them set a new PIN.
            var pin by remember { mutableStateOf("") }
            var confirm by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = CardSurfaceAlt,
                title = { Text("Set a new PIN", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "The wait is over. Choose a new 6-digit PIN — make it one you'll actually remember this time.",
                            style = MaterialTheme.typography.bodySmall, color = TextTertiary
                        )
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { pin = it; error = null } },
                            label = { Text("New 6-digit PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { confirm = it; error = null } },
                            label = { Text("Confirm PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when {
                                pin.length != 6 -> error = "PIN must be 6 digits"
                                pin != confirm -> error = "PINs don't match"
                                else -> {
                                    BlockerRepository.setStrictMode(
                                        config.copy(pinHash = PinHasher.hash(pin), pinResetRequestedAt = 0L)
                                    )
                                    onNewPinSaved()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("Save new PIN", fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
            )
        }
    }
}
