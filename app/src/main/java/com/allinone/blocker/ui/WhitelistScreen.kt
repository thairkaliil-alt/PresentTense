package com.allinone.blocker.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.ui.motion.rememberHaptics
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgDarkest
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextTertiary

// ════════════════════════════════════════════════════════════════════════════
// The dedicated whitelist manager — this is what opens when someone taps the
// merged whitelist card on the Lockdown screen. Everything about picking which
// apps stay unblocked lives here in one place: a live count, a search box, and
// the full toggle list, all styled to match the rest of the app instead of the
// plain default Material look it used to have.
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) { InstalledApps.ensureLoaded(context) }
    val all by InstalledApps.apps.collectAsState()
    val loadingApps by InstalledApps.loading.collectAsState()

    val whitelist by BlockerRepository.whitelist.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = remember(all, query) {
        if (query.isBlank()) all
        else all.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = BgDarkest,
        topBar = {
            TopAppBar(
                title = { Text("Whitelist", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { InstalledApps.refresh(context) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh app list", tint = TextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDarkest)
            )
        }
    ) { pad ->
        LazyColumn(
            modifier            = Modifier.padding(pad).fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Info banner ───────────────────────────────────────────────
            item(key = "whitelist_info") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "Apps you turn on below stay open during any lockdown. Calls and texts are always allowed automatically, even if not listed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }

            // ── Live count ────────────────────────────────────────────────
            item(key = "whitelist_count") {
                Text(
                    if (whitelist.isEmpty()) "No apps whitelisted yet"
                    else "${whitelist.size} app${if (whitelist.size == 1) "" else "s"} allowed during lockdown",
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (whitelist.isEmpty()) TextTertiary else AccentTeal,
                    modifier   = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
            }

            // ── Search ────────────────────────────────────────────────────
            item(key = "whitelist_search") {
                OutlinedTextField(
                    value         = query,
                    onValueChange = { query = it },
                    placeholder   = { Text("Search apps…") },
                    singleLine    = true,
                    leadingIcon   = { Icon(Icons.Filled.Search, contentDescription = null) },
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(14.dp)
                )
            }

            if (all.isEmpty() && loadingApps) {
                item(key = "whitelist_loading") {
                    Text(
                        "Loading installed apps…",
                        Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            } else if (filtered.isEmpty()) {
                item(key = "whitelist_empty") {
                    Text(
                        "No apps match \"$query\"",
                        Modifier.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }

            items(filtered, key = { it.packageName }) { device ->
                WhitelistToggleRow(
                    packageName = device.packageName,
                    label       = device.label,
                    whitelisted = whitelist.contains(device.packageName),
                    onToggle    = { checked ->
                        if (checked) BlockerRepository.addToWhitelist(device.packageName)
                        else BlockerRepository.removeFromWhitelist(device.packageName)
                    },
                    // animateItem() smooths out the list whenever rows are
                    // added/removed here — e.g. typing in the search box
                    // above, which is what actually changes which rows are
                    // in this list (see note in WhitelistToggleRow).
                    modifier = Modifier.animateItem()
                )
            }

            item(key = "bottom_spacer") { Spacer(Modifier.size(12.dp)) }
        }
    }
}

// One row per installed app — colored to match the allowed/blocked state, with
// the same soft color-fade + status dot used elsewhere in the lockdown flow so
// the manager screen feels like part of the same feature, not a bolted-on page.
@Composable
private fun WhitelistToggleRow(
    packageName : String,
    label       : String,
    whitelisted : Boolean,
    onToggle    : (Boolean) -> Unit,
    modifier    : Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val cardBg by animateColorAsState(
        targetValue   = if (whitelisted) AccentTeal.copy(alpha = 0.10f) else AccentRed.copy(alpha = 0.10f),
        animationSpec = tween(durationMillis = 400),
        label         = "whitelistRowBg"
    )
    val borderColor by animateColorAsState(
        targetValue   = if (whitelisted) AccentTeal.copy(alpha = 0.30f) else AccentRed.copy(alpha = 0.25f),
        animationSpec = tween(durationMillis = 400),
        label         = "whitelistRowBorder"
    )

    val dotAlpha by if (!whitelisted) {
        val infinite = rememberInfiniteTransition(label = "whitelistDotBlink")
        infinite.animateFloat(
            initialValue  = 1f,
            targetValue   = 0.25f,
            animationSpec = infiniteRepeatable(
                animation  = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotBlink"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        border    = BorderStroke(1.5.dp, borderColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconOrLetter(packageName = packageName, label = label)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background((if (whitelisted) AccentTeal else AccentRed).copy(alpha = dotAlpha))
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text  = if (whitelisted) "Allowed in lockdown" else "Blocked in lockdown",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (whitelisted) AccentTeal else AccentRed
                    )
                }
            }
            Switch(
                checked         = whitelisted,
                onCheckedChange = { checked -> haptics.toggleTick(); onToggle(checked) },
                colors          = SwitchDefaults.colors(
                    checkedThumbColor    = Color.White,
                    checkedTrackColor    = AccentTeal,
                    checkedBorderColor   = AccentTeal,
                    uncheckedThumbColor  = TextTertiary,
                    uncheckedTrackColor  = BgDarkest,
                    uncheckedBorderColor = TextTertiary
                )
            )
        }
    }
}
