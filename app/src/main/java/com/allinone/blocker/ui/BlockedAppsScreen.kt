package com.allinone.blocker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.allinone.blocker.data.BlockPreset
import com.allinone.blocker.data.BlockedApp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.ui.motion.LocalReducedMotion
import com.allinone.blocker.ui.motion.MotionDurations
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentBlueSoft
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.AccentTealSoft
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedAppsScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val apps by BlockerRepository.apps.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked apps", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgScreen,
                    scrolledContainerColor = BgScreen
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add app") }
            )
        },
        containerColor = BgScreen
    ) { pad ->
        BlockedAppsList(
            apps = apps,
            onEdit = onEdit,
            modifier = Modifier.padding(pad)
        )
    }
}

@Composable
private fun BlockedAppsList(apps: List<BlockedApp>, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    if (apps.isEmpty()) {
        Box(
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No apps blocked yet.\nTap \"Add app\" to choose one.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    } else {
        LazyColumn(
            modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                BlockedAppRow(
                    app = app,
                    onEdit = onEdit,
                    // animateItem() is what makes the rows above and below
                    // a deleted app slide smoothly into the gap instead of
                    // jumping there instantly.
                    modifier = Modifier.animateItem()
                )
            }
            item { Spacer(Modifier.size(72.dp)) }
        }
    }
}

@Composable
private fun BlockedAppRow(app: BlockedApp, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()

    // Controls whether this row is on screen. Deleting an app doesn't
    // remove it from the real list right away — it first fades + shrinks
    // out over REMOVE_ANIM_MS, and only then is it actually deleted from
    // BlockerRepository. That's what turns "row instantly disappears"
    // into "row plays a little exit animation, then is gone".
    var visible by remember(app.packageName) { mutableStateOf(true) }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(REMOVE_ANIM_MS)
            BlockerRepository.removeApp(app.packageName)
        }
    }

    val onToggle: (Boolean) -> Unit = remember(app) {
        { wantsOn ->
            if (wantsOn) {
                BlockerRepository.upsertApp(app.copy(enabled = true))
            } else {
                StrictModeGate.guard {
                    BlockerRepository.upsertApp(app.copy(enabled = false))
                }
            }
        }
    }
    // guard() may run this immediately, or hold it until the person clears
    // a Strict Mode challenge — either way, all it does here is flip
    // `visible` to false, which is what kicks off the exit animation above.
    val onDelete: () -> Unit = remember(app.packageName) {
        { StrictModeGate.guard { visible = false } }
    }
    val onRowClick: () -> Unit = remember(app.packageName) {
        { onEdit(app.packageName) }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(REMOVE_ANIM_MS.toInt())) + scaleIn(initialScale = 0.9f, animationSpec = tween(REMOVE_ANIM_MS.toInt())),
        exit = fadeOut(tween(REMOVE_ANIM_MS.toInt())) + scaleOut(targetScale = 0.85f, animationSpec = tween(REMOVE_ANIM_MS.toInt()))
    ) {
        Card(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(onClick = onRowClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconOrLetter(packageName = app.packageName, label = app.appName)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        app.appName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    val summary = remember(app.rules, app.protection, app.preset) {
                        when (app.preset) {
                            BlockPreset.MINDFUL       -> "Mindful use · 30 min/day"
                            BlockPreset.HARD_LIMITS   -> "Hard limits · time window"
                            BlockPreset.FULLY_BLOCKED -> "Fully blocked"
                            BlockPreset.CUSTOM        ->
                                if (app.rules.isEmpty()) "Custom · always blocked"
                                else "Custom · ${app.rules.size} rule${if (app.rules.size == 1) "" else "s"}"
                        }
                    }
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Switch(
                    checked = app.enabled,
                    onCheckedChange = { checked -> haptics.toggleTick(); onToggle(checked) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor    = Color.White,
                        checkedTrackColor    = AccentBlue,
                        checkedBorderColor   = AccentBlue,
                        uncheckedThumbColor  = TextTertiary,
                        uncheckedTrackColor  = BgDarkest,
                        uncheckedBorderColor = TextTertiary
                    )
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = TextSecondary)
                }
            }
        }
    }
}

// Shared duration (in milliseconds) for the row's own fade+scale exit
// animation. Kept short so deleting still feels instant/responsive, not
// sluggish.
private const val REMOVE_ANIM_MS = 180L

private val AVATAR_COLORS = listOf(
    AccentBlue,
    AccentTeal,
    AccentRed,
    AccentAmber,
    AccentBlueSoft,
    AccentTealSoft
)

@Composable
fun LetterAvatar(name: String) {
    val color = remember(name) { AVATAR_COLORS[(name.sumOf { it.code }) % AVATAR_COLORS.size] }
    val letter = if (name.isBlank()) "?" else name.trim().first().uppercase()
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape),
        color = color
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(letter, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AppIconOrLetter(icon: ImageBitmap?, label: String) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    } else {
        LetterAvatar(label)
    }
}

@Composable
fun AppIconOrLetter(packageName: String, label: String) {
    // PERFORMANCE FIX: the vast majority of rows already have their icon
    // sitting in InstalledApps' cache by the time they're drawn — the scan
    // runs once at app startup (see BlockerApp.kt), long before the user
    // opens Blocked apps / Whitelist / Stats. Checking the cache directly at
    // composition time (not inside remember{}'s lazily-read initial value —
    // this is a plain val, read fresh every time this row composes) lets us
    // skip ALL of the animation setup below (an extra remembered state, a
    // coroutine, and an animateFloatAsState) for that common case. That
    // setup being paid for on EVERY row, EVERY time it scrolled into view,
    // is what was making Blocked apps / Whitelist / Stats laggy — this was
    // the "shared version of the fix" mentioned below, so the fast path
    // benefits all three screens (and Lockdown launcher) at once.
    val cachedIcon = InstalledApps.iconFor(packageName)
    if (cachedIcon != null) {
        Image(
            bitmap = cachedIcon,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        return
    }

    // Slow path — only reached for the rare row whose icon genuinely isn't
    // cached yet (e.g. a fresh install, or opening the screen a beat before
    // the startup scan finishes). Same "icon can pop in mid-scroll" issue
    // the app picker had — icons load into InstalledApps' cache off the main
    // thread, so a row can render a beat before its icon is ready. This
    // polls briefly and crossfades the icon in once it resolves instead of
    // popping it in instantly. This is the shared version of the fix from
    // AppPickerScreen.kt's PickerRow, so every screen that lists apps
    // (Blocked apps, Whitelist, Lockdown launcher, Stats) behaves the same
    // way once the icon does resolve.
    var icon by remember(packageName) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(packageName) {
        // Check every 80ms for up to ~1.6s, then give up — covers the
        // "still scanning" window without polling forever for apps that
        // genuinely have no icon (loop exits itself either way).
        repeat(20) {
            delay(80)
            val found = InstalledApps.iconFor(packageName)
            if (found != null) {
                icon = found
                return@LaunchedEffect
            }
        }
    }

    val reducedMotion = LocalReducedMotion.current
    val iconAlpha by animateFloatAsState(
        targetValue = if (icon != null) 1f else 0f,
        animationSpec = if (reducedMotion) MotionSpecs.standard(0)
        else MotionSpecs.standard(MotionDurations.Standard),
        label = "appIconFade"
    )

    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        // Neutral placeholder sits underneath at all times; the real icon
        // crossfades in on top of it once it resolves, so there's never a
        // hard "pop".
        LetterAvatar(label)
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
}
