package com.allinone.blocker.ui

// ═══════════════════════════════════════════════════════════════════════════
// PomodoroStatsCard.kt  —  the stats card at the bottom of the Pomodoro screen
//
// TWO LEVELS, ONE CARD
//
//   COLLAPSED (what you always see)
//     • A goal ring: sessions finished today out of your daily goal.
//     • How long you focused today (it ticks up live while a session runs).
//     • A one-line look back at yesterday.
//     • Your all-time sessions and total focus time.
//     • A flame badge with your current streak.
//
//   EXPANDED (tap the card)
//     • A bar chart of the week: switch between "Sessions" and "Focus time",
//       tap any bar to see that day, use the arrows to walk back through
//       earlier weeks.
//     • This week's totals compared with the week before.
//     • Records: current streak, longest streak, best day ever.
//
//   CELEBRATION
//     • When a finished session unlocks something (first session ever, the
//       daily goal, a milestone like 10/25/50/100 sessions, a personal-best
//       day, a streak) a banner pops in at the top of the card.
//
// This file only DRAWS. All the numbers come from the logbook
// (PomodoroStats.kt) and are calculated by PomodoroStatsMath, which has been
// tested separately.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.data.Achievement
import com.allinone.blocker.data.AchievementKind
import com.allinone.blocker.data.DayStats
import com.allinone.blocker.data.PomodoroStatsMath
import com.allinone.blocker.data.StatsSummary
import com.allinone.blocker.data.WeekDay
import com.allinone.blocker.ui.motion.LocalReducedMotion
import com.allinone.blocker.ui.motion.MotionDurations
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.motion.MotionTokens
import com.allinone.blocker.ui.motion.animatedCountAsState
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentGreen
import com.allinone.blocker.ui.theme.AccentPurple
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.max

/** Which number the weekly bars show. */
private enum class PomodoroStatsMetric(val label: String) {
    SESSIONS("Sessions"),
    TIME("Focus time")
}

// ─────────────────────────────────────────────────────────────────────────
// SMALL HELPERS
// ─────────────────────────────────────────────────────────────────────────

/** The app's standard animation feel — or "instant" if the phone has animations turned off. */
@Composable
private fun <T> statsSpec(durationMs: Int = MotionDurations.Slow): FiniteAnimationSpec<T> {
    val reduced = LocalReducedMotion.current
    return if (reduced) MotionSpecs.standard<T>(0) else MotionSpecs.enter<T>(durationMs)
}

/** 4500 -> "1h 15m", 2700 -> "45m", 30 -> "< 1m", 0 -> "0m". */
private fun statsDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "0m"
    if (totalSeconds < 60L) return "< 1m"
    val totalMinutes = totalSeconds / 60L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}

/** Very short version for the labels above the bars: 4500 -> "1.3h", 2700 -> "45m", 0 -> "". */
private fun statsCompactTime(seconds: Int): String {
    if (seconds <= 0) return ""
    if (seconds < 3600) return "${(seconds / 60).coerceAtLeast(1)}m"
    val hours = seconds / 3600.0
    return String.format(Locale.US, "%.1f", hours).removeSuffix(".0") + "h"
}

private fun statsSessions(count: Int): String = if (count == 1) "1 session" else "$count sessions"

private fun statsDays(count: Int): String = when (count) {
    0 -> "None yet"
    1 -> "1 day"
    else -> "$count days"
}

/** "Mon", "Tue"… in the phone's language. */
private fun statsDayName(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())

/** "Nov 9 – 15", or "Oct 30 – Nov 5" when the week crosses a month. */
private fun statsRange(start: LocalDate): String {
    val end = start.plusDays(6)
    val short = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    return if (start.month == end.month) {
        "${start.format(short)} – ${end.dayOfMonth}"
    } else {
        "${start.format(short)} – ${end.format(short)}"
    }
}

private fun statsLongDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))

// ─────────────────────────────────────────────────────────────────────────
// THE CARD
// ─────────────────────────────────────────────────────────────────────────

/**
 * @param history          the whole logbook (every day you ever focused)
 * @param today            the calendar day the app considers "today"
 * @param liveFocusSeconds focus seconds of the session that is running right
 *                         now (not in the logbook yet) — added on top so the
 *                         numbers tick up live
 * @param dailyGoal        sessions per day you are aiming for
 * @param achievement      the "well done!" message for the last finished session, if any
 */
@Composable
fun PomodoroStatsCard(
    history: Map<LocalDate, DayStats>,
    today: LocalDate,
    liveFocusSeconds: Int,
    dailyGoal: Int,
    achievement: Achievement?,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()

    var expanded by remember { mutableStateOf(false) }
    // 0 = this week, 1 = last week, 2 = two weeks ago…
    var weekOffset by remember { mutableIntStateOf(0) }
    var metric by remember { mutableStateOf(PomodoroStatsMetric.SESSIONS) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    // If the history gets cleared, jump back to the current week.
    LaunchedEffect(history.isEmpty()) {
        if (history.isEmpty()) {
            weekOffset = 0
            selectedDay = null
        }
    }

    // The logbook plus the seconds of the session running right now.
    val book = remember(history, today, liveFocusSeconds) {
        if (liveFocusSeconds > 0) {
            val t = history[today] ?: DayStats()
            history + (today to DayStats(t.sessions, t.focusSeconds + liveFocusSeconds))
        } else {
            history
        }
    }

    val summary = remember(book, today, dailyGoal) {
        PomodoroStatsMath.summarize(book, today, dailyGoal)
    }
    val yesterday = book[today.minusDays(1)]

    // Weeks start on the day that is normal for the phone's region.
    val firstDay = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val currentWeekStart = remember(today, firstDay) { PomodoroStatsMath.weekStart(today, firstDay) }
    val shownWeekStart = currentWeekStart.minusWeeks(weekOffset.toLong())
    val week = remember(book, shownWeekStart, today) {
        PomodoroStatsMath.week(book, shownWeekStart, today)
    }
    val priorWeek = remember(book, shownWeekStart, today) {
        PomodoroStatsMath.week(book, shownWeekStart.minusWeeks(1), today)
    }
    val earliest = remember(history) { history.keys.minOrNull() }
    val canGoBack = earliest != null && earliest.isBefore(shownWeekStart)
    val canGoForward = weekOffset > 0

    val expandIn = expandVertically(animationSpec = statsSpec()) + fadeIn(animationSpec = statsSpec())
    val collapseOut = shrinkVertically(animationSpec = statsSpec(MotionDurations.Standard)) +
        fadeOut(animationSpec = statsSpec(MotionDurations.Standard))

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // ── Celebration banner ───────────────────────────────────────
            StatsAchievementSlot(achievement)

            // ── Collapsed part: tap anywhere on it to open / close the week ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressable(
                        pressedScale = 0.985f,
                        onClick = {
                            haptics.tap()
                            expanded = !expanded
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                StatsHeader(streak = summary.currentStreak, expanded = expanded)
                StatsTodayRow(summary = summary, yesterday = yesterday)
                HorizontalDivider(color = TextTertiary.copy(alpha = 0.15f))
                StatsTotalsRow(summary = summary)
            }

            // ── Expanded part: the week ──────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandIn,
                exit = collapseOut
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalDivider(color = TextTertiary.copy(alpha = 0.15f))

                    StatsWeekHeader(
                        weekOffset = weekOffset,
                        start = shownWeekStart,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        onBack = {
                            haptics.tap()
                            selectedDay = null
                            weekOffset += 1
                        },
                        onForward = {
                            haptics.tap()
                            selectedDay = null
                            weekOffset = (weekOffset - 1).coerceAtLeast(0)
                        }
                    )

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StatsMetricToggle(
                            selected = metric,
                            onSelect = {
                                if (it != metric) {
                                    haptics.toggleTick()
                                    metric = it
                                }
                            }
                        )
                    }

                    StatsWeekChart(
                        week = week,
                        metric = metric,
                        goal = summary.goal,
                        selected = selectedDay,
                        onSelect = { date ->
                            haptics.tap()
                            selectedDay = if (selectedDay == date) null else date
                        }
                    )

                    StatsChartFooter(
                        selected = week.firstOrNull { it.date == selectedDay },
                        metric = metric,
                        goal = summary.goal
                    )

                    StatsWeekSummary(week = week, priorWeek = priorWeek)

                    StatsRecords(summary = summary)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// CELEBRATION BANNER
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsAchievementSlot(achievement: Achievement?) {
    // Remember the last message so it can still be drawn while it slides away.
    var last by remember { mutableStateOf(achievement) }
    LaunchedEffect(achievement) {
        if (achievement != null) last = achievement
    }

    AnimatedVisibility(
        visible = achievement != null,
        enter = expandVertically(animationSpec = statsSpec()) + fadeIn(animationSpec = statsSpec()),
        exit = shrinkVertically(animationSpec = statsSpec(MotionDurations.Standard)) +
            fadeOut(animationSpec = statsSpec(MotionDurations.Standard))
    ) {
        val shown = achievement ?: last
        if (shown != null) {
            Column {
                StatsAchievementBanner(shown)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatsAchievementBanner(achievement: Achievement) {
    val look: Pair<ImageVector, Color> = when (achievement.kind) {
        AchievementKind.FIRST_EVER -> Icons.Filled.StarRate to AccentPurple
        AchievementKind.MILESTONE -> Icons.Filled.EmojiEvents to AccentAmber
        AchievementKind.DAILY_GOAL -> Icons.Filled.CheckCircle to AccentGreen
        AchievementKind.BEST_DAY -> Icons.Filled.Leaderboard to AccentAmber
        AchievementKind.STREAK -> Icons.Filled.LocalFireDepartment to AccentRed
    }
    val icon = look.first
    val color = look.second

    // The badge pops in with a little bounce.
    val reduced = LocalReducedMotion.current
    val pop = remember(achievement) { Animatable(if (reduced) 1f else 0.4f) }
    LaunchedEffect(achievement) {
        pop.animateTo(1f, MotionSpecs.expressive<Float>())
    }

    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(CircleShape)
                .background(color.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = achievement.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// COLLAPSED PART
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsHeader(streak: Int, expanded: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = statsSpec(),
        label = "statsChevron"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Your focus",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = if (expanded) "Your week at a glance" else "Tap to see your week",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        if (streak > 0) {
            StatsStreakPill(streak)
            Spacer(Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(TextTertiary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Hide weekly progress" else "Show weekly progress",
                tint = TextSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation)
            )
        }
    }
}

@Composable
private fun StatsStreakPill(streak: Int) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(AccentAmber.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = AccentAmber,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (streak == 1) "1 day" else "$streak days",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = AccentAmber
        )
    }
}

@Composable
private fun StatsTodayRow(summary: StatsSummary, yesterday: DayStats?) {
    val shownSeconds by animatedCountAsState(summary.todayFocusSeconds)
    val goalReached = summary.todaySessions >= summary.goal

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        StatsGoalRing(
            sessions = summary.todaySessions,
            goal = summary.goal,
            diameter = 96.dp
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "TODAY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = TextTertiary
            )
            Text(
                text = statsDuration(shownSeconds.toLong()),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (goalReached) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = if (goalReached) {
                        "Daily goal reached"
                    } else {
                        "${summary.todaySessions} of ${summary.goal} sessions"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (goalReached) AccentGreen else TextSecondary
                )
            }
            if (yesterday != null && (yesterday.sessions > 0 || yesterday.focusSeconds > 0)) {
                Text(
                    text = "Yesterday · ${statsSessions(yesterday.sessions)} · " +
                        statsDuration(yesterday.focusSeconds.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The ring that fills up as you get closer to today's goal. */
@Composable
private fun StatsGoalRing(sessions: Int, goal: Int, diameter: Dp) {
    val reached = sessions >= goal
    val progress by animateFloatAsState(
        targetValue = (sessions.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = statsSpec(),
        label = "statsRing"
    )
    val ringColor by animateColorAsState(
        targetValue = if (reached) AccentGreen else AccentBlue,
        animationSpec = statsSpec(),
        label = "statsRingColor"
    )
    val shown by animatedCountAsState(sessions)
    val trackColor = TextTertiary.copy(alpha = 0.18f)

    Box(
        modifier = Modifier.size(diameter),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokeWidth = 9.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            if (progress > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$shown",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (reached) ringColor else TextPrimary
            )
            Text(
                text = "of $goal",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun StatsTotalsRow(summary: StatsSummary) {
    val sessions by animatedCountAsState(summary.totalSessions)
    val totalSeconds by animatedCountAsState(
        summary.totalFocusSeconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatsTotalItem(
            icon = Icons.Filled.CheckCircle,
            color = AccentTeal,
            value = "$sessions",
            label = if (sessions == 1) "session all time" else "sessions all time",
            modifier = Modifier.weight(1f)
        )
        StatsTotalItem(
            icon = Icons.Filled.AccessTime,
            color = AccentPurple,
            value = statsDuration(totalSeconds.toLong()),
            label = "total focus time",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatsTotalItem(
    icon: ImageVector,
    color: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// EXPANDED PART: WEEK NAVIGATION, SWITCH, CHART
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsWeekHeader(
    weekOffset: Int,
    start: LocalDate,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit
) {
    val title = when (weekOffset) {
        0 -> "This week"
        1 -> "Last week"
        else -> statsRange(start)
    }
    val subtitle = if (weekOffset <= 1) statsRange(start) else "$weekOffset weeks ago"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatsNavArrow(forward = false, enabled = canGoBack, onClick = onBack)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        StatsNavArrow(forward = true, enabled = canGoForward, onClick = onForward)
    }
}

@Composable
private fun StatsNavArrow(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .pressable(
                enabled = enabled,
                pressedScale = MotionTokens.PressScaleSmall,
                onClick = onClick
            )
            .clip(CircleShape)
            .background(TextTertiary.copy(alpha = if (enabled) 0.14f else 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = if (forward) "Next week" else "Previous week",
            tint = if (enabled) TextPrimary else TextTertiary.copy(alpha = 0.4f),
            modifier = Modifier
                .size(22.dp)
                .rotate(if (forward) 0f else 180f)
        )
    }
}

@Composable
private fun StatsMetricToggle(
    selected: PomodoroStatsMetric,
    onSelect: (PomodoroStatsMetric) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(TextTertiary.copy(alpha = 0.12f))
            .padding(3.dp)
    ) {
        PomodoroStatsMetric.values().forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) AccentBlue.copy(alpha = 0.22f) else Color.Transparent,
                animationSpec = statsSpec(MotionDurations.Standard),
                label = "statsToggleBg"
            )
            Box(
                modifier = Modifier
                    .pressable(
                        pressedScale = MotionTokens.PressScaleSmall,
                        onClick = { onSelect(option) }
                    )
                    .clip(CircleShape)
                    .background(background)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) AccentBlue else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun StatsWeekChart(
    week: List<WeekDay>,
    metric: PomodoroStatsMetric,
    goal: Int,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit
) {
    // The tallest bar is 100dp; the extra room above it holds the number label.
    val plotHeight = 124.dp
    val barMax = 100.dp

    val axisMax: Float = when (metric) {
        PomodoroStatsMetric.SESSIONS ->
            max(week.maxOfOrNull { it.sessions } ?: 0, goal).coerceAtLeast(1).toFloat()
        PomodoroStatsMetric.TIME ->
            (week.maxOfOrNull { it.focusSeconds } ?: 0).coerceAtLeast(1).toFloat()
    }
    val goalFraction = if (metric == PomodoroStatsMetric.SESSIONS) goal / axisMax else 0f
    val goalLineColor = TextTertiary.copy(alpha = 0.55f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight)
        ) {
            // Dashed line = the daily goal (only for the sessions view).
            Canvas(modifier = Modifier.matchParentSize()) {
                if (goalFraction > 0f && goalFraction <= 1f) {
                    val y = this.size.height - barMax.toPx() * goalFraction
                    drawLine(
                        color = goalLineColor,
                        start = Offset(0f, y),
                        end = Offset(this.size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                }
            }
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                week.forEach { day ->
                    StatsDayBar(
                        day = day,
                        metric = metric,
                        axisMax = axisMax,
                        barMax = barMax,
                        goal = goal,
                        isSelected = selected == day.date,
                        hasSelection = selected != null,
                        onClick = { onSelect(day.date) }
                    )
                }
            }
        }

        // Day names under the bars. Today is highlighted.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            week.forEach { day ->
                Text(
                    text = statsDayName(day.date),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (day.isToday) AccentBlue else TextTertiary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RowScope.StatsDayBar(
    day: WeekDay,
    metric: PomodoroStatsMetric,
    axisMax: Float,
    barMax: Dp,
    goal: Int,
    isSelected: Boolean,
    hasSelection: Boolean,
    onClick: () -> Unit
) {
    val value = when (metric) {
        PomodoroStatsMetric.SESSIONS -> day.sessions.toFloat()
        PomodoroStatsMetric.TIME -> day.focusSeconds.toFloat()
    }
    // Any day with something in it gets at least a small visible bar.
    val fraction = when {
        day.isFuture || value <= 0f -> 0f
        else -> (value / axisMax).coerceIn(0.06f, 1f)
    }
    val barHeight by animateDpAsState(
        targetValue = barMax * fraction,
        animationSpec = statsSpec(),
        label = "statsBar"
    )

    // Sessions view: a day that reached the goal turns teal.
    val baseColor = when {
        metric == PomodoroStatsMetric.TIME -> AccentPurple
        day.sessions >= goal -> AccentTeal
        else -> AccentBlue
    }
    val barAlpha = when {
        hasSelection -> if (isSelected) 1f else 0.4f
        day.isToday -> 1f
        else -> 0.65f
    }
    val trackColor = TextTertiary.copy(alpha = 0.10f)

    val label = when {
        day.isFuture -> ""
        metric == PomodoroStatsMetric.SESSIONS -> if (day.sessions > 0) "${day.sessions}" else ""
        else -> statsCompactTime(day.focusSeconds)
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .pressable(
                enabled = !day.isFuture,
                pressedScale = MotionTokens.PressScaleSmall,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected || day.isToday) TextPrimary else TextSecondary,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .height(barMax)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                .background(trackColor),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(baseColor.copy(alpha = barAlpha))
            )
        }
    }
}

/** Under the chart: details of the tapped day, or a short hint. */
@Composable
private fun StatsChartFooter(selected: WeekDay?, metric: PomodoroStatsMetric, goal: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TextTertiary.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statsLongDate(selected.date),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (selected.sessions == 0 && selected.focusSeconds == 0) {
                        "No focus time"
                    } else {
                        "${statsSessions(selected.sessions)} · ${statsDuration(selected.focusSeconds.toLong())}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Tap a bar for details",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                if (metric == PomodoroStatsMetric.SESSIONS) {
                    Text(
                        text = "Dashed line: your daily goal of $goal",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// EXPANDED PART: WEEK TOTALS AND RECORDS
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun StatsWeekSummary(week: List<WeekDay>, priorWeek: List<WeekDay>) {
    val sessions = week.sumOf { it.sessions }
    val seconds = week.sumOf { it.focusSeconds }
    val activeDays = week.count { it.sessions > 0 }
    val priorSessions = priorWeek.sumOf { it.sessions }
    val priorSeconds = priorWeek.sumOf { it.focusSeconds }
    val hasPrior = priorSessions > 0 || priorSeconds > 0

    val sessionDiff = sessions - priorSessions
    val secondsDiff = seconds - priorSeconds

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatsSummaryTile(
                label = "Sessions",
                value = "$sessions",
                deltaSign = if (hasPrior) sessionDiff.coerceIn(-1, 1) else null,
                deltaText = when {
                    sessionDiff > 0 -> "+$sessionDiff"
                    sessionDiff < 0 -> "$sessionDiff"
                    else -> "Same"
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            StatsSummaryTile(
                label = "Focus time",
                value = statsDuration(seconds.toLong()),
                deltaSign = if (hasPrior) secondsDiff.coerceIn(-1, 1) else null,
                deltaText = when {
                    secondsDiff > 0 -> "+${statsDuration(secondsDiff.toLong())}"
                    secondsDiff < 0 -> "-${statsDuration((-secondsDiff).toLong())}"
                    else -> "Same"
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            StatsSummaryTile(
                label = "Active days",
                value = "$activeDays/7",
                deltaSign = null,
                deltaText = "",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        if (hasPrior) {
            Text(
                text = "Arrows compare with the week before",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun StatsSummaryTile(
    label: String,
    value: String,
    deltaSign: Int?,
    deltaText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TextTertiary.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1
        )
        if (deltaSign != null) {
            val tint = when {
                deltaSign > 0 -> AccentGreen
                deltaSign < 0 -> AccentRed
                else -> TextTertiary
            }
            val trendIcon: ImageVector = when {
                deltaSign > 0 -> Icons.AutoMirrored.Filled.TrendingUp
                deltaSign < 0 -> Icons.AutoMirrored.Filled.TrendingDown
                else -> Icons.AutoMirrored.Filled.TrendingFlat
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = trendIcon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = deltaText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun StatsRecords(summary: StatsSummary) {
    val bestDay = summary.bestDay
    val bestDayText = if (bestDay != null) {
        "${statsSessions(summary.bestDaySessions)} · ${bestDay.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}"
    } else {
        "None yet"
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "RECORDS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = TextTertiary
        )
        StatsRecordRow(
            icon = Icons.Filled.LocalFireDepartment,
            color = AccentAmber,
            label = "Current streak",
            value = statsDays(summary.currentStreak)
        )
        StatsRecordRow(
            icon = Icons.Filled.EmojiEvents,
            color = AccentPurple,
            label = "Longest streak",
            value = statsDays(summary.longestStreak)
        )
        StatsRecordRow(
            icon = Icons.Filled.Leaderboard,
            color = AccentTeal,
            label = "Best day",
            value = bestDayText
        )
    }
}

@Composable
private fun StatsRecordRow(
    icon: ImageVector,
    color: Color,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1
        )
    }
}
