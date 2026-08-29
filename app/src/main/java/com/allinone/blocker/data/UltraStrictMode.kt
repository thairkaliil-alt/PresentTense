package com.allinone.blocker.data

import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────────────────
// UltraStrictMode.kt
//
// PLAIN-ENGLISH SUMMARY:
// This is the on/off switch for the "fourth layer" — AccessibilityWatchdog's
// instant alarm that fires the moment Present Tense's Accessibility
// permission gets switched off in Android Settings (see
// AccessibilityWatchdog.kt). That alarm used to fire unconditionally, all
// the time. This file makes it optional: OFF by default, and only watching
// once you deliberately switch "Ultra-Strict Layer" on.
//
// Turning it ON is instant (the first time, it just asks you to set a
// password — see UltraStrictLayerCard.kt for the actual screens). Turning
// it OFF is deliberately NOT instant — that's the whole point of a layer
// called "ultra-strict". Three things all have to happen, IN ORDER, IN ONE
// SITTING:
//   1. A mandatory 5-minute wait, watched live in the popup.
//   2. Your password.
//   3. Typing out "I GIVE UP ON MY DREAMS" — exactly, on purpose. Reading
//      that back to yourself is meant to feel bad enough that you only
//      push through it when you genuinely mean it.
//
// IMPORTANT — the 5-minute wait is intentionally NOT saved anywhere. It
// lives only in the popup's on-screen state (see UltraStrictDisableDialog
// in UltraStrictLayerCard.kt). Close the popup, leave the screen, or close
// the app before it hits zero, and the wait is gone — reopening starts a
// fresh 5:00, every time. This is the opposite of how the "forgot password"
// wait below behaves on purpose: this wait is friction meant to require
// staying put, not a delay meant to be lived through in the background.
// If a future change makes this persist across closes again, it quietly
// turns back into something you can start and forget about — which is
// exactly what this is supposed to prevent.
//
// PinHasher (see StrictMode.kt) is reused here for the password — it's
// just a one-way hash of a string, nothing specific to 6-digit PINs.
// ─────────────────────────────────────────────────────────────────────────────

/** The exact sentence that has to be typed, character-for-character (after
 *  trimming leading/trailing whitespace), to finish turning Ultra-Strict off. */
const val ULTRA_STRICT_PLEDGE = "I GIVE UP ON MY DREAMS"

data class UltraStrictConfig(
    val enabled: Boolean = false,
    val passwordHash: String = "",
    // Same idea as StrictModeGate's PIN reset — a slower, separate wait
    // (see UltraStrictGate.PASSWORD_RESET_DELAY_MS) so genuinely forgetting
    // the password can never be a permanent lockout, without making
    // "forgot it" a fast way around the password step. Unlike the 5-minute
    // disable wait, THIS one is deliberately persisted — a 24-hour wait
    // that didn't survive closing the app would be pointless.
    val passwordResetRequestedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("passwordHash", passwordHash)
        put("passwordResetRequestedAt", passwordResetRequestedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): UltraStrictConfig = UltraStrictConfig(
            enabled = o.optBoolean("enabled", false),
            passwordHash = o.optString("passwordHash", ""),
            passwordResetRequestedAt = o.optLong("passwordResetRequestedAt", 0L)
        )
    }
}

/**
 * Everything involved in turning Ultra-Strict Layer off — the 5-minute
 * wait, the password check, and the final pledge check. Turning it ON
 * doesn't go through this object at all; that's just a direct
 * BlockerRepository.setUltraStrict(config.copy(enabled = true)) call once a
 * password exists (see UltraStrictLayerCard.kt).
 */
object UltraStrictGate {

    /** How long the live wait popup counts down from. Purely a starting
     *  value for the UI's own countdown (see UltraStrictDisableDialog in
     *  UltraStrictLayerCard.kt) — this object doesn't track when a wait
     *  started or whether one is "in progress", on purpose. */
    const val DISABLE_WAIT_MS = 5L * 60L * 1000L // 5 minutes

    /** How long you have to wait after tapping "Forgot password?". Longer
     *  than the PIN reset used elsewhere on purpose — Ultra-Strict is meant
     *  to be the hardest layer to talk your way out of, and a fast-ish
     *  recovery would make the password step nearly pointless. */
    const val PASSWORD_RESET_DELAY_MS = 24L * 60L * 60L * 1000L // 24 hours

    /** Checks [enteredPassword] against the stored hash. No side effects —
     *  this is a separate, explicit step the UI walks through before the
     *  pledge step (see UltraStrictDisableDialog), not something bundled
     *  into the final [finalizeDisable] call. */
    fun verifyPassword(config: UltraStrictConfig, enteredPassword: String): Boolean =
        config.passwordHash.isNotBlank() && PinHasher.matches(enteredPassword, config.passwordHash)

    /**
     * The final step — only reachable in the UI after the live 5-minute
     * wait has run to zero in the same sitting AND [verifyPassword] already
     * returned true earlier in the same flow. Turns Ultra-Strict off if
     * [enteredPledge] matches [ULTRA_STRICT_PLEDGE] exactly (after
     * trimming). Returns true on success.
     *
     * Counts against today's streak, same as any other guarded disable
     * elsewhere in the app (see StrictModeGate.confirm / AccessibilityWatchdog)
     * — going through the front door instead of around it doesn't make it free.
     */
    fun finalizeDisable(config: UltraStrictConfig, enteredPledge: String): Boolean {
        if (enteredPledge.trim() != ULTRA_STRICT_PLEDGE) return false

        BlockerRepository.setUltraStrict(config.copy(enabled = false))
        StreakRepository.recordSuccessfulDisable()
        return true
    }

    // ── Forgot password? ─────────────────────────────────────────────────────
    //
    // A password with literally no recovery path is a real lockout risk —
    // same reasoning as StrictModeGate's PIN reset. The fix is the same
    // too: recovery stays possible, it just can't be fast. 24 hours here
    // instead of the PIN's 1 hour, on purpose — this layer is supposed to
    // be the hardest one to talk your way out of.

    /** Starts the 24-hour wait. Safe to call more than once. */
    fun requestPasswordReset() {
        val config = BlockerRepository.ultraStrict.value
        if (config.passwordResetRequestedAt <= 0L) {
            BlockerRepository.setUltraStrict(config.copy(passwordResetRequestedAt = System.currentTimeMillis()))
        }
    }

    /** Cancels a pending password-reset request — e.g. the password came back to you. */
    fun cancelPasswordReset() {
        val config = BlockerRepository.ultraStrict.value
        BlockerRepository.setUltraStrict(config.copy(passwordResetRequestedAt = 0L))
    }

    /** Milliseconds left before a requested password reset unlocks, or 0 if
     *  none is pending or the wait is already over. */
    fun passwordResetRemainingMs(config: UltraStrictConfig = BlockerRepository.ultraStrict.value): Long {
        if (config.passwordResetRequestedAt <= 0L) return 0L
        val readyAt = config.passwordResetRequestedAt + PASSWORD_RESET_DELAY_MS
        return (readyAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** True once a requested password reset has cleared its 24-hour wait. */
    fun isPasswordResetReady(config: UltraStrictConfig = BlockerRepository.ultraStrict.value): Boolean =
        config.passwordResetRequestedAt > 0L && passwordResetRemainingMs(config) <= 0L

    /** Sets a brand-new password once a requested reset has cleared its
     *  wait. The 5-minute disable wait isn't affected either way — it was
     *  never saved to begin with (see the file header), so there's nothing
     *  here to clear or preserve. */
    fun setNewPasswordAfterReset(newPassword: String) {
        val config = BlockerRepository.ultraStrict.value
        if (!isPasswordResetReady(config)) return
        BlockerRepository.setUltraStrict(
            config.copy(
                passwordHash = PinHasher.hash(newPassword),
                passwordResetRequestedAt = 0L
            )
        )
    }
}
