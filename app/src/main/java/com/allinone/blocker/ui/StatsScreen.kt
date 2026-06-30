package com.allinone.blocker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.draw.blur
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.ScreenTimeTracker
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import com.allinone.blocker.ui.motion.AnimatedAppearance
import com.allinone.blocker.ui.motion.AnimatedCount
import com.allinone.blocker.ui.motion.LocalReducedMotion
import com.allinone.blocker.ui.motion.MotionTokens
import com.allinone.blocker.ui.motion.animatedCountAsState
import com.allinone.blocker.ui.motion.pressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

// True sinusoidal ease — velocity is zero at both ends, so every oscillation
// glides in and out with no visible "stop and reverse" jolt. Compose has no
// built-in SineEasing; this mirrors the same private helper already defined
// in AppFlame.kt (same formula, kept identical on purpose) since each file
// needs its own copy — Kotlin `private` top-level declarations aren't shared
// across files even within the same package.
private val SineEasing = Easing { fraction ->
    ((1f - cos(fraction * Math.PI).toFloat()) / 2f)
}

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

data class AppUsageStat(
    val packageName: String,
    val appName: String,
    val todayMillis: Long,
    val blockedAttempts: Int = 0,
    val streakDays: Int = 0
)

// Per-domain (per-website) usage — what the "Browsing Time" tab actually shows.
// Populated from ScreenTimeTracker.domainStatsToday() which reads the
// domain_daily_totals table written by the accessibility service.
data class DomainUsageStat(
    val domain: String,
    val todayMillis: Long,
    val blockedAttempts: Int = 0
)

private val BROWSER_PACKAGES = setOf(
    "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
    "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
    "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
    "com.brave.browser", "com.brave.browser_beta", "com.duckduckgo.mobile.android",
    "com.UCMobile.intl", "com.uc.browser.en", "com.kiwibrowser.browser", "com.vivaldi.browser",
    "com.sec.android.app.sbrowser", "org.bromite.bromite",
    "com.stoutner.privacybrowser.standard", "io.github.forkmaintainers.iceraven",
    "com.yandex.browser"
)

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var totalTodayMinutes    by remember { mutableIntStateOf(0) }
    var appStats             by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }
    var browserStats         by remember { mutableStateOf<List<AppUsageStat>>(emptyList()) }
    var domainStats          by remember { mutableStateOf<List<DomainUsageStat>>(emptyList()) }
    var weeklyMinutes        by remember { mutableStateOf<List<Int>>(emptyList()) }
    var hourlyMillis         by remember { mutableStateOf<List<Long>>(emptyList()) }
    var totalBlockedToday    by remember { mutableIntStateOf(0) }
    var topBlockedApp        by remember { mutableStateOf<AppUsageStat?>(null) }

    var loading              by remember { mutableStateOf(true) }
    var selectedTab          by remember { mutableIntStateOf(0) }
    var goalMinutes           by remember { mutableIntStateOf(BlockerRepository.dailyGoalMinutes()) }
    var showGoalDialog        by remember { mutableStateOf(false) }

    // BUGFIX (scroll flicker): rows inside a LazyColumn are destroyed when they
    // scroll off-screen and rebuilt from scratch when they scroll back into
    // view. AnimatedAppearance plays its fade-in/slide-in "first appearance"
    // animation every single time a row is rebuilt — which is exactly what
    // made the app list look like it kept disappearing and reappearing while
    // scrolling. This flag is remembered once at the StatsScreen level (not
    // per-row), so it survives rows being recycled. The entrance animation
    // plays only once, right after the real data loads — after that, every
    // row (even ones scrolling back into view) renders instantly, no replay.
    var entranceAnimationPlayed by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            ScreenTimeTracker.reconcile(context, force = true)

            val weekly        = ScreenTimeTracker.weeklyTotals(context, 7)
            val todayMap      = weekly.values.firstOrNull() ?: emptyMap()
            val blockedMap    = ScreenTimeTracker.blockedAttemptsToday(context)
            val hourly        = ScreenTimeTracker.hourlyTotalsToday(context)

            totalTodayMinutes = (todayMap.values.sum() / 60_000L).toInt()
            totalBlockedToday = blockedMap.values.sum()
            hourlyMillis      = hourly

            val blockedPkgs = BlockerRepository.apps.value.associate { it.packageName to it.appName }
            val allPkgs     = (blockedPkgs.keys + todayMap.keys + blockedMap.keys).toSet()

            val rawStats = allPkgs.mapNotNull { pkg ->
                val millis   = todayMap[pkg] ?: 0L
                val attempts = blockedMap[pkg] ?: 0
                if (millis <= 0L && attempts == 0) return@mapNotNull null
                val name = blockedPkgs[pkg] ?: InstalledApps.labelFor(context, pkg)
                AppUsageStat(pkg, name, millis, attempts, 0)
            }.sortedByDescending { it.todayMillis }

            val blockedPkgList = rawStats.filter { it.packageName in blockedPkgs }.map { it.packageName }
            val streaks = ScreenTimeTracker.streaksForPackages(context, blockedPkgList)

            val allStats = rawStats.map { it.copy(streakDays = streaks[it.packageName] ?: 0) }

            browserStats  = allStats.filter { it.packageName in BROWSER_PACKAGES }
            appStats      = allStats.filter { it.packageName !in BROWSER_PACKAGES }
            weeklyMinutes = weekly.values.map { dayMap -> (dayMap.values.sumOf { it } / 60_000L).toInt() }

            // Build per-domain stats — the actual sites visited, not just the browser app.
            // domainStatsToday() reads from the domain_daily_totals table that
            // the accessibility service writes to via onDomainChanged() every time
            // the URL bar changes. This is what shows "youtube.com: 23m" instead
            // of just "Chrome: 23m".
            val rawDomainMillis = ScreenTimeTracker.domainStatsToday(context)
            val domainBlockedMap = ScreenTimeTracker.domainBlockedAttemptsToday(context)
            domainStats = (rawDomainMillis.keys + domainBlockedMap.keys)
                .toSet()
                .mapNotNull { domain ->
                    val millis   = rawDomainMillis[domain] ?: 0L
                    val attempts = domainBlockedMap[domain] ?: 0
                    if (millis <= 0L && attempts == 0) return@mapNotNull null
                    DomainUsageStat(domain = domain, todayMillis = millis, blockedAttempts = attempts)
                }
                .sortedByDescending { it.todayMillis }

            topBlockedApp = allStats.filter { it.blockedAttempts > 0 }
                .maxByOrNull { it.blockedAttempts }

            loading = false
        }
        // Entrance animation should run exactly once, right after this first
        // load. Setting it true here (outside the IO block, after loading
        // flips) means later recompositions — like scrolling rows in and out
        // of view — see entranceAnimationPlayed == true and skip the animation.
        entranceAnimationPlayed = true
    }

    Scaffold(
        containerColor = BgDarkest,
        topBar = {
            TopAppBar(
                title = { Text("Screen time", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDarkest)
            )
        }
    ) { pad ->
        if (loading) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading stats…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
            return@Scaffold
        }

        // ── FIX: Every section is its own top-level lazy item so the list
        // virtualises correctly. App rows are individual items — not nested
        // inside a single forEach item — so only visible rows are composed.
        // The old code wrapped everything in one giant item{} which meant
        // ALL rows were always composed, causing the scroll jank.
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 0.dp,
                end = 16.dp,
                bottom = 88.dp   // clears the bottom nav bar (~80dp) plus breathing room
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "spacer_top") { Spacer(Modifier.height(8.dp)) }

            // ── 1. Today's total card ────────────────────────────────────────
            item(key = "today_total") {
                if (!entranceAnimationPlayed) {
                    AnimatedAppearance(delayMs = 0) {
                        TodayTotalCard(
                            totalMinutes = totalTodayMinutes,
                            topStreakDays = appStats.map { it.streakDays }.maxOrNull() ?: 0,
                            goalMinutes = goalMinutes,
                            onEditGoal = { showGoalDialog = true }
                        )
                    }
                } else {
                    TodayTotalCard(
                        totalMinutes = totalTodayMinutes,
                        topStreakDays = appStats.map { it.streakDays }.maxOrNull() ?: 0,
                        goalMinutes = goalMinutes,
                        onEditGoal = { showGoalDialog = true }
                    )
                }
            }

            // ── 2. Blocked attempts summary ──────────────────────────────────
            if (totalBlockedToday > 0 || topBlockedApp != null) {
                item(key = "blocked_attempts") {
                    if (!entranceAnimationPlayed) {
                        AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs) {
                            BlockedAttemptsCard(
                                totalBlocked = totalBlockedToday,
                                topApp = topBlockedApp
                            )
                        }
                    } else {
                        BlockedAttemptsCard(
                            totalBlocked = totalBlockedToday,
                            topApp = topBlockedApp
                        )
                    }
                }
            }

            // ── 3 & 4. Hourly + Weekly charts — swipeable side-by-side ───────
            item(key = "chart_pager") {
                if (!entranceAnimationPlayed) {
                    AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs * 2) {
                        ChartPager(
                            hourlyMillis = hourlyMillis,
                            weeklyMinutes = weeklyMinutes
                        )
                    }
                } else {
                    ChartPager(
                        hourlyMillis = hourlyMillis,
                        weeklyMinutes = weeklyMinutes
                    )
                }
            }

            // ── 5. Tab strip ─────────────────────────────────────────────────
            item(key = "tab_strip") {
                val tabStripContent: @Composable () -> Unit = {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor   = CardSurface,
                        contentColor     = AccentBlue,
                        modifier         = Modifier.clip(MaterialTheme.shapes.medium)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick  = { selectedTab = 0 },
                            text = {
                                Text(
                                    "Apps",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) AccentBlue else TextMuted
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick  = { selectedTab = 1 },
                            text = {
                                Text(
                                    "Browsing Time",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) AccentBlue else TextMuted
                                )
                            }
                        )
                    }
                }
                if (!entranceAnimationPlayed) {
                    AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs * 3) { tabStripContent() }
                } else {
                    tabStripContent()
                }
            }

            // ── 6. Per-app stats — FIX: individual lazy items, not forEach
            // inside a single item{}. This is the main fix for scroll lag.
            // Each app row is its own composable that is only created when
            // it scrolls into view, instead of all at once.
            if (selectedTab == 0) {
                if (appStats.isEmpty()) {
                    item(key = "apps_empty") {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                            Text(
                                "No app usage recorded yet today. Come back after using your phone for a bit.",
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                } else {
                    val maxMillis = appStats.first().todayMillis.coerceAtLeast(1L)
                    itemsIndexed(appStats, key = { _, stat -> "app_${stat.packageName}" }) { index, stat ->
                        if (index < 6 && !entranceAnimationPlayed) {
                            // First few rows settle in with a stagger — a list
                            // "long enough to feel alive" cap (README guidance:
                            // keep cascades short or the tail feels slow).
                            // Only happens on the very first composition of the
                            // list (see entranceAnimationPlayed above) — not
                            // every time this row scrolls back into view.
                            AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs * (4 + index)) {
                                AppStatRow(stat = stat, maxMillis = maxMillis)
                            }
                        } else {
                            AppStatRow(stat = stat, maxMillis = maxMillis)
                        }
                    }
                }
            } else {
                // ── Browsing tab: show individual sites, not just the browser app ──
                if (domainStats.isEmpty()) {
                    item(key = "browser_empty") { BrowsingEmptyCard() }
                } else {
                    item(key = "browser_total") { BrowsingTotalCard(domainStats) }
                    val maxMillis = domainStats.first().todayMillis.coerceAtLeast(1L)
                    itemsIndexed(domainStats, key = { _, stat -> "domain_${stat.domain}" }) { index, stat ->
                        if (index < 6 && !entranceAnimationPlayed) {
                            AnimatedAppearance(delayMs = MotionTokens.StaggerStepMs * (4 + index)) {
                                DomainStatRow(stat = stat, maxMillis = maxMillis)
                            }
                        } else {
                            DomainStatRow(stat = stat, maxMillis = maxMillis)
                        }
                    }
                }
            }

            item(key = "spacer_bottom") { Spacer(Modifier.height(16.dp)) }
        }

        // ── Daily screen-time goal dialog — opened by tapping the hero card ──
        if (showGoalDialog) {
            GoalDialog(
                currentGoalMinutes = goalMinutes,
                onDismiss = { showGoalDialog = false },
                onSave = { newGoal ->
                    BlockerRepository.setDailyGoalMinutes(newGoal)
                    goalMinutes = newGoal
                    showGoalDialog = false
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Today total + daily goal card  (redesigned hero card)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TodayTotalCard(
    totalMinutes: Int,
    topStreakDays: Int,
    goalMinutes: Int,
    onEditGoal: () -> Unit
) {
    val hasGoal = goalMinutes > 0
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }

    // Display-only rolling number — purely cosmetic. Every threshold, color,
    // and status-line decision below still uses the real `totalMinutes`
    // directly, so the ring's color and copy can never lag behind the truth;
    // only the printed number itself eases toward it.
    val displayMinutes by animatedCountAsState(totalMinutes)

    // ── Arc fill ──────────────────────────────────────────────────────────
    // With a goal set: the ring tracks % of the goal used (the thing people
    // actually came here to check). Without a goal: falls back to the old
    // "progress through the waking day" behaviour so nothing changes for
    // someone who hasn't set one up yet.
    val rawFraction = if (hasGoal) {
        totalMinutes.toFloat() / goalMinutes.toFloat()
    } else {
        val wakingMinutesSoFar = ((currentHour - 6).coerceAtLeast(0) * 60).toFloat()
        wakingMinutesSoFar / (18 * 60f)
    }
    val fraction = rawFraction.coerceIn(0f, 1f)

    val animatedFraction by animateFloatAsState(
        targetValue   = fraction,
        animationSpec = tween(durationMillis = 900, easing = EaseOutCubic),
        label = "arcFraction"
    )

    // ── Living-ring motion ───────────────────────────────────────────────
    // A handful of small "wisp" lights drift along the filled portion of the
    // arc, wobbling slightly in angle and bulging in/out past the stroke —
    // the "energy not fully contained" feel. Each channel uses its own
    // duration (mirroring AppFlame's breathe/sway/rise pattern) so the wisps
    // never move in lockstep.
    //
    // IMPORTANT: the animateFloat calls below always run, every recomposition,
    // in the same fixed order — Compose requires that. Respecting the OS
    // "remove animations" setting is handled separately, by multiplying the
    // resulting motion by zero (motionScale) rather than skipping the calls.
    val reducedMotion = LocalReducedMotion.current
    val motionScale = if (reducedMotion) 0f else 1f
    val ringMotion = rememberInfiniteTransition(label = "ringEnergy")

    // Seven wisps, evenly spread along the filled arc to start.
    val wispBaseFractions = remember { listOf(0.08f, 0.22f, 0.38f, 0.52f, 0.66f, 0.80f, 0.94f) }
    val wispAngleDurations = remember { listOf(2600, 3100, 2300, 3700, 2900, 3300, 2100) }
    val wispLeakDurations  = remember { listOf(3600, 4300, 3200, 5100, 4000, 4600, 2900) }

    val wispAngle0 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispAngleDurations[0], easing = SineEasing), RepeatMode.Reverse), label = "wA0")
    val wispAngle1 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispAngleDurations[1], easing = SineEasing), RepeatMode.Reverse), label = "wA1")
    val wispAngle2 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispAngleDurations[2], easing = SineEasing), RepeatMode.Reverse), label = "wA2")
    val wispAngle3 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispAngleDurations[3], easing = SineEasing), RepeatMode.Reverse), label = "wA3")
    val wispAngle4 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispAngleDurations[4], easing = SineEasing), RepeatMode.Reverse), label = "wA4")
    val wispAngle5 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispAngleDurations[5], easing = SineEasing), RepeatMode.Reverse), label = "wA5")
    val wispAngle6 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispAngleDurations[6], easing = SineEasing), RepeatMode.Reverse), label = "wA6")
    val wispAngleOffsets = listOf(wispAngle0, wispAngle1, wispAngle2, wispAngle3, wispAngle4, wispAngle5, wispAngle6)

    val wispLeak0 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispLeakDurations[0], easing = SineEasing), RepeatMode.Reverse), label = "wL0")
    val wispLeak1 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispLeakDurations[1], easing = SineEasing), RepeatMode.Reverse), label = "wL1")
    val wispLeak2 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispLeakDurations[2], easing = SineEasing), RepeatMode.Reverse), label = "wL2")
    val wispLeak3 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispLeakDurations[3], easing = SineEasing), RepeatMode.Reverse), label = "wL3")
    val wispLeak4 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispLeakDurations[4], easing = SineEasing), RepeatMode.Reverse), label = "wL4")
    val wispLeak5 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispLeakDurations[5], easing = SineEasing), RepeatMode.Reverse), label = "wL5")
    val wispLeak6 by ringMotion.animateFloat(-1f, 1f, infiniteRepeatable(tween(wispLeakDurations[6], easing = SineEasing), RepeatMode.Reverse), label = "wL6")
    val wispLeakOffsets = listOf(wispLeak0, wispLeak1, wispLeak2, wispLeak3, wispLeak4, wispLeak5, wispLeak6)

    // A slow, gentle pulse behind the flame — the "contained energy" glow.
    val corePulse by ringMotion.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = SineEasing), RepeatMode.Reverse),
        label = "corePulse"
    )

    // Arc colour: with a goal, teal (on track) → amber (getting close) → red
    // (over the goal) — a clear signal reserved for when it's actually true,
    // not just because the day is getting long. Without a goal, the old
    // teal → blue → amber-by-time-of-day behaviour is unchanged.
    val arcColor: Color = when {
        hasGoal && rawFraction >= 1f    -> AccentRed
        hasGoal && rawFraction >= 0.7f  -> AccentAmber
        hasGoal                         -> AccentTeal
        rawFraction >= 0.75f            -> AccentAmber
        rawFraction >= 0.40f            -> AccentBlue
        else                            -> AccentTeal
    }

    val minutesOverGoal = totalMinutes - goalMinutes

    // Status line: goal-aware copy takes over once a goal exists.
    val contextLine: String = when {
        hasGoal && minutesOverGoal > 0  -> "Over your goal"
        hasGoal && totalMinutes == 0    -> "No usage yet"
        hasGoal                          -> "${formatMinutes(goalMinutes - totalMinutes)} left today"
        totalMinutes == 0               -> "No usage yet"
        totalMinutes < 60                -> "Light day so far"
        totalMinutes in 60..179          -> "Moderate usage"
        else                              -> "Heavy day"
    }

    // Nudge: goal-aware once a goal exists, otherwise the old heavy-day nudge.
    val showNudge = if (hasGoal) minutesOverGoal > 0 else totalMinutes >= 180
    val nudgeText = if (hasGoal) "${formatMinutes(minutesOverGoal)} over — consider a break" else "Consider a break soon"

    Card(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onEditGoal),
        shape  = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {

            // ── Top label + goal pill ───────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TODAY'S SCREEN TIME",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = TextMuted
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (hasGoal) AccentBlue.copy(alpha = 0.12f)
                            else AccentTeal.copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (hasGoal) "Goal: ${formatMinutes(goalMinutes)}" else "Set a goal",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (hasGoal) AccentBlue else AccentTeal
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Main row: arc on the left, text on the right ──────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // ── Left: arc ring with flame inside ──────────────────────
                val arcSize = 120.dp
                Box(
                    modifier = Modifier
                        .size(arcSize)
                        .drawBehind {
                            val strokeWidth = 8.dp.toPx()
                            val inset       = strokeWidth / 2f
                            val arcRect     = androidx.compose.ui.geometry.Rect(
                                left   = inset,
                                top    = inset,
                                right  = size.width  - inset,
                                bottom = size.height - inset
                            )
                            drawArc(
                                color      = arcColor.copy(alpha = 0.10f),
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter  = false,
                                topLeft    = arcRect.topLeft,
                                size       = arcRect.size,
                                style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color      = arcColor,
                                startAngle = 135f,
                                sweepAngle = 270f * animatedFraction,
                                useCenter  = false,
                                topLeft    = arcRect.topLeft,
                                size       = arcRect.size,
                                style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )

                            // ── Living-ring wisps ─────────────────────────
                            // Small soft lights drifting along the filled
                            // arc — wobbling in angle, bulging slightly past
                            // the stroke radius, fading in and out. Reads as
                            // "energy moving, not fully contained" rather
                            // than a flat, static fill.
                            if (animatedFraction > 0.02f) {
                                val ringRadius = (size.width - strokeWidth) / 2f
                                val centerOffset = androidx.compose.ui.geometry.Offset(
                                    size.width / 2f,
                                    size.height / 2f
                                )
                                val filledSweep = 270f * animatedFraction

                                for (i in wispBaseFractions.indices) {
                                    val baseFrac = wispBaseFractions[i]
                                    if (baseFrac > animatedFraction) continue // wisp hasn't been "reached" yet

                                    val angleWobbleDeg = wispAngleOffsets[i] * 7f
                                    val angleDeg = 135f + filledSweep * baseFrac + angleWobbleDeg
                                    val angleRad = (angleDeg * (Math.PI / 180f)).toFloat()

                                    val leak = wispLeakOffsets[i] * motionScale
                                    val radiusOffset = leak * (strokeWidth * 0.7f)
                                    val wispRadius = ringRadius + radiusOffset

                                    val wx = centerOffset.x + wispRadius * cos(angleRad)
                                    val wy = centerOffset.y + wispRadius * sin(angleRad)
                                    val wispOffset = androidx.compose.ui.geometry.Offset(wx, wy)

                                    // Breathing opacity, offset per-wisp so they don't blink together.
                                    val opacityWave = (sin(wispAngleOffsets[i] * 3f + i) + 1f) / 2f
                                    val baseAlpha = 0.18f + 0.30f * opacityWave

                                    // Soft halo + bright core — cheap stand-in for a blur.
                                    drawCircle(
                                        color = arcColor.copy(alpha = baseAlpha * 0.4f),
                                        radius = strokeWidth * 0.62f,
                                        center = wispOffset
                                    )
                                    drawCircle(
                                        color = arcColor.copy(alpha = baseAlpha),
                                        radius = strokeWidth * 0.30f,
                                        center = wispOffset
                                    )
                                }
                            }

                            // ── Core pulse ─────────────────────────────────
                            // A slow, gentle glow behind the flame — contained
                            // energy breathing rather than a static fill.
                            val pulseAlpha = (0.08f + 0.07f * corePulse) * motionScale + 0.06f
                            drawCircle(
                                color = arcColor.copy(alpha = pulseAlpha),
                                radius = (size.width / 2f) - strokeWidth,
                                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        // Flame with its own soft ground glow (built into AppFlame)
                        AppFlame(
                            modifier    = Modifier.size(38.dp),
                            flicker     = true,
                            glow        = true,
                            desaturated = false
                        )

                        Spacer(Modifier.height(4.dp))

                        // Time number directly below the flame — uses the
                        // rolling display value; the conditions still check
                        // the real totalMinutes so the format never flickers
                        // between "Xh" and "Xm" mid-roll in a confusing way.
                        Text(
                            text       = if (totalMinutes >= 60) "${displayMinutes / 60}h" else "${displayMinutes}m",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = TextPrimary
                        )
                        if (totalMinutes >= 60 && totalMinutes % 60 != 0) {
                            Text(
                                text  = "${displayMinutes % 60}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // ── Right: status + nudge + streak ───────────────────────
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {

                    // Status line — colored once usage/goal status calls for it
                    Text(
                        text       = contextLine,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = if (hasGoal) arcColor else if (totalMinutes >= 180) arcColor else TextSecondary
                    )

                    // Subtle nudge — goal-aware text, toned-down muted color
                    if (showNudge) {
                        Text(
                            text  = nudgeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // ── Streak pill — real data from blocked apps ──────────
                    if (topStreakDays > 0) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(AccentAmber.copy(alpha = 0.10f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppFlame(modifier = Modifier.size(16.dp), flicker = false, desaturated = false)
                            AnimatedCount(
                                value      = topStreakDays,
                                suffix     = " day streak",
                                style      = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color      = AccentAmber
                            )
                        }
                    } else {
                        Text(
                            text  = "Block an app to start a streak",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Blocked attempts card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BlockedAttemptsCard(totalBlocked: Int, topApp: AppUsageStat?) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(AccentAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Blocked attempts today", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                    val displayBlocked by animatedCountAsState(totalBlocked)
                    Text(
                        "$displayBlocked ${if (totalBlocked == 1) "attempt" else "attempts"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            if (topApp != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentAmber.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon: ImageBitmap? = remember(topApp.packageName) { InstalledApps.iconFor(topApp.packageName) }
                    AppIconOrLetter(icon = icon, label = topApp.appName, size = 32)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Most attempted", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(topApp.appName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    val displayTopAttempts by animatedCountAsState(topApp.blockedAttempts)
                    Text(
                        "${displayTopAttempts}×",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hourly bar chart (24 bars)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HourlyBarChart(hourlyMillis: List<Long>) {
    if (hourlyMillis.size < 24) {
        Text("No data yet.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        return
    }

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val maxMillis   = remember(hourlyMillis) { hourlyMillis.maxOrNull()?.coerceAtLeast(1L) ?: 1L }
    val labelHours  = setOf(0, 6, 12, 18, 23)

    Column {
        Row(
            Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            hourlyMillis.forEachIndexed { hour, millis ->
                val fraction = millis.toFloat() / maxMillis
                val isCurrent = hour == currentHour
                val barColor  = when {
                    isCurrent -> AccentBlue
                    millis == 0L -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else -> AccentTeal.copy(alpha = 0.7f)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height((64 * fraction).coerceAtLeast(if (millis > 0L) 3f else 1f).dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(barColor)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (hour in 0..23) {
                Box(Modifier.weight(1f)) {
                    if (hour in labelHours) {
                        Text(
                            text = when (hour) {
                                0 -> "12a"; 6 -> "6a"; 12 -> "12p"; 18 -> "6p"; 23 -> "11p"; else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = if (hour == currentHour) AccentBlue else TextTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7-day bar chart
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeekBarChart(minutesPerDay: List<Int>) {
    if (minutesPerDay.isEmpty()) {
        Text("No data yet.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        return
    }
    val maxMinutes = remember(minutesPerDay) { minutesPerDay.maxOrNull()?.coerceAtLeast(1) ?: 1 }
    val dayLabels  = remember(minutesPerDay.size) { buildDayLabels(minutesPerDay.size) }

    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        minutesPerDay.forEachIndexed { index, minutes ->
            val fraction = remember(minutes, maxMinutes) { minutes.toFloat() / maxMinutes }
            val barColor = if (index == 0) AccentBlue else AccentTeal.copy(alpha = 0.6f)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (minutes > 0) {
                    Text(formatMinutesShort(minutes), style = MaterialTheme.typography.labelSmall, color = if (index == 0) AccentBlue else TextMuted)
                    Spacer(Modifier.height(2.dp))
                }
                Box(
                    Modifier.fillMaxWidth()
                        .height((90 * fraction).coerceAtLeast(if (minutes > 0) 4f else 0f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(barColor)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    dayLabels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == 0) AccentBlue else TextSecondary,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Trend chip (vs yesterday)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TrendChip(today: Int, yesterday: Int) {
    val diff = today - yesterday
    val (icon, color, label) = when {
        yesterday == 0 -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, TextMuted, "No data")
        diff > 5  -> Triple(Icons.AutoMirrored.Filled.TrendingUp, AccentAmber, "+${formatMinutesShort(diff)}")
        diff < -5 -> Triple(Icons.AutoMirrored.Filled.TrendingDown, AccentTeal, "-${formatMinutesShort(-diff)}")
        else -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, TextSecondary, "Same")
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-app row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppStatRow(stat: AppUsageStat, maxMillis: Long) {
    // FIX: icon is looked up once per unique packageName and cached by `remember`.
    // Previously this was also done with remember but was inside a forEach in
    // a single item{} — now each row is its own lazy item, so Compose can skip
    // re-composing rows that haven't changed.
    val icon: ImageBitmap? = remember(stat.packageName) { InstalledApps.iconFor(stat.packageName) }
    val fraction  = remember(stat.todayMillis, maxMillis) { (stat.todayMillis.toFloat() / maxMillis).coerceIn(0f, 1f) }
    val minutes   = remember(stat.todayMillis) { (stat.todayMillis / 60_000L).toInt() }
    val timeColor = remember(minutes) { when { minutes >= 60 -> AccentAmber; minutes >= 30 -> AccentBlue; else -> AccentTeal } }

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = CardSurface)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppIconOrLetter(icon = icon, label = stat.appName)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stat.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (stat.streakDays > 0) {
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            AppFlame(
                                modifier = Modifier.size(12.dp),
                                desaturated = false
                            )
                            Text(
                                "${stat.streakDays} day streak",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentAmber
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (minutes > 0) {
                        Text(formatMinutes(minutes), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = timeColor)
                    }
                    if (stat.blockedAttempts > 0) {
                        Text(
                            "blocked ${stat.blockedAttempts}×",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentAmber.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            if (stat.todayMillis > 0L) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(timeColor))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Browsing tab composables — show individual sites, not browser apps
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BrowsingEmptyCard() {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text("No browsing data yet today", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Sites you visit in Chrome, Firefox, Edge and other browsers will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Private / incognito tabs can't be detected.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BrowsingTotalCard(stats: List<DomainUsageStat>) {
    val totalMinutes = remember(stats) { (stats.sumOf { it.todayMillis } / 60_000L).toInt() }
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardSurface)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Total browsing today", style = MaterialTheme.typography.labelMedium, color = TextMuted)
                Text(formatMinutes(totalMinutes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Text("${stats.size} site${if (stats.size != 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

@Composable
private fun DomainStatRow(stat: DomainUsageStat, maxMillis: Long) {
    val fraction  = remember(stat.todayMillis, maxMillis) { (stat.todayMillis.toFloat() / maxMillis).coerceIn(0f, 1f) }
    val minutes   = remember(stat.todayMillis) { (stat.todayMillis / 60_000L).toInt() }
    val timeColor = remember(minutes) { when { minutes >= 60 -> AccentAmber; minutes >= 30 -> AccentBlue; else -> AccentTeal } }

    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = CardSurface)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Globe icon with a tinted background instead of an app icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(AccentBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // The domain itself — this is the key info that was missing before
                    Text(
                        stat.domain,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (stat.blockedAttempts > 0) {
                        Text(
                            "Blocked ${stat.blockedAttempts}× today",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentAmber.copy(alpha = 0.85f)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (minutes > 0) {
                        Text(
                            formatMinutes(minutes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = timeColor
                        )
                    } else if (stat.blockedAttempts > 0) {
                        // Blocked but never actually visited — show 0m so the row still makes sense
                        Text("0m", style = MaterialTheme.typography.labelLarge, color = TextMuted)
                    }
                }
            }
            if (stat.todayMillis > 0L) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(timeColor))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Goal dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GoalDialog(
    currentGoalMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var text by remember { mutableStateOf(if (currentGoalMinutes > 0) currentGoalMinutes.toString() else "") }
    val parsed = text.toIntOrNull()
    val isValid = parsed != null && parsed in 1..1440

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurface,
        title = {
            Text("Daily screen time goal", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Set a daily limit for total screen time. You'll see a progress bar on the Stats screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Minutes (e.g. 120 = 2 hours)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = TextMuted,
                        focusedLabelColor = AccentBlue,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (parsed != null && parsed > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "= ${formatMinutes(parsed)} per day",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentTeal
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onSave(parsed!!) },
                enabled = isValid
            ) {
                Text("Save", color = if (isValid) AccentBlue else TextMuted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (currentGoalMinutes > 0) {
                TextButton(onClick = { onSave(0) }) {
                    Text("Clear goal", color = TextMuted)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted)
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// AppIconOrLetter overload that accepts a size parameter
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppIconOrLetter(icon: ImageBitmap?, label: String, size: Int = 40) {
    if (size == 40) {
        AppIconOrLetter(icon = icon, label = label)
    } else {
        Box(modifier = Modifier.size(size.dp), contentAlignment = Alignment.Center) {
            AppIconOrLetter(icon = icon, label = label)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Swipeable chart pager  (Today by hour  ←→  Last 7 days)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChartPager(
    hourlyMillis: List<Long>,
    weeklyMinutes: List<Int>
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    Column {
        // Each page is slightly narrower than the screen so the next card
        // peeks on the right — giving the user a clear hint to swipe.
        HorizontalPager(
            state = pagerState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 32.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            // Slight scale-down for the non-active card — makes the peek feel
            // intentional rather than accidental.
            val pageOffset = (pagerState.currentPage - page +
                    pagerState.currentPageOffsetFraction).absoluteValue
            val scale = 1f - (pageOffset * 0.04f).coerceIn(0f, 0.04f)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleY = scale; scaleX = scale },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    when (page) {
                        0 -> {
                            Text(
                                "Today by hour",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Screen time across the day",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                            Spacer(Modifier.height(12.dp))
                            HourlyBarChart(hourlyMillis)
                        }
                        1 -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Last 7 days",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                }
                                if (weeklyMinutes.size >= 2) {
                                    TrendChip(
                                        today = weeklyMinutes[0],
                                        yesterday = weeklyMinutes[1]
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            WeekBarChart(weeklyMinutes)
                        }
                    }
                }
            }
        }

        // Dot indicators — so the user knows there are exactly 2 pages
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(4.dp)
                        .width(if (isSelected) 20.dp else 6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isSelected) AccentBlue
                            else TextMuted.copy(alpha = 0.4f)
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatMinutes(totalMinutes: Int): String {
    if (totalMinutes <= 0) return "0 min"
    val h = totalMinutes / 60; val m = totalMinutes % 60
    return when { h == 0 -> "${m}m"; m == 0 -> "${h}h"; else -> "${h}h ${m}m" }
}

private fun formatMinutesShort(minutes: Int): String {
    val h = minutes / 60; val m = minutes % 60
    return if (h > 0) "${h}h" else "${m}m"
}

private fun buildDayLabels(count: Int): List<String> {
    val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    return (0 until count).map { offset ->
        if (offset == 0) "Today"
        else { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -offset); dayNames[c.get(Calendar.DAY_OF_WEEK) - 1] }
    }
}
