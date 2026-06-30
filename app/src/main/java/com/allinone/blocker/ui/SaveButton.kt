package com.allinone.blocker.ui

// ═══════════════════════════════════════════════════════════════════════════
// SaveButton.kt  —  Reusable Save button design token
//
// A single composable that covers every "Save" moment in the app:
//   • Bottom-of-screen full-width save (alarm editor, website rules, etc.)
//   • Compact version for tight layouts or top bars
//   • Ghost (outlined) variant for secondary save actions
//
// Three animated states built in:
//   Idle     →  "Save"  with floppy-disk icon
//   Loading  →  spinner + "Saving…"  (while the write is in progress)
//   Done     →  checkmark + "Saved"  (auto-resets after 1.2 s)
//
// HOW TO USE IN A SCREEN
// ──────────────────────
// 1. Add a state variable for saving:
//      var saveState by remember { mutableStateOf(SaveState.Idle) }
//
// 2. Drop the button anywhere you need it:
//      SaveButton(
//          state    = saveState,
//          onClick  = {
//              saveState = SaveState.Loading
//              // do your save work here, then:
//              saveState = SaveState.Done
//              // the button resets itself after 1.2 s automatically
//          }
//      )
//
// 3. For compact (e.g. inside a row or top bar):
//      SaveButton(state = saveState, size = SaveButtonSize.Compact, onClick = { … })
//
// 4. For ghost / outlined style:
//      SaveButton(state = saveState, variant = SaveButtonVariant.Ghost, onClick = { … })
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.allinone.blocker.ui.theme.AccentTealContainer
import com.allinone.blocker.ui.theme.DarkTertiary
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// PUBLIC API  —  the three things callers control
// ─────────────────────────────────────────────────────────────────────────────

/** The three visual states a SaveButton cycles through. */
enum class SaveState {
    /** Default — shows "Save" with the floppy-disk icon. */
    Idle,

    /** In-progress — shows a spinner + "Saving…". Button is disabled. */
    Loading,

    /**
     * Finished — shows a checkmark + "Saved" in teal for 1.2 s, then
     * automatically calls [onReset] so the caller can flip back to [Idle].
     */
    Done
}

/** Full-width (default) vs. compact for tight spaces. */
enum class SaveButtonSize {
    /** Full-width, 52 dp tall — for bottom-of-screen placement. */
    Default,

    /** Fixed-width, 40 dp tall — for inline / top-bar placement. */
    Compact
}

/** Filled (default, AccentBlue) vs. outlined / ghost. */
enum class SaveButtonVariant {
    /** Solid AccentBlue fill with white text — the primary save action. */
    Filled,

    /** Outlined / ghost — for secondary save or when a filled button is nearby. */
    Ghost
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN COMPOSABLE
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The single Save button for the entire app.
 *
 * @param state    current [SaveState] — drive this from your screen's state.
 * @param onClick  called when the user taps in [SaveState.Idle]; the caller
 *                 should immediately flip [state] to [SaveState.Loading],
 *                 perform the save, then flip to [SaveState.Done].
 * @param onReset  called automatically 1.2 s after [SaveState.Done] so the
 *                 caller can reset [state] back to [SaveState.Idle].
 *                 Defaults to a no-op — only needed if you want explicit control.
 * @param size     [SaveButtonSize.Default] (full-width) or [SaveButtonSize.Compact].
 * @param variant  [SaveButtonVariant.Filled] (solid blue) or [SaveButtonVariant.Ghost].
 * @param modifier passed through to the button root.
 */
@Composable
fun SaveButton(
    state: SaveState,
    onClick: () -> Unit,
    onReset: () -> Unit = {},
    size: SaveButtonSize = SaveButtonSize.Default,
    variant: SaveButtonVariant = SaveButtonVariant.Filled,
    modifier: Modifier = Modifier
) {
    // ── Auto-reset after Done ─────────────────────────────────────────────
    LaunchedEffect(state) {
        if (state == SaveState.Done) {
            delay(1200)
            onReset()
        }
    }

    // ── Press-scale animation (same spring as the rest of the app) ────────
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.97f else 1f,
        animationSpec = MotionSpecs.tactile(),
        label = "saveButtonScale"
    )

    // ── Size tokens ───────────────────────────────────────────────────────
    val buttonHeight: Dp = if (size == SaveButtonSize.Compact) 40.dp else 52.dp
    val cornerRadius: Dp = if (size == SaveButtonSize.Compact) 10.dp else 14.dp
    val iconSize: Dp     = if (size == SaveButtonSize.Compact) 16.dp else 20.dp

    // ── Color tokens — change per state ───────────────────────────────────
    val containerColor: Color = when (state) {
        SaveState.Done    -> AccentTealContainer
        else              -> if (variant == SaveButtonVariant.Filled) AccentBlue
                             else Color.Transparent
    }
    val contentColor: Color = when (state) {
        SaveState.Done    -> DarkTertiary   // teal text on teal-dark container
        else              -> if (variant == SaveButtonVariant.Filled) Color.White
                             else AccentBlue
    }

    // ── Width ─────────────────────────────────────────────────────────────
    val widthModifier = if (size == SaveButtonSize.Default) Modifier.fillMaxWidth()
                        else Modifier

    // ── Build the button ──────────────────────────────────────────────────
    val shape = RoundedCornerShape(cornerRadius)
    val contentPadding = PaddingValues(horizontal = if (size == SaveButtonSize.Compact) 20.dp else 24.dp)
    val isEnabled = state == SaveState.Idle

    val buttonModifier = widthModifier
        .then(modifier)
        .height(buttonHeight)
        .scale(scale)

    if (variant == SaveButtonVariant.Ghost) {
        OutlinedButton(
            onClick = onClick,
            enabled = isEnabled,
            shape = shape,
            modifier = buttonModifier,
            contentPadding = contentPadding,
            interactionSource = interactionSource,
            border = BorderStroke(
                width = 1.5.dp,
                color = if (state == SaveState.Done) AccentTealContainer
                        else AccentBlue.copy(alpha = if (isEnabled) 1f else 0.38f)
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor    = containerColor,
                contentColor      = contentColor,
                disabledContentColor   = contentColor.copy(alpha = 0.6f),
                disabledContainerColor = containerColor
            )
        ) {
            SaveButtonContent(state, iconSize, contentColor)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = isEnabled,
            shape = shape,
            modifier = buttonModifier,
            contentPadding = contentPadding,
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
            SaveButtonContent(state, iconSize, contentColor)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// INNER CONTENT  —  animates between Idle / Loading / Done
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SaveButtonContent(
    state: SaveState,
    iconSize: Dp,
    contentColor: Color
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(animationSpec = MotionSpecs.standard()) +
             scaleIn(initialScale = 0.88f, animationSpec = MotionSpecs.enter()))
                .togetherWith(
             fadeOut(animationSpec = MotionSpecs.exit()) +
             scaleOut(targetScale = 0.88f, animationSpec = MotionSpecs.exit())
                )
        },
        contentAlignment = Alignment.Center,
        label = "saveButtonContent"
    ) { currentState ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (currentState) {
                SaveState.Idle -> {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = contentColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor
                    )
                }
                SaveState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(iconSize),
                        color = contentColor,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Saving…",
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor
                    )
                }
                SaveState.Done -> {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = contentColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor
                    )
                }
            }
        }
    }
}
