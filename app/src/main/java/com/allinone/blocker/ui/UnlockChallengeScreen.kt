package com.allinone.blocker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.data.FRICTION_ORDER
import com.allinone.blocker.data.FrictionType
import com.allinone.blocker.data.PinHasher
import com.allinone.blocker.data.StrictModeConfig
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.CardSurfaceAlt
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

// A fixed pool of 6–9 letter words that are familiar but non-trivial to
// unscramble quickly under pressure. Deliberately avoids anything offensive.
private val SCRAMBLE_WORDS = listOf(
    "PRESENT", "MINDFUL", "PATIENCE", "FREEDOM", "BALANCE",
    "CLARITY", "FOCUSED", "SILENCE", "BREATHE", "CONTROL",
    "JOURNAL", "WALKING", "READING", "MORNING", "OFFLINE",
    "PERSIST", "RESOLVE", "CAREFUL", "REFLECT", "GROUNDED"
)

@Composable
fun UnlockChallengeScreen(
    config: StrictModeConfig,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val steps = remember(config.activeFrictions) {
        FRICTION_ORDER.filter { it in config.activeFrictions }
    }
    var stepIndex by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (stepIndex >= steps.size) {
            LaunchedEffect(Unit) { onSuccess() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Spacer(Modifier.height(56.dp))

                // ── Progress dots ────────────────────────────────────────────
                if (steps.size > 1) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.forEachIndexed { i, _ ->
                            val active = i == stepIndex
                            val done   = i < stepIndex
                            Box(
                                modifier = Modifier
                                    .size(if (active) 10.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            done   -> AccentBlue
                                            active -> AccentBlue
                                            else   -> TextMuted.copy(alpha = 0.3f)
                                        }
                                    )
                            )
                            if (i < steps.lastIndex) Spacer(Modifier.width(8.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Step ${stepIndex + 1} of ${steps.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(36.dp))
                } else {
                    Spacer(Modifier.height(36.dp))
                }

                // ── Animated step content ────────────────────────────────────
                AnimatedContent(
                    targetState = stepIndex,
                    transitionSpec = {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                    },
                    modifier = Modifier.weight(1f),
                    label = "step_transition"
                ) { idx ->
                    Box(Modifier.fillMaxSize()) {
                       when (steps.getOrNull(idx)) {
                            FrictionType.PIN           ->
                                PinStep(config.pinHash, onPassed = { stepIndex++ })
                            FrictionType.COOLDOWN      ->
                                CooldownStep(config.cooldownSeconds, onPassed = { stepIndex++ })
                            FrictionType.TYPING_PLEDGE ->
                                PledgeStep(config.pledgePhrase, onPassed = { stepIndex++ })
                            FrictionType.MATH_PUZZLE   ->
                                MathStep(onPassed = { stepIndex++ })
                            FrictionType.WORD_SCRAMBLE ->
                                WordScrambleStep(onPassed = { stepIndex++ })
                            FrictionType.LOCATION_LOCK -> {} // hard block — never reaches this screen
                            FrictionType.PLAN_LOCK     -> {} // automatic enforcer — never reaches this screen, see FRICTION_ORDER
                            null -> {}
                        }
                    }
                }

                // ── Cancel ───────────────────────────────────────────────────
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TextMuted
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Keep it blocked",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED LAYOUT HELPERS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepHeadline(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = TextPrimary,
        lineHeight = 34.sp
    )
}

@Composable
private fun StepSubtitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextTertiary,
        lineHeight = 22.sp
    )
}

@Composable
private fun PrimaryActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentBlue,
            contentColor   = Color.White,
            disabledContainerColor = AccentBlue.copy(alpha = 0.25f),
            disabledContentColor   = Color.White.copy(alpha = 0.4f)
        )
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PIN STEP — upgraded to 6 digits
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PinStep(expectedHash: String, onPassed: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StepHeadline("Enter your PIN")
        StepSubtitle("Type the 6-digit PIN you set in Strict Mode settings.")

        OutlinedTextField(
            value = input,
            onValueChange = {
                if (it.length <= 6 && it.all(Char::isDigit)) { input = it; error = false }
                if (it.length == 6 && PinHasher.matches(it, expectedHash)) onPassed()
            },
            label = { Text("6-digit PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (input.length == 6) {
                    if (PinHasher.matches(input, expectedHash)) onPassed()
                    else { error = true; input = "" }
                }
            }),
            isError = error,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                errorBorderColor = AccentRed
            )
        )
        if (error) {
            Text(
                "Wrong PIN — try again",
                style = MaterialTheme.typography.bodySmall,
                color = AccentRed
            )
        }

        PrimaryActionButton(
            label = "Confirm",
            enabled = input.length == 6,
            onClick = {
                if (PinHasher.matches(input, expectedHash)) onPassed()
                else { error = true; input = "" }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COOLDOWN STEP
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CooldownStep(seconds: Int, onPassed: () -> Unit) {
    var elapsedMs by remember { mutableStateOf(0L) }
    val totalMs = seconds * 1000L

    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            elapsedMs = System.currentTimeMillis() - start
            if (elapsedMs >= totalMs) break
            delay(100)
        }
    }

    val progress  = (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val remaining = ((totalMs - elapsedMs) / 1000L).coerceAtLeast(0)
    val done      = progress >= 1f

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StepHeadline(if (done) "Time's up." else "Wait it out.")
        StepSubtitle(
            if (done) "You've waited. If you still want to unlock, go ahead."
            else "This is the mandatory pause. You can't skip it."
        )

        // Big time display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurface)
                .padding(vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else "$remaining",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (done) AccentBlue else TextPrimary
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = AccentBlue,
            trackColor = AccentBlue.copy(alpha = 0.15f)
        )

        PrimaryActionButton(
            label = "Continue",
            enabled = done,
            onClick = onPassed
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TYPING PLEDGE STEP
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PledgeStep(phrase: String, onPassed: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val matches = input.trim() == phrase.trim()

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StepHeadline("Type this out.")
        StepSubtitle("Exactly. No shortcuts, no autocorrect mercy.")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardSurface)
                .padding(16.dp)
        ) {
            Text(
                "\u201C$phrase\u201D",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                lineHeight = 24.sp
            )
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Type it here") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (matches) AccentBlue else AccentBlue.copy(alpha = 0.5f)
            )
        )

        PrimaryActionButton(
            label = "Continue",
            enabled = matches,
            onClick = onPassed
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MATH STEP — harder: 2–3 step problems with numbers up to 99
// ─────────────────────────────────────────────────────────────────────────────

private data class MathProblem(
    val display: String,
    val answer: Int
)

private fun generateMathProblem(): MathProblem {
    // Always 2 operations to make it genuinely harder.
    // Form: (A op B) op C  — op is either + or ×
    // We keep answers positive and below 9999 to avoid nonsense numbers.
    val ops = listOf("+", "×")
    val a = Random.nextInt(10, 99)
    val b = Random.nextInt(2, 19)
    val c = Random.nextInt(2, 19)
    val op1 = ops.random()
    val op2 = ops.random()

    val mid = when (op1) {
        "×" -> a * b
        else -> a + b
    }
    val result = when (op2) {
        "×" -> mid * c
        else -> mid + c
    }

    // Keep result sane — if it blows up, fall back to a simpler problem
    return if (result > 9999 || result < 0) {
        val sa = Random.nextInt(10, 49)
        val sb = Random.nextInt(2, 12)
        val sc = Random.nextInt(2, 12)
        MathProblem("($sa + $sb) × $sc", (sa + sb) * sc)
    } else {
        MathProblem("($a $op1 $b) $op2 $c", result)
    }
}

@Composable
private fun MathStep(onPassed: () -> Unit) {
    var problem by remember { mutableStateOf(generateMathProblem()) }
    var input   by remember { mutableStateOf("") }
    var error   by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StepHeadline("Solve this.")
        StepSubtitle(
            if (attempts == 0) "Wrong answer resets the problem."
            else "Attempt ${attempts + 1}. New problem, same deal."
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurface)
                .padding(vertical = 28.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                problem.display,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it.filter(Char::isDigit); error = false },
            label = { Text("Your answer") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (input.isNotEmpty()) {
                    if (input.toIntOrNull() == problem.answer) {
                        onPassed()
                    } else {
                        error = true; input = ""; attempts++
                        problem = generateMathProblem()
                    }
                }
            }),
            isError = error,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                errorBorderColor = AccentRed
            )
        )
        if (error) {
            Text(
                "Not quite — new problem incoming",
                style = MaterialTheme.typography.bodySmall,
                color = AccentRed
            )
        }

        PrimaryActionButton(
            label = "Check",
            enabled = input.isNotEmpty(),
            onClick = {
                if (input.toIntOrNull() == problem.answer) {
                    onPassed()
                } else {
                    error = true; input = ""; attempts++
                    problem = generateMathProblem()
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WORD SCRAMBLE STEP — new friction type
// ─────────────────────────────────────────────────────────────────────────────

private fun scramble(word: String): String {
    val chars = word.toMutableList()
    // Keep shuffling until it's genuinely different from the original
    var result = chars.shuffled()
    var attempts = 0
    while (result.joinToString("") == word && attempts < 10) {
        result = chars.shuffled()
        attempts++
    }
    return result.joinToString("")
}

@Composable
private fun WordScrambleStep(onPassed: () -> Unit) {
    val target   = remember { SCRAMBLE_WORDS.random() }
    var scrambled by remember { mutableStateOf(scramble(target)) }
    var input    by remember { mutableStateOf("") }
    var error    by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StepHeadline("Unscramble this.")
        StepSubtitle("Rearrange the letters into a real word.")

        // Scrambled word display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurfaceAlt)
                .padding(vertical = 28.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    scrambled,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentBlue,
                    letterSpacing = 6.sp,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { scrambled = scramble(target) }) {
                    Text(
                        "Reshuffle",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it.uppercase().filter(Char::isLetter); error = false
            },
            label = { Text("Your answer") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (input == target) onPassed()
                else { error = true; input = ""; scrambled = scramble(target) }
            }),
            isError = error,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                errorBorderColor = AccentRed
            )
        )
        if (error) {
            Text(
                "Not quite — letters reshuffled",
                style = MaterialTheme.typography.bodySmall,
                color = AccentRed
            )
        }

        PrimaryActionButton(
            label = "Confirm",
            enabled = input.length == target.length,
            onClick = {
                if (input == target) onPassed()
                else { error = true; input = ""; scrambled = scramble(target) }
            }
        )
    }
}
