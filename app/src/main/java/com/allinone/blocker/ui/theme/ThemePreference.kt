package com.allinone.blocker.ui.theme

import android.content.Context

// Saves and loads the user's dark/light mode preference.
// Uses Android's simple key-value storage (SharedPreferences) so it
// persists across app restarts.
object ThemePreference {

    private const val PREFS_NAME = "blocker_theme_prefs"
    private const val KEY_DARK   = "is_dark_mode"

    fun isDarkMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK, true)  // default = dark mode on
    }

    fun setDarkMode(context: Context, isDark: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, isDark)
            .apply()
    }
}
