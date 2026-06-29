package com.allinone.blocker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ─────────────────────────────────────────────────────────────────────────────
// SettingsPreferences.kt
//
// PLAIN-ENGLISH SUMMARY:
// This file is the "permanent memory" for the three switches on the new
// Settings screen (block reminders, daily summary, vibration). DataStore is
// just Android's modern storage box for small settings like this — every
// time a switch is flipped, the new value is written to disk here, so it's
// still set the same way the next time the app is opened.
//
// This is a SEPARATE storage box from the one the rest of the app uses
// (BlockerRepository, which uses SharedPreferences). We're not touching that
// one — this file only handles the brand-new Settings-screen toggles.
// ─────────────────────────────────────────────────────────────────────────────

// This line creates one single DataStore "file" named "settings", attached
// to the Context. Android requires this to be a top-level property (outside
// any class) so that only one instance of it ever exists per app process.
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsPreferences {

    // Each toggle gets its own "key" — think of it like a labeled box inside
    // the settings file. booleanPreferencesKey just means "this box holds a
    // true/false value".
    private val KEY_BLOCK_REMINDERS = booleanPreferencesKey("block_reminders_enabled")
    private val KEY_DAILY_SUMMARY   = booleanPreferencesKey("daily_summary_enabled")
    private val KEY_VIBRATION       = booleanPreferencesKey("notification_vibration_enabled")

    // Defaults used the very first time the app runs, before the user has
    // touched any of these switches.
    private const val DEFAULT_BLOCK_REMINDERS = true
    private const val DEFAULT_DAILY_SUMMARY   = true
    private const val DEFAULT_VIBRATION       = true

    /**
     * A live, always-up-to-date stream of "are block reminder notifications on?".
     * The Settings screen watches this so the switch always shows the saved value.
     */
    fun blockRemindersEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_BLOCK_REMINDERS] ?: DEFAULT_BLOCK_REMINDERS }

    fun dailySummaryEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_DAILY_SUMMARY] ?: DEFAULT_DAILY_SUMMARY }

    fun vibrationEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_VIBRATION] ?: DEFAULT_VIBRATION }

    /** Saves the new switch value permanently. Called the moment a switch is tapped. */
    suspend fun setBlockRemindersEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_BLOCK_REMINDERS] = enabled }
    }

    suspend fun setDailySummaryEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_DAILY_SUMMARY] = enabled }
    }

    suspend fun setVibrationEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_VIBRATION] = enabled }
    }
}
