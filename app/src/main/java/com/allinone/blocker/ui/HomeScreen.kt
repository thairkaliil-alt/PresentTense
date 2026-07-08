package com.allinone.blocker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.util.Calendar
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.R
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.ScreenTimeTracker
import com.allinone.blocker.data.StreakRepository
import com.allinone.blocker.ui.motion.AnimatedAppearance
import com.allinone.blocker.ui.motion.MotionTokens
import com.allinone.blocker.ui.motion.animatedCountAsState
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// HOME ENTRANCE — "cold open" cascade
//
// The first time Home is shown after the app process starts, its main blocks
// (greeting, screen-time card, shortcuts, quick controls, presets) should
// gently cascade in one after another — a soft "the app just woke up" feel.
// But tapping between bottom-nav tabs (Stats → Home, Lockdown → Home, etc.)
// during the same session should NOT replay it every time.
//
// Bottom-nav tab switching in AppRoot (MainActivity.kt) works by flipping a
// `screen` state and re-entering this composable's branch of a `when` block —
// which disposes and rebuilds HomeScreen's composition on every switch. A
// plain `remember { }` living inside HomeScreen would therefore reset (and
// replay the entrance) on every tab switch, since a fresh composition means a
// fresh `remember`. To survive that, "have we already played the entrance
// this app session" is tracked in a plain top-level object instead of
// composition state — it only resets when the process actually dies, i.e. a
// real cold start.
//
// This mirrors the "once per X" pattern StreaksScreen already uses for its
// flame entrance (see ENTRANCE_PREFS there) — same idea, adapted: Streaks
// persists "once per calendar day" via SharedPreferences (it needs to survive
// an app kill), while Home only needs "once per process", since relaunching
// the app IS itself a fresh "waking up" moment worth re-playing.
// ─────────────────────────────────────────────────────────────────────────────
private object HomeEntranceState {
    var played = false
}

// ─────────────────────────────────────────────────────────────────────────────
// HOME SCREEN-TIME CACHE — survives Home being disposed/rebuilt on tab switch
//
// Home's screen-time numbers are loaded on a background thread (see the
// LaunchedEffect in HomeScreen below) so they never block the tab-switch
// animation. Without this cache, every fresh visit to Home would briefly show
// "0m" while the background load ran, even though the previous numbers were
// already known — a visible flicker. Stashing the last-loaded values here
// (same "outlives composition" trick as HomeEntranceState above) means Home
// shows the last real numbers instantly, then quietly refreshes them.
// ─────────────────────────────────────────────────────────────────────────────
private object HomeScreenTimeCache {
    var totalMinutes = 0
    var yesterdayMinutes = 0
    var goalMinutes = 0
}

/**
 * Wraps [content] in the shared [AnimatedAppearance] entrance, staggered by
 * [index] using the same [MotionTokens.StaggerStepMs] timing as the rest of
 * the app (see StatsScreen/SleepCalculatorScreen for the same pattern) — but
 * only while [animate] is true. Once the entrance has played for this Home
 * composition, callers pass `animate = false` and content renders instantly,
 * with no wrapper at all.
 */
@Composable
private fun HomeEntranceSection(
    index: Int,
    animate: Boolean,
    content: @Composable () -> Unit
) {
    if (animate) {
        AnimatedAppearance(delayMs = index * MotionTokens.StaggerStepMs) { content() }
    } else {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    refreshKey: Int,
    onPermissions: () -> Unit,
    onOpenBlockedApps: () -> Unit,
    onOpenBlockedWebsites: () -> Unit,
    onOpenPresets: () -> Unit,
    onSettings: () -> Unit,
    onAlarmClick: () -> Unit = {},
    onPomodoroClick: () -> Unit = {},
    onOpenStreaks: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val apps     by BlockerRepository.apps.collectAsState()
    val websites by BlockerRepository.websites.collectAsState()
    val reels    by BlockerRepository.reelsKillSwitch.collectAsState()

    val streak      by StreakRepository.streak.collectAsState()
    val brokenToday by StreakRepository.brokenToday.collectAsState()

    // PERF FIX (tab-switch stutter): this used to call ScreenTimeTracker.reconcile()
    // and read the database directly inside remember(refreshKey) — which runs
    // during composition, on the main/UI thread. reconcile() talks to Android's
    // UsageStatsManager (a cross-process call) and writes to SQLite, both of
    // which take real time. Doing that synchronously meant the ENTIRE screen —
    // including the tab-switch animation — was stuck waiting for it to finish
    // before it could even start drawing. That's what caused the visible pause
    // every time Home reloaded (returning from another tab, resuming the app).
    //
    // Fix: do the same work in a coroutine on Dispatchers.IO (background thread)
    // instead, so composition + the animation can proceed immediately. The
    // small HomeScreenTimeCache below remembers the last values that were
    // loaded so returning to Home shows real numbers right away instead of
    // flashing "0" while the fresh numbers load in the background.
    var totalScreenMinutes     by remember { mutableIntStateOf(HomeScreenTimeCache.totalMinutes) }
    var yesterdayScreenMinutes by remember { mutableIntStateOf(HomeScreenTimeCache.yesterdayMinutes) }
    var dailyGoalMinutes       by remember { mutableIntStateOf(HomeScreenTimeCache.goalMinutes) }

    LaunchedEffect(refreshKey) {
        val (today, yesterday, goal) = withContext(Dispatchers.IO) {
            // Force a reconcile so the cache is warm on every home visit —
            // same behavior as before, just off the main thread now.
            ScreenTimeTracker.reconcile(context, force = true)
            Triple(
                ScreenTimeTracker.todayTotalMinutes(context),
                ScreenTimeTracker.yesterdayTotalMinutes(context),
                BlockerRepository.dailyGoalMinutes()
            )
        }
        totalScreenMinutes = today
        yesterdayScreenMinutes = yesterday
        dailyGoalMinutes = goal
        HomeScreenTimeCache.totalMinutes = today
        HomeScreenTimeCache.yesterdayMinutes = yesterday
        HomeScreenTimeCache.goalMinutes = goal
    }
    val onReelsChange: (Boolean) -> Unit = remember {
        { BlockerRepository.setReelsKillSwitch(it) }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Snapshot "has the entrance already played this session" once, the first
    // time this composition appears. See HomeEntranceState above for why this
    // lives outside `remember` state.
    val shouldAnimateEntrance = remember { !HomeEntranceState.played }
    LaunchedEffect(Unit) {
        HomeEntranceState.played = true
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                onAlarmClick = {
                    scope.launch {
                        drawerState.close()
                        onAlarmClick()
                    }
                },
                onPomodoroClick = {
                    scope.launch {
                        drawerState.close()
                        onPomodoroClick()
                    }
                },
                onSettingsClick = {
                    scope.launch {
                        drawerState.close()
                        onSettings()
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Open menu",
                                    tint = TextPrimary
                                )
                            }
                            StreakBadge(
                                streak = streak,
                                brokenToday = brokenToday,
                                onClick = onOpenStreaks
                            )
                        }
                    },
                    title = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Home",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    },
                    actions = {
                        ThemeToggleSwitch(
                            checked = isDarkTheme,
                            onCheckedChange = onThemeToggle,
                            trackWidth = 38.dp,
                            trackHeight = 21.dp,
                            modifier = Modifier.padding(end = 16.dp)
                        )
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                HomeEntranceSection(index = 0, animate = shouldAnimateEntrance) {
                    GreetingHeader(userName = "Arthur")
                }

                HomeEntranceSection(index = 1, animate = shouldAnimateEntrance) {
                    ScreenTimeCard(
                        totalMinutes = totalScreenMinutes,
                        yesterdayMinutes = yesterdayScreenMinutes,
                        goalMinutes = dailyGoalMinutes,
                        streak = streak,
                        brokenToday = brokenToday,
                        onShowStats = onOpenStats,
                        onStreakClick = onOpenStreaks
                    )
                }

                HomeEntranceSection(index = 2, animate = shouldAnimateEntrance) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeSectionHeader(text = "Shortcuts")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ShortcutCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Filled.Apps,
                                count = apps.size,
                                label = "Blocked Apps",
                                accentColor = AccentBlue,
                                onClick = onOpenBlockedApps
                            )
                            ShortcutCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Filled.Language,
                                count = websites.size,
                                label = "Blocked Websites",
                                accentColor = AccentBlue,
                                onClick = onOpenBlockedWebsites
                            )
                        }
                    }
                }

                HomeEntranceSection(index = 3, animate = shouldAnimateEntrance) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeSectionHeader(text = "Quick Controls")
                        ToggleCard(
                            title = "Reels & Shorts",
                            subtitle = "Instantly block Instagram, YouTube & TikTok feeds",
                            checked = reels,
                            accentColor = AccentRed,
                            icon = Icons.Filled.VideocamOff,
                            onChange = onReelsChange
                        )
                    }
                }

                // The 12 preset cards used to live inline right here. They now
                // live on their own screen (PresetsScreen.kt), opened by
                // tapping this single row — one entry point instead of 12
                // cards competing for attention on first load.
                HomeEntranceSection(index = 4, animate = shouldAnimateEntrance) {
                    QuickStartPresetsCard(onClick = onOpenPresets)
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QUICK START PRESETS CARD
//
// Collapses what used to be 12 inline preset cards on Home into one row.
// Tapping it opens PresetsScreen, where all 12 presets live now. Styled to
// match the existing "Blocked Apps" / "Blocked Websites" shortcut cards
// (same card surface, corner shape, icon-chip treatment) so it reads as part
// of the same family, just full-width.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuickStartPresetsCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentBlue.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quick start a preset",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "12 ready-made blocking modes",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextTertiary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STREAK BADGE (top bar)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StreakBadge(
    streak: Int,
    brokenToday: Boolean,
    onClick: () -> Unit
) {
    val flameColor = if (brokenToday && streak == 0) TextTertiary else AccentAmber

    val numScale = remember { Animatable(1f) }
    val streakVersion = remember { mutableIntStateOf(0) }
    LaunchedEffect(streak) {
        streakVersion.intValue++
        if (streakVersion.intValue > 1) {
            numScale.snapTo(1.4f)
            numScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                )
            )
        }
    }

    Row(
        modifier = Modifier
            .padding(start = 2.dp, end = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(flameColor.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AppFlame(
            modifier = Modifier.size(18.dp),
            desaturated = brokenToday && streak == 0,
            pulseKey = streak
        )
        Text(
            text = "$streak",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = flameColor,
            modifier = Modifier.scale(numScale.value)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SIDE DRAWER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppDrawerContent(onSettingsClick: () -> Unit, onAlarmClick: () -> Unit, onPomodoroClick: () -> Unit) {
    ModalDrawerSheet(
        drawerContainerColor = BgDarkest,
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp, horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Menu",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f))
            Spacer(Modifier.height(20.dp))
            DrawerItem(icon = Icons.Filled.AlarmOn, label = "Strict Alarm", onClick = onAlarmClick)
            Spacer(Modifier.height(8.dp))
            DrawerItem(icon = Icons.Filled.Timer, label = "Pomodoro", onClick = onPomodoroClick)
            Spacer(Modifier.height(8.dp))
            DrawerItem(icon = Icons.Filled.Settings, label = "Settings",     onClick = onSettingsClick)
        }
    }
}

@Composable
fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
    }
}

@Composable
fun HomeSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN TIME HERO CARD
//
// Three fixes applied here vs. previous version:
//
// FIX 1 — STREAK: Always show the streak row. When streak == 0 it shows
//   "Day 1 – keep it up!" instead of being invisible. The top-bar badge
//   already hides contextually; the card should always give feedback.
//
// FIX 2 — "Show me →": Promoted from labelMedium to bodyMedium + SemiBold
//   with a solid tinted pill background so it reads as a real tappable
//   element, not just small text.
//
// FIX 3 — BAR: When totalMinutes == 0 the bar now shows a faint shimmer
//   "ghost" fill (15% of bar width) so it never looks broken. A "No usage
//   recorded yet" sub-label appears below to explain why. The bar itself
//   also animates in on composition so the user sees it load.
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// TrendPill — small "you're doing better than yesterday" badge.
//
// Design notes (why it looks the way it does):
//  - Capsule shape with a soft tinted background (10% accent fill) instead of
//    a bare icon floating in space. This is the same pattern Apple Health,
//    Oura, and Headspace use for small positive-trend indicators — a single
//    self-contained "chip" reads as one calm object, not three loose elements.
//  - The arrow is hand-drawn with Canvas instead of using Android's stock
//    KeyboardArrowDown glyph. The stock chevron is a generic UI affordance
//    (used for "expand/collapse" everywhere) and looks cheap here. Instead
//    it's a small curved "sparkline" swoop with an arrowhead at the end —
//    the same soft, curved trend-line motif used by stock/market tickers
//    and health apps (Robinhood, Apple Stocks, Oura), which reads as more
//    alive and premium than a straight ruler-line diagonal.
//  - Kept deliberately tiny (10dp arrow, compact text) and low-contrast
//    background so it never competes with the big number for attention —
//    it's a quiet confidence boost, not a banner.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TrendPill(percent: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AccentBlue.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TrendDownArrow(
            color = AccentBlue,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = "$percent% better than yesterday",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = AccentBlue
        )
    }
}

@Composable
private fun TrendDownArrow(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.14f
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // A gentle curved "sparkline" swoop instead of a straight diagonal —
        // it dips up slightly first, then descends, the same soft S-curve
        // shape used for small trend glyphs in stock/finance and health apps.
        val start = Offset(size.width * 0.10f, size.height * 0.34f)
        val control1 = Offset(size.width * 0.40f, size.height * 0.04f)
        val control2 = Offset(size.width * 0.58f, size.height * 0.58f)
        val end = Offset(size.width * 0.86f, size.height * 0.82f)

        val curve = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)
        }
        drawPath(path = curve, color = color, style = stroke)

        // Small arrowhead at the end, angled to match the direction the
        // curve is actually travelling in (rather than a fixed diagonal),
        // so it looks like a natural continuation of the swoop.
        val exitAngle = atan2(end.y - control2.y, end.x - control2.x)
        val headLength = size.minDimension * 0.30f
        val headSpread = 0.52f // radians, ~30° each side — a narrow, sharp head

        val leftWing = Offset(
            end.x - headLength * cos(exitAngle - headSpread),
            end.y - headLength * sin(exitAngle - headSpread)
        )
        val rightWing = Offset(
            end.x - headLength * cos(exitAngle + headSpread),
            end.y - headLength * sin(exitAngle + headSpread)
        )

        val arrowHead = Path().apply {
            moveTo(leftWing.x, leftWing.y)
            lineTo(end.x, end.y)
            lineTo(rightWing.x, rightWing.y)
        }
        drawPath(path = arrowHead, color = color, style = stroke)
    }
}

@Composable
fun ScreenTimeCard(
    totalMinutes: Int,
    yesterdayMinutes: Int = 0,
    goalMinutes: Int,
    streak: Int,
    brokenToday: Boolean,
    onShowStats: () -> Unit,
    onStreakClick: () -> Unit
) {
    val hasGoal = goalMinutes > 0

    // Smoothly counts toward totalMinutes instead of snapping straight to it.
    // Home now opens instantly on cached numbers and loads the fresh ones in
    // the background (see HomeScreen above) — when those fresh numbers land,
    // this makes the headline number visibly tick up/down to the new value,
    // and the color/label/progress-bar below ride along with it, instead of
    // everything just popping to a new state.
    val displayMinutes by animatedCountAsState(totalMinutes)

    val usageRatio by animateFloatAsState(
        targetValue = if (hasGoal) (displayMinutes.toFloat() / goalMinutes.toFloat()).coerceIn(0f, 1f)
                      else (displayMinutes / 180f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "usageBar"
    )

    val noData = displayMinutes == 0

    // Previously this color changed with usage (amber near/over goal, teal
    // under threshold, blue in between) to encode urgency. As part of the
    // Home screen color pass, this is now a single consistent accent —
    // see the "what to test" notes for a flag on this tradeoff.
    val accentColor: Color = AccentBlue

    val mutedColor = Color(0xFF8A8FA8)

    // Single inline label sitting just below the big number
    val statusLabel = when {
        noData                                       -> "No data yet"
        hasGoal && displayMinutes >= goalMinutes      -> "Goal reached"
        hasGoal                                      -> "${goalMinutes - displayMinutes} min left"
        displayMinutes < 60                           -> "Light day"
        displayMinutes in 60..179                     -> "Moderate"
        else                                          -> "Heavy day"
    }

    // Percentage of the actual saved goal (0 = no goal set)
    val goalPercent = if (hasGoal) ((displayMinutes.toFloat() / goalMinutes.toFloat()) * 100).toInt().coerceAtMost(100) else 0

    // Quiet positive-only comparison vs. yesterday. Only ever shows improvement -
    // if today is equal to or higher than yesterday, this is simply null and the
    // pill doesn't render. No red state, no "worse than yesterday" message ever.
    val improvementPercent: Int? = if (yesterdayMinutes > 0 && displayMinutes < yesterdayMinutes) {
        (((yesterdayMinutes - displayMinutes).toFloat() / yesterdayMinutes.toFloat()) * 100)
            .toInt()
            .coerceIn(1, 99)
    } else null
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {

            // ── 1. Small section label + quiet positive comparison ────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Screen time today",
                    style = MaterialTheme.typography.labelMedium,
                    color = mutedColor
                )

                if (improvementPercent != null) {
                    TrendPill(percent = improvementPercent)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── 2. Big number — the only hero element ─────────────────────
            Text(
                text = if (noData) "–" else formatMinutes(displayMinutes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (noData) mutedColor else accentColor
            )

            // ── 3. Status + goal — one quiet line below the number ────────
            Text(
                text = if (noData) "Tracking will start shortly"
                      else if (hasGoal) "$statusLabel · $goalPercent% of goal"
                       else statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor
            )

            Spacer(Modifier.height(14.dp))

            // ── 4. Thin progress bar — 4dp, solid colour, no gradient ─────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (noData) 0f else usageRatio)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── 5. Bottom row: streak (left) + Details link (right) ───────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Streak — tappable, always visible
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onStreakClick)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    AppFlame(
                        modifier = Modifier.size(14.dp),
                        flicker = false,
                        desaturated = brokenToday && streak == 0
                    )
                    Text(
                        text = when {
                            streak == 0 && !brokenToday -> "Start your streak"
                            streak == 0 && brokenToday  -> "Streak lost"
                            streak == 1                 -> "1 day streak"
                            else                        -> "$streak day streak"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (brokenToday && streak == 0) mutedColor else AccentAmber
                    )
                }

                // Details link — plain text, no pill/background
                Text(
                    text = "Details →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = accentColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onShowStats)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHORTCUT CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ShortcutCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    count: Int,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(0.9f),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "$count",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOGGLE CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    accentColor: Color,
    icon: ImageVector,
    onChange: (Boolean) -> Unit,
    useSunMoonSwitch: Boolean = false
) {
    val cardBg by animateColorAsState(
        targetValue = if (checked) accentColor.copy(alpha = 0.18f) else CardSurface,
        animationSpec = tween(350),
        label = "toggleBg"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) accentColor else TextTertiary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title,    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,  color = TextSecondary)
            }
            if (useSunMoonSwitch) {
                ThemeToggleSwitch(checked = checked, onCheckedChange = onChange)
            } else {
                Switch(
                    checked = checked,
                    onCheckedChange = onChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor    = Color.White,
                        checkedTrackColor    = accentColor,
                        checkedBorderColor   = accentColor,
                        uncheckedThumbColor  = TextTertiary,
                        uncheckedTrackColor  = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = TextTertiary
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GREETING HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GreetingHeader(userName: String) {
    val hour      = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val dayOfYear = remember { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) }

    val (greetingPrefix, showName) = remember(hour) {
        when (hour) {
            in 5..11  -> Pair("Good morning,",   true)
            in 12..16 -> Pair("Good afternoon,", true)
            in 17..20 -> Pair("Good evening,",   true)
            else      -> Pair("Working late, are we?", false)
        }
    }

    val followUpQuestion: String? = remember(hour, dayOfYear) {
        when (hour) {
            in 5..11  -> listOf("How are we doing today?", "What are we working on today?")[dayOfYear % 2]
            in 12..20 -> listOf("How was your day so far?", "Everything going according to plan?")[dayOfYear % 2]
            else      -> null
        }
    }

    val greetingText = buildAnnotatedString {
        withStyle(SpanStyle(color = TextPrimary)) { append(greetingPrefix) }
        if (showName) {
            append(" ")
            withStyle(SpanStyle(color = AccentBlue)) { append(userName) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = greetingText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            lineHeight = MaterialTheme.typography.headlineMedium.fontSize * 1.1
        )
        if (followUpQuestion != null) {
            Text(
                text = followUpQuestion,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Normal,
                color = TextSecondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────

private fun formatMinutes(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}
