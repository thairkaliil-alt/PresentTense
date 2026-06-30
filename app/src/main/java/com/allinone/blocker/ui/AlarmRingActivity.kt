package com.allinone.blocker.ui

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.service.AlarmRingingService
import com.allinone.blocker.ui.theme.BlockerTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        blockBackButton()

        // Get the alarm label from the intent if provided (for future use)
        val alarmLabel = intent?.getStringExtra(EXTRA_ALARM_LABEL) ?: "Strict Alarm"

        setContent {
            BlockerTheme(darkTheme = true) {
                AlarmRingScreen(
                    alarmLabel = alarmLabel,
                    onDismissed = { dismiss() }
                )
            }
        }
    }

    private fun blockBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally blocked — user must solve the puzzle.
            }
        })
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

    private fun dismiss() {
        AlarmRingingService.activeInstance?.stopRinging()
        finish()
    }

    companion object {
        const val EXTRA_ALARM_LABEL = "alarm_label"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DATA
// ─────────────────────────────────────────────────────────────────────────────

private data class MathProblem(val display: String, val answer: Int)

private fun newProblem(): MathProblem {
    val a = Random.nextInt(10, 30)
    val b = Random.nextInt(10, 30)
    val c = Random.nextInt(2, 9)
    // Always: (a + b) × c  — genuinely requires mental arithmetic, no ambiguity
    return MathProblem("($a + $b) × $c", (a + b) * c)
}

// ─────────────────────────────────────────────────────────────────────────────
// COLORS — alarm-specific palette (dark, urgent, readable)
// ─────────────────────────────────────────────────────────────────────────────

private val AlarmBg        = Color(0xFF0A0A14)
private val AlarmCard      = Color(0xFF16162A)
private val AlarmAccent    = Color(0xFF7C6AF7)   // soft purple — calm but present
private val AlarmAccentAlt = Color(0xFF5E5CE6)
private val AlarmError     = Color(0xFFFF6B6B)
private val AlarmSuccess   = Color(0xFF4ADE80)
private val AlarmTextPri   = Color(0xFFF1F0FF)
private val AlarmTextSec   = Color(0xFFADADB8)
private val AlarmTextMuted = Color(0xFF6A6A7A)

// ─────────────────────────────────────────────────────────────────────────────
// ROOT SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlarmRingScreen(alarmLabel: String, onDismissed: () -> Unit) {

    // Live clock
    var timeString by remember {
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    LaunchedEffect(Unit) {
        while (true) {
            timeString = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            delay(10_000)
        }
    }

    // Puzzle state — all on this same screen, nothing hidden
    var problem  by remember { mutableStateOf(newProblem()) }
    var input    by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    var isError  by remember { mutableStateOf(false) }
    var isWon    by remember { mutableStateOf(false) }

    val keyboard = LocalSoftwareKeyboardController.current

    fun checkAnswer() {
        val guess = input.toIntOrNull()
        if (guess == problem.answer) {
            isWon = true
            keyboard?.hide()
        } else {
            isError = true
            input = ""
            attempts++
            problem = newProblem()
        }
    }

    // Auto-dismiss once puzzle is solved
    LaunchedEffect(isWon) {
        if (isWon) {
            delay(800) // let the user see the success state briefly
            onDismissed()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AlarmBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            // ── Pulsing alarm icon ────────────────────────────────────────────
            PulsingAlarmIcon(won = isWon)

            Spacer(Modifier.height(20.dp))

            // ── Live time ────────────────────────────────────────────────────
            Text(
                text = timeString,
                fontSize = 64.sp,
                fontWeight = FontWeight.Thin,
                color = AlarmTextPri,
                letterSpacing = (-1).sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            // ── Alarm label ──────────────────────────────────────────────────
            Text(
                text = alarmLabel,
                style = MaterialTheme.typography.titleMedium,
                color = AlarmAccent,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // ── Puzzle card — problem + answer input on the same screen ───────
            if (!isWon) {
                PuzzleCard(
                    problem       = problem,
                    input         = input,
                    isError       = isError,
                    attempts      = attempts,
                    onInputChange = { isError = false; input = it.filter(Char::isDigit) },
                    onSubmit      = ::checkAnswer
                )
            } else {
                WonCard()
            }

            Spacer(Modifier.weight(1f))

            // ── Dismiss / confirm button ─────────────────────────────────────
            Button(
                onClick       = ::checkAnswer,
                enabled       = input.isNotEmpty() && !isWon,
                modifier      = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape  = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = AlarmAccent,
                    contentColor           = Color.White,
                    disabledContainerColor = AlarmAccent.copy(alpha = 0.22f),
                    disabledContentColor   = Color.White.copy(alpha = 0.35f)
                )
            ) {
                Text(
                    "Dismiss alarm",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PULSING ICON
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PulsingAlarmIcon(won: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (won) AlarmSuccess.copy(alpha = 0.18f)
                      else     AlarmAccent.copy(alpha = 0.15f),
        animationSpec = tween(400),
        label = "iconBg"
    )

    Box(
        modifier = Modifier
            .scale(if (won) 1f else scale)
            .size(80.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text     = if (won) "✓" else "⏰",
            fontSize = 36.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PUZZLE CARD — problem visible + input field, both on the same card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PuzzleCard(
    problem:       MathProblem,
    input:         String,
    isError:       Boolean,
    attempts:      Int,
    onInputChange: (String) -> Unit,
    onSubmit:      () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AlarmCard)
            .padding(24.dp),
        verticalArrangement   = Arrangement.spacedBy(16.dp),
        horizontalAlignment   = Alignment.CenterHorizontally
    ) {
        // Header label
        Text(
            text  = if (attempts == 0) "Solve to dismiss" else "Try again — new problem",
            style = MaterialTheme.typography.labelLarge,
            color = if (isError) AlarmError else AlarmTextMuted
        )

        // ── The math problem — big, always visible ───────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(AlarmAccentAlt.copy(alpha = 0.18f), AlarmAccent.copy(alpha = 0.10f))
                    )
                )
                .padding(vertical = 22.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = problem.display + " = ?",
                fontSize   = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = AlarmTextPri,
                textAlign  = TextAlign.Center,
                letterSpacing = 1.sp
            )
        }

        // ── Answer input — directly below the problem, no separate screen ────
        OutlinedTextField(
            value          = input,
            onValueChange  = onInputChange,
            placeholder    = { Text("Your answer", color = AlarmTextMuted) },
            singleLine     = true,
            isError        = isError,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction    = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = AlarmAccent,
                unfocusedBorderColor = AlarmTextMuted.copy(alpha = 0.4f),
                errorBorderColor     = AlarmError,
                focusedTextColor     = AlarmTextPri,
                unfocusedTextColor   = AlarmTextPri,
                cursorColor          = AlarmAccent
            )
        )

        if (isError) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("✕  Wrong — ", color = AlarmError, style = MaterialTheme.typography.bodySmall)
                Text(
                    "new problem above",
                    color = AlarmTextSec,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WON STATE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WonCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AlarmSuccess.copy(alpha = 0.12f))
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("✓", fontSize = 42.sp)
            Text(
                "Correct!",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = AlarmSuccess
            )
            Text(
                "Alarm dismissed",
                style = MaterialTheme.typography.bodyMedium,
                color = AlarmTextSec
            )
        }
    }
}
