package com.allinone.blocker.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.allinone.blocker.ui.theme.*

@Composable
fun PomodoroScreen(
    onBack: () -> Unit,
    viewModel: PomodoroViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgScreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Top bar
            TopAppBar(
                title = { 
                    Text(
                        "Pomodoro Timer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, "Settings", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(20.dp))

            // Mode selector
            ModeSelector(
                currentMode = state.mode,
                onModeChange = { viewModel.switchMode(it) },
                enabled = state.timerState == TimerState.IDLE
            )

            Spacer(Modifier.height(40.dp))

            // Timer display with circular progress
            TimerDisplay(
                timeRemaining = state.timeRemaining,
                totalTime = state.totalTime,
                mode = state.mode
            )

            Spacer(Modifier.height(40.dp))

            // Control buttons
            TimerControls(
                timerState = state.timerState,
                onStart = { viewModel.startTimer() },
                onPause = { viewModel.pauseTimer() },
                onReset = { viewModel.resetTimer() }
            )

            Spacer(Modifier.height(30.dp))

            // Stats card
            StatsCard(
                completedSessions = state.completedSessions,
                sessionsUntilLongBreak = state.settings.sessionsUntilLongBreak
            )

            Spacer(Modifier.weight(1f))
        }

        // Settings dialog
        if (showSettings) {
            PomodoroSettingsDialog(
                settings = state.settings,
                onDismiss = { showSettings = false },
                onSave = { newSettings ->
                    viewModel.updateSettings(newSettings)
                    showSettings = false
                },
                onResetStats = { viewModel.resetStats() }
            )
        }
    }
}

@Composable
fun ModeSelector(
    currentMode: PomodoroMode,
    onModeChange: (PomodoroMode) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModeButton(
            text = "Focus",
            isSelected = currentMode == PomodoroMode.FOCUS,
            onClick = { onModeChange(PomodoroMode.FOCUS) },
            enabled = enabled,
            color = AccentBlue,
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            text = "Short Break",
            isSelected = currentMode == PomodoroMode.SHORT_BREAK,
            onClick = { onModeChange(PomodoroMode.SHORT_BREAK) },
            enabled = enabled,
            color = AccentTeal,
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            text = "Long Break",
            isSelected = currentMode == PomodoroMode.LONG_BREAK,
            onClick = { onModeChange(PomodoroMode.LONG_BREAK) },
            enabled = enabled,
            color = AccentAmber,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) color else CardSurface
    val textColor = if (isSelected) Color.White else TextSecondary

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor,
            disabledContainerColor = CardSurface.copy(alpha = 0.5f),
            disabledContentColor = TextTertiary
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = if (isSelected) ButtonDefaults.buttonElevation(4.dp) else ButtonDefaults.buttonElevation(0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

@Composable
fun TimerDisplay(
    timeRemaining: Int,
    totalTime: Int,
    mode: PomodoroMode
) {
    val progress = if (totalTime > 0) timeRemaining.toFloat() / totalTime.toFloat() else 0f
    val minutes = timeRemaining / 60
    val seconds = timeRemaining % 60

    val modeColor = when (mode) {
        PomodoroMode.FOCUS -> AccentBlue
        PomodoroMode.SHORT_BREAK -> AccentTeal
        PomodoroMode.LONG_BREAK -> AccentAmber
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Circular progress
        val cardSurface = CardSurface
        Canvas(
            modifier = Modifier.size(280.dp)
    ) {
            val strokeWidth = 20.dp.toPx()
            val size = this.size.minDimension - strokeWidth
            val topLeft = Offset((this.size.width - size) / 2, (this.size.height - size) / 2)

            // Background circle
            drawArc(
                color = cardSurface,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(size, size),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(modeColor, modeColor.copy(alpha = 0.6f), modeColor)
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = Size(size, size),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Time text
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = String.format("%02d:%02d", minutes, seconds),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 56.sp
            )
            Text(
                text = when (mode) {
                    PomodoroMode.FOCUS -> "Focus Time"
                    PomodoroMode.SHORT_BREAK -> "Short Break"
                    PomodoroMode.LONG_BREAK -> "Long Break"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun TimerControls(
    timerState: TimerState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reset button
        IconButton(
            onClick = onReset,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(CardSurface),
            enabled = timerState != TimerState.IDLE
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Reset",
                tint = if (timerState != TimerState.IDLE) TextPrimary else TextTertiary,
                modifier = Modifier.size(28.dp)
            )
        }

        // Play/Pause button (large center button)
        FloatingActionButton(
            onClick = {
                when (timerState) {
                    TimerState.IDLE, TimerState.PAUSED -> onStart()
                    TimerState.RUNNING -> onPause()
                }
            },
            modifier = Modifier.size(80.dp),
            containerColor = AccentBlue,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = when (timerState) {
                    TimerState.RUNNING -> Icons.Filled.Pause
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = when (timerState) {
                    TimerState.RUNNING -> "Pause"
                    else -> "Start"
                },
                modifier = Modifier.size(36.dp)
            )
        }

        // Skip button (placeholder for future feature)
        IconButton(
            onClick = { /* Future: skip to next session */ },
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(CardSurface),
            enabled = false // Disabled for now
        ) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "Skip",
                tint = TextTertiary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun StatsCard(
    completedSessions: Int,
    sessionsUntilLongBreak: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Session Stats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Completed Today",
                    value = completedSessions.toString(),
                    icon = Icons.Filled.CheckCircle,
                    color = AccentTeal
                )

                StatItem(
                    label = "Until Long Break",
                    value = (sessionsUntilLongBreak - (completedSessions % sessionsUntilLongBreak)).toString(),
                    icon = Icons.Filled.Timer,
                    color = AccentAmber
                )
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }

        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun PomodoroSettingsDialog(
    settings: PomodoroSettings,
    onDismiss: () -> Unit,
    onSave: (PomodoroSettings) -> Unit,
    onResetStats: () -> Unit
) {
    var focusDuration by remember { mutableStateOf(settings.focusDuration) }
    var shortBreakDuration by remember { mutableStateOf(settings.shortBreakDuration) }
    var longBreakDuration by remember { mutableStateOf(settings.longBreakDuration) }
    var sessionsUntilLongBreak by remember { mutableStateOf(settings.sessionsUntilLongBreak) }
    var autoStartBreaks by remember { mutableStateOf(settings.autoStartBreaks) }
    var autoStartPomodoros by remember { mutableStateOf(settings.autoStartPomodoros) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = {
            Text(
                "Pomodoro Settings",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Duration settings
                Text(
                    "Duration (minutes)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                DurationSetting(
                    label = "Focus Time",
                    value = focusDuration,
                    onValueChange = { focusDuration = it.coerceIn(1, 60) },
                    color = AccentBlue
                )

                DurationSetting(
                    label = "Short Break",
                    value = shortBreakDuration,
                    onValueChange = { shortBreakDuration = it.coerceIn(1, 30) },
                    color = AccentTeal
                )

                DurationSetting(
                    label = "Long Break",
                    value = longBreakDuration,
                    onValueChange = { longBreakDuration = it.coerceIn(5, 60) },
                    color = AccentAmber
                )

                HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f))

                // Sessions until long break
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sessions until long break",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { sessionsUntilLongBreak = (sessionsUntilLongBreak - 1).coerceAtLeast(2) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Remove, null, tint = TextSecondary)
                        }
                        Text(
                            "$sessionsUntilLongBreak",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        IconButton(
                            onClick = { sessionsUntilLongBreak = (sessionsUntilLongBreak + 1).coerceAtMost(10) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Add, null, tint = TextSecondary)
                        }
                    }
                }

                HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f))

                // Auto-start options
                Text(
                    "Auto-start Options",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                SwitchSetting(
                    label = "Auto-start breaks",
                    checked = autoStartBreaks,
                    onCheckedChange = { autoStartBreaks = it }
                )

                SwitchSetting(
                    label = "Auto-start focus sessions",
                    checked = autoStartPomodoros,
                    onCheckedChange = { autoStartPomodoros = it }
                )

                HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f))

                // Reset stats button
                TextButton(
                    onClick = {
                        onResetStats()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.RestartAlt, null, tint = AccentRed)
                        Text("Reset Session Stats", color = AccentRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        PomodoroSettings(
                            focusDuration = focusDuration,
                            shortBreakDuration = shortBreakDuration,
                            longBreakDuration = longBreakDuration,
                            sessionsUntilLongBreak = sessionsUntilLongBreak,
                            autoStartBreaks = autoStartBreaks,
                            autoStartPomodoros = autoStartPomodoros
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Save")
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
fun DurationSetting(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onValueChange(value - 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Remove, null, tint = TextSecondary)
            }

            Text(
                "$value min",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.width(60.dp),
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = { onValueChange(value + 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.Add, null, tint = TextSecondary)
            }
        }
    }
}

@Composable
fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = TextTertiary.copy(alpha = 0.3f)
            )
        )
    }
}
