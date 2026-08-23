package com.allinone.blocker.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockEngine
import com.allinone.blocker.data.BlockPreset
import com.allinone.blocker.data.BlockRule
import com.allinone.blocker.data.BlockRuleType
import com.allinone.blocker.data.BlockedApp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.StrictModeGate
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.CardSurfaceAlt
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary

// ─────────────────────────────────────────────────────────────────────────────
// PRESET DEFINITIONS
// Each preset has a label, icon, colour, description, and the rules/protection
// it applies. This is the single source of truth — changing it here changes
// everything automatically.
// ─────────────────────────────────────────────────────────────────────────────

private data class PresetDef(
    val preset: BlockPreset,
    val label: String,
    val tagline: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val defaultRules: List<BlockRule>
)

private val PRESETS = listOf(
    PresetDef(
        preset      = BlockPreset.MINDFUL,
        label       = "Mindful use",
        tagline     = "30 min/day allowed",
        description = "You can still open this app, but once you've used it for 30 minutes today it closes. Good for apps you want to cut back on, not quit cold turkey.",
        icon        = Icons.Filled.Psychology,
        color       = AccentTeal,
        defaultRules = listOf(BlockRule(type = BlockRuleType.DAILY_LIMIT, limitMinutes = 30))
    ),
    PresetDef(
        preset      = BlockPreset.HARD_LIMITS,
        label       = "Hard limits",
        tagline     = "Blocked outside set hours",
        description = "You decide a window when this app is allowed (e.g. 6 PM–8 PM). Outside that window it's closed. Great for apps you want to use at a specific time and nowhere else.",
        icon        = Icons.Filled.AccessTime,
        color       = AccentBlue,
        defaultRules = listOf(BlockRule(type = BlockRuleType.TIME_INTERVAL, startMinutes = 18 * 60, endMinutes = 20 * 60))
    ),
    PresetDef(
        preset      = BlockPreset.FULLY_BLOCKED,
        label       = "Fully blocked",
        tagline     = "No access, no exceptions",
        description = "This app is completely off-limits. Tapping it does nothing — it's treated like it doesn't exist. Use this for apps you want out of your life entirely.",
        icon        = Icons.Filled.Block,
        color       = AccentRed,
        defaultRules = emptyList() // empty rules = always blocked (BlockEngine rule)
    ),
    PresetDef(
        preset      = BlockPreset.CUSTOM,
        label       = "Custom",
        tagline     = "Fine-tune your own rules",
        description = "Mix and match rules yourself. For power users who want exact control over daily limits, session caps, open counts, cooldowns, and more.",
        icon        = Icons.Filled.Lock,
        color       = AccentAmber,
        defaultRules = emptyList()
    )
)

private fun defFor(preset: BlockPreset) = PRESETS.first { it.preset == preset }

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppRulesScreen(packageName: String?, isNew: Boolean = false, onBack: () -> Unit, onOpenStrictMode: () -> Unit) {
    val context = LocalContext.current
    val haptics = com.allinone.blocker.ui.motion.rememberHaptics()

    // ── Load existing saved app (null if this is a brand-new addition) ────
    val savedApp = remember {
        packageName?.let { BlockerRepository.appFor(it) }
    }

    // ── For new apps, seed app name/icon from InstalledApps ──────────────
    val deviceApp = remember(packageName) {
        packageName?.let { pkg -> InstalledApps.apps.value.firstOrNull { it.packageName == pkg } }
    }

    // ── Local draft — only committed to the repo when Save is tapped ─────
    // For new apps: start with a sensible blank (no preset selected yet).
    // For existing apps: start from what's already saved.
    var draft by remember {
        mutableStateOf(
            savedApp ?: packageName?.let { pkg ->
                BlockedApp(
                    packageName = pkg,
                    appName     = deviceApp?.label ?: pkg,
                    isReels     = InstalledApps.isReels(pkg),
                    preset      = BlockPreset.FULLY_BLOCKED
                )
            }
        )
    }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var customExpanded    by remember { mutableStateOf(false) }
    var saveState         by remember { mutableStateOf(SaveState.Idle) }
    // For new apps: tracks whether the user has explicitly picked a preset.
    // Prevents saving a default FULLY_BLOCKED without a conscious choice.
    var presetChosen      by remember { mutableStateOf(!isNew || savedApp != null) }

    val current = draft
    if (current == null) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text("App settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }) { pad -> Text("App not found.", Modifier.padding(pad).padding(16.dp)) }
        return
    }

    // ── Draft helpers — update local state only, no repo touch ───────────
    fun updateDraft(updated: BlockedApp) { draft = updated }

    fun applyPreset(def: PresetDef) {
        haptics.tap()
        updateDraft(
            current.copy(
                preset = def.preset,
                rules  = if (def.preset == BlockPreset.CUSTOM) current.rules else def.defaultRules
            )
        )
        presetChosen = true
        if (def.preset == BlockPreset.CUSTOM) customExpanded = true
    }

    // Strict Mode is a global, separate system (see StrictModeGate.guard) —
    // it protects turning off ANY block, not just this one app. So rather
    // than a per-app flag that silently did nothing unless Strict Mode also
    // happened to be configured, this screen just reflects the real global
    // state and links straight to where it's actually managed.
    val strictConfig by BlockerRepository.strictMode.collectAsState()
    val strictModeActive = strictConfig.enabled && strictConfig.activeFrictions.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIconOrLetter(icon = remember(current.packageName) { InstalledApps.iconFor(current.packageName) }, label = current.appName)
                        Text(current.appName, color = TextPrimary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgScreen, scrolledContainerColor = BgScreen)
            )
        },
        floatingActionButton = {
            SaveButton(
                state   = saveState,
                enabled = presetChosen,
                onClick = {
                    // Committing a NEW app's first-ever block only ever adds
                    // protection, so it never needs to go through Strict
                    // Mode — same asymmetry BlockedAppsScreen's on/off
                    // toggle already uses (turning ON is free, turning OFF
                    // is guarded). Editing an EXISTING app's rules, though,
                    // can loosen or remove a block entirely — exactly the
                    // "weakening your setup" Active Plan (and every other
                    // Strict Mode friction) exists to stop. Previously this
                    // called upsertApp() directly with no guard at all,
                    // which meant Active Plan could be fully bypassed just
                    // by editing a block's rules from this screen instead
                    // of toggling it off from the app list.
                    val save: () -> Unit = {
                        saveState = SaveState.Loading
                        BlockerRepository.upsertApp(current)
                        saveState = SaveState.Done
                    }
                    if (savedApp == null) {
                        save()
                    } else {
                        StrictModeGate.guard(save)
                    }
                },
                // Fires automatically a moment after "Saved" — sends the
                // user back to the app list instead of leaving them on
                // this screen needing a second tap on Back.
                onReset = onBack
            )
        },
        containerColor = BgScreen
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Section header
            Text(
                if (isNew) "Choose how to block this app" else "How do you want to block this?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                if (isNew) "Pick an option below, then tap Save to add the block."
                else "Pick the option that matches your goal. You can change it any time.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(4.dp))

            // ── Preset cards — update draft only ──────────────────────────
            // For new apps, nothing is pre-selected — user must make a choice.
            PRESETS.forEach { def ->
                PresetCard(
                    def      = def,
                    selected = presetChosen && current.preset == def.preset,
                    onClick  = { applyPreset(def) }
                )
            }

            // ── Custom rule editor (only visible when Custom is selected) ─
            if (current.preset == BlockPreset.CUSTOM) {
                Spacer(Modifier.height(4.dp))
                CustomRuleSection(
                    app            = current,
                    expanded       = customExpanded,
                    onToggleExpand = { customExpanded = !customExpanded },
                    onAddRule      = { showAddRuleDialog = true },
                    onChangeRule   = { index, updated ->
                        val newRules = current.rules.toMutableList()
                        newRules[index] = updated
                        updateDraft(current.copy(rules = newRules))
                    },
                    onDeleteRule   = { index ->
                        val newRules = current.rules.toMutableList()
                        newRules.removeAt(index)
                        updateDraft(current.copy(rules = newRules))
                    },
                    context = context
                )
            }

            // ── Strict Mode link — teleports to the real settings screen ──
            Spacer(Modifier.height(4.dp))
            StrictModeLinkCard(
                strictModeOn = strictModeActive,
                onClick      = onOpenStrictMode
            )

            // ── Hint shown when preset not yet chosen ─────────────────────
            if (isNew && !presetChosen) {
                Text(
                    "Choose an option above to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Bottom padding so content clears the FAB ──────────────────
            Spacer(Modifier.height(88.dp))
        }
    }

    if (showAddRuleDialog) {
        AddRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onPick    = { type ->
                showAddRuleDialog = false
                val newRules = current.rules.toMutableList()
                newRules.add(BlockRule(type = type))
                updateDraft(current.copy(rules = newRules))
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PRESET CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PresetCard(def: PresetDef, selected: Boolean, onClick: () -> Unit) {
    // Animate every colour that changes on selection, instead of snapping
    // instantly — this is what makes the state change read as a deliberate
    // response to the tap rather than "did anything happen at all?".
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue   = if (selected) def.color else CardSurfaceAlt,
        animationSpec = com.allinone.blocker.ui.motion.MotionSpecs.standard(),
        label = "presetBorderColor"
    )
    val borderWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue   = if (selected) 2.dp else 0.5.dp,
        animationSpec = com.allinone.blocker.ui.motion.MotionSpecs.standard(),
        label = "presetBorderWidth"
    )
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue   = if (selected) def.color.copy(alpha = 0.10f) else CardSurface,
        animationSpec = com.allinone.blocker.ui.motion.MotionSpecs.standard(),
        label = "presetBgColor"
    )
    val iconBubbleAlpha by animateFloatAsState(
        targetValue   = if (selected) 0.22f else 0.14f,
        animationSpec = com.allinone.blocker.ui.motion.MotionSpecs.standard(),
        label = "presetIconBubbleAlpha"
    )
    val labelColor by androidx.compose.animation.animateColorAsState(
        targetValue   = if (selected) def.color else TextPrimary,
        animationSpec = com.allinone.blocker.ui.motion.MotionSpecs.standard(),
        label = "presetLabelColor"
    )
    val descriptionColor by androidx.compose.animation.animateColorAsState(
        targetValue   = if (selected) TextPrimary else TextSecondary,
        animationSpec = com.allinone.blocker.ui.motion.MotionSpecs.standard(),
        label = "presetDescriptionColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // Tactile press-squeeze instead of a plain clickable — the card
            // "gives" slightly under a finger, the same feedback used for
            // the Strict Mode card below it.
            .pressable(onClick = onClick),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(width = borderWidth, color = borderColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon bubble
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(def.color.copy(alpha = iconBubbleAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = def.icon,
                    contentDescription = null,
                    tint = def.color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        def.label,
                        style    = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color    = labelColor
                    )
                    // Tagline pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(def.color.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            def.tagline,
                            style  = MaterialTheme.typography.labelSmall,
                            color  = def.color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    def.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = descriptionColor
                )
            }

            // Checkmark pops in with a scale+fade rather than abruptly
            // appearing — a small flourish that draws the eye right to the
            // moment selection happened.
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = androidx.compose.animation.scaleIn(
                    initialScale = 0.4f,
                    animationSpec = com.allinone.blocker.ui.motion.MotionSpecs.expressive()
                ) + androidx.compose.animation.fadeIn(animationSpec = com.allinone.blocker.ui.motion.MotionSpecs.standard()),
                exit = androidx.compose.animation.fadeOut(animationSpec = com.allinone.blocker.ui.motion.MotionSpecs.standard())
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = def.color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CUSTOM RULE SECTION (collapsible)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomRuleSection(
    app: BlockedApp,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddRule: () -> Unit,
    onChangeRule: (Int, BlockRule) -> Unit,
    onDeleteRule: (Int) -> Unit,
    context: android.content.Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Header row (always visible — tap to expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Custom rules",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        if (app.rules.isEmpty()) "No rules yet — tap to add one"
                        else "${app.rules.size} rule${if (app.rules.size == 1) "" else "s"} configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = TextTertiary
                )
            }

            // Collapsible content
            AnimatedVisibility(
                visible = expanded,
                enter   = expandVertically(),
                exit    = shrinkVertically()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = TextTertiary.copy(alpha = 0.15f))
                    Spacer(Modifier.height(2.dp))

                    if (app.rules.isEmpty()) {
                        Text(
                            "No rules yet. An app with no rules is always blocked while the switch is on. Add a rule to allow limited access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    app.rules.forEachIndexed { index, rule ->
                        RuleCard(
                            rule     = rule,
                            context  = context,
                            onChange = { updated -> onChangeRule(index, updated) },
                            onDelete = { onDeleteRule(index) }
                        )
                    }

                    OutlinedButton(
                        onClick  = onAddRule,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp)
                    ) { Text("+ Add rule") }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STRICT MODE LINK CARD
//
// "Hard to undo" isn't something this screen can grant on its own — the only
// thing that actually stands between a person and turning a block off is
// Strict Mode's challenge (see StrictModeGate.guard, which is global: it
// protects every block, not a per-app flag). The old version here was a
// switch that flipped a per-app value with no real challenge behind it — a
// promise the app couldn't back up on its own. This card is honest instead:
// it shows the real state of Strict Mode and always leads straight to where
// it's actually configured, the same "summary row → dedicated manager"
// pattern used for Whitelist on the Lockdown screen.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StrictModeLinkCard(strictModeOn: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .pressable(onClick = onClick),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (strictModeOn) AccentAmber.copy(alpha = 0.10f) else CardSurface
        ),
        border   = BorderStroke(
            width = if (strictModeOn) 1.5.dp else 0.5.dp,
            color = if (strictModeOn) AccentAmber else CardSurfaceAlt
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentAmber.copy(alpha = if (strictModeOn) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = AccentAmber,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Make this hard to undo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (strictModeOn) AccentAmber else TextPrimary
                )
                Text(
                    if (strictModeOn)
                        "Strict Mode is on — disabling or deleting any block requires passing a challenge first. Tap to manage."
                    else
                        "Turn on a Strict Mode challenge so blocks can't be casually switched off. Tap to set it up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Strict Mode settings",
                tint = TextTertiary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EXISTING RULE CARD (used inside Custom section)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RuleCard(
    rule: BlockRule,
    context: android.content.Context,
    onChange: (BlockRule) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(0.dp),
        border   = BorderStroke(0.5.dp, CardSurfaceAlt)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ruleTitle(rule.type),
                    Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete rule", tint = TextTertiary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))

            when (rule.type) {
                BlockRuleType.PERMANENT ->
                    Text("This app is fully blocked with no exceptions.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                BlockRuleType.TIME_INTERVAL -> {
                    Text("Blocked between:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            pickTime(context, rule.startMinutes) { m -> onChange(rule.copy(startMinutes = m)) }
                        }, shape = RoundedCornerShape(8.dp)) { Text("From ${BlockEngine.formatMinutes(rule.startMinutes)}") }
                        OutlinedButton(onClick = {
                            pickTime(context, rule.endMinutes) { m -> onChange(rule.copy(endMinutes = m)) }
                        }, shape = RoundedCornerShape(8.dp)) { Text("To ${BlockEngine.formatMinutes(rule.endMinutes)}") }
                    }
                }

                BlockRuleType.DAILY_LIMIT ->
                    RuleStepper("Minutes allowed per day", rule.limitMinutes, 5, 5) { onChange(rule.copy(limitMinutes = it)) }

                BlockRuleType.SESSION_LIMIT -> {
                    RuleStepper("Minutes per session", rule.limitMinutes, 5, 1) { onChange(rule.copy(limitMinutes = it)) }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = CardSurfaceAlt, thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))
                    SessionWindowSelector(rule.sessionWindowMinutes) {
                        onChange(rule.copy(sessionWindowMinutes = it))
                    }
                }

                BlockRuleType.OPEN_COUNT ->
                    RuleStepper("Opens allowed today", rule.count, 1, 1) { onChange(rule.copy(count = it)) }

                BlockRuleType.COOLDOWN ->
                    RuleStepper("Minutes between opens", rule.cooldownMinutes, 5, 1) { onChange(rule.copy(cooldownMinutes = it)) }
            }
        }
    }
}

@Composable
private fun RuleStepper(label: String, value: Int, step: Int, min: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        OutlinedButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) { Text("−") }
        Spacer(Modifier.width(8.dp))
        Text("$value", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = { onChange(value + step) },
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) { Text("+") }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SESSION WINDOW SELECTOR (SESSION_LIMIT only)
//
// Lets the user set how long a "session" lasts — the stretch of time their
// usage keeps adding up across closes/reopens before it clears itself out.
// Deliberately its own small, pill-styled control (not another plain
// RuleStepper) so it reads as a distinct, considered setting rather than
// just a second number next to the first — this is the one place in the app
// explaining WHY the block can't be dodged by closing and reopening.
// Always whole hours: min 1h, +/- 1h per tap, matching the requirement that
// this only ever moves in 1-hour increments with a 1-hour default.
// ─────────────────────────────────────────────────────────────────────────────

private const val MIN_SESSION_WINDOW_HOURS = 1

@Composable
private fun SessionWindowSelector(windowMinutes: Int, onChange: (Int) -> Unit) {
    val hours = (windowMinutes / 60).coerceAtLeast(MIN_SESSION_WINDOW_HOURS)

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(AccentBlue.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AccessTime,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Session length",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )

            SessionWindowStepButton(symbol = "−", enabled = hours > MIN_SESSION_WINDOW_HOURS) {
                onChange((hours - 1).coerceAtLeast(MIN_SESSION_WINDOW_HOURS) * 60)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentBlue.copy(alpha = 0.14f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${hours}h",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue
                )
            }
            Spacer(Modifier.width(8.dp))
            SessionWindowStepButton(symbol = "+", enabled = true) {
                onChange((hours + 1) * 60)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Time in this app adds up for $hours ${if (hours == 1) "hour" else "hours"} — closing and reopening it won't reset the clock, only waiting out the full $hours ${if (hours == 1) "hour" else "hours"} does.",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun SessionWindowStepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) CardSurfaceAlt else CardSurfaceAlt.copy(alpha = 0.4f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (enabled) TextPrimary else TextTertiary
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ADD RULE DIALOG (for Custom mode)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onPick: (BlockRuleType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BlockRuleType.values().forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(type) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text(ruleTitle(type), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(ruleDescription(type), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────

private fun ruleTitle(type: BlockRuleType): String = when (type) {
    BlockRuleType.TIME_INTERVAL -> "Time window block"
    BlockRuleType.DAILY_LIMIT   -> "Daily time limit"
    BlockRuleType.SESSION_LIMIT -> "Session time limit"
    BlockRuleType.OPEN_COUNT    -> "Open count limit"
    BlockRuleType.COOLDOWN      -> "Cooldown between opens"
    BlockRuleType.PERMANENT     -> "Permanent block"
}

private fun ruleDescription(type: BlockRuleType): String = when (type) {
    BlockRuleType.TIME_INTERVAL -> "Blocked during a recurring daily window (e.g. 9 AM–5 PM)"
    BlockRuleType.DAILY_LIMIT   -> "Blocks after you've used the app for X minutes today"
    BlockRuleType.SESSION_LIMIT -> "Blocks after X minutes of use in a session — reopening the app won't reset it"
    BlockRuleType.OPEN_COUNT    -> "Blocks after you've opened it N times today"
    BlockRuleType.COOLDOWN      -> "Forces a wait between every time you open it"
    BlockRuleType.PERMANENT     -> "Blocked at all times with no exceptions"
}

private fun pickTime(context: android.content.Context, currentMinutes: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, h, m -> onPicked(h * 60 + m) },
        currentMinutes / 60,
        currentMinutes % 60,
        false
    ).show()
}
