package com.allinone.blocker.ui

// ═══════════════════════════════════════════════════════════════════════════
// DailyTasksScreen.kt  —  Pomodoro rewards, part 1: the Daily Tasks tab
//
// WHAT YOU SEE
//   One big white card (like a quest list). Every task is a row:
//
//        [ colourful icon ]   Task name
//                             [██████░░░░  1 / 2 ]            [ chest ]
//
//   • The bar fills up with a little animation as you make progress.
//   • The chest on the right starts silver (nothing done), turns tan once
//     you have started, and turns GOLD and wiggles when the task is finished.
//     Tap the golden chest to open it (confetti!).
//   • Top right: how many hours are left until the tasks reset at midnight.
//   • "Add task" lets you create your own tasks. Tap one of YOUR tasks to
//     add progress, undo, or delete it.
//
// WHERE THE DATA LIVES
//   Everything is kept in DailyTasks.kt (data folder). This file only DRAWS
//   it and calls the repository when you tap something. The built-in
//   "full Pomodoro set" task is filled by the timer (PomodoroViewModel).
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.data.DailyTask
import com.allinone.blocker.data.DailyTaskRules
import com.allinone.blocker.data.DailyTasksRepository
import com.allinone.blocker.ui.motion.AnimatedAppearance
import com.allinone.blocker.ui.motion.LocalReducedMotion
import com.allinone.blocker.ui.motion.MotionDurations
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.motion.MotionTokens
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

// ─────────────────────────────────────────────────────────────────────────
// LOOK  (colours shared by every piece below)
// ─────────────────────────────────────────────────────────────────────────

/**
 * The bright task colours. A task stores a NUMBER (0 to 5); this list says
 * which colour that number is. Keep the list the same length as
 * DailyTaskRules.COLOR_COUNT.
 */
private val TaskColors = listOf(
    Color(0xFF58CC02), // 0  green
    Color(0xFF1CB0F6), // 1  blue
    Color(0xFFFFC800), // 2  yellow
    Color(0xFFCE82FF), // 3  purple
    Color(0xFFFF6B5B), // 4  tomato red (the built-in Pomodoro task)
    Color(0xFFFF9600)  // 5  orange
)

private fun taskColor(index: Int): Color =
    TaskColors[index.coerceIn(0, TaskColors.size - 1)]

/** A deeper shade of [c] — used for the "thickness" under icons and for text on a bar. */
private fun darken(c: Color, amount: Float = 0.25f): Color = lerp(c, Color.Black, amount)

/** A paler shade of [c] — the top of the icon gradient. */
private fun lighten(c: Color, amount: Float = 0.25f): Color = lerp(c, Color.White, amount)

private val TimerOrange = Color(0xFFFF9600)

/** The icons you can pick for your own tasks. */
private val TaskEmojis = listOf(
    "\uD83C\uDFAF", // 🎯
    "\uD83D\uDCDA", // 📚
    "\uD83D\uDCA7", // 💧
    "\uD83C\uDFC3", // 🏃
    "\uD83E\uDDD8", // 🧘
    "\u270D\uFE0F", // ✍️
    "\uD83C\uDF4E", // 🍎
    "\uD83D\uDE34", // 😴
    "\uD83D\uDCAA", // 💪
    "\uD83E\uDDF9"  // 🧹
)

// ─────────────────────────────────────────────────────────────────────────
// THE SCREEN
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTasksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { DailyTasksRepository.get(context) }
    val state by repo.state.collectAsState()
    val haptics = rememberHaptics()

    var showConfetti by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DailyTask?>(null) }
    // Which row is opened up to show its extra buttons / hint (only one at a time).
    var expandedId by remember { mutableStateOf<String?>(null) }

    // Keeps the "hours left" badge correct and flips to the new day after midnight.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            repo.refreshDay()
            now = LocalDateTime.now()
            delay(30_000L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BgScreen,
                        scrolledContainerColor = BgScreen
                    )
                )
            },
            containerColor = BgScreen
        ) { pad ->
            Column(
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: title on the left, hours left on the right.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Daily Tasks",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    TimeLeftBadge(now)
                }

                Spacer(Modifier.height(14.dp))

                // The big white card with one row per task.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = CardSurface,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.animateContentSize()) {
                        state.tasks.forEachIndexed { index, task ->
                            // key() makes each row keep its own animation when
                            // tasks are added or removed.
                            key(task.id) {
                                if (index > 0) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                }
                                AnimatedAppearance(
                                    modifier = Modifier.fillMaxWidth(),
                                    delayMs = index * MotionTokens.StaggerStepMs
                                ) {
                                    TaskRow(
                                        task = task,
                                        expanded = expandedId == task.id,
                                        onToggleExpanded = {
                                            haptics.tap()
                                            expandedId = if (expandedId == task.id) null else task.id
                                        },
                                        onClaim = {
                                            if (repo.claim(task.id)) {
                                                haptics.confirm()
                                                showConfetti = true
                                            }
                                        },
                                        onAddProgress = { delta ->
                                            val willFinish = delta > 0 && task.progress + delta >= task.target
                                            if (repo.addProgress(task.id, delta)) {
                                                if (willFinish) haptics.confirm() else haptics.tap()
                                            }
                                        },
                                        onDelete = { pendingDelete = task }
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.customCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tap one of your tasks to add progress or delete it",
                        fontSize = 12.sp,
                        color = TextTertiary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(Modifier.height(18.dp))

                val canAdd = state.customCount < DailyTaskRules.MAX_CUSTOM_TASKS
                AddTaskButton(
                    enabled = canAdd,
                    onClick = {
                        haptics.tap()
                        showAddDialog = true
                    }
                )

                Spacer(Modifier.height(32.dp))
            }
        }

        ConfettiOverlay(
            trigger = showConfetti,
            onFinished = { showConfetti = false }
        )
    }

    if (showAddDialog) {
        AddTaskDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, target, emoji, colorIndex ->
                if (repo.addCustomTask(title, target, emoji, colorIndex)) haptics.confirm()
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = CardSurface,
            title = {
                Text("Delete this task?", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    "${task.title} will be removed from your daily tasks.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.error()
                        repo.deleteCustomTask(task.id)
                        if (expandedId == task.id) expandedId = null
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Keep it", color = TextMuted)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// HEADER BADGE  ("7 HOURS" until the tasks reset)
// ─────────────────────────────────────────────────────────────────────────

/** "7 HOURS" while there is more than an hour left, "25 MIN" in the last hour. */
private fun timeLeftLabel(now: LocalDateTime): String {
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
    val seconds = Duration.between(now, midnight).seconds
    val minutes = ((seconds + 59L) / 60L).toInt().coerceAtLeast(1)
    return if (minutes > 60) "${(minutes + 59) / 60} HOURS" else "$minutes MIN"
}

@Composable
private fun TimeLeftBadge(now: LocalDateTime) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Outlined.Timer,
            contentDescription = null,
            tint = TimerOrange,
            modifier = Modifier.size(26.dp)
        )
        Text(
            timeLeftLabel(now),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = TimerOrange
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// ONE TASK ROW
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun TaskRow(
    task: DailyTask,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClaim: () -> Unit,
    onAddProgress: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val color = taskColor(task.colorIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskIconTile(task = task, color = color)

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2
                )
                if (task.subtitle != null) {
                    Text(
                        task.subtitle,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                TaskProgressBar(task = task, color = color)
            }

            Spacer(Modifier.width(12.dp))

            TaskChest(task = task, onClaim = onClaim)
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            if (task.isBuiltIn) {
                Text(
                    "Tracked automatically. Finish all your focus sessions and reach the long break, and this fills up by itself.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 76.dp, top = 12.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 76.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionPill(
                        label = "+1",
                        background = color,
                        textColor = darken(color, 0.55f),
                        enabled = !task.done,
                        onClick = { onAddProgress(1) }
                    )
                    ActionPill(
                        label = "Undo",
                        background = MaterialTheme.colorScheme.surfaceVariant,
                        textColor = TextSecondary,
                        enabled = task.progress > 0 && !task.claimed,
                        onClick = { onAddProgress(-1) }
                    )
                    Spacer(Modifier.weight(1f))
                    ActionPill(
                        label = "Delete",
                        background = AccentRed.copy(alpha = 0.15f),
                        textColor = AccentRed,
                        enabled = true,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    background: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .pressable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
    }
}

// ─────────────────────────────────────────────────────────────────────────
// THE ICON TILE  (colourful, a little "3D", bobs gently while still to do)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun TaskIconTile(task: DailyTask, color: Color) {
    val reduced = LocalReducedMotion.current

    // Slow up-and-down float while the task is still open.
    val infinite = rememberInfiniteTransition(label = "tileBob")
    val bob by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tileBobValue"
    )

    // A quick happy "pop" when the task becomes finished.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(task.done) {
        if (task.done && !reduced) {
            pop.animateTo(1.2f, tween(durationMillis = 110))
            pop.animateTo(1f, MotionSpecs.expressive())
        }
    }

    // The built-in task is round, your own tasks are rounded squares.
    val shape = if (task.isBuiltIn) CircleShape else RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .size(width = 60.dp, height = 64.dp)
            .graphicsLayer {
                translationY = if (reduced || task.done) 0f else bob.dp.toPx()
                scaleX = pop.value
                scaleY = pop.value
            }
    ) {
        // The darker shape underneath gives the tile its thickness.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(60.dp)
                .clip(shape)
                .background(darken(color))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(60.dp)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(lighten(color), color))),
            contentAlignment = Alignment.Center
        ) {
            Text(task.emoji, fontSize = 30.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// THE PROGRESS BAR  (fills with an animation, glossy stripe, "1 / 2" inside)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun TaskProgressBar(task: DailyTask, color: Color) {
    val reduced = LocalReducedMotion.current

    // Starts empty and slides up to the real value, also when the screen opens.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(task.fraction) {
        if (reduced) {
            shown.snapTo(task.fraction)
        } else {
            shown.animateTo(task.fraction, MotionSpecs.enter(MotionDurations.Slow + 280))
        }
    }

    val fillAmount = shown.value.coerceIn(0f, 1f)
    // Same trick as the reference: the number turns into a darker shade of the
    // bar colour once the bar reaches behind it.
    val numberColor = if (fillAmount >= 0.5f) darken(color, 0.55f) else TextTertiary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.CenterStart
    ) {
        if (fillAmount > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillAmount)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(color)
            ) {
                // The light stripe along the top makes the bar look glossy.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(start = 9.dp, end = 9.dp, top = 6.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.35f))
                )
            }
        }
        Text(
            "${task.progress} / ${task.target}",
            modifier = Modifier.align(Alignment.Center),
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = numberColor
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// THE TREASURE CHEST  (drawn in code, so no image files are needed)
//
//   silver  = nothing done yet
//   tan     = started
//   gold    = finished, tap to open (wiggles and glows)
//   opened  = collected (green tick)
// ─────────────────────────────────────────────────────────────────────────

private enum class ChestState { LOCKED, STARTED, READY, OPENED }

private fun chestStateOf(task: DailyTask): ChestState = when {
    task.claimed -> ChestState.OPENED
    task.done -> ChestState.READY
    task.progress > 0 -> ChestState.STARTED
    else -> ChestState.LOCKED
}

@Composable
private fun TaskChest(task: DailyTask, onClaim: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val chest = chestStateOf(task)
    val ready = chest == ChestState.READY

    val infinite = rememberInfiniteTransition(label = "chest")

    // Little left-right shake, then a pause, over and over.
    val wiggle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1800
                0f at 0
                -10f at 150
                10f at 300
                -8f at 450
                8f at 600
                0f at 750
            }
        ),
        label = "chestWiggle"
    )

    // Breathing glow behind the golden chest.
    val glow by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chestGlow"
    )

    // A bigger "pop" when the chest has just been opened.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(chest) {
        if (chest == ChestState.OPENED && !reduced) {
            pop.animateTo(1.35f, tween(durationMillis = 120))
            pop.animateTo(1f, MotionSpecs.expressive())
        }
    }

    val description = when (chest) {
        ChestState.LOCKED -> "Chest, locked"
        ChestState.STARTED -> "Chest, in progress"
        ChestState.READY -> "Chest ready, tap to open"
        ChestState.OPENED -> "Chest opened"
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = description }
            .pressable(enabled = ready, role = Role.Button, onClick = onClaim)
            .graphicsLayer {
                rotationZ = if (ready && !reduced) wiggle else 0f
                scaleX = pop.value
                scaleY = pop.value
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawChest(chest, if (reduced) 0.6f else glow)
        }
    }
}

private fun DrawScope.drawChest(state: ChestState, glow: Float) {
    val w = size.width
    val h = size.height

    val frame: Color
    val plate: Color
    when (state) {
        ChestState.LOCKED -> { frame = Color(0xFFC3CCDA); plate = Color(0xFF9AA8BE) }
        ChestState.STARTED -> { frame = Color(0xFFDFAF7D); plate = Color(0xFFF6E3BD) }
        ChestState.READY -> { frame = Color(0xFFF7C934); plate = Color(0xFFFFF4C2) }
        ChestState.OPENED -> { frame = Color(0xFFF7C934); plate = Color(0xFFFFF4C2) }
    }
    val wood = if (state == ChestState.OPENED) Color(0xFF5B3320) else Color(0xFF9A5B33)
    val woodTop = if (state == ChestState.OPENED) Color(0xFF47281A) else Color(0xFF7D4726)

    // Soft golden glow behind a chest that is ready to open.
    if (state == ChestState.READY) {
        val radius = size.minDimension * 0.8f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFE066).copy(alpha = 0.6f * glow), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }

    val top = h * 0.07f
    val bodyHeight = h * 0.86f

    // Outer frame (the coloured posts on the left and right).
    drawRoundRect(
        color = frame,
        topLeft = Offset(0f, top),
        size = Size(w, bodyHeight),
        cornerRadius = CornerRadius(w * 0.12f)
    )
    // Wooden middle, with a darker band along the top.
    drawRect(color = wood, topLeft = Offset(w * 0.22f, top), size = Size(w * 0.56f, bodyHeight))
    drawRect(color = woodTop, topLeft = Offset(w * 0.22f, top), size = Size(w * 0.56f, bodyHeight * 0.16f))

    if (state == ChestState.OPENED) {
        // Green badge with a white tick.
        val badge = Offset(w * 0.5f, h * 0.52f)
        drawCircle(color = Color(0xFF58CC02), radius = w * 0.2f, center = badge)
        val tick = Path().apply {
            moveTo(badge.x - w * 0.09f, badge.y)
            lineTo(badge.x - w * 0.02f, badge.y + w * 0.07f)
            lineTo(badge.x + w * 0.1f, badge.y - w * 0.07f)
        }
        drawPath(
            path = tick,
            color = Color.White,
            style = Stroke(width = w * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    } else {
        // Lock plate with a keyhole.
        drawRoundRect(
            color = plate,
            topLeft = Offset(w * 0.35f, h * 0.37f),
            size = Size(w * 0.3f, h * 0.26f),
            cornerRadius = CornerRadius(w * 0.05f)
        )
        drawCircle(color = wood, radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.47f))
        drawRect(
            color = wood,
            topLeft = Offset(w * 0.5f - w * 0.015f, h * 0.47f),
            size = Size(w * 0.03f, h * 0.09f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// "ADD TASK" BUTTON AND DIALOG
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun AddTaskButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .pressable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .background(AccentBlue.copy(alpha = 0.14f))
            .border(2.dp, AccentBlue, shape)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(18.dp)
        )
        Text(
            if (enabled) "Add task" else "Task limit reached",
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AccentBlue
        )
    }
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, target: Int, emoji: String, colorIndex: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(1) }
    var emoji by remember { mutableStateOf(TaskEmojis.first()) }
    var colorIndex by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = {
            Text("New daily task", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(DailyTaskRules.MAX_TITLE_LENGTH) },
                    label = { Text("What do you want to do?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // How many times per day.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Times per day",
                        modifier = Modifier.weight(1f),
                        color = TextSecondary
                    )
                    StepperButton(Icons.Filled.Remove, "Fewer") {
                        target = (target - 1).coerceAtLeast(1)
                    }
                    Text(
                        "$target",
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    StepperButton(Icons.Filled.Add, "More") {
                        target = (target + 1).coerceAtMost(DailyTaskRules.MAX_TARGET)
                    }
                }

                // Pick an icon.
                Text("Icon", fontSize = 13.sp, color = TextSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskEmojis.forEach { option ->
                        val selected = option == emoji
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .pressable(onClick = { emoji = option })
                                .clip(CircleShape)
                                .background(
                                    if (selected) AccentBlue.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    2.dp,
                                    if (selected) AccentBlue else Color.Transparent,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(option, fontSize = 22.sp)
                        }
                    }
                }

                // Pick a colour.
                Text("Colour", fontSize = 13.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TaskColors.forEachIndexed { index, swatch ->
                        val selected = index == colorIndex
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .pressable(onClick = { colorIndex = index })
                                .border(
                                    3.dp,
                                    if (selected) TextPrimary else Color.Transparent,
                                    CircleShape
                                )
                                .padding(5.dp)
                                .clip(CircleShape)
                                .background(swatch)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title, target, emoji, colorIndex) }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = TextPrimary, modifier = Modifier.size(18.dp))
    }
}
