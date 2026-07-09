package com.allinone.blocker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ─────────────────────────────────────────────────────────────────────────────
// ProfileRepository.kt
//
// PLAIN-ENGLISH SUMMARY:
// This holds the small bits of "who is this person" data for the new Profile
// tab — their display name, which color their avatar circle is, and the date
// they first opened the app ("Member since"). Nothing here talks to the
// internet; it's all saved locally on the phone with SharedPreferences,
// exactly like StreakRepository does for the streak count.
//
// WHY A SEPARATE FILE FROM StreakRepository:
// Streaks/shields are about BEHAVIOR (did you stay off blocked apps).
// This file is about IDENTITY (who are you, since when). Keeping them
// separate means the future social features (leaderboard, groups, chat)
// can read from ProfileRepository for "who" without dragging in all of the
// streak-calculation logic, and vice versa.
//
// WHAT THIS DOES NOT DO YET (on purpose — foundation only):
//   - No accounts, no login, no server sync. "Display name" is just a local
//     nickname, like naming your device.
//   - No levels/XP storage — ProfileScreen computes those live from
//     StreakRepository's existing numbers instead of duplicating them here.
// ─────────────────────────────────────────────────────────────────────────────

object ProfileRepository {

    private const val PREFS = "profile_prefs"
    private const val KEY_DISPLAY_NAME  = "display_name"
    private const val KEY_AVATAR_COLOR  = "avatar_color_key"
    private const val KEY_JOINED_AT     = "joined_at_millis"

    const val DEFAULT_DISPLAY_NAME = "You"
    const val DEFAULT_AVATAR_COLOR = "blue"

    /** The full set of avatar colors the user can pick between. Keys are
     *  stored as plain strings (not Color ints) so this survives theme
     *  changes and re-colors cleanly — ProfileScreen maps the key to an
     *  actual Color from the app's existing accent palette. */
    val AVATAR_COLOR_KEYS = listOf("blue", "teal", "purple", "amber", "green", "red")

    private val _displayName = MutableStateFlow(DEFAULT_DISPLAY_NAME)
    val displayName: StateFlow<String> = _displayName

    private val _avatarColorKey = MutableStateFlow(DEFAULT_AVATAR_COLOR)
    val avatarColorKey: StateFlow<String> = _avatarColorKey

    /** Set once, the very first time the app is opened after this update —
     *  never changes again after that, so "Member since" stays meaningful. */
    private var _joinedAtMillis: Long = 0L
    val joinedAtMillis: Long get() = _joinedAtMillis

    private lateinit var prefs: SharedPreferences
    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (!prefs.contains(KEY_JOINED_AT)) {
            prefs.edit().putLong(KEY_JOINED_AT, System.currentTimeMillis()).apply()
        }

        _displayName.value    = prefs.getString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME) ?: DEFAULT_DISPLAY_NAME
        _avatarColorKey.value = prefs.getString(KEY_AVATAR_COLOR, DEFAULT_AVATAR_COLOR) ?: DEFAULT_AVATAR_COLOR
        _joinedAtMillis        = prefs.getLong(KEY_JOINED_AT, System.currentTimeMillis())

        initialized = true
    }

    fun setDisplayName(name: String) {
        if (!initialized) return
        val trimmed = name.trim().take(24).ifBlank { DEFAULT_DISPLAY_NAME }
        prefs.edit().putString(KEY_DISPLAY_NAME, trimmed).apply()
        _displayName.value = trimmed
    }

    fun setAvatarColor(colorKey: String) {
        if (!initialized) return
        val safeKey = if (colorKey in AVATAR_COLOR_KEYS) colorKey else DEFAULT_AVATAR_COLOR
        prefs.edit().putString(KEY_AVATAR_COLOR, safeKey).apply()
        _avatarColorKey.value = safeKey
    }
}
