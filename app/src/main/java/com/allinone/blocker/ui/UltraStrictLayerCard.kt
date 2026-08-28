package com.allinone.blocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.PinHasher
import com.allinone.blocker.data.ULTRA_STRICT_PLEDGE
import com.allinone.blocker.data.UltraStrictConfig
import com.allinone.blocker.data.UltraStrictGate
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.CardSurfaceAlt
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// UltraStrictLayerCard.kt
//
// PLAIN-ENGLISH SUMMARY:
// The screen half of Ultra-Strict Layer (see UltraStrictMode.kt for the
// actual rules). Drop UltraStrictLayerSection() into any screen — it reads
// and writes its own state from BlockerRepository, the same way
// PinRecoveryDialog does, so the call site doesn't need to pass anything in.
//
// Turning ON: if no password exists yet, UltraStrictPasswordSetupDialog
// asks for one and turns Ultra-Strict on the moment it's saved. If a
// password already exists (e.g. it was turned off before), flipping the
// switch on is instant.
//
// Turning OFF: the switch does NOT flip off directly. Tapping it while on
// opens UltraStrictDisableDialog instead, which walks through, in order:
// the 5-minute wait, the password, then the typed pledge. Nothing here
// short-circuits that order — the password step only appears once the wait
// is over, and the pledge step only appears once the password is verified.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun UltraStrictLayerSection() {
    val config by BlockerRepository.ultraStrict.collectAsState()
    var showPasswordSetup by remember { mutableStateOf(false) }
    var showDisableFlow by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }

    Text(
        "Beyond Strict Mode",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Text(
        "A separate, optional fourth layer. Closes the one loophole none of the presets above catch: walking straight into Android's own Settings app and switching off Present Tense's Accessibility permission there.",
        style = MaterialTheme.typography.bodySmall,
        color = TextTertiary,
        lineHeight = 18.sp
    )

    UltraStrictToggleCard(
        config = config,
        onToggleOn = {
            if (config.passwordHash.isBlank()) {
                showPasswordSetup = true
            } else {
                BlockerRepository.setUltraStrict(config.copy(enabled = true))
            }
        },
        onOpenDisableFlow = {
            // Starts the wait right here, synchronously, before the dialog
            // ever composes — so UltraStrictDisableDialog's very first
            // frame already sees the correct remaining time instead of
            // briefly showing 0 while its own LaunchedEffect catches up.
            // Covers both entry points (the switch and the banner), since
            // both call this same lambda.
            UltraStrictGate.requestDisable()
            showDisableFlow = true
        }
    )

    if (showPasswordSetup) {
        UltraStrictPasswordSetupDialog(
            onDismiss = { showPasswordSetup = false },
            onSaved = { hash ->
                BlockerRepository.setUltraStrict(config.copy(passwordHash = hash, enabled = true))
                showPasswordSetup = false
            }
        )
    }

    if (showDisableFlow) {
        UltraStrictDisableDialog(
            onDismiss = { showDisableFlow = false },
            onForgotPassword = {
                showDisableFlow = false
                showForgotPassword = true
            }
        )
    }

    if (showForgotPassword) {
        UltraStrictForgotPasswordDialog(
            onDismiss = { showForgotPassword = false },
            onNewPasswordSaved = {
                showForgotPassword = false
                // Straight back into the disable flow — the wait is
                // already over by now (a 24h password wait always outlasts
                // the 5-minute disable wait), so this lands right back on
                // the password step, ready for the new password.
                showDisableFlow = true
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOGGLE CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UltraStrictToggleCard(
    config: UltraStrictConfig,
    onToggleOn: () -> Unit,
    onOpenDisableFlow: () -> Unit
) {
    // Ticks once a second while a turn-off is pending, so the "ready in
    // Xm" status below actually counts down instead of sitting frozen.
    var remainingMs by remember { mutableStateOf(UltraStrictGate.disableRemainingMs(config)) }
    LaunchedEffect(config.disableRequestedAt) {
        while (true) {
            remainingMs = UltraStrictGate.disableRemainingMs(config)
            if (remainingMs <= 0L) break
            delay(1000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (config.enabled) AccentRed.copy(alpha = 0.14f) else CardSurfaceAlt
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (config.enabled) AccentRed.copy(alpha = 0.35f) else TextTertiary.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (config.enabled) AccentRed.copy(alpha = 0.22f)
                            else TextTertiary.copy(alpha = 0.10f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (config.enabled) AccentRed else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "Ultra-Strict Layer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        if (!config.enabled) "Off — turning off Accessibility in Settings won't trigger anything"
                        else "On — turning off Accessibility in Settings instantly alarms and breaks your streak",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (config.enabled) AccentRed else TextTertiary,
                        lineHeight = 17.sp
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { wantsOn -> if (wantsOn) onToggleOn() else onOpenDisableFlow() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentRed
                    )
                )
            }

            // A turn-off request survives leaving this screen — this banner
            // is how you find your way back to it, and shows live progress
            // either way.
            if (config.disableRequestedAt > 0L) {
                HorizontalDivider(color = TextTertiary.copy(alpha = 0.10f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenDisableFlow)
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Turning off in progress",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed
                        )
                        Text(
                            if (remainingMs > 0L) "Ready in ${(remainingMs / 60000L) + 1}m — tap to continue"
                            else "Wait is over — tap to finish turning it off",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PASSWORD SETUP (first time turning Ultra-Strict on)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UltraStrictPasswordSetupDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurfaceAlt,
        title = { Text("Set your Ultra-Strict password", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This is separate from your Strict Mode PIN. You'll need it — plus a 5-minute wait and a typed pledge — to ever turn Ultra-Strict Layer back off.",
                    style = MaterialTheme.typography.bodySmall, color = TextTertiary
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.length < 4 -> error = "Use at least 4 characters"
                        password != confirm -> error = "Passwords don't match"
                        else -> onSaved(PinHasher.hash(password))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) { Text("Turn on Ultra-Strict Layer", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// THE DISABLE FLOW — 5-minute wait → password → pledge, strictly in order
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UltraStrictDisableDialog(
    onDismiss: () -> Unit,
    onForgotPassword: () -> Unit
) {
    val config by BlockerRepository.ultraStrict.collectAsState()

    // Defensive fallback only — the real trigger is onOpenDisableFlow in
    // UltraStrictLayerSection, which already calls this synchronously
    // before this dialog ever composes (so the very first frame already
    // has the correct remaining time). Idempotent, so calling it again
    // here is harmless — it just guards against this dialog somehow being
    // opened some other way in the future without going through that path.
    LaunchedEffect(Unit) { UltraStrictGate.requestDisable() }

    var remainingMs by remember { mutableStateOf(UltraStrictGate.disableRemainingMs(config)) }
    LaunchedEffect(config.disableRequestedAt) {
        while (true) {
            remainingMs = UltraStrictGate.disableRemainingMs(config)
            if (remainingMs <= 0L) break
            delay(1000)
        }
    }

    // Local to this dialog instance only — reset automatically every time
    // the dialog is reopened, since it then leaves composition entirely.
    var passwordVerified by remember { mutableStateOf(false) }

    when {
        remainingMs > 0L -> {
            val totalSeconds = (remainingMs / 1000L).toInt()
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = CardSurfaceAlt,
                title = { Text("Hang on.", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Ultra-Strict Layer can't be turned off instantly — that's the whole point. " +
                                "${m}m ${s}s left before you can continue.",
                            style = MaterialTheme.typography.bodySmall, color = TextTertiary
                        )
                        Text(
                            "It's still fully on right now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                        Text("OK, I'll wait", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { UltraStrictGate.cancelDisableRequest(); onDismiss() }) {
                        Text("Cancel — keep it on", color = TextMuted)
                    }
                }
            )
        }

        !passwordVerified -> {
            var password by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = CardSurfaceAlt,
                title = { Text("Enter your password", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "The wait is over. Enter your Ultra-Strict password to continue.",
                            style = MaterialTheme.typography.bodySmall, color = TextTertiary
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; error = null },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = onForgotPassword) {
                            Text(
                                "Forgot it? Reset your password instead",
                                color = AccentBlue,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (UltraStrictGate.verifyPassword(config, password)) {
                                passwordVerified = true
                            } else {
                                error = "That's not the right password"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("Continue", fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
            )
        }

        else -> {
            var pledge by remember { mutableStateOf("") }
            val matches = pledge.trim() == ULTRA_STRICT_PLEDGE
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = CardSurfaceAlt,
                title = { Text("Last step.", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Type this out exactly to finish turning Ultra-Strict Layer off:",
                            style = MaterialTheme.typography.bodySmall, color = TextTertiary
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentRed.copy(alpha = 0.10f))
                                .padding(12.dp)
                        ) {
                            Text(
                                "\u201C$ULTRA_STRICT_PLEDGE\u201D",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed
                            )
                        }
                        OutlinedTextField(
                            value = pledge,
                            onValueChange = { pledge = it },
                            label = { Text("Type it here") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (UltraStrictGate.finalizeDisable(config, pledge)) onDismiss()
                        },
                        enabled = matches,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentRed,
                            disabledContainerColor = AccentRed.copy(alpha = 0.3f)
                        )
                    ) { Text("Turn off Ultra-Strict Layer", fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FORGOT PASSWORD — mirrors PinRecoveryDialog, 24-hour wait instead of 1-hour
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UltraStrictForgotPasswordDialog(onDismiss: () -> Unit, onNewPasswordSaved: () -> Unit) {
    val config by BlockerRepository.ultraStrict.collectAsState()
    var remainingMs by remember { mutableStateOf(UltraStrictGate.passwordResetRemainingMs(config)) }

    LaunchedEffect(config.passwordResetRequestedAt) {
        while (true) {
            remainingMs = UltraStrictGate.passwordResetRemainingMs(config)
            if (remainingMs <= 0L) break
            delay(1000)
        }
    }

    when {
        config.passwordResetRequestedAt <= 0L -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = CardSurfaceAlt,
                title = { Text("Forgot your password?", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Text(
                        "This is deliberately slow — Ultra-Strict Layer is supposed to be the hardest one to talk your way out of. " +
                            "Tap below to start a 24-hour wait — after that, you'll be able to set a new password.",
                        style = MaterialTheme.typography.bodySmall, color = TextTertiary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { UltraStrictGate.requestPasswordReset() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("Start 24-hour wait", fontWeight = FontWeight.SemiBold) }
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
                        "You can set a new password in $label. Ultra-Strict Layer stays fully on until then — this is just here in case you genuinely don't remember it.",
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
                    TextButton(onClick = { UltraStrictGate.cancelPasswordReset() }) {
                        Text("Cancel reset request", color = TextMuted)
                    }
                }
            )
        }

        else -> {
            var pw by remember { mutableStateOf("") }
            var confirm by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = CardSurfaceAlt,
                title = { Text("Set a new password", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "The wait is over. Choose a new password you'll actually remember this time.",
                            style = MaterialTheme.typography.bodySmall, color = TextTertiary
                        )
                        OutlinedTextField(
                            value = pw,
                            onValueChange = { pw = it; error = null },
                            label = { Text("New password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it; error = null },
                            label = { Text("Confirm password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when {
                                pw.length < 4 -> error = "Use at least 4 characters"
                                pw != confirm -> error = "Passwords don't match"
                                else -> {
                                    UltraStrictGate.setNewPasswordAfterReset(pw)
                                    onNewPasswordSaved()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("Save new password", fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
            )
        }
    }
}
