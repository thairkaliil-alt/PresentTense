package com.allinone.blocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.allinone.blocker.data.BlockRule
import com.allinone.blocker.data.BlockRuleType
import com.allinone.blocker.data.BlockedApp
import com.allinone.blocker.data.BlockerRepository
import com.allinone.blocker.data.ProtectionLevel
import com.allinone.blocker.ui.motion.pressable
import com.allinone.blocker.ui.theme.AccentAmber
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentGreen
import com.allinone.blocker.ui.theme.AccentPurple
import com.allinone.blocker.ui.theme.AccentRed
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextPrimary
import com.allinone.blocker.ui.theme.TextSecondary
import com.allinone.blocker.ui.theme.TextTertiary
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODEL
// ─────────────────────────────────────────────────────────────────────────────

data class BlockingPreset(
    val id: String,
    val emoji: String,
    val name: String,
    val tagline: String,
    val description: String,
    val accentColor: Color,
    val appsToBlock: List<PresetApp>,
    val rule: BlockRule
)

data class PresetApp(
    val packageName: String,
    val displayName: String
)

// ─────────────────────────────────────────────────────────────────────────────
// PRESET DEFINITIONS
// ─────────────────────────────────────────────────────────────────────────────

val SocialMediaBreakPreset = BlockingPreset(
    id = "social_media_break",
    emoji = "📵",
    name = "Social Media Break",
    tagline = "Until you turn it off",
    description = "Blocks Instagram, TikTok, Facebook, YouTube, Snapchat, and X. Active until you manually switch it off — perfect for a proper digital detox.",
    accentColor = AccentRed,
    appsToBlock = listOf(
        PresetApp("com.instagram.android",      "Instagram"),
        PresetApp("com.zhiliaoapp.musically",   "TikTok"),
        PresetApp("com.facebook.katana",        "Facebook"),
        PresetApp("com.google.android.youtube", "YouTube"),
        PresetApp("com.snapchat.android",       "Snapchat"),
        PresetApp("com.twitter.android",        "X (Twitter)"),
        PresetApp("com.facebook.orca",          "Messenger"),
    ),
    rule = BlockRule(type = BlockRuleType.PERMANENT)
)

val ExamModePreset = BlockingPreset(
    id = "exam_mode",
    emoji = "📚",
    name = "Exam Mode",
    tagline = "All day, every day",
    description = "Locks out social media, games, and streaming apps for the full day. Strict protection is on — you'll need to face a challenge to undo it. Study hard.",
    accentColor = AccentBlue,
    appsToBlock = listOf(
        PresetApp("com.instagram.android",      "Instagram"),
        PresetApp("com.zhiliaoapp.musically",   "TikTok"),
        PresetApp("com.facebook.katana",        "Facebook"),
        PresetApp("com.google.android.youtube", "YouTube"),
        PresetApp("com.snapchat.android",       "Snapchat"),
        PresetApp("com.twitter.android",        "X (Twitter)"),
        PresetApp("com.reddit.frontpage",       "Reddit"),
        PresetApp("com.netflix.mediaclient",    "Netflix"),
        PresetApp("com.spotify.music",          "Spotify"),
        PresetApp("com.whatsapp",               "WhatsApp"),
        PresetApp("com.google.android.apps.gaming.arcade", "Play Games"),
    ),
    rule = BlockRule(type = BlockRuleType.PERMANENT)
)

val WindDownPreset = BlockingPreset(
    id = "wind_down",
    emoji = "🌙",
    name = "Wind Down",
    tagline = "9 PM – 7 AM every night",
    description = "Every night from 9 PM to 7 AM, social media and YouTube are automatically blocked. Your phone stays quiet so your brain can actually rest.",
    accentColor = AccentTeal,
    appsToBlock = listOf(
        PresetApp("com.instagram.android",      "Instagram"),
        PresetApp("com.zhiliaoapp.musically",   "TikTok"),
        PresetApp("com.facebook.katana",        "Facebook"),
        PresetApp("com.google.android.youtube", "YouTube"),
        PresetApp("com.snapchat.android",       "Snapchat"),
        PresetApp("com.twitter.android",        "X (Twitter)"),
        PresetApp("com.reddit.frontpage",       "Reddit"),
    ),
    rule = BlockRule(
        type = BlockRuleType.TIME_INTERVAL,
        startMinutes = 21 * 60,
        endMinutes   = 7 * 60
    )
)

val ClearYourMindPreset = BlockingPreset(
    id = "clear_your_mind",
    emoji = "🧘",
    name = "Clear Your Mind",
    tagline = "2 hours of quiet",
    description = "Blocks all social apps for 2 hours so you can breathe, think, or just exist without the noise. Great for when you're overwhelmed and need to reset.",
    accentColor = AccentAmber,
    appsToBlock = listOf(
        PresetApp("com.instagram.android",       "Instagram"),
        PresetApp("com.zhiliaoapp.musically",    "TikTok"),
        PresetApp("com.facebook.katana",         "Facebook"),
        PresetApp("com.google.android.youtube",  "YouTube"),
        PresetApp("com.snapchat.android",        "Snapchat"),
        PresetApp("com.twitter.android",         "X (Twitter)"),
        PresetApp("com.reddit.frontpage",        "Reddit"),
        PresetApp("com.google.android.apps.news","Google News"),
    ),
    rule = BlockRule(
        type = BlockRuleType.DAILY_LIMIT,
        limitMinutes = 0
    )
)

// ── NEW PRESETS ───────────────────────────────────────────────────────────────

val MealTimePreset = BlockingPreset(
    id = "meal_time",
    emoji = "🍽️",
    name = "Meal Time",
    tagline = "Phone down, be present",
    description = "The dinner table is the single highest-impact place to reclaim. Blocks the scroll-loop apps so you can actually be in the room with the people you're eating with.",
    accentColor = AccentAmber,
    appsToBlock = listOf(
        PresetApp("com.instagram.android",       "Instagram"),
        PresetApp("com.zhiliaoapp.musically",    "TikTok"),
        PresetApp("com.google.android.youtube",  "YouTube"),
        PresetApp("com.twitter.android",         "X (Twitter)"),
        PresetApp("com.reddit.frontpage",        "Reddit"),
        PresetApp("com.facebook.katana",         "Facebook"),
        PresetApp("com.snapchat.android",        "Snapchat"),
    ),
    rule = BlockRule(
        type = BlockRuleType.SESSION_LIMIT,
        limitMinutes = 60
    )
)

val DeepWorkPreset = BlockingPreset(
    id = "deep_work",
    emoji = "🧠",
    name = "Deep Work",
    tagline = "Flow state, protected",
    description = "For adults with real jobs and real deadlines. Kills every distraction for 90 minutes so your brain can actually sink into the work — not just look like it is.",
    accentColor = AccentPurple,
    appsToBlock = listOf(
        PresetApp("com.instagram.android",       "Instagram"),
        PresetApp("com.zhiliaoapp.musically",    "TikTok"),
        PresetApp("com.google.android.youtube",  "YouTube"),
        PresetApp("com.twitter.android",         "X (Twitter)"),
        PresetApp("com.reddit.frontpage",        "Reddit"),
        PresetApp("com.facebook.katana",         "Facebook"),
        PresetApp("com.snapchat.android",        "Snapchat"),
        PresetApp("com.whatsapp",                "WhatsApp"),
        PresetApp("com.discord",                 "Discord"),
        PresetApp("com.linkedin.android",        "LinkedIn"),
        PresetApp("com.google.android.apps.news","Google News"),
    ),
    rule = BlockRule(
        type = BlockRuleType.SESSION_LIMIT,
        limitMinutes = 90
    )
)

val GymModePreset = BlockingPreset(
    id = "gym_mode",
    emoji = "🏋️",
    name = "Gym Mode",
    tagline = "Between sets, not on your phone",
    description = "Research shows phone use between sets kills workout intensity and focus. This blocks the scroll apps so rest time stays rest time — not a TikTok session.",
    accentColor = AccentGreen,
    appsToBlock = listOf(
        PresetApp("com.instagram.android",       "Instagram"),
        PresetApp("com.zhiliaoapp.musically",    "TikTok"),
        PresetApp("com.google.android.youtube",  "YouTube"),
        PresetApp("com.twitter.android",         "X (Twitter)"),
        PresetApp("com.reddit.frontpage",        "Reddit"),
        PresetApp("com.snapchat.android",        "Snapchat"),
        PresetApp("com.facebook.katana",         "Facebook"),
    ),
    rule = BlockRule(
        type = BlockRuleType.SESSION_LIMIT,
        limitMinutes = 90
    )
)

val MorningRoutinePreset = BlockingPreset(
    id = "morning_routine",
    emoji = "🌅",
    name = "Morning Routine",
    tagline = "First hour belongs to you",
    description = "The first 30–60 minutes after you wake up sets your brain's dopamine baseline for the whole day. Don't hand it to an algorithm. Runs from 6 AM to 8 AM every morning.",
    accentColor = AccentGreen,
    appsToBlock = listOf(
        PresetApp("com.instagram.android",       "Instagram"),
        PresetApp("com.zhiliaoapp.musically",    "TikTok"),
        PresetApp("com.google.android.youtube",  "YouTube"),
        PresetApp("com.twitter.android",         "X (Twitter)"),
        PresetApp("com.reddit.frontpage",        "Reddit"),
        PresetApp("com.facebook.katana",         "Facebook"),
        PresetApp("com.snapchat.android",        "Snapchat"),
        PresetApp("com.google.android.apps.news","Google News"),
        PresetApp("com.discord",                 "Discord"),
    ),
    rule = BlockRule(
        type = BlockRuleType.TIME_INTERVAL,
        startMinutes = 6 * 60,
        endMinutes   = 8 * 60
    )
)

val NewsDoomscrollPreset = BlockingPreset(
    id = "news_doomscroll_detox",
    emoji = "📰",
    name = "News Detox",
    tagline = "Step away from the headlines",
    description = "Different from social media — this targets the anxiety-loop specifically. News, Reddit, and doomscroll apps. For when you notice you keep refreshing headlines and it's not helping.",
    accentColor = AccentRed,
    appsToBlock = listOf(
        PresetApp("com.google.android.apps.news","Google News"),
        PresetApp("com.reddit.frontpage",        "Reddit"),
        PresetApp("com.twitter.android",         "X (Twitter)"),
        PresetApp("com.flipboard.app",           "Flipboard"),
        PresetApp("com.microsoft.amp.apps.bingapps", "Bing News"),
        PresetApp("com.cnn.mobile.android.phone","CNN"),
        PresetApp("com.bbc.mobile.news.ww",      "BBC News"),
        PresetApp("uk.co.bbc.news",              "BBC News UK"),
    ),
    rule = BlockRule(type = BlockRuleType.PERMANENT)
)

val WeekendModePreset = BlockingPreset(
    id = "weekend_mode",
    emoji = "☀️",
    name = "Weekend Mode",
    tagline = "Sat & Sun, 10 AM – 8 PM",
    description = "Lighter touch for the weekend — not a lockdown, just a guardrail. Keeps the worst scroll-trap apps away during peak hours so weekends feel like weekends again.",
    accentColor = AccentTeal,
    appsToBlock = listOf(
        PresetApp("com.zhiliaoapp.musically",    "TikTok"),
        PresetApp("com.instagram.android",       "Instagram"),
        PresetApp("com.twitter.android",         "X (Twitter)"),
        PresetApp("com.reddit.frontpage",        "Reddit"),
        PresetApp("com.snapchat.android",        "Snapchat"),
    ),
    rule = BlockRule(
        type = BlockRuleType.TIME_INTERVAL,
        startMinutes = 10 * 60,
        endMinutes   = 20 * 60
    )
)

val DrivingPreset = BlockingPreset(
    id = "driving",
    emoji = "🚗",
    name = "Driving",
    tagline = "Eyes on the road",
    description = "Serious safety preset. Blocks everything except Maps and phone calls the moment you activate it. No notifications, no glances, no exceptions. You can wait.",
    accentColor = AccentRed,
    appsToBlock = listOf(
        PresetApp("com.instagram.android",       "Instagram"),
        PresetApp("com.zhiliaoapp.musically",    "TikTok"),
        PresetApp("com.google.android.youtube",  "YouTube"),
        PresetApp("com.twitter.android",         "X (Twitter)"),
        PresetApp("com.reddit.frontpage",        "Reddit"),
        PresetApp("com.facebook.katana",         "Facebook"),
        PresetApp("com.snapchat.android",        "Snapchat"),
        PresetApp("com.whatsapp",                "WhatsApp"),
        PresetApp("com.discord",                 "Discord"),
        PresetApp("com.facebook.orca",           "Messenger"),
        PresetApp("com.netflix.mediaclient",     "Netflix"),
        PresetApp("com.spotify.music",           "Spotify"),
        PresetApp("com.google.android.apps.news","Google News"),
    ),
    rule = BlockRule(type = BlockRuleType.PERMANENT)
)

val LateNightPreset = BlockingPreset(
    id = "late_night_doom",
    emoji = "🛑",
    name = "Late Night",
    tagline = "11 PM – 2 AM blackout",
    description = "The 11 PM–2 AM window is when doom-scrolling is most destructive — you're tired, your willpower is gone, and the algorithm knows it. This shuts the door before you fall in.",
    accentColor = AccentPurple,
    appsToBlock = listOf(
        PresetApp("com.instagram.android",       "Instagram"),
        PresetApp("com.zhiliaoapp.musically",    "TikTok"),
        PresetApp("com.google.android.youtube",  "YouTube"),
        PresetApp("com.twitter.android",         "X (Twitter)"),
        PresetApp("com.reddit.frontpage",        "Reddit"),
        PresetApp("com.facebook.katana",         "Facebook"),
        PresetApp("com.snapchat.android",        "Snapchat"),
        PresetApp("com.netflix.mediaclient",     "Netflix"),
        PresetApp("com.discord",                 "Discord"),
        PresetApp("com.google.android.apps.news","Google News"),
    ),
    rule = BlockRule(
        type = BlockRuleType.TIME_INTERVAL,
        startMinutes = 23 * 60,
        endMinutes   = 2 * 60
    )
)

val ALL_PRESETS = listOf(
    // Original four
    SocialMediaBreakPreset,
    ExamModePreset,
    WindDownPreset,
    ClearYourMindPreset,
    // New eight
    MealTimePreset,
    DeepWorkPreset,
    GymModePreset,
    MorningRoutinePreset,
    NewsDoomscrollPreset,
    WeekendModePreset,
    DrivingPreset,
    LateNightPreset,
)

// ─────────────────────────────────────────────────────────────────────────────
// MAIN SECTION COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsSection() {
    var selectedPreset by remember { mutableStateOf<BlockingPreset?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        HomeSectionHeader(text = "Blocking Presets")

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Ready-made plans — tap one to activate instantly.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(Modifier.height(16.dp))

        // Cards cascade in on first show — a calm "settling into place" that makes
        // the section feel alive without shouting. Stagger is capped low so the
        // tail of a 12-item list doesn't feel slow.
        com.allinone.blocker.ui.motion.StaggeredColumn(
            items = ALL_PRESETS,
            spacing = 16.dp,
            stepMs = 40
        ) { preset ->
            PresetCard(
                preset = preset,
                position = ALL_PRESETS.indexOf(preset) + 1,
                total = ALL_PRESETS.size,
                onClick = { selectedPreset = preset }
            )
        }
    }

    if (selectedPreset != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedPreset = null },
            sheetState = sheetState,
            containerColor = CardSurface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            PresetConfirmationSheet(
                preset = selectedPreset!!,
                onActivate = {
                    scope.launch {
                        activatePreset(selectedPreset!!, context)
                        sheetState.hide()
                        selectedPreset = null
                    }
                },
                onDismiss = {
                    scope.launch {
                        sheetState.hide()
                        selectedPreset = null
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PRESET CARD — full-width vertical stack, premium offer style
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PresetCard(
    preset: BlockingPreset,
    position: Int,
    total: Int,
    onClick: () -> Unit
) {
    val accent = preset.accentColor

    val cardGradient = remember(accent) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0.00f to accent.copy(alpha = 0.22f),
                0.45f to accent.copy(alpha = 0.10f),
                1.00f to accent.copy(alpha = 0.18f)
            ),
            start = Offset(0f, 0f),
            end   = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    val glossGradient = remember {
        Brush.linearGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = 0.09f),
                0.40f to Color.White.copy(alpha = 0.03f),
                1.00f to Color.Transparent
            ),
            start = Offset(0f, 0f),
            end   = Offset(Float.POSITIVE_INFINITY, 300f)
        )
    }

    // A large gradient surface, so the press uses a SHALLOW scale (0.98) — enough
    // to feel responsive without the whole card visibly lurching. We drive it
    // ourselves with pressScale and drop the Material ripple, which was the source
    // of the lag (an expensive ripple draw over the layered gradients).
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(width = 1.dp, color = accent.copy(alpha = 0.28f), shape = shape)
            .pressable(pressedScale = 0.98f, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardGradient)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(glossGradient)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(accent.copy(alpha = 0.15f))
                        )
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(accent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.emoji,
                                fontSize = 30.sp
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            lineHeight = 26.sp
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(accent.copy(alpha = 0.20f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = preset.tagline,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = accent,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                HorizontalDivider(color = accent.copy(alpha = 0.18f))

                Spacer(Modifier.height(14.dp))

                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 21.sp
                )

                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accent)
                        )
                        Text(
                            text = "${preset.appsToBlock.size} apps blocked",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        accent,
                                        accent.copy(red   = (accent.red   * 0.80f).coerceIn(0f, 1f),
                                                    green = (accent.green * 0.80f).coerceIn(0f, 1f),
                                                    blue  = (accent.blue  * 0.80f).coerceIn(0f, 1f))
                                    )
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Activate →",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CONFIRMATION BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PresetConfirmationSheet(
    preset: BlockingPreset,
    onActivate: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = preset.emoji,
            fontSize = 48.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = preset.name.replace("\n", " "),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = preset.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = preset.accentColor,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        HorizontalDivider(color = TextTertiary.copy(alpha = 0.15f))

        Spacer(Modifier.height(16.dp))

        Text(
            text = preset.description,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(16.dp))

        AppChipRow(apps = preset.appsToBlock, accentColor = preset.accentColor)

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onActivate,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = preset.accentColor,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Activate Preset",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = TextTertiary.copy(alpha = 0.4f)
            )
        ) {
            Text(
                text = "Maybe Later",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// APP CHIP ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppChipRow(apps: List<PresetApp>, accentColor: Color) {
    val rows = apps.chunked(3)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { rowApps ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowApps.forEach { app ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = app.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVATION LOGIC
// ─────────────────────────────────────────────────────────────────────────────

private fun activatePreset(
    preset: BlockingPreset,
    context: android.content.Context
) {
    BlockerRepository.init(context)

    preset.appsToBlock.forEach { presetApp ->
        val existing = BlockerRepository.appFor(presetApp.packageName)
        val updatedApp = if (existing != null) {
            val alreadyHasRule = existing.rules.any {
                it.type == preset.rule.type &&
                it.startMinutes == preset.rule.startMinutes &&
                it.endMinutes == preset.rule.endMinutes
            }
            if (alreadyHasRule) existing
            else existing.copy(
                rules = existing.rules + preset.rule,
                enabled = true
            )
        } else {
            BlockedApp(
                packageName = presetApp.packageName,
                appName     = presetApp.displayName,
                enabled     = true,
                protection  = ProtectionLevel.NORMAL,
                rules       = listOf(preset.rule)
            )
        }
        BlockerRepository.upsertApp(updatedApp)
    }
}
