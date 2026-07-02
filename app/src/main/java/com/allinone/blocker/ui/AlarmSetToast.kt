package com.allinone.blocker.ui

// ═══════════════════════════════════════════════════════════════════════════
// AlarmSetToast.kt  —  the confirmation pill shown right after saving an alarm
//
// WHY THIS EXISTS AS ITS OWN COMPONENT
//   The old version reused Material 3's stock Snackbar — a full-width bar
//   glued to the very bottom edge of the screen. That's the right shape for
//   things with an action button (Undo, etc.), but for a pure "here's what
//   just happened" confirmation it reads as heavier and more utilitarian than
//   it needs to be.
//
//   Every app that's known for polish handles this kind of "quiet confirm"
//   moment the same general way: a compact, self-sized pill — not full width
//   — that floats a little clear of the screen edge instead of touching it,
//   with rounded (often fully round) corners and a soft shadow so it reads as
//   floating above the content rather than docked to the chrome. That's the
//   shape iOS's own toasts use, and it's the same language Duolingo, Linear
//   and Superhuman reach for whenever they confirm something without asking
//   for a decision back. Material 3's own guidance agrees here too — a
//   Snackbar is for messages that may need an action; a plain confirmation
//   is better as a lightweight, self-dismissing surface.
//
//   So this toast:
//     • is a pill, not a bar — width hugs its content instead of stretching
//       edge-to-edge, using the app's own `extraLarge` shape token (the same
//       one used for "big overlay panels" per Shape.kt).
//     • floats up from the very bottom instead of touching it, so it never
//       feels stuck to the edge or fights with a bottom nav bar.
//     • leads with a small circular icon chip (alarm bell) in the app's own
//       AccentBlue, echoing the pattern already used for chips elsewhere
//       (see the icon containers in Lockdown/Pomodoro).
//     • uses the app's shared MotionSpecs enter/exit curves (same
//       decelerate-in / accelerate-out easing as everything else) so it
//       breathes with the same rhythm as the rest of the app instead of
//       introducing a one-off animation feel.
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.allinone.blocker.ui.motion.MotionDurations
import com.allinone.blocker.ui.motion.MotionEasing
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentBlueContainer
import com.allinone.blocker.ui.theme.CardSurface
import com.allinone.blocker.ui.theme.TextSecondary

/**
 * Floating confirmation pill — "Alarm set" + a short relative time, e.g. "7h 12m from now".
 *
 * Call this inside a [Box] (it needs [BoxScope] to position itself), alongside
 * — not inside — the Scaffold, so it floats freely above whatever content is
 * on screen instead of being confined to the Scaffold's snackbar slot.
 *
 * @param visible whether the toast should be showing right now.
 * @param relativeTime the short "Xh Ym" / "Xm" span until the alarm fires.
 */
@Composable
fun BoxScope.AlarmSetToast(
    visible: Boolean,
    relativeTime: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        // Rises in from just below its resting spot while it fades in —
        // small enough displacement to read as "arriving", not "flying in".
        enter = fadeIn(tween(MotionDurations.Emphasized, easing = MotionEasing.Decelerate)) +
            slideInVertically(
                animationSpec  = tween(MotionDurations.Emphasized, easing = MotionEasing.Decelerate),
                initialOffsetY = { fullHeight -> fullHeight / 3 }
            ),
        // Exits quickly and drops slightly — gets out of the way, doesn't linger.
        exit = fadeOut(tween(MotionDurations.Standard, easing = MotionEasing.Accelerate)) +
            slideOutVertically(
                animationSpec = tween(MotionDurations.Standard, easing = MotionEasing.Accelerate),
                targetOffsetY = { fullHeight -> fullHeight / 4 }
            ),
        modifier = modifier
            .align(Alignment.BottomCenter)
            // Clear of the very bottom edge — "lower half of the screen, but
            // not glued to the bottom" — and clear of any system gesture bar.
            .padding(bottom = 96.dp)
            .wrapContentWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                // Soft floating shadow — reads as "above the content", the
                // same cue iOS/Material use for transient overlays.
                .shadow(
                    elevation = 14.dp,
                    shape     = MaterialTheme.shapes.extraLarge,
                    clip      = false
                )
                .clip(MaterialTheme.shapes.extraLarge)
                .background(CardSurface, MaterialTheme.shapes.extraLarge)
                .padding(start = 10.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AccentBlueContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.Alarm,
                    contentDescription = null,
                    tint               = AccentBlue,
                    modifier           = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text       = "Alarm set",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text  = "$relativeTime from now",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
