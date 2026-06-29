package com.allinone.blocker.ui

// ─────────────────────────────────────────────────────────────────────────────
// ThemeToggleSwitch.kt
//
// A custom-drawn sun/moon switch used only for the Dark Mode toggle on the
// Home screen. It replaces the plain Material `Switch` with something that:
//   • slides a circular "thumb" left (light) / right (dark)
//   • morphs that thumb into a sun (light) or a moon with craters (dark)
//   • makes the track briefly "flash" a soft highlight color the instant
//     you tap it, then settles back to its normal resting color
//
// Everything here is plain Canvas drawing + Compose animation APIs
// (animateFloatAsState / animateColorAsState), same approach already used
// in LockdownScreen.kt elsewhere in this app.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A sun/moon animated switch.
 *
 * @param checked true = dark mode (moon, slid right), false = light mode (sun, slid left)
 * @param onCheckedChange called with the new value the instant the user taps it
 * @param trackWidth / trackHeight let you render a smaller version (e.g. in a top bar)
 *        without changing how it looks anywhere else it's already used.
 */
@Composable
fun ThemeToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trackWidth: androidx.compose.ui.unit.Dp = 56.dp,
    trackHeight: androidx.compose.ui.unit.Dp = 30.dp
) {
    // 0f = fully light (thumb left), 1f = fully dark (thumb right).
    // Animating this one float drives the thumb position AND the colors below,
    // so everything stays perfectly in sync no matter how fast you tap.
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = LinearOutSlowInEasing),
        label = "themeProgress"
    )

    // A short-lived "flash" pulse: jumps to 1 the instant you tap, then eases
    // back down to 0. We layer this on TOP of the track color so the flash
    // reads as a quick bright pulse rather than a permanent color shift.
    val flash = remember { Animatable(0f) }
    var lastChecked by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked != lastChecked) {
            lastChecked = checked
            flash.snapTo(1f)
            flash.animateTo(0f, animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing))
        }
    }

    // Resting track colors (no flash applied).
    val trackLight = Color(0xFF9FD3F0)   // soft sky blue
    val trackDark  = Color(0xFF1B2333)   // deep navy

    // The flash colors layered on top at peak pulse.
    val flashToDark  = Color(0xFFFFFFFF) // brief white flash when going dark
    val flashToLight = Color(0xFFBFE3FF) // brief pale-blue flash when going light

    val baseTrackColor = lerp(trackLight, trackDark, progress)
    val flashColor = if (checked) flashToDark else flashToLight
    val trackColor = lerp(baseTrackColor, flashColor, flash.value * 0.55f)

    val interactionSource = remember { MutableInteractionSource() }

    Canvas(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch
            ) { onCheckedChange(!checked) }
            .semantics { role = Role.Switch }
    ) {
        val w = size.width
        val h = size.height
        val thumbRadius = h * 0.38f
        val padding = h * 0.12f

        // Track background
        drawRoundRect(
            color = trackColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f)
        )

        // A few tiny "stars" that fade in on the dark side of the track,
        // and fade out as you slide back toward light.
        val starAlpha = (progress - 0.4f).coerceIn(0f, 0.6f) / 0.6f * 0.8f
        if (starAlpha > 0f) {
            val starR = h * 0.045f
            drawCircle(Color.White.copy(alpha = starAlpha), radius = starR * 1.2f, center = Offset(w * 0.22f, h * 0.32f))
            drawCircle(Color.White.copy(alpha = starAlpha), radius = starR,        center = Offset(w * 0.30f, h * 0.62f))
            drawCircle(Color.White.copy(alpha = starAlpha), radius = starR * 1.1f, center = Offset(w * 0.16f, h * 0.55f))
        }

        // Thumb position: slides from left padding to right padding as progress 0 -> 1
        val thumbCenter = Offset(
            x = (padding + thumbRadius) + progress * (w - h),
            y = h / 2f
        )

        // Thumb body color: warm cream sun -> pale grey moon
        val sunColor = Color(0xFFFFD66B)
        val moonColor = Color(0xFFE7E9F0)
        val thumbColor = lerp(sunColor, moonColor, progress)

        // Soft glow behind the thumb, brighter mid-flash
        val glowAlpha = 0.25f + flash.value * 0.35f
        drawCircle(
            color = thumbColor.copy(alpha = glowAlpha),
            radius = thumbRadius * 1.6f,
            center = thumbCenter
        )

        // The thumb itself
        drawCircle(color = thumbColor, radius = thumbRadius, center = thumbCenter)

        // Sun rays: fully visible at progress = 0, fade out by progress = 0.5
        val rayAlpha = (1f - progress * 2f).coerceIn(0f, 1f)
        if (rayAlpha > 0f) {
            val rayLength = thumbRadius * 0.55f
            val rayStart = thumbRadius * 1.15f
            for (i in 0 until 8) {
                val angle = (i * 45f) * (Math.PI / 180f)
                val sx = thumbCenter.x + cos(angle).toFloat() * rayStart
                val sy = thumbCenter.y + sin(angle).toFloat() * rayStart
                val ex = thumbCenter.x + cos(angle).toFloat() * (rayStart + rayLength)
                val ey = thumbCenter.y + sin(angle).toFloat() * (rayStart + rayLength)
                drawLine(
                    color = sunColor.copy(alpha = rayAlpha),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = (h * 0.07f).coerceAtLeast(1.2f),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }

        // Moon craters: fully visible at progress = 1, fade in starting at progress = 0.5
        val craterAlpha = ((progress - 0.5f).coerceIn(0f, 0.5f) / 0.5f)
        if (craterAlpha > 0f) {
            val craterColor = Color(0xFFC7CBDA).copy(alpha = craterAlpha)
            drawCircle(craterColor, radius = thumbRadius * 0.22f, center = Offset(thumbCenter.x - thumbRadius * 0.32f, thumbCenter.y - thumbRadius * 0.28f))
            drawCircle(craterColor, radius = thumbRadius * 0.14f, center = Offset(thumbCenter.x + thumbRadius * 0.18f, thumbCenter.y + thumbRadius * 0.05f))
            drawCircle(craterColor, radius = thumbRadius * 0.10f, center = Offset(thumbCenter.x + thumbRadius * 0.30f, thumbCenter.y - thumbRadius * 0.30f))
        }
    }
}
