package com.allinone.blocker.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.allinone.blocker.data.ProfileRepository
import com.allinone.blocker.data.StreakRepository
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentGreen
import com.allinone.blocker.ui.theme.AccentPurple
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.BgScreen
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextMuted
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// ProfileScreen.kt
//
// PLAIN-ENGLISH SUMMARY:
// This is the first version of the Profile tab. Today it's a foundation, not
// the full social vision — no accounts, no real leaderboard, no real chat
// yet. What it DOES do, all with real data already tracked elsewhere in the
// app (StreakRepository):
//
//   1. IDENTITY HEADER — a colored initial avatar + editable local nickname
//      + the date you started using the app.
//   2. LEVEL CARD — turns your current streak into a "level" (Newcomer →
//      Legend), with a progress bar toward the next one. This reuses the
//      exact same milestone numbers (3/7/14/30/60/100 days) that
//      StreaksScreen already celebrates, so the two screens always agree.
//   3. STAT PILLS — current streak, best streak seen recently, shields left.
//   4. ACHIEVEMENTS SHELF — one badge per milestone, unlocked the moment
//      you've ever reached it.
//   5. COMMUNITY SECTION — Leaderboard / Groups & Friends / Chat rows,
//      clearly marked "Soon". This is intentionally NOT wired to anything
//      yet — it exists so the social direction is visible in the app and
//      easy to build into later, without pretending those features work
//      today.
//
// WHAT NEEDS TO HAPPEN BEFORE STEP 5 CAN BE REAL:
// Leaderboards, groups, and chat all require people's data to leave their
// own phone and be compared with other people's — which needs a backend
// (a server + accounts), something this app doesn't have yet. Nothing to
// fix here; just the natural next big project once the foundation feels
// right.
// ─────────────────────────────────────────────────────────────────────────────

/** One rung on the level ladder. [daysNeeded] is the streak length required
 *  to REACH this level; the next entry's daysNeeded is the "next milestone"
 *  shown in the progress bar. Mirrors StreakRepository.MILESTONE_DAYS so the
 *  Profile tab and the Streaks screen always tell the same story. */
private data class LevelTier(val daysNeeded: Int, val title: String)

private val LEVEL_TIERS = listOf(
    LevelTier(0,   "Newcomer"),
    LevelTier(3,   "Rising"),
    LevelTier(7,   "Momentum"),
    LevelTier(14,  "Committed"),
    LevelTier(30,  "Disciplined"),
    LevelTier(60,  "Master"),
    LevelTier(100, "Legend"),
)

@Composable
fun ProfileScreen(onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current

    val displayName   by ProfileRepository.displayName.collectAsState()
    val avatarColorKey by ProfileRepository.avatarColorKey.collectAsState()
    val streak        by StreakRepository.streak.collectAsState()
    val shieldsLeft    by StreakRepository.shieldsAvailable.collectAsState()
    val shieldsCap     by StreakRepository.shieldsCap.collectAsState()
    val history        by StreakRepository.history.collectAsState()

    // "Best streak" derived from the 30-day rolling history — the longest
    // run of consecutive clean days on record — compared against today's
    // live streak, whichever is higher.
    val bestStreak = remember(history, streak) {
        var best = 0
        var run = 0
        history.sortedBy { it.first }.forEach { (_, clean) ->
            run = if (clean) run + 1 else 0
            if (run > best) best = run
        }
        maxOf(best, streak)
    }

    val currentTierIndex = remember(bestStreak) {
        LEVEL_TIERS.indexOfLast { bestStreak >= it.daysNeeded }.coerceAtLeast(0)
    }
    val currentTier = LEVEL_TIERS[currentTierIndex]
    val nextTier = LEVEL_TIERS.getOrNull(currentTierIndex + 1)
    val levelProgress = remember(bestStreak, currentTierIndex) {
        if (nextTier == null) 1f
        else {
            val span = (nextTier.daysNeeded - currentTier.daysNeeded).coerceAtLeast(1)
            ((bestStreak - currentTier.daysNeeded).toFloat() / span).coerceIn(0f, 1f)
        }
    }

    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(containerColor = BgScreen) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // ── TOP ROW: screen title + settings shortcut ──────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── IDENTITY HEADER ─────────────────────────────────────────────
            ProfileHeaderCard(
                displayName = displayName,
                avatarColorKey = avatarColorKey,
                joinedAtMillis = ProfileRepository.joinedAtMillis,
                onEditClick = { showEditDialog = true }
            )

            Spacer(Modifier.height(16.dp))

            // ── LEVEL CARD ───────────────────────────────────────────────────
            LevelCard(
                tierTitle = currentTier.title,
                levelNumber = currentTierIndex + 1,
                progress = levelProgress,
                bestStreak = bestStreak,
                nextTier = nextTier
            )

            Spacer(Modifier.height(16.dp))

            // ── STAT PILLS ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatPill(
                    modifier = Modifier.weight(1f),
                    value = "$streak",
                    label = "day streak",
                    color = AccentAmber
                )
                StatPill(
                    modifier = Modifier.weight(1f),
                    value = "$bestStreak",
                    label = "best streak",
                    color = AccentBlue
                )
                StatPill(
                    modifier = Modifier.weight(1f),
                    value = "$shieldsLeft/$shieldsCap",
                    label = "shields",
                    color = AccentTeal
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── ACHIEVEMENTS ─────────────────────────────────────────────────
            SectionLabel("Achievements")
            AchievementsShelf(bestStreak = bestStreak)

            Spacer(Modifier.height(20.dp))

            // ── COMMUNITY (preview / roadmap) ────────────────────────────────
            SectionLabel("Community")
            Text(
                "The social side of Present Tense — coming in a future update.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ComingSoonRow(
                        icon = Icons.Filled.Leaderboard,
                        iconColor = AccentBlue,
                        title = "Leaderboard",
                        subtitle = "See how your streak compares to friends",
                        context = context
                    )
                    RowDivider()
                    ComingSoonRow(
                        icon = Icons.Filled.Groups,
                        iconColor = AccentGreen,
                        title = "Groups & Friends",
                        subtitle = "Build accountability circles together",
                        context = context
                    )
                    RowDivider()
                    ComingSoonRow(
                        icon = Icons.Filled.Chat,
                        iconColor = AccentPurple,
                        title = "Chat",
                        subtitle = "Encourage each other, day to day",
                        context = context
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            currentName = displayName,
            currentColorKey = avatarColorKey,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, colorKey ->
                ProfileRepository.setDisplayName(name)
                ProfileRepository.setAvatarColor(colorKey)
                showEditDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AVATAR COLOR MAPPING
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun avatarColorFor(key: String): Color = when (key) {
    "teal"   -> AccentTeal
    "purple" -> AccentPurple
    "amber"  -> AccentAmber
    "green"  -> AccentGreen
    "red"    -> AccentRed
    else     -> AccentBlue
}

// ─────────────────────────────────────────────────────────────────────────────
// IDENTITY HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeaderCard(
    displayName: String,
    avatarColorKey: String,
    joinedAtMillis: Long,
    onEditClick: () -> Unit
) {
    val avatarColor = avatarColorFor(avatarColorKey)
    val joinedText = remember(joinedAtMillis) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(joinedAtMillis))
    }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.18f))
                    .border(2.dp, avatarColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.trim().take(1).ifBlank { "Y" }.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = avatarColor
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Member since $joinedText",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            IconButton(onClick = onEditClick) {
                Icon(Icons.Filled.EditNote, contentDescription = "Edit profile", tint = TextSecondary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LEVEL CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LevelCard(
    tierTitle: String,
    levelNumber: Int,
    progress: Float,
    bestStreak: Int,
    nextTier: LevelTier?
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Level $levelNumber",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        tierTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = AccentAmber,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AccentBlue,
                trackColor = TextMuted.copy(alpha = 0.18f)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (nextTier != null)
                    "$bestStreak / ${nextTier.daysNeeded} days to \"${nextTier.title}\""
                else
                    "Top level reached — legendary consistency.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STAT PILL
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatPill(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    color: Color
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ACHIEVEMENTS SHELF
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AchievementsShelf(bestStreak: Int) {
    val milestones = StreakRepository.MILESTONE_DAYS.sorted()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        milestones.take(3).forEach { days ->
            AchievementBadge(
                modifier = Modifier.weight(1f),
                days = days,
                unlocked = bestStreak >= days
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        milestones.drop(3).forEach { days ->
            AchievementBadge(
                modifier = Modifier.weight(1f),
                days = days,
                unlocked = bestStreak >= days
            )
        }
    }
}

@Composable
private fun AchievementBadge(
    modifier: Modifier = Modifier,
    days: Int,
    unlocked: Boolean
) {
    val badgeColor = if (unlocked) AccentAmber else TextMuted
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) CardSurface else CardSurface.copy(alpha = 0.6f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "$days-day",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (unlocked) TextPrimary else TextMuted
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMMUNITY ROW ("Soon" — not wired to anything yet)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ComingSoonRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    context: android.content.Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                Toast.makeText(context, "Coming soon — on the roadmap!", Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        }
        SoonChip()
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SoonChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AccentBlue.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            "Soon",
            style = MaterialTheme.typography.labelSmall,
            color = AccentBlue,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RowDivider() {
    androidx.compose.material3.HorizontalDivider(
        color = TextMuted.copy(alpha = 0.12f),
        modifier = Modifier.padding(start = 64.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION LABEL — matches SettingsScreen's SectionHeader styling
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = AccentBlue,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// EDIT PROFILE DIALOG — rename + pick avatar color
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditProfileDialog(
    currentName: String,
    currentColorKey: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorKey: String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var colorKey by remember { mutableStateOf(currentColorKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Avatar color",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileRepository.AVATAR_COLOR_KEYS.forEach { key ->
                        val c = avatarColorFor(key)
                        val selected = key == colorKey
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = TextPrimary,
                                    shape = CircleShape
                                )
                                .clickable { colorKey = key },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, colorKey) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
