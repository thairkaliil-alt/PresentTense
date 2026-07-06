package com.allinone.blocker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.ui.motion.LocalReducedMotion
import com.allinone.blocker.ui.motion.MotionDurations
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.motion.MotionTokens
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// "Popular to block" — these appear at the top of the list if installed.
// Order here controls the order they appear in the section.
// ─────────────────────────────────────────────────────────────────────────────
private val POPULAR_PACKAGES = listOf(
    "com.instagram.android",
    "com.google.android.youtube",
    "com.zhiliaoapp.musically",     // TikTok
    "com.facebook.katana",          // Facebook
    "com.snapchat.android",
    "com.twitter.android",
    "com.reddit.frontpage",
    "com.linkedin.android",
    "com.pinterest",
    "com.netflix.mediaclient",
    "com.spotify.music"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(onBack: () -> Unit, onPicked: (String) -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) { InstalledApps.ensureLoaded(context) }
    val all by InstalledApps.apps.collectAsState()
    val loadingApps by InstalledApps.loading.collectAsState()
    val blocked by BlockerRepository.apps.collectAsState()

    var query by remember { mutableStateOf("") }

    val blockedPkgs = remember(blocked) { blocked.map { it.packageName }.toSet() }

    // Split into popular (installed + in POPULAR_PACKAGES) and the rest
    val installedPkgs = remember(all) { all.map { it.packageName }.toSet() }

    val popularApps = remember(all, query) {
        if (query.isNotBlank()) emptyList()
        else POPULAR_PACKAGES
            .filter { it in installedPkgs }
            .mapNotNull { pkg -> all.firstOrNull { it.packageName == pkg } }
    }

    val popularPkgSet = remember(popularApps) { popularApps.map { it.packageName }.toSet() }

    val filteredAll = remember(all, query, popularPkgSet) {
        val base = if (query.isBlank()) {
            all.filter { it.packageName !in popularPkgSet }
        } else {
            all.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
        base
    }

    // Just navigate — do NOT save here. AppRulesScreen holds the draft and
    // only commits to the repo when the user taps Save. Back = nothing added.
    fun pick(device: DeviceApp) {
        onPicked(device.packageName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose an app", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { InstalledApps.refresh(context) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh app list", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgScreen, scrolledContainerColor = BgScreen)
            )
        },
        containerColor = BgScreen
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextTertiary) },
                singleLine  = true,
                modifier    = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = AccentBlue,
                    unfocusedBorderColor = TextTertiary.copy(alpha = 0.3f)
                )
            )

            if (all.isEmpty() && loadingApps) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading apps…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                return@Scaffold
            }

            // PERFORMANCE + BUGFIX (same pattern as BlockedAppsScreen): the
            // "settle into place" entrance should play exactly ONCE, the
            // first time this screen opens — not every time a row scrolls
            // back into view, and not on every keystroke while searching.
            // LazyColumn throws away and rebuilds rows that scroll off
            // screen, so without this screen-level flag every row would
            // have no memory of "have I already appeared" and would try to
            // re-appear constantly, which is what reads as "no real
            // animation" (it's playing, just re-triggering nonstop and
            // fighting with scroll for frame time).
            var entranceAnimationPlayed by remember { mutableStateOf(false) }
            val showPopularSection = popularApps.isNotEmpty() && query.isBlank()
            // Only the first ANIMATED_ROW_CAP rows of EACH section cascade in —
            // "keep cascades short or the tail feels slow" (see Appearance.kt).
            // The "All apps" section's cascade starts right after the popular
            // section's finishes, so the two read as one continuous settle
            // instead of two separate pops.
            val popularAnimCount = if (showPopularSection) minOf(popularApps.size, ANIMATED_ROW_CAP) else 0
            val allSectionStartDelay = popularAnimCount * MotionTokens.StaggerStepMs

            LaunchedEffect(Unit) {
                val cascadeMs = allSectionStartDelay +
                    MotionTokens.StaggerStepMs * ANIMATED_ROW_CAP +
                    MotionDurations.Emphasized
                delay(cascadeMs.toLong())
                entranceAnimationPlayed = true
            }

            LazyColumn(Modifier.fillMaxSize()) {

                // ── Popular to block section ──────────────────────────────
                if (showPopularSection) {
                    item {
                        SectionHeader(
                            title    = "Popular to block",
                            subtitle = "Apps most people add first"
                        )
                    }
                    itemsIndexed(popularApps, key = { _, app -> "pop_${app.packageName}" }) { index, device ->
                        PickerRow(
                            device          = device,
                            alreadyBlocked  = blockedPkgs.contains(device.packageName),
                            onPick          = { pick(device) },
                            animateEntrance = !entranceAnimationPlayed && index < ANIMATED_ROW_CAP,
                            entranceDelayMs = index * MotionTokens.StaggerStepMs
                        )
                    }
                    item {
                        SectionHeader(
                            title    = "All apps",
                            subtitle = "Everything installed on your phone"
                        )
                    }
                }

                // ── Full list (or search results) ─────────────────────────
                itemsIndexed(filteredAll, key = { _, app -> app.packageName }) { index, device ->
                    PickerRow(
                        device          = device,
                        alreadyBlocked  = blockedPkgs.contains(device.packageName),
                        onPick          = { pick(device) },
                        animateEntrance = !entranceAnimationPlayed && index < ANIMATED_ROW_CAP,
                        entranceDelayMs = allSectionStartDelay + index * MotionTokens.StaggerStepMs
                    )
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// Cap on how many rows play the entrance cascade per section — matches the
// app-wide "keep staggered cascades short" convention (see Appearance.kt and
// BlockedAppsScreen.kt).
private const val ANIMATED_ROW_CAP = 8

// ─────────────────────────────────────────────────────────────────────────────
// SECTION HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            title,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = TextPrimary
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PICKER ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PickerRow(
    device: DeviceApp,
    alreadyBlocked: Boolean,
    onPick: () -> Unit,
    animateEntrance: Boolean = false,
    entranceDelayMs: Int = 0
) {
    val haptics = rememberHaptics()
    val reducedMotionForEntrance = LocalReducedMotion.current

    // Entrance cascade — same technique as BlockedAppsScreen's rows: a plain
    // Animatable read only inside Modifier.graphicsLayer (a draw-phase-only
    // callback), so animating it never forces a fresh measure/layout pass.
    // That's what makes it safe to have several of these animating in a
    // LazyColumn at once without the scroll/fling stuttering.
    val entranceProgress = remember(device.packageName) {
        Animatable(if (animateEntrance && !reducedMotionForEntrance) 0f else 1f)
    }
    val enterSlidePx = with(LocalDensity.current) { MotionTokens.EnterSlideDp.dp.toPx() }
    LaunchedEffect(device.packageName, animateEntrance) {
        if (animateEntrance && !reducedMotionForEntrance) {
            if (entranceDelayMs > 0) delay(entranceDelayMs.toLong())
            entranceProgress.animateTo(1f, animationSpec = MotionSpecs.enter(MotionDurations.Emphasized))
        }
    }

    // The icon is USUALLY already sitting in InstalledApps' cache by the time
    // this row shows up (the scan loads every icon before the app list is
    // published). But right after a cold start or a manual refresh, a row can
    // render a beat before its icon is cached. Instead of a one-shot lookup
    // that either has the icon or doesn't, we hold it in state and briefly
    // poll the cache until it appears — then stop. This makes the icon
    // "reactive" without touching how InstalledApps loads things.
    var icon by remember(device.packageName) {
        mutableStateOf(InstalledApps.iconFor(device.packageName))
    }
    LaunchedEffect(device.packageName, icon) {
        if (icon != null) return@LaunchedEffect
        // Check every 80ms for up to ~1.6s, then give up — this covers the
        // "still scanning" window without polling forever for apps that
        // genuinely have no icon (loop exits itself either way).
        repeat(20) {
            delay(80)
            val found = InstalledApps.iconFor(device.packageName)
            if (found != null) {
                icon = found
                return@LaunchedEffect
            }
        }
    }

    // Crossfade the icon in over the app's standard fade duration once it
    // resolves, instead of popping in. Respects the reduced-motion setting.
    val reducedMotion = LocalReducedMotion.current
    val iconAlpha by animateFloatAsState(
        targetValue = if (icon != null) 1f else 0f,
        animationSpec = if (reducedMotion) MotionSpecs.standard(0)
        else MotionSpecs.standard(MotionDurations.Standard),
        label = "appIconFade"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entranceProgress.value
                translationY = (1f - entranceProgress.value) * enterSlidePx
            }
            .clickable(enabled = !alreadyBlocked, onClick = { haptics.tap(); onPick() })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Neutral placeholder (the same letter-avatar fallback used app-wide)
        // sits underneath at all times; the real icon crossfades in on top
        // of it once it resolves, so there's never a hard "pop".
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            LetterAvatar(device.label)
            val resolvedIcon = icon
            if (resolvedIcon != null) {
                Image(
                    bitmap = resolvedIcon,
                    contentDescription = null,
                    alpha = iconAlpha,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                device.label,
                fontWeight = FontWeight.Medium,
                style      = MaterialTheme.typography.bodyLarge,
                color      = if (alreadyBlocked) TextSecondary else TextPrimary
            )
            Text(
                device.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                maxLines = 1
            )
        }

        if (alreadyBlocked) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentBlue.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint     = AccentBlue,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "Added",
                    style  = MaterialTheme.typography.labelSmall,
                    color  = AccentBlue,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        color    = TextTertiary.copy(alpha = 0.10f)
    )
}
