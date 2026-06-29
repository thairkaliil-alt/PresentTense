package com.allinone.blocker.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.allinone.blocker.BuildConfig
import com.allinone.blocker.data.SettingsPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// SettingsViewModel.kt
//
// PLAIN-ENGLISH SUMMARY:
// This is the "brain" behind the Settings screen. The screen itself
// (SettingsScreen.kt) only draws things on screen — this file is what
// actually:
//   - Reads the saved on/off state of each notification switch
//   - Saves a switch's new state the moment it's tapped
//   - Builds the Android "intents" (requests to other apps/screens) for
//     things like opening the Play Store, an email app, or a web link
//
// It's an AndroidViewModel (not a plain ViewModel) because it needs access
// to the app's Context to read/write DataStore and to build those intents.
// ─────────────────────────────────────────────────────────────────────────────

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext get() = getApplication<Application>().applicationContext

    // ── Notification toggle state ────────────────────────────────────────────
    // Each of these is a "live" value that the screen watches. The moment the
    // underlying DataStore value changes (because the user tapped a switch),
    // this updates automatically and the switch on screen redraws itself.

    val blockRemindersEnabled: StateFlow<Boolean> =
        SettingsPreferences.blockRemindersEnabled(appContext)
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val dailySummaryEnabled: StateFlow<Boolean> =
        SettingsPreferences.dailySummaryEnabled(appContext)
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val vibrationEnabled: StateFlow<Boolean> =
        SettingsPreferences.vibrationEnabled(appContext)
            .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setBlockRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { SettingsPreferences.setBlockRemindersEnabled(appContext, enabled) }
    }

    fun setDailySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch { SettingsPreferences.setDailySummaryEnabled(appContext, enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { SettingsPreferences.setVibrationEnabled(appContext, enabled) }
    }

    // ── About & Support ───────────────────────────────────────────────────────

    /** App version shown in the About section, e.g. "1.0". Pulled from build.gradle.kts. */
    val appVersionName: String get() = BuildConfig.VERSION_NAME

    /**
     * Opens this app's Play Store listing. Falls back to the Play Store
     * website if the Play Store app itself isn't installed (e.g. on an
     * emulator), so the button never silently does nothing.
     *
     * NOTE: This app isn't published yet, so this currently points at a
     * placeholder. Once it's live, nothing here needs to change — Android
     * automatically resolves this to the real listing using the app's own
     * package name.
     */
    fun openPlayStoreListing() {
        val packageName = appContext.packageName
        try {
            launchIntent(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            )
        } catch (_: Exception) {
            launchIntent(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            )
        }
    }

    /**
     * Opens the user's email app with a feedback email pre-filled.
     * PLACEHOLDER address — swap "feedback@example.com" for your real
     * support email once you have one.
     */
    fun openFeedbackEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:feedback@example.com")
            putExtra(Intent.EXTRA_SUBJECT, "AllinOneBlocker Feedback (v$appVersionName)")
        }
        launchIntent(intent)
    }

    /** PLACEHOLDER URL — swap for your real privacy policy link once you have one. */
    fun openPrivacyPolicy() {
        launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/privacy-policy")))
    }

    /** PLACEHOLDER URL — swap for your real terms-of-service link once you have one. */
    fun openTermsOfService() {
        launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/terms-of-service")))
    }

    /**
     * Starts an intent safely. If there's no app on the device able to handle
     * it (e.g. no email app installed), this quietly does nothing instead of
     * crashing the app.
     */
    private fun launchIntent(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(intent)
        } catch (_: Exception) {
            // No app available to handle this — fail silently rather than crash.
            // (e.g. ACTION_SENDTO with no email client installed)
        }
    }
}
