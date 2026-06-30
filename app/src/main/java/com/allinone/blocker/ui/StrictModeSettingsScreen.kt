package com.allinone.blocker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.CurrentLocationHelper
import com.allinone.blocker.data.FrictionType
import com.allinone.blocker.data.GeofenceManager
import com.allinone.blocker.data.LocationZone
import com.allinone.blocker.data.PinHasher
import com.allinone.blocker.data.StrictModeConfig
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.CardSurfaceAlt
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

// ─────────────────────────────────────────────────────────────────────────────
// PRESET DATA MODEL
// ─────────────────────────────────────────────────────────────────────────────

private data class StrictPreset(
    val emoji: String,
    val name: String,
    val tagline: String,
    val description: String,
    val frictions: Set<FrictionType>,
    val accentColor: Color,
    val needsPin: Boolean = false,
    val needsPlan: Boolean = false
)

private val STRICT_PRESETS = listOf(
    StrictPreset(
        emoji = "🌱",
        name = "Speed bump",
        tagline = "For when your future self will thank you",
        description = "A brief pause before disabling a block. Enough to break the reflex — not enough to feel like a fight.",
        frictions = setOf(FrictionType.COOLDOWN),
        accentColor = Color(0xFF4CAF82)
    ),
    StrictPreset(
        emoji = "🧠",
        name = "Think first",
        tagline = "Interrupts the autopilot",
        description = "You can still get through — but you'll have to prove you really want to. A wait plus a puzzle breaks the trance.",
        frictions = setOf(FrictionType.COOLDOWN, FrictionType.MATH_PUZZLE, FrictionType.TYPING_PLEDGE),
        accentColor = Color(0xFF9B7FD4)
    ),
    StrictPreset(
        emoji = "🔐",
        name = "Committed",
        tagline = "You set it up. You hold the key.",
        description = "Multiple layers including a PIN only you know. Bypassing this takes real effort — the kind you'll consciously decide against.",
        frictions = setOf(FrictionType.COOLDOWN, FrictionType.MATH_PUZZLE, FrictionType.WORD_SCRAMBLE, FrictionType.PIN),
        accentColor = Color(0xFFF4A33C),
        needsPin = true
    ),
    StrictPreset(
        emoji = "🏰",
        name = "No way back",
        tagline = "You've decided. The app respects that.",
        description = "Every layer, plus a time commitment. When this is on, the door is sealed for as long as you chose. Not for the half-committed.",
        frictions = setOf(FrictionType.COOLDOWN, FrictionType.MATH_PUZZLE, FrictionType.WORD_SCRAMBLE, FrictionType.PIN, FrictionType.TYPING_PLEDGE, FrictionType.PLAN_LOCK),
        accentColor = Color(0xFFE4895F),
        needsPin = true,
        needsPlan = true
    )
)

private fun frictionLabel(type: FrictionType): String = when (type) {
    FrictionType.COOLDOWN      -> "Cooldown timer"
    FrictionType.MATH_PUZZLE   -> "Math puzzle"
    FrictionType.WORD_SCRAMBLE -> "Word scramble"
    FrictionType.PIN           -> "PIN code"
    FrictionType.TYPING_PLEDGE -> "Typing pledge"
    FrictionType.LOCATION_LOCK -> "Location lock"
    FrictionType.PLAN_LOCK     -> "Active plan"
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictModeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config by BlockerRepository.strictMode.collectAsState()

    var showCustom by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showPledgeEdit by remember { mutableStateOf(false) }
    var showPlanPicker by remember { mutableStateOf(false) }
    var pendingPreset by remember { mutableStateOf<StrictPreset?>(null) }
    var pendingPlanPreset by remember { mutableStateOf<StrictPreset?>(null) }

    var permissionRefresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasForegroundLocation = remember(permissionRefresh) { Permissions.hasForegroundLocation(context) }
    val hasFullLocationAccess = remember(permissionRefresh) { Permissions.hasFullLocationAccess(context) }

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRefresh++
        if (granted) Permissions.openAppLocationSettings(context)
    }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(config.planActiveUntil) {
        while (config.isPlanActive(nowMillis)) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    fun applyPreset(preset: StrictPreset, frictions: Set<FrictionType> = preset.frictions) {
        BlockerRepository.setStrictMode(config.copy(enabled = true, activeFrictions = frictions))
        if (FrictionType.LOCATION_LOCK !in frictions) GeofenceManager.sync(context, emptyList())
    }

    Scaffold(
        containerColor = BgDarkest,
        topBar = {
            TopAppBar(
                title = { Text("Strict Mode", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDarkest)
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                // BUGFIX (can't scroll to see last items): the app's custom
                // bottom nav bar (the 5-icon strip) floats on top of every
                // root-tab screen's content and is NOT accounted for by
                // navigationBarsPadding() — that only covers the system
                // gesture/button bar. 32dp of bottom space wasn't enough to
                // clear it, so the last card(s) ended up hidden underneath
                // it even once scrolled all the way down. 100dp matches the
                // clearance already used on the Stats screen for the same
                // reason.
                .padding(top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Master toggle ────────────────────────────────────────────────
            MasterToggleCard(
                enabled = config.enabled,
                frictionCount = config.activeFrictions.size,
                onToggle = { wantsOn ->
                    if (!wantsOn) {
                        if (!StrictModeGate.isSettingsLockedByPlan(config)) {
                            StrictModeGate.guard {
                                BlockerRepository.setStrictMode(config.copy(enabled = false))
                            }
                        }
                    } else {
                        BlockerRepository.setStrictMode(config.copy(enabled = true))
                    }
                }
            )

            // ── Section label ────────────────────────────────────────────────
            Text(
                "Choose your protection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // ── Preset cards ─────────────────────────────────────────────────
            STRICT_PRESETS.forEach { preset ->
                val isActive = config.activeFrictions == preset.frictions && config.enabled
                StrictPresetCard(
                    preset = preset,
                    isActive = isActive,
                    isLocked = StrictModeGate.isSettingsLockedByPlan(config),
                    onSelect = {
                        when {
                            preset.needsPin && config.pinHash.isBlank() -> {
                                pendingPreset = preset
                                showPinSetup = true
                            }
                            preset.needsPlan -> {
                                applyPreset(preset, preset.frictions - FrictionType.PLAN_LOCK)
                                pendingPlanPreset = preset
                                showPlanPicker = true
                            }
                            else -> applyPreset(preset)
                        }
                    }
                )
            }

            // ── Build your own ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .clickable(
                        enabled = !StrictModeGate.isSettingsLockedByPlan(config),
                        onClick = { showCustom = true }
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(TextTertiary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Build your own",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Pick exactly which layers you want",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Custom sheet ─────────────────────────────────────────────────────────
    if (showCustom) {
        CustomFrictionSheet(
            config = config,
            hasForegroundLocation = hasForegroundLocation,
            hasFullLocationAccess = hasFullLocationAccess,
            permissionRefresh = permissionRefresh,
            nowMillis = nowMillis,
            onPermissionRefresh = { permissionRefresh++ },
            onForegroundLocationRequest = {
                foregroundLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            onBackgroundLocationRequest = { Permissions.openAppLocationSettings(context) },
            onDismiss = { showCustom = false }
        )
    }

    // ── PIN setup ─────────────────────────────────────────────────────────────
    if (showPinSetup) {
        PinSetupDialog(
            onDismiss = { showPinSetup = false; pendingPreset = null },
            onSaved = { hash ->
                val preset = pendingPreset
                if (preset != null) {
                    val frictions = preset.frictions - FrictionType.PLAN_LOCK
                    BlockerRepository.setStrictMode(
                        config.copy(pinHash = hash, enabled = true, activeFrictions = frictions)
                    )
                    if (preset.needsPlan) {
                        pendingPlanPreset = preset
                        showPlanPicker = true
                    }
                } else {
                    BlockerRepository.setStrictMode(
                        config.copy(pinHash = hash, activeFrictions = config.activeFrictions + FrictionType.PIN)
                    )
                }
                showPinSetup = false
                pendingPreset = null
            }
        )
    }

    if (showPledgeEdit) {
        PledgeEditDialog(
            current = config.pledgePhrase,
            onDismiss = { showPledgeEdit = false },
            onSaved = { phrase ->
                BlockerRepository.setStrictMode(config.copy(pledgePhrase = phrase))
                showPledgeEdit = false
            }
        )
    }

    if (showPlanPicker) {
        PlanPickerDialog(
            onDismiss = { showPlanPicker = false; pendingPlanPreset = null },
            onPlanChosen = { durationMillis, label ->
                BlockerRepository.startStrictPlan(durationMillis, label)
                val preset = pendingPlanPreset
                if (preset != null) {
                    BlockerRepository.setStrictMode(
                        BlockerRepository.strictMode.value.copy(activeFrictions = preset.frictions)
                    )
                }
                showPlanPicker = false
                pendingPlanPreset = null
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PRESET CARD
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StrictPresetCard(
    preset: StrictPreset,
    isActive: Boolean,
    isLocked: Boolean,
    onSelect: () -> Unit
) {
    val accent = preset.accentColor
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) accent.copy(alpha = 0.10f) else CardSurface
        ),
        border = if (isActive)
            androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f))
        else
            androidx.compose.foundation.BorderStroke(1.dp, TextTertiary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(preset.emoji, fontSize = 22.sp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) accent else TextPrimary
                    )
                    Text(
                        preset.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        lineHeight = 16.sp
                    )
                }
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(accent.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Active",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
            }

            // Description
            Text(
                preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            // Friction chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                preset.frictions.forEach { friction ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent.copy(alpha = 0.12f))
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            frictionLabel(friction),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Action button — only shown when not already active
            if (!isActive) {
                Button(
                    onClick = onSelect,
                    enabled = !isLocked,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.copy(alpha = 0.18f),
                        contentColor = accent,
                        disabledContainerColor = TextTertiary.copy(alpha = 0.08f),
                        disabledContentColor = TextMuted
                    )
                ) {
                    Text("Set this", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MASTER TOGGLE CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MasterToggleCard(enabled: Boolean, frictionCount: Int, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) AccentBlue.copy(alpha = 0.14f) else CardSurfaceAlt
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) AccentBlue.copy(alpha = 0.35f) else TextTertiary.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (enabled) AccentBlue.copy(alpha = 0.22f)
                        else TextTertiary.copy(alpha = 0.10f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (enabled) Icons.Filled.Shield else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (enabled) AccentBlue else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "Strict Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    if (!enabled) "Off — blocks can be removed instantly"
                    else if (frictionCount == 0) "On — but no layers selected yet"
                    else "$frictionCount friction layer${if (frictionCount > 1) "s" else ""} active",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled && frictionCount > 0) AccentBlue else TextTertiary
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentBlue
                )
            )
        }
    }

    AnimatedVisibility(
        visible = enabled && frictionCount == 0,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        WarningNote("Strict Mode is on, but no layers are selected — nothing is protected yet.")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CUSTOM FRICTION SHEET  ("Build your own")
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFrictionSheet(
    config: StrictModeConfig,
    hasForegroundLocation: Boolean,
    hasFullLocationAccess: Boolean,
    permissionRefresh: Int,
    nowMillis: Long,
    onPermissionRefresh: () -> Unit,
    onForegroundLocationRequest: () -> Unit,
    onBackgroundLocationRequest: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showPinSetup by remember { mutableStateOf(false) }
    var showPledgeEdit by remember { mutableStateOf(false) }
    var showPlanPicker by remember { mutableStateOf(false) }

    fun setFriction(type: FrictionType, on: Boolean) {
        if (!on && StrictModeGate.isSettingsLockedByPlan(config)) return
        val updated = if (on) config.activeFrictions + type else config.activeFrictions - type
        val newConfig = config.copy(activeFrictions = updated)
        BlockerRepository.setStrictMode(newConfig)
        if (type == FrictionType.LOCATION_LOCK) {
            if (on) GeofenceManager.sync(context, newConfig.locationZones)
            else GeofenceManager.sync(context, emptyList())
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Scaffold(
            containerColor = BgDarkest,
            topBar = {
                TopAppBar(
                    title = { Text("Build your own", fontWeight = FontWeight.Bold, color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text("Done", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDarkest)
                )
            }
        ) { innerPad ->
            Column(
                Modifier
                    .padding(innerPad)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Mix and match any combination of layers. Each one runs in order before any block can be turned off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    lineHeight = 17.sp
                )

                // Cooldown
                FrictionCard(
                    emoji = "⏳", title = "Cooldown timer",
                    description = "A mandatory wait. You can't proceed until the timer runs out — and you can't skip it.",
                    checked = FrictionType.COOLDOWN in config.activeFrictions,
                    onCheckedChange = { on -> setFriction(FrictionType.COOLDOWN, on) }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Wait time", style = MaterialTheme.typography.labelLarge, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                            DurationPill(label = formatSeconds(config.cooldownSeconds), color = AccentBlue)
                        }
                        Slider(
                            value = config.cooldownSeconds.toFloat(),
                            onValueChange = { BlockerRepository.setStrictMode(config.copy(cooldownSeconds = it.toInt())) },
                            valueRange = 10f..300f, steps = 28,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentBlue,
                                activeTrackColor = AccentBlue,
                                inactiveTrackColor = AccentBlue.copy(alpha = 0.2f)
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("10s", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text("5 min", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }
                }

                // Math puzzle
                FrictionCard(
                    emoji = "🧮", title = "Math puzzle",
                    description = "Solve a multi-step arithmetic problem. Wrong answer resets the puzzle with a harder one.",
                    checked = FrictionType.MATH_PUZZLE in config.activeFrictions,
                    onCheckedChange = { on -> setFriction(FrictionType.MATH_PUZZLE, on) }
                ) {
                    DifficultyNote("Problems use 2–3 steps with numbers up to 99. Takes real mental effort — not something you can blitz through on autopilot.")
                }

                // Word scramble
                FrictionCard(
                    emoji = "🔤", title = "Word scramble",
                    description = "Unscramble a word before you can continue. Simple enough to be solvable, slow enough to interrupt the impulse.",
                    checked = FrictionType.WORD_SCRAMBLE in config.activeFrictions,
                    onCheckedChange = { on -> setFriction(FrictionType.WORD_SCRAMBLE, on) }
                ) {
                    DifficultyNote("Words are 6–9 letters. Each wrong attempt reshuffles the letters.")
                }

                // PIN
                FrictionCard(
                    emoji = "🔐", title = "PIN code",
                    description = "A 6-digit PIN only you know. Set it to something you won't type on impulse.",
                    checked = FrictionType.PIN in config.activeFrictions,
                    onCheckedChange = { on ->
                        if (on && config.pinHash.isBlank()) showPinSetup = true
                        else setFriction(FrictionType.PIN, on)
                    }
                ) {
                    val pinChangeLocked = config.pinHash.isNotBlank() && StrictModeGate.isSettingsLockedByPlan(config)
                    OutlinedButton(
                        onClick = { showPinSetup = true },
                        enabled = !pinChangeLocked,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = if (pinChangeLocked) 0.2f else 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                    ) {
                        Text(if (config.pinHash.isBlank()) "Set PIN" else "Change PIN", fontWeight = FontWeight.SemiBold)
                    }
                    if (pinChangeLocked) {
                        Spacer(Modifier.height(6.dp))
                        Text("Locked by your Active Plan until it ends.", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    }
                }

                // Location lock
                FrictionCard(
                    emoji = "📍", title = "Location lock",
                    description = "Add locations where your blocks become impossible to disable. Being inside a zone is an unbreakable lock — no challenge, no bypass.",
                    checked = FrictionType.LOCATION_LOCK in config.activeFrictions,
                    onCheckedChange = { on -> setFriction(FrictionType.LOCATION_LOCK, on) }
                ) {
                    LocationPermissionStatus(
                        hasForeground = hasForegroundLocation,
                        hasFull = hasFullLocationAccess,
                        onGrantForeground = onForegroundLocationRequest,
                        onGrantBackground = onBackgroundLocationRequest
                    )
                    Spacer(Modifier.height(10.dp))
                    LocationZoneManager(
                        zones = config.locationZones,
                        zoneRemovalLocked = StrictModeGate.isSettingsLockedByPlan(config),
                        hasForegroundLocation = hasForegroundLocation,
                        onPermissionStateChanged = onPermissionRefresh,
                        onZonesChanged = { updated ->
                            if (updated.size < config.locationZones.size && StrictModeGate.isSettingsLockedByPlan(config)) return@LocationZoneManager
                            val newConfig = config.copy(locationZones = updated)
                            BlockerRepository.setStrictMode(newConfig)
                            GeofenceManager.sync(context, updated)
                        }
                    )
                }

                // Active Plan
                FrictionCard(
                    emoji = "🗓️", title = "Active Plan",
                    description = "Commit to a length of time. While a plan is running, Strict Mode's settings are frozen — no turning off frictions, no PIN changes, no exceptions until it ends.",
                    checked = FrictionType.PLAN_LOCK in config.activeFrictions,
                    switchEnabled = !config.isPlanActive(nowMillis),
                    onCheckedChange = { on ->
                        if (on) showPlanPicker = true
                        else if (!config.isPlanActive()) setFriction(FrictionType.PLAN_LOCK, false)
                    }
                ) {
                    ActivePlanStatus(config = config, nowMillis = nowMillis, onChangePlan = { showPlanPicker = true })
                }

                // Typing pledge
                FrictionCard(
                    emoji = "✍️", title = "Typing pledge",
                    description = "Type a full sentence exactly to unlock. The act of typing slows your brain down enough to reconsider.",
                    checked = FrictionType.TYPING_PLEDGE in config.activeFrictions,
                    onCheckedChange = { on -> setFriction(FrictionType.TYPING_PLEDGE, on) }
                ) {
                    OutlinedButton(
                        onClick = { showPledgeEdit = true },
                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                    ) {
                        Text("Edit phrase", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showPinSetup) {
        PinSetupDialog(
            onDismiss = { showPinSetup = false },
            onSaved = { hash ->
                BlockerRepository.setStrictMode(
                    config.copy(pinHash = hash, activeFrictions = config.activeFrictions + FrictionType.PIN)
                )
                showPinSetup = false
            }
        )
    }
    if (showPledgeEdit) {
        PledgeEditDialog(
            current = config.pledgePhrase,
            onDismiss = { showPledgeEdit = false },
            onSaved = { phrase ->
                BlockerRepository.setStrictMode(config.copy(pledgePhrase = phrase))
                showPledgeEdit = false
            }
        )
    }
    if (showPlanPicker) {
        PlanPickerDialog(
            onDismiss = { showPlanPicker = false },
            onPlanChosen = { durationMillis, label ->
                BlockerRepository.startStrictPlan(durationMillis, label)
                showPlanPicker = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FRICTION CARD  (used inside Custom sheet)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrictionCard(
    emoji: String,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchEnabled: Boolean = true,
    extraContent: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = if (checked) androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.25f)) else null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(emoji, fontSize = 24.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = TextTertiary, lineHeight = 18.sp)
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = switchEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentBlue)
                )
            }
            AnimatedVisibility(
                visible = checked,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = TextTertiary.copy(alpha = 0.10f))
                    Spacer(Modifier.height(14.dp))
                    extraContent()
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SMALL HELPERS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DurationPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun DifficultyNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(TextTertiary.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextTertiary, lineHeight = 17.sp)
    }
}

@Composable
private fun WarningNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AccentRed.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = AccentRed, lineHeight = 17.sp)
    }
}

private fun formatSeconds(s: Int): String = when {
    s < 60 -> "${s}s"
    s % 60 == 0 -> "${s / 60}m"
    else -> "${s / 60}m ${s % 60}s"
}

// ─────────────────────────────────────────────────────────────────────────────
// PIN + PLEDGE DIALOGS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PinSetupDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurfaceAlt,
        title = { Text("Set your PIN", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Use 6 digits you won't type reflexively under pressure.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { pin = it; error = null } },
                    label = { Text("6-digit PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { confirm = it; error = null } },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pin.length != 6 -> error = "PIN must be 6 digits"
                        pin != confirm  -> error = "PINs don't match"
                        else            -> onSaved(PinHasher.hash(pin))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun PledgeEditDialog(current: String, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurfaceAlt,
        title = { Text("Pledge phrase", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("You'll have to type this out exactly before any block can be removed.", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("What you'll have to type") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onSaved(text) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVE PLAN
// ─────────────────────────────────────────────────────────────────────────────

private fun formatPlanRemaining(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000).coerceAtLeast(0)
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0    -> "${days}d ${hours}h ${minutes}m"
        hours > 0   -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else        -> "${seconds}s"
    }
}

@Composable
private fun ActivePlanStatus(config: StrictModeConfig, nowMillis: Long, onChangePlan: () -> Unit) {
    if (config.isPlanActive(nowMillis)) {
        val remaining = config.planActiveUntil - nowMillis
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AccentBlue.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(config.planLabel.ifBlank { "Active Plan" }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = AccentBlue)
            Text("${formatPlanRemaining(remaining)} remaining — Strict Mode settings are locked until then.", style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 17.sp)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("No plan running. Choose a length below to lock in your commitment.", style = MaterialTheme.typography.bodySmall, color = TextTertiary, lineHeight = 17.sp)
            OutlinedButton(
                onClick = onChangePlan,
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Choose a plan", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanPickerDialog(onDismiss: () -> Unit, onPlanChosen: (Long, String) -> Unit) {
    var showCustom by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurfaceAlt,
        title = { Text("Choose a plan", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Once started, Strict Mode settings are frozen until the plan ends. Pick a length you actually want to commit to.",
                    style = MaterialTheme.typography.bodySmall, color = TextTertiary, lineHeight = 17.sp
                )
                PlanOptionRow("📌", "24-Hour Lock", "Locked in for a full day") { onPlanChosen(24L * 60 * 60 * 1000, "24-Hour Lock") }
                PlanOptionRow("⚙️", "Custom", "Pick your own length, up to ${StrictModeConfig.MAX_CUSTOM_PLAN_DAYS} days") { showCustom = true }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )

    if (showCustom) {
        CustomPlanDialog(
            onDismiss = { showCustom = false },
            onConfirm = { days, hours, label ->
                onPlanChosen((days * 24L + hours) * 60 * 60 * 1000, label)
            }
        )
    }
}

@Composable
private fun PlanOptionRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AccentBlue.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(emoji, fontSize = 22.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
    }
}

@Composable
private fun CustomPlanDialog(onDismiss: () -> Unit, onConfirm: (days: Int, hours: Int, label: String) -> Unit) {
    var daysText by remember { mutableStateOf("1") }
    var hoursText by remember { mutableStateOf("0") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardSurfaceAlt,
        title = { Text("Custom plan length", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Maximum ${StrictModeConfig.MAX_CUSTOM_PLAN_DAYS} days — long enough to commit, capped so a typo can't lock you out for longer than you meant.",
                    style = MaterialTheme.typography.bodySmall, color = TextTertiary, lineHeight = 17.sp
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = daysText, onValueChange = { daysText = it.filter(Char::isDigit); error = null }, label = { Text("Days") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = hoursText, onValueChange = { hoursText = it.filter(Char::isDigit); error = null }, label = { Text("Hours") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = daysText.toIntOrNull() ?: 0
                    val hours = hoursText.toIntOrNull() ?: 0
                    val totalHours = days * 24 + hours
                    when {
                        totalHours <= 0 -> error = "Enter a length greater than zero"
                        days > StrictModeConfig.MAX_CUSTOM_PLAN_DAYS -> error = "Max is ${StrictModeConfig.MAX_CUSTOM_PLAN_DAYS} days"
                        else -> {
                            val label = buildString {
                                if (days > 0) append("${days}d ")
                                if (hours > 0) append("${hours}h ")
                            }.trim().ifBlank { "Custom plan" }
                            onConfirm(days, hours, label)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text("Start plan", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// LOCATION PERMISSION STATUS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocationPermissionStatus(hasForeground: Boolean, hasFull: Boolean, onGrantForeground: () -> Unit, onGrantBackground: () -> Unit) {
    when {
        hasFull -> {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AccentTeal.copy(alpha = 0.10f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("✅ Location access granted — zones will work even when the app is closed.", style = MaterialTheme.typography.bodySmall, color = AccentTeal, lineHeight = 17.sp)
            }
        }
        hasForeground -> {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AccentRed.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚠️ One more step: set location to \"Allow all the time\" so zones work in the background.", style = MaterialTheme.typography.bodySmall, color = AccentRed.copy(alpha = 0.85f), lineHeight = 17.sp)
                OutlinedButton(onClick = onGrantBackground, border = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)) {
                    Text("Open settings", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AccentRed.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚠️ Location access is required for zones to work. Tap below to grant it.", style = MaterialTheme.typography.bodySmall, color = AccentRed.copy(alpha = 0.85f), lineHeight = 17.sp)
                Button(onClick = onGrantForeground, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                    Text("Grant location access", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOCATION ZONE MANAGER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocationZoneManager(
    zones: List<LocationZone>,
    zoneRemovalLocked: Boolean = false,
    hasForegroundLocation: Boolean = false,
    onPermissionStateChanged: () -> Unit = {},
    onZonesChanged: (List<LocationZone>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMapPicker by remember { mutableStateOf(false) }
    var mapInitialPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var mapInitialName by remember { mutableStateOf("") }
    var fetchingLocation by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onPermissionStateChanged()
        if (granted) { mapInitialPoint = null; mapInitialName = ""; showMapPicker = true }
    }

    fun openMapBlank() {
        if (hasForegroundLocation) { mapInitialPoint = null; mapInitialName = ""; showMapPicker = true }
        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun openMapAtMyLocation() {
        if (!hasForegroundLocation) { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION); return }
        fetchingLocation = true
        scope.launch {
            val loc = CurrentLocationHelper.fetch(context)
            fetchingLocation = false
            if (loc != null) { mapInitialPoint = GeoPoint(loc.latitude, loc.longitude); mapInitialName = "My location"; showMapPicker = true }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (zones.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TextTertiary.copy(alpha = 0.07f)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text("No zones added yet. Add a place — home, school, office — and your blocks become unbypassable there.", style = MaterialTheme.typography.bodySmall, color = TextTertiary, lineHeight = 17.sp)
            }
        } else {
            zones.forEach { zone ->
                LocationZoneRow(zone = zone, deleteLocked = zoneRemovalLocked, onDelete = { onZonesChanged(zones.filter { it.id != zone.id }) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { openMapBlank() }, modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Zone on Map", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = { openMapAtMyLocation() }, enabled = !fetchingLocation, modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentBlue.copy(alpha = if (fetchingLocation) 0.2f else 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue), shape = RoundedCornerShape(12.dp)
            ) {
                if (fetchingLocation) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentBlue)
                else Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (fetchingLocation) "Finding you…" else "Use my location", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }

    if (showMapPicker) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showMapPicker = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize()) {
                LocationPickerScreen(
                    onBack = { showMapPicker = false },
                    onZoneSaved = { zone -> onZonesChanged(zones + zone); showMapPicker = false },
                    initialLocation = mapInitialPoint,
                    initialName = mapInitialName
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOCATION ZONE ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocationZoneRow(zone: LocationZone, deleteLocked: Boolean = false, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TextTertiary.copy(alpha = 0.07f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(zone.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                "%.5f, %.5f · ${zone.radiusMeters.toInt()}m radius".format(zone.latitude, zone.longitude),
                style = MaterialTheme.typography.bodySmall, color = TextTertiary
            )
        }
        IconButton(onClick = onDelete, enabled = !deleteLocked, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove zone", tint = AccentRed.copy(alpha = if (deleteLocked) 0.25f else 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}
