package com.allinone.blocker.ui

// ═══════════════════════════════════════════════════════════════════════════
// SaveButton.kt  —  Reusable Save button design token
//
// Pill-shaped Extended FAB to match the "+ Add app" button:
//   • Floats bottom-end of screen with a real drop shadow
//   • Compact pill shape (fully rounded corners)
//   • Text-only — no icon, per MD3 text-only Extended FAB guidance
//   • Three animated states: Idle → Loading → Done, then auto-fires onReset
//
// HOW TO USE IN A SCREEN
// ──────────────────────
// 1. Add a state variable:
//      var saveState by remember { mutableStateOf(SaveState.Idle) }
//
// 2. Place it inside a Scaffold's floatingActionButton slot:
//      floatingActionButton = {
//          SaveButton(
//              state   = saveState,
//              onClick = {
//                  saveState = SaveState.Loading
//                  // do your save, then:
//                  saveState = SaveState.Done
//              },
//              // onReset fires automatically ~500ms after Done — this is the
//              // right place to navigate back so the user doesn't have to
//              // tap Save and then tap Back separately.
//              onReset = { onBack() }
//          )
//      }
//
// 3. For inline / compact use (e.g. inside a Row or top bar):
//      SaveButton(state = saveState, size = SaveButtonSize.Compact, onClick = { … })
//
// 4. For ghost / outlined style:
//      SaveButton(state = saveState, variant = SaveButtonVariant.Ghost, onClick = { … })
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.allinone.blocker.ui.motion.LocalReducedMotion
import com.allinone.blocker.ui.motion.MotionSpecs
import com.allinone.blocker.ui.theme.AccentBlue
import com.allinone.blocker.ui.theme.AccentTeal
import com.allinone.blocker.ui.theme.AccentTealContainer
import com.allinone.blocker.ui.theme.DarkTertiary
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// PUBLIC API
// ─────────────────────────────────────────────────────────────────────────────

/** The three visual states a SaveButton cycles through. */
enum class SaveState {
    Idle,
    Loading,
    Done
}

/** Full-width FAB (default) vs. compact inline pill. */
enum class SaveButtonSize {
    Default,
    Compact
}

/** Filled (default) vs. outlined / ghost. */
enum class SaveButtonVariant {
    Filled,
    Ghost
}

/**
 * How long the button lingers on "Saved" before [SaveButton]'s onReset fires.
 * Screens typically use onReset to navigate back, so this is effectively the
 * "confirmation flash" the user sees before being returned to the previous
 * screen — short enough to feel immediate, long enough to register.
 */
private const val DONE_HOLD_MILLIS = 450L

// ─────────────────────────────────────────────────────────────────────────────
// MAIN COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The single Save button for the entire app.
 *
 * Default mode renders as a text-only ExtendedFloatingActionButton (pill +
 * shadow), matching the "+ Add app" button in size, shape, and elevation —
 * no leading icon, per MD3's text-only Extended FAB spacing rules.
 *
 * @param state    current [SaveState].
 * @param onClick  called on tap in [SaveState.Idle].
 * @param onReset  called automatically ~450ms after [SaveState.Done]. Screens
 *                 should use this to navigate back, so the user never has to
 *                 tap Save and then tap Back separately.
 * @param size     [SaveButtonSize.Default] (FAB pill) or [SaveButtonSize.Compact] (inline pill).
 * @param variant  [SaveButtonVariant.Filled] or [SaveButtonVariant.Ghost].
 * @param modifier passed through to the button root.
 */
@Composable
fun SaveButton(
    state: SaveState,
    onClick: () -> Unit,
    onReset: () -> Unit = {},
    enabled: Boolean = true,
    size: SaveButtonSize = SaveButtonSize.Default,
    variant: SaveButtonVariant = SaveButtonVariant.Filled,
    modifier: Modifier = Modifier
) {
    // ── Auto-fire onReset after Done (screens hook this to navigate back) ──
    LaunchedEffect(state) {
        if (state == SaveState.Done) {
            delay(DONE_HOLD_MILLIS)
            onReset()
        }
    }

    // ── Press-scale animation ─────────────────────────────────────────────
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.96f else 1f,
        animationSpec = MotionSpecs.tactile(),
        label = "saveButtonScale"
    )

    val isEnabled = state == SaveState.Idle && enabled

    // ── Default: Extended FAB — pill shape, real shadow, bottom-end float ─
    // Uses the text-only content-lambda overload (no icon slot), which
    // applies MD3's correct symmetric padding for a label-only Extended FAB.
    if (size == SaveButtonSize.Default && variant == SaveButtonVariant.Filled) {
        val containerColor = when (state) {
            SaveState.Done -> AccentTeal
            else           -> AccentBlue
        }
        val contentColor = Color.White

        ExtendedFloatingActionButton(
            onClick = { if (isEnabled) onClick() },
            modifier = modifier.scale(scale),
            containerColor = containerColor,
            contentColor = contentColor,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation  = 6.dp,
                pressedElevation  = 2.dp,
                focusedElevation  = 6.dp,
                hoveredElevation  = 8.dp
            )
        ) {
            SaveButtonLabel(state, contentColor = contentColor)
        }
        return
    }

    // ── Compact / Ghost fallback — inline pill button ─────────────────────
    val buttonHeight: Dp = if (size == SaveButtonSize.Compact) 40.dp else 52.dp

    val containerColor: Color = when (state) {
        SaveState.Done -> AccentTealContainer
        else           -> if (variant == SaveButtonVariant.Filled) AccentBlue else Color.Transparent
    }
    val contentColor: Color = when (state) {
        SaveState.Done -> DarkTertiary
        else           -> if (variant == SaveButtonVariant.Filled) Color.White else AccentBlue
    }

    val buttonModifier = modifier.height(buttonHeight).scale(scale)

    if (variant == SaveButtonVariant.Ghost) {
        OutlinedButton(
            onClick = onClick,
            enabled = isEnabled,
            shape = CircleShape,
            modifier = buttonModifier,
            contentPadding = PaddingValues(horizontal = 20.dp),
            interactionSource = interactionSource,
            border = BorderStroke(
                width = 1.5.dp,
                color = if (state == SaveState.Done) AccentTeal
                        else AccentBlue.copy(alpha = if (isEnabled) 1f else 0.38f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor         = containerColor,
                contentColor           = contentColor,
                disabledContentColor   = contentColor.copy(alpha = 0.6f),
                disabledContainerColor = containerColor
            )
        ) {
            SaveButtonLabel(state, contentColor = contentColor)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = isEnabled,
            shape = CircleShape,
            modifier = buttonModifier,
            contentPadding = PaddingValues(horizontal = 20.dp),
            interactionSource = interactionSource,
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation  = if (state == SaveState.Done) 0.dp else 3.dp,
                pressedElevation  = 1.dp,
                disabledElevation = 0.dp
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor         = containerColor,
                contentColor           = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor   = contentColor.copy(alpha = 0.85f)
            )
        ) {
            SaveButtonLabel(state, contentColor = contentColor)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INNER HELPERS
// ─────────────────────────────────────────────────────────────────────────────

/** Animated label only — the button's entire content, no icon. */
@Composable
private fun SaveButtonLabel(state: SaveState, contentColor: Color) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(animationSpec = MotionSpecs.standard()))
                .togetherWith(fadeOut(animationSpec = MotionSpecs.exit()))
        },
        contentAlignment = Alignment.Center,
        label = "saveButtonLabel"
    ) { s ->
        Text(
            text  = when (s) {
                SaveState.Idle    -> "Save"
                SaveState.Loading -> "Saving…"
                SaveState.Done    -> "Saved"
            },
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}
