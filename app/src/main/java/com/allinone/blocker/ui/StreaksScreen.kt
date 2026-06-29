package com.allinone.blocker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.data.StreakRepository
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

// ─────────────────────────────────────────────────────────────────────────────
// STREAKS SCREEN
//
// PLAIN-ENGLISH SUMMARY:
// This is the full detail view for the streak — opened by tapping the small
// flame badge in the Home top bar. It shows:
//   - A big flame with the current streak number (BigFlameHero)
//   - A status line (clean so far / broken today / no goal set, etc.)
//   - The shields row (free passes that protect the streak)
//   - A 30-day calendar-style strip showing which days were clean vs broken
//
// ANIMATION SUMMARY (BigFlameHero):
//   On the FIRST open each calendar day, a cinematic entrance plays once:
//     1. Flame rises up from below while fading + scaling in (spring, overshoot)
//     2. Warm radial glow blooms outward from behind the flame (plays once, holds)
//     3. ~300ms pause
//     4. Streak number counts up one digit at a time (120ms per tick),
//        each digit slides in from below / out above (AnimatedContent)
//     5. "days" label fades in softly after the count finishes
//     6. On milestone days (3/7/14/30/60/100), a congratulations label
//        springs in below with a bounce entrance
//
//   On every subsequent open that same day, the flame is completely still —
//   no looping, no pulse, no flicker. The glow stays visible but static.
//
// "ONCE PER DAY" LOGIC:
//   SharedPreferences key "streak_entrance_last_date" stores the last date
//   the animation played as "YYYY-MM-DD". On open, compare to today's date.
//   If they match → skip animation, snap to final state immediately.
//   If they differ → play full sequence, then save today's date.
//
//   Kept in StreaksScreen (not StreakRepository) because it is purely a UI
//   decision — whether to play an entrance — not streak data logic.
// ─────────────────────────────────────────────────────────────────────────────

private const val ENTRANCE_PREFS    = "streak_entrance_prefs"
private const val KEY_ENTRANCE_DATE = "streak_entrance_last_date"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreaksScreen(onBack: () -> Unit) {
    val streak       by StreakRepository.streak.collectAsState()
    val shieldsLeft  by StreakRepository.shieldsAvailable.collectAsState()
    val shieldsCap   by StreakRepository.shieldsCap.collectAsState()
    val brokenToday  by StreakRepository.brokenToday.collectAsState()
    val history      by StreakRepository.history.collectAsState()

    val isMilestone = remember(streak) { StreakRepository.isMilestone(streak) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                title = {
                    Text(
                        "Streak",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            BigFlameHero(
                streak      = streak,
                brokenToday = brokenToday,
                isMilestone = isMilestone
            )

            StatusLine(streak = streak, brokenToday = brokenToday)

            ShieldsCard(available = shieldsLeft, cap = shieldsCap)

            HistorySection(history = history)

            ExplainerCard()

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BIG FLAME HERO
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BigFlameHero(streak: Int, brokenToday: Boolean, isMilestone: Boolean) {
    val context    = LocalContext.current
    val flameColor = if (brokenToday && streak == 0) TextTertiary else AccentAmber
    val glowColor  = if (brokenToday && streak == 0) TextTertiary else AccentRed

    // ── Decide once: should we play the entrance today? ───────────────────
    // remember { } runs only once when the composable first appears.
    // We read the stored date; if it matches today we skip everything.
    val todayString = remember { LocalDate.now().toString() }  // e.g. "2025-06-27"
    val shouldAnimate = remember {
        val prefs = context.getSharedPreferences(ENTRANCE_PREFS, android.content.Context.MODE_PRIVATE)
        prefs.getString(KEY_ENTRANCE_DATE, "") != todayString
    }

    // ── Animated values ───────────────────────────────────────────────────
    // Each starts at "hidden" if we're playing the entrance, or at its
    // final resting value if we're skipping (already played today).
    val flameScale    = remember { Animatable(if (shouldAnimate) 0f    else 1f)    }
    val flameAlpha    = remember { Animatable(if (shouldAnimate) 0f    else 1f)    }
    // translationY in pixels — positive = shifted downward (starts below centre)
    val flameOffsetPx = remember { Animatable(if (shouldAnimate) 120f  else 0f)   }
    val glowAlpha     = remember { Animatable(if (shouldAnimate) 0f    else 0.18f) }
    val glowScale     = remember { Animatable(if (shouldAnimate) 0.3f  else 1f)   }

    // What number to display during the count-up
    var displayedCount by remember { mutableIntStateOf(if (shouldAnimate) 0 else streak) }
    // Whether "days" label is visible
    var daysVisible    by remember { mutableStateOf(!shouldAnimate) }
    // Whether the milestone label has sprung in
    var milestoneVisible by remember { mutableStateOf(!shouldAnimate && isMilestone) }

    // ── Master entrance sequence ───────────────────────────────────────────
    // All stages run inside a single LaunchedEffect coroutine, in order.
    // We use launch { } for animations that should run in parallel, and
    // just call animateTo directly for sequential ones.
    LaunchedEffect(Unit) {
        if (!shouldAnimate) return@LaunchedEffect

        // ── Stage 1: flame rises, fades in, and scales up simultaneously ──
        // launch { } starts child coroutines; all three animate in parallel.
        // We then delay long enough for the spring to settle (~700ms covers
        // the longest spring at StiffnessMediumLow with LowBouncy damping).
        val flameSpring = spring<Float>(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness    = Spring.StiffnessMediumLow
        )
        launch { flameOffsetPx.animateTo(0f,   animationSpec = flameSpring) }
        launch { flameScale.animateTo(1f,       animationSpec = flameSpring) }
        launch { flameAlpha.animateTo(1f,       animationSpec = tween(350)) }
        delay(700L)  // let the spring settle before starting the glow

        // ── Stage 2: glow blooms outward once, then holds ─────────────────
        launch { glowAlpha.animateTo(0.18f, animationSpec = tween(600)) }
        launch { glowScale.animateTo(1f,    animationSpec = tween(700)) }
        delay(750L)  // wait for glow to finish

        // ── Pause before count-up ─────────────────────────────────────────
        delay(300L)

        // ── Stage 3: count up digit by digit ─────────────────────────────
        // Start from 1 (or 0 if streak is 0), tick every 120ms.
        val startCount = if (streak > 0) 1 else 0
        for (n in startCount..streak) {
            displayedCount = n
            delay(120L)
        }

        // ── Stage 4: "days" fades in ──────────────────────────────────────
        daysVisible = true

        // ── Stage 5: milestone label springs in ───────────────────────────
        if (isMilestone) {
            delay(400L)
            milestoneVisible = true
        }

        // ── Save today's date so we don't replay on the next open today ───
        context.getSharedPreferences(ENTRANCE_PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRANCE_DATE, todayString)
            .apply()
    }

    // ── Ground glow: warm amber ellipse that breathes below the flame ────
    // Pulses slowly between two alpha values — like light cast on a floor.
    // Completely independent of the entrance animation; it just loops forever.
    val groundGlowAlpha = remember { Animatable(0.18f) }
    LaunchedEffect(Unit) {
        groundGlowAlpha.animateTo(
            targetValue = 0.38f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    // ── Layout ────────────────────────────────────────────────────────────
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {

            // ── Glow rings: bloom in once, then hold static ────────────────
            // Outer halo
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .graphicsLayer {
                        scaleX = glowScale.value
                        scaleY = glowScale.value
                        alpha  = glowAlpha.value
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = 0.55f),
                                flameColor.copy(alpha = 0.28f),
                                flameColor.copy(alpha = 0f)
                            )
                        )
                    )
            )
            // Inner warm core
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .graphicsLayer {
                        scaleX = glowScale.value
                        scaleY = glowScale.value
                        alpha  = glowAlpha.value
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                flameColor.copy(alpha = 0.30f),
                                flameColor.copy(alpha = 0f)
                            )
                        )
                    )
            )

            // ── Content circle — applies entrance transforms ───────────────
            // graphicsLayer shifts the circle visually without affecting layout,
            // so the surrounding Column doesn't jump around during animation.
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .graphicsLayer {
                        translationY = flameOffsetPx.value
                        scaleX       = flameScale.value
                        scaleY       = flameScale.value
                        alpha        = flameAlpha.value
                    }
                    .clip(CircleShape)
                    .background(flameColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // Flame icon — flickers gently and continuously on this screen
                    AppFlame(
                        modifier = Modifier.size(64.dp),
                        desaturated = brokenToday && streak == 0,
                        flicker = true,
                        glow = true
                    )

                    // Counting number: each digit slides in from below,
                    // previous digit slides out above (AnimatedContent).
                    AnimatedContent(
                        targetState = displayedCount,
                        transitionSpec = {
                            (slideInVertically { h -> h } + fadeIn(tween(80)))
                                .togetherWith(slideOutVertically { h -> -h } + fadeOut(tween(80)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "streak_count"
                    ) { count ->
                        Text(
                            text       = "$count",
                            style      = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color      = flameColor
                        )
                    }

                    // "days" — invisible (alpha 0) until count finishes, then snaps to 1.
                    // We use a simple alpha modifier rather than AnimatedVisibility so the
                    // layout space is always reserved (no size-jump when it appears).
                    Text(
                        text     = if (displayedCount == 1) "day" else "days",
                        style    = MaterialTheme.typography.labelLarge,
                        color    = TextSecondary,
                        modifier = Modifier.alpha(if (daysVisible) 1f else 0f)
                    )
                }
            }
        }

        // ── Ground glow: warm ellipse that pools below the flame ──────────
        // Looks like warm light cast downward from the flame onto a surface.
        // Breathes slowly in sync with the flicker — fully independent of
        // the entrance animation, just loops forever.
        Box(
            modifier = Modifier
                .size(width = 160.dp, height = 28.dp)
                .graphicsLayer { alpha = groundGlowAlpha.value }
                .blur(18.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            flameColor.copy(alpha = 0.85f),
                            glowColor.copy(alpha = 0.40f),
                            flameColor.copy(alpha = 0f)
                        )
                    )
                )
        )

        // ── Milestone label ────────────────────────────────────────────────
        // Only rendered on milestone days. Springs in with bounce after the
        // count finishes. On skip-animation days it's instantly visible.
        if (isMilestone) {
            val milestoneScale = remember { Animatable(if (milestoneVisible) 1f else 0f) }
            val milestoneAlpha = remember { Animatable(if (milestoneVisible) 1f else 0f) }

            LaunchedEffect(milestoneVisible) {
                if (milestoneVisible) {
                    launch {
                        milestoneScale.animateTo(
                            targetValue    = 1f,
                            animationSpec  = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness    = Spring.StiffnessLow
                            )
                        )
                    }
                    launch {
                        milestoneAlpha.animateTo(1f, animationSpec = tween(200))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text       = milestoneCopy(streak),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = AccentAmber,
                modifier   = Modifier
                    .graphicsLayer {
                        scaleX = milestoneScale.value
                        scaleY = milestoneScale.value
                        alpha  = milestoneAlpha.value
                    }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STATUS LINE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusLine(streak: Int, brokenToday: Boolean) {
    val (text, color) = when {
        brokenToday && streak > 0 -> "Shield used today — your streak is safe" to AccentTeal
        brokenToday                -> "Streak reset today" to AccentRed
        streak == 0                -> "Start your streak today" to TextSecondary
        else                       -> "Clean day so far — keep it up" to AccentTeal
    }
    Text(
        text       = text,
        style      = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color      = color
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SHIELDS CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShieldsCard(available: Int, cap: Int) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.medium,
        colors    = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Shields",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "$available/$cap this week",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "A shield automatically protects your streak the one time you slip — break a block, or go over your screen-time goal. No taps needed, it just saves you.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 1..cap) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                if (i <= available) AccentTeal.copy(alpha = 0.18f)
                                else TextTertiary.copy(alpha = 0.10f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint     = if (i <= available) AccentTeal else TextTertiary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CALENDAR VIEW (Duolingo-style with fire icons on streak days)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HistorySection(history: List<Pair<Int, Boolean>>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Your Streak Calendar",
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = TextPrimary,
            modifier   = Modifier.padding(bottom = 10.dp)
        )

         if (history.isEmpty()) {
            Text(
                "No streak days recorded yet — keep going!",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Convert history to a map for easy lookup
        val historyMap = history.toMap()
        
        // Get current date and build calendar
        val today = java.util.Calendar.getInstance()
        val currentMonth = today.get(java.util.Calendar.MONTH)
        val currentYear = today.get(java.util.Calendar.YEAR)
        val currentDayOfMonth = today.get(java.util.Calendar.DAY_OF_MONTH)
        
        // Month name
        val monthName = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(today.time)
        
        Text(
            monthName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Day of week headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))

        // Build calendar grid
        val calendar = java.util.Calendar.getInstance()
        calendar.set(currentYear, currentMonth, 1)
        val daysInMonth = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
        
        // Calculate total cells needed
        val totalCells = firstDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height((rows * 56).dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Empty cells before first day
            items(firstDayOfWeek) {
                Box(modifier = Modifier.aspectRatio(1f))
            }
            
            // Days of the month
            items(daysInMonth) { index ->
                val dayOfMonth = index + 1
                val cal = java.util.Calendar.getInstance()
                cal.set(currentYear, currentMonth, dayOfMonth)
                val dayKey = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
                
                val isToday = dayOfMonth == currentDayOfMonth
                val isFuture = dayOfMonth > currentDayOfMonth
                val isClean = historyMap[dayKey] ?: false
                val hasData = historyMap.containsKey(dayKey)
                
                CalendarDayCell(
                    dayNumber = dayOfMonth,
                    isToday = isToday,
                    isFuture = isFuture,
                    isClean = isClean,
                    hasData = hasData
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppFlame(
                    modifier = Modifier.size(16.dp),
                    desaturated = false
                )
                Text("Streak day", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(AccentRed.copy(alpha = 0.45f))
                )
                Text("Broken day", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CALENDAR DAY CELL — Duolingo-style "number sitting inside the flame"
//
// PLAIN-ENGLISH SUMMARY:
// On a streak day, the flame isn't a tiny icon floating above the number —
// it's drawn big, filling almost the whole cell, and the day number sits
// on top of it near the bottom (right where the flame's hot core is), so it
// looks like the number is glowing out of the fire — same as Duolingo's
// calendar badges.
//
// On a non-streak day, there's no flame at all — just a plain muted circle
// (or nothing, for days with no data yet) with the number on top.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    isToday: Boolean,
    isFuture: Boolean,
    isClean: Boolean,
    hasData: Boolean
) {
    val isStreakDay = hasData && isClean && !isFuture
    val isBrokenDay = hasData && !isClean && !isFuture

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            // ── Streak day: big flame fills the cell, number sits inside it ──
            isStreakDay -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AppFlame(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.35f), // flame overflows the cell slightly, like Duolingo's badge
                        desaturated = false
                    )
                    Text(
                        text = "$dayNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 8.dp) // nudge down toward the flame's hot core
                    )
                }
            }

            // ── Broken day: plain muted red circle, no flame ─────────────────
            isBrokenDay -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.78f)
                        .clip(CircleShape)
                        .background(AccentRed.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$dayNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal,
                        color = AccentRed,
                        fontSize = 12.sp
                    )
                }
            }

            // ── Today, no result yet / future / no data: plain background ───
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.78f)
                        .clip(CircleShape)
                        .background(
                            if (isToday) AccentAmber.copy(alpha = 0.15f) else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$dayNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isFuture) TextTertiary.copy(alpha = 0.4f)
                                else if (isToday) AccentAmber
                                else TextPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EXPLAINER CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExplainerCard() {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = MaterialTheme.shapes.medium,
        colors    = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "How this streak works",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your streak grows by one for every day you don't turn off a block, and you stay within your daily screen-time goal (if you've set one). Either slip-up resets it back to zero — unless a shield is available to protect you.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = TextTertiary.copy(alpha = 0.12f))
            Spacer(Modifier.height(8.dp))
            Text(
                "Missing a day isn't the end — it just means starting again from day one.",
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color      = TextSecondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────

private fun milestoneCopy(days: Int): String = when (days) {
    3   -> "🔥 3 days! Great start"
    7   -> "🔥 One week! You're on fire"
    14  -> "🔥 Two weeks strong"
    30  -> "🔥 30 days — legendary"
    60  -> "🔥 60 days. Unreal."
    100 -> "🔥 100 days. Unstoppable."
    else -> "🔥 $days days!"
}
