package com.allinone.blocker.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.allinone.blocker.data.ProtectionLevel
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
    val defaultRules: List<BlockRule>,
    val defaultProtection: ProtectionLevel
)

private val PRESETS = listOf(
    PresetDef(
        preset      = BlockPreset.MINDFUL,
        label       = "Mindful use",
        tagline     = "30 min/day allowed",
        description = "You can still open this app, but once you've used it for 30 minutes today it closes. Good for apps you want to cut back on, not quit cold turkey.",
        icon        = Icons.Filled.Psychology,
        color       = AccentTeal,
        defaultRules = listOf(BlockRule(type = BlockRuleType.DAILY_LIMIT, limitMinutes = 30)),
        defaultProtection = ProtectionLevel.SOFT
    ),
    PresetDef(
        preset      = BlockPreset.HARD_LIMITS,
        label       = "Hard limits",
        tagline     = "Blocked outside set hours",
        description = "You decide a window when this app is allowed (e.g. 6 PM–8 PM). Outside that window it's closed. Great for apps you want to use at a specific time and nowhere else.",
        icon        = Icons.Filled.AccessTime,
        color       = AccentBlue,
        defaultRules = listOf(BlockRule(type = BlockRuleType.TIME_INTERVAL, startMinutes = 18 * 60, endMinutes = 20 * 60)),
        defaultProtection = ProtectionLevel.NORMAL
    ),
    PresetDef(
        preset      = BlockPreset.FULLY_BLOCKED,
        label       = "Fully blocked",
        tagline     = "No access, no exceptions",
        description = "This app is completely off-limits. Tapping it does nothing — it's treated like it doesn't exist. Use this for apps you want out of your life entirely.",
        icon        = Icons.Filled.Block,
        color       = AccentRed,
        defaultRules = emptyList(), // empty rules = always blocked (BlockEngine rule)
        defaultProtection = ProtectionLevel.STRICT
    ),
    PresetDef(
        preset      = BlockPreset.CUSTOM,
        label       = "Custom",
        tagline     = "Fine-tune your own rules",
        description = "Mix and match rules yourself. For power users who want exact control over daily limits, session caps, open counts, cooldowns, and more.",
        icon        = Icons.Filled.Lock,
        color       = AccentAmber,
        defaultRules = emptyList(),
        defaultProtection = ProtectionLevel.NORMAL
    )
)

private fun defFor(preset: BlockPreset) = PRESETS.first { it.preset == preset }

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppRulesScreen(packageName: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    var app by remember {
        mutableStateOf(packageName?.let { BlockerRepository.appFor(it) })
    }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var customExpanded by remember { mutableStateOf(false) }

    val current = app
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

    fun save(updated: BlockedApp) {
        app = updated
        BlockerRepository.upsertApp(updated)
    }

    // When a preset card is tapped: apply its default rules + protection, save
    fun applyPreset(def: PresetDef) {
        save(
            current.copy(
                preset     = def.preset,
                rules      = if (def.preset == BlockPreset.CUSTOM) current.rules else def.defaultRules,
                protection = def.defaultProtection
            )
        )
        // Open the custom rule editor automatically when Custom is picked
        if (def.preset == BlockPreset.CUSTOM) customExpanded = true
    }

    val hardToUndoOn = current.protection >= ProtectionLevel.STRICT

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
                "How do you want to block this?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Pick the option that matches your goal. You can change it any time.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(4.dp))

            // ── Preset cards ──────────────────────────────────────────────
            PRESETS.forEach { def ->
                PresetCard(
                    def      = def,
                    selected = current.preset == def.preset,
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
                        save(current.copy(rules = newRules))
                    },
                    onDeleteRule   = { index ->
                        val newRules = current.rules.toMutableList()
                        newRules.removeAt(index)
                        save(current.copy(rules = newRules))
                    },
                    context = context
                )
            }

            // ── Hard-to-undo toggle ───────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            HardToUndoCard(
                checked   = hardToUndoOn,
                onChange  = { wantsHard ->
                    save(
                        current.copy(
                            protection = if (wantsHard) ProtectionLevel.STRICT else ProtectionLevel.SOFT
                        )
                    )
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddRuleDialog) {
        AddRuleDialog(
            onDismiss = { showAddRuleDialog = false },
            onPick    = { type ->
                showAddRuleDialog = false
                val newRules = current.rules.toMutableList()
                newRules.add(BlockRule(type = type))
                save(current.copy(rules = newRules))
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PRESET CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PresetCard(def: PresetDef, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) def.color else Color.Transparent
    val bgColor     = if (selected) def.color.copy(alpha = 0.10f) else CardSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(width = if (selected) 2.dp else 0.5.dp, color = if (selected) borderColor else CardSurfaceAlt),
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
                    .background(def.color.copy(alpha = if (selected) 0.22f else 0.14f)),
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
                        color    = if (selected) def.color else TextPrimary
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
                    color = if (selected) TextPrimary else TextSecondary
                )
            }

            // Checkmark when selected
            if (selected) {
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
// HARD-TO-UNDO TOGGLE CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HardToUndoCard(checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (checked) AccentAmber.copy(alpha = 0.10f) else CardSurface
        ),
        border   = BorderStroke(
            width = if (checked) 1.5.dp else 0.5.dp,
            color = if (checked) AccentAmber else CardSurfaceAlt
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
                    .background(AccentAmber.copy(alpha = if (checked) 0.22f else 0.14f)),
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
                    color = if (checked) AccentAmber else TextPrimary
                )
                Text(
                    "Disabling or editing this block will require passing a Strict Mode challenge first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked  = checked,
                onCheckedChange = onChange,
                colors   = SwitchDefaults.colors(
                    checkedThumbColor  = Color.White,
                    checkedTrackColor  = AccentAmber,
                    checkedBorderColor = AccentAmber
                )
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

                BlockRuleType.SESSION_LIMIT ->
                    RuleStepper("Minutes per session", rule.limitMinutes, 5, 1) { onChange(rule.copy(limitMinutes = it)) }

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
    BlockRuleType.SESSION_LIMIT -> "Blocks after you've had it open for X minutes in one go"
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
