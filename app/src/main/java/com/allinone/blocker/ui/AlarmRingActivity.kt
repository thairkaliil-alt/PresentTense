package com.allinone.blocker.ui

import android.app.KeyguardManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.data.AlarmChallengeDifficulty
import com.allinone.blocker.data.AlarmChallengeType
import com.allinone.blocker.data.AlarmScheduler
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.StrictAlarmEntry
import com.allinone.blocker.data.effectiveChallengeRounds
import com.allinone.blocker.data.effectiveTypingPhrase
import com.allinone.blocker.service.AlarmRingingService
import com.allinone.blocker.ui.motion.MotionDurations
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.motion.SuccessCheckmark
import com.allinone.blocker.ui.motion.rememberChallengeFeedback
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.motion.shakeAndFlash
import com.allinone.blocker.ui.theme.BlockerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The full-screen alarm ring UI.
 *
 * DEFAULT BEHAVIOUR (this is the important part): if the alarm that fired
 * has Strict Mode OFF — which is every alarm unless someone deliberately
 * turned it on in the editor — this shows a completely plain ring screen:
 * big time, a Snooze button (if that alarm allows it) and a Dismiss button.
 * One tap and it's off, exactly like stock Android/Samsung/Apple Clock.
 *
 * Only when the alarm has Strict Mode ON does this show a challenge
 * (Math / Typing / Shake, per that alarm's own saved settings) that has to
 * be solved [StrictAlarmEntry.effectiveChallengeRounds] times before the
 * alarm will actually stop.
 */
class AlarmRingActivity : ComponentActivity() {

    private var alarmEntry: StrictAlarmEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        blockBackButton()

        val alarmId = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)
        alarmEntry = BlockerRepository.strictAlarms.value.firstOrNull { it.id == alarmId }

        setContent {
            BlockerTheme(darkTheme = true) {
                AlarmRingScreen(
                    alarm = alarmEntry,
                    onDismissed = { dismiss() },
                    onSnoozed = { snooze() }
                )
            }
        }
    }

    private fun blockBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally blocked — same reason a stock alarm clock
                // blocks it too: an alarm should never be dismissable by
                // accident. Use one of the on-screen buttons instead.
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

    /** Schedules one extra ring [StrictAlarmEntry.snoozeMinutes] from now,
     *  then closes this screen — the regular schedule for this alarm is
     *  completely untouched (see [AlarmScheduler.scheduleSnooze]). */
    private fun snooze() {
        alarmEntry?.let { AlarmScheduler.scheduleSnooze(applicationContext, it, it.snoozeMinutes) }
        AlarmRingingService.activeInstance?.stopRinging()
        finish()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CHALLENGE GENERATION — difficulty-aware. Named AlarmMathProblem to avoid
// clashing with MathProblem in UnlockChallengeScreen.kt (same package).
// ─────────────────────────────────────────────────────────────────────────────

private data class AlarmMathProblem(val display: String, val answer: Int)

private fun newAlarmProblem(difficulty: AlarmChallengeDifficulty): AlarmMathProblem =
    when (difficulty) {
        AlarmChallengeDifficulty.EASY -> {
            val a = Random.nextInt(3, 20)
            val b = Random.nextInt(3, 20)
            AlarmMathProblem("$a + $b", a + b)
        }
        AlarmChallengeDifficulty.MEDIUM -> {
            // Unchanged from this app's original single hard-coded puzzle —
            // MEDIUM is a deliberate "nothing feels different" baseline.
            val a = Random.nextInt(10, 30)
            val b = Random.nextInt(10, 30)
            val c = Random.nextInt(2, 9)
            AlarmMathProblem("($a + $b) × $c", (a + b) * c)
        }
        AlarmChallengeDifficulty.HARD -> {
            val a = Random.nextInt(6, 13)
            val b = Random.nextInt(6, 13)
            val c = Random.nextInt(10, 50)
            AlarmMathProblem("($a × $b) + $c", (a * b) + c)
        }
    }

private fun shakeTarget(difficulty: AlarmChallengeDifficulty): Int = when (difficulty) {
    AlarmChallengeDifficulty.EASY -> 10
    AlarmChallengeDifficulty.MEDIUM -> 20
    AlarmChallengeDifficulty.HARD -> 35
}

private const val SHAKE_THRESHOLD = 13f
private const val SHAKE_COOLDOWN_MS = 350L

// ─────────────────────────────────────────────────────────────────────────────
// COLORS
// ─────────────────────────────────────────────────────────────────────────────

private val AlarmBg        = Color(0xFF0A0A14)
private val AlarmCard      = Color(0xFF16162A)
private val AlarmAccent    = Color(0xFF7C6AF7)
private val AlarmAccentAlt = Color(0xFF5E5CE6)
private val AlarmSuccess   = Color(0xFF4ADE80)
private val AlarmTextPri   = Color(0xFFF1F0FF)
private val AlarmTextSec   = Color(0xFFADADB8)
private val AlarmTextMuted = Color(0xFF6A6A7A)

// ─────────────────────────────────────────────────────────────────────────────
// ROOT SCREEN — branches to a plain ring screen unless Strict Mode is on
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlarmRingScreen(
    alarm: StrictAlarmEntry?,
    onDismissed: () -> Unit,
    onSnoozed: () -> Unit
) {
    val label = alarm?.label?.ifBlank { "Alarm" } ?: "Alarm"

    var timeString by remember {
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    LaunchedEffect(Unit) {
        while (true) {
            timeString = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            delay(10_000)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AlarmBg) {
        if (alarm != null && alarm.strictModeEnabled) {
            StrictRingContent(
                alarm = alarm,
                timeString = timeString,
                label = label,
                onDismissed = onDismissed,
                onSnoozed = onSnoozed
            )
        } else {
            NormalRingContent(
                timeString = timeString,
                label = label,
                snoozeEnabled = alarm?.snoozeEnabled ?: true,
                snoozeMinutes = alarm?.snoozeMinutes ?: 9,
                onDismiss = onDismissed,
                onSnooze = onSnoozed
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL RING CONTENT — the default experience. No puzzle, no friction:
// exactly one or two taps, like stock Android/Samsung/Apple Clock.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NormalRingContent(
    timeString: String,
    label: String,
    snoozeEnabled: Boolean,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))

        PulsingAlarmIcon(won = false)

        Spacer(Modifier.height(24.dp))

        Text(
            text = timeString,
            fontSize = 72.sp,
            fontWeight = FontWeight.Thin,
            color = AlarmTextPri,
            letterSpacing = (-1).sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = AlarmAccent,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        if (snoozeEnabled) {
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AlarmTextMuted.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlarmTextPri)
            ) {
                Text(
                    "Snooze · $snoozeMinutes min",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AlarmAccent,
                contentColor = Color.White
            )
        ) {
            Text(
                "Dismiss alarm",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STRICT RING CONTENT — round-tracking wrapper around whichever challenge
// this alarm was configured with. Only reachable when strictModeEnabled.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StrictRingContent(
    alarm: StrictAlarmEntry,
    timeString: String,
    label: String,
    onDismissed: () -> Unit,
    onSnoozed: () -> Unit
) {
    val totalRounds = alarm.effectiveChallengeRounds
    var roundsCompleted by remember { mutableIntStateOf(0) }
    var roundKey by remember { mutableIntStateOf(0) }
    val allRoundsComplete = roundsCompleted >= totalRounds

    fun onRoundPassed() {
        if (roundsCompleted + 1 >= totalRounds) {
            roundsCompleted = totalRounds
        } else {
            roundsCompleted += 1
            roundKey += 1
        }
    }

    // A longer, satisfying pause once EVERY round is done (the WonCard is
    // visible during this) — separate from and on top of the brief per-round
    // checkmark beat each challenge card already plays before calling back.
    LaunchedEffect(allRoundsComplete) {
        if (allRoundsComplete) {
            delay(800)
            onDismissed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))

        PulsingAlarmIcon(won = allRoundsComplete)

        Spacer(Modifier.height(16.dp))

        Text(
            text = timeString,
            fontSize = 46.sp,
            fontWeight = FontWeight.Thin,
            color = AlarmTextPri,
            letterSpacing = (-1).sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = AlarmAccent,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        if (totalRounds > 1) {
            Spacer(Modifier.height(14.dp))
            RoundDots(completed = roundsCompleted, total = totalRounds)
        }

        Spacer(Modifier.height(24.dp))

        if (!allRoundsComplete) {
            key(roundKey) {
                when (alarm.challengeType) {
                    AlarmChallengeType.MATH -> MathChallengeCard(
                        difficulty = alarm.challengeDifficulty,
                        onPassed = ::onRoundPassed
                    )
                    AlarmChallengeType.TYPING -> TypingChallengeCard(
                        phrase = alarm.effectiveTypingPhrase,
                        onPassed = ::onRoundPassed
                    )
                    AlarmChallengeType.SHAKE -> ShakeChallengeCard(
                        difficulty = alarm.challengeDifficulty,
                        onPassed = ::onRoundPassed
                    )
                }
            }
        } else {
            WonCard()
        }

        Spacer(Modifier.weight(1f))

        if (!allRoundsComplete && alarm.snoozeEnabled) {
            TextButton(onClick = onSnoozed) {
                Text(
                    "Snooze instead · ${alarm.snoozeMinutes} min",
                    color = AlarmTextSec,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RoundDots(completed: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val filled = i < completed
            Box(
                modifier = Modifier
                    .size(if (filled) 8.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (filled) AlarmAccent else AlarmTextMuted.copy(alpha = 0.35f))
            )
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
// MATH CHALLENGE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MathChallengeCard(difficulty: AlarmChallengeDifficulty, onPassed: () -> Unit) {
    var problem  by remember { mutableStateOf(newAlarmProblem(difficulty)) }
    var input    by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }

    val feedback = rememberChallengeFeedback()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        val guess = input.toIntOrNull()
        if (guess == problem.answer) {
            keyboard?.hide()
            scope.launch {
                haptics.confirm()
                feedback.succeed()
                delay(MotionDurations.Emphasized.toLong())
                onPassed()
            }
        } else {
            haptics.error()
            scope.launch { feedback.fail() }
            input = ""
            attempts++
            problem = newAlarmProblem(difficulty)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shakeAndFlash(feedback, cornerRadius = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AlarmCard)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = if (attempts == 0) "Solve to continue" else "Try again — new problem",
                style = MaterialTheme.typography.labelLarge,
                color = AlarmTextMuted
            )

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
                    fontSize   = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = AlarmTextPri,
                    textAlign  = TextAlign.Center,
                    letterSpacing = 1.sp
                )
            }

            OutlinedTextField(
                value          = input,
                onValueChange  = { input = it.filter(Char::isDigit) },
                placeholder    = { Text("Your answer", color = AlarmTextMuted) },
                singleLine     = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AlarmAccent,
                    unfocusedBorderColor = AlarmTextMuted.copy(alpha = 0.4f),
                    focusedTextColor     = AlarmTextPri,
                    unfocusedTextColor   = AlarmTextPri,
                    cursorColor          = AlarmAccent
                )
            )

            Button(
                onClick  = ::submit,
                enabled  = input.isNotEmpty() && !feedback.isSucceeding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = AlarmAccent,
                    contentColor           = Color.White,
                    disabledContainerColor = AlarmAccent.copy(alpha = 0.22f),
                    disabledContentColor   = Color.White.copy(alpha = 0.35f)
                )
            ) {
                Text("Check answer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        SuccessCheckmark(feedback, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TYPING CHALLENGE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TypingChallengeCard(phrase: String, onPassed: () -> Unit) {
    var input by remember { mutableStateOf("") }

    val feedback = rememberChallengeFeedback()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    fun submit() {
        if (input.trim().equals(phrase.trim(), ignoreCase = true)) {
            keyboard?.hide()
            scope.launch {
                haptics.confirm()
                feedback.succeed()
                delay(MotionDurations.Emphasized.toLong())
                onPassed()
            }
        } else {
            haptics.error()
            scope.launch { feedback.fail() }
            input = ""
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shakeAndFlash(feedback, cornerRadius = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AlarmCard)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = "Type the phrase exactly",
                style = MaterialTheme.typography.labelLarge,
                color = AlarmTextMuted
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(AlarmAccentAlt.copy(alpha = 0.18f), AlarmAccent.copy(alpha = 0.10f))
                        )
                    )
                    .padding(vertical = 20.dp, horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u201C$phrase\u201D",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontStyle = FontStyle.Italic,
                    color = AlarmTextPri,
                    textAlign = TextAlign.Center
                )
            }

            OutlinedTextField(
                value          = input,
                onValueChange  = { input = it },
                placeholder    = { Text("Type it here", color = AlarmTextMuted) },
                singleLine     = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AlarmAccent,
                    unfocusedBorderColor = AlarmTextMuted.copy(alpha = 0.4f),
                    focusedTextColor     = AlarmTextPri,
                    unfocusedTextColor   = AlarmTextPri,
                    cursorColor          = AlarmAccent
                )
            )

            Button(
                onClick  = ::submit,
                enabled  = input.isNotEmpty() && !feedback.isSucceeding,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = AlarmAccent,
                    contentColor           = Color.White,
                    disabledContainerColor = AlarmAccent.copy(alpha = 0.22f),
                    disabledContentColor   = Color.White.copy(alpha = 0.35f)
                )
            ) {
                Text("Check phrase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        SuccessCheckmark(feedback, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHAKE CHALLENGE — uses the accelerometer directly (no permission needed).
// Falls back to a tap counter on the rare device with no accelerometer at
// all, so nobody can ever get permanently stuck on this screen.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShakeChallengeCard(difficulty: AlarmChallengeDifficulty, onPassed: () -> Unit) {
    val target = shakeTarget(difficulty)
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(SensorManager::class.java) }
    val accelerometer = remember { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    var count by remember { mutableIntStateOf(0) }
    var jolt by remember { mutableStateOf(false) }
    val done = count >= target

    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    fun registerHit() {
        if (count >= target) return
        count++
        haptics.tap()
        jolt = true
        if (count >= target) {
            scope.launch {
                haptics.confirm()
                delay(MotionDurations.Emphasized.toLong())
                onPassed()
            }
        }
    }

    if (accelerometer != null) {
        DisposableEffect(Unit) {
            var lastShakeAt = 0L
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val delta = sqrt((x * x + y * y + z * z).toDouble()).toFloat() - SensorManager.GRAVITY_EARTH
                    val now = System.currentTimeMillis()
                    if (delta > SHAKE_THRESHOLD && now - lastShakeAt > SHAKE_COOLDOWN_MS) {
                        lastShakeAt = now
                        registerHit()
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager?.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensorManager?.unregisterListener(listener) }
        }
    }

    LaunchedEffect(jolt) {
        if (jolt) {
            delay(120)
            jolt = false
        }
    }

    val boxScale by animateFloatAsState(
        targetValue = if (jolt) 1.05f else 1f,
        animationSpec = MotionSpecs.tactile(),
        label = "shakeJolt"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(boxScale)
            .clip(RoundedCornerShape(24.dp))
            .background(AlarmCard)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (accelerometer != null) "Shake your phone" else "Tap to wake up",
            style = MaterialTheme.typography.labelLarge,
            color = AlarmTextMuted
        )

        Text(text = "📳", fontSize = 40.sp)

        Text(
            text = "$count / $target",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AlarmTextPri
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(AlarmTextMuted.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (count.toFloat() / target).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(AlarmAccent)
            )
        }

        if (accelerometer == null) {
            Button(
                onClick  = ::registerHit,
                enabled  = !done,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AlarmAccent, contentColor = Color.White)
            ) {
                Text("Tap ($count/$target)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WON STATE — shown once every round for this alarm has been cleared
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
                "Nicely done!",
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
