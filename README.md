# Present Tense — Full Feature Specification
> Android Native App | Kotlin | Target: Personal Productivity & Digital Discipline

---

## 1. CORE APP BLOCKING

### 1.1 Block Modes (per app)
Each app the user chooses to block can be set to one or more of the following rules:

- **Time Interval Block** — Block the app during a specific time window (e.g., 9:00 AM – 6:00 PM)
- **Daily Usage Limit** — Allow the app only for X minutes/hours per day total (e.g., max 30 min/day)
- **Session Usage Limit** — Allow the app only for X minutes per single session (e.g., max 10 min per open)
- **Open Count Limit** — Allow the app to be opened only X times per day (e.g., max 3 opens/day)
- **Cooldown Between Opens** — After closing the app, block it again for X minutes before it can be reopened (e.g., 45-min cooldown)
- **Permanent Block** — App is fully blocked with no exceptions until manually removed (with strict mode friction)
- **Combination Rules** — User can stack multiple rules on one app (e.g., max 20 min/day AND only between 7–9 PM)

### 1.2 Block Behavior
- When a blocked app is opened, an overlay screen immediately appears covering the app
- The overlay shows: app name, reason for block, time remaining (if applicable), and a motivational message
- The user cannot interact with the app underneath the overlay
- Overlay cannot be dismissed by back button or home button tricks

---

## 2. FOCUS SESSIONS

### 2.1 Quick Focus Mode
- User sets a duration (e.g., 25 min, 1 hour, custom)
- During the session, all non-whitelisted apps are blocked
- A persistent notification shows session countdown
- Session cannot be cancelled without strict mode friction (see Section 5)

### 2.2 Pomodoro Mode
- Built-in Pomodoro timer: 25 min focus → 5 min break cycles
- Fully customizable: work duration, short break, long break, number of cycles
- During work intervals: selected apps blocked
- During break intervals: selected apps temporarily unblocked
- Audio/notification alerts at each transition

### 2.3 Deep Work Mode
- Blocks everything except the whitelist
- Disables notifications from blocked apps
- Cannot be exited without completing the full session or passing strict mode challenge
- Optional: screen stays on a focus wallpaper/quote during session

---

## 3. FULL PHONE LOCKDOWN

### 3.1 Total Lockdown Mode
- Blocks the entire phone except whitelist apps
- Can be triggered manually or on a schedule
- Phone calls and SMS always remain functional (never blocked)
- Emergency contacts always reachable

### 3.2 Scheduled Daily Lockdown
- Set recurring daily blocks: e.g., "Lock phone every day from 11 PM to 7 AM"
- Multiple schedules allowed (e.g., work hours + sleep hours)
- Day-of-week customization (e.g., stricter on weekdays)

### 3.3 Whitelist
- User selects apps that are NEVER blocked under any mode
- Default whitelist suggestions: Phone, Messages, Maps, Camera, Calculator, Wallet, Email
- Whitelist is editable but editing requires password/strict mode

---

## 4. SOCIAL MEDIA SPECIFIC FEATURES

### 4.1 Reels / Shorts Kill Switch
- A single toggle in the home screen that blocks access to short-form video content
- Targets: Instagram Reels, YouTube Shorts, TikTok, Facebook Reels, Snapchat Spotlight
- Works by blocking the entire app OR (if possible) using overlay to detect and block specific in-app sections
- Toggle can be locked with strict mode so it can't be easily turned off

### 4.2 Infinite Scroll Blocker (if technically feasible)
- Detects and blocks infinite scroll behavior in social apps
- After X minutes of continuous scrolling, overlay appears requiring user to confirm they want to continue

---

## 5. STRICT MODE & PROTECTION LAYERS

### 5.1 Password Protection
- User sets a PIN or password required to:
  - Disable any active block
  - Edit blocked apps list
  - Change any settings
  - Uninstall the app
- Option to use a "accountability password" — user gives the password to someone else (friend/family) so they cannot unblock themselves

### 5.2 Delay Timer (Friction Layer)
- When user tries to cancel a block or change settings, a mandatory wait timer appears (e.g., "Are you sure? Changes apply in 10 minutes")
- Timer is customizable: 1 min, 5 min, 10 min, 30 min, 1 hour
- During the wait, user can cancel the change request — but if they wait the full time, the change goes through
- Purpose: gives the user time to reconsider impulsive decisions

### 5.3 Challenge to Unlock
- Instead of (or in addition to) a password, user must complete a task to disable a block:
  - Type a long custom phrase (e.g., "I am choosing to break my focus session")
  - Solve a math problem (difficulty customizable: easy/medium/hard)
  - Wait X minutes with the phone face down (honor system timer)
  - Custom text prompt that makes the user reflect
- Challenges can be stacked (e.g., wait 5 min + solve math + type phrase)

### 5.4 Protection Levels (User-Selectable)
User picks a protection level per block or globally:

| Level | What it means |
|-------|--------------|
| **Soft** | Can disable with one tap, no friction |
| **Normal** | Password required |
| **Strict** | Password + delay timer |
| **Hardcore** | Password + delay timer + challenge task |
| **Locked** | Cannot be changed until scheduled end time. Period. |

### 5.5 Uninstall Protection
- App protects itself from being uninstalled during an active strict block
- If uninstall is attempted, app warns and requires password
- Uses Device Admin permission to prevent uninstall during locked sessions

---

## 6. PROFILES & MODES

### 6.1 Custom Profiles
- User creates named profiles (e.g., "Work Mode", "Study Mode", "Sleep Mode", "Weekend")
- Each profile has its own: blocked apps, whitelist, protection level, schedule
- Switch between profiles with one tap (or automatically by schedule)

### 6.2 Auto-Activation by Schedule
- Profiles activate automatically at set times
- Example: "Study Mode" activates Mon–Fri 9 AM–12 PM automatically

### 6.3 Emergency Override
- A single "Emergency Exit" option always available
- Requires typing a long specific phrase + 5-minute wait
- Every emergency exit is logged with timestamp (for self-accountability)

---

## 7. USAGE STATISTICS & REPORTS

### 7.1 Daily Dashboard
- Screen time per app (today)
- Number of times each app was opened
- Number of block triggers (how many times you tried to open a blocked app)
- Total focus time completed

### 7.2 Weekly & Monthly Reports
- Bar charts showing screen time trends per app over time
- Most-blocked apps
- Focus sessions completed vs. abandoned
- Streak tracking (e.g., "5 days without opening Instagram")

### 7.3 Insights & Warnings
- "You've already used Instagram for 28 min today. 2 min left."
- "You opened YouTube 12 times today. Yesterday it was 4."
- "Your most distracting hour is 9–10 PM."

---

## 8. NOTIFICATIONS & NUDGES

### 8.1 Real-Time Usage Alerts
- Notify user when they've used X% of their daily limit for an app
- Warning at 50%, 80%, and 100% of limit

### 8.2 Motivational Block Screen
- When a blocked app is opened, show a custom motivational message
- User can write their own messages
- Optionally show the user's current streak or goal

### 8.3 Daily Summary Notification
- End of day: push notification showing daily screen time summary
- Includes wins (goals met) and areas to improve

---

## 9. WEBSITE BLOCKING (VPN-BASED)

### 9.1 Browser & Website Block
- Block specific websites across all browsers on the device
- Implemented using a local VPN (no data leaves the device)
- User adds URLs to blocklist
- Works even in Chrome, Firefox, Samsung Browser, etc.

### 9.2 Category-Based Web Blocking
- Block entire categories: Social Media, Adult Content, News, Gaming, Shopping
- Pre-built category lists with ability to add/remove individual sites

---

## 10. BEDTIME & MORNING FEATURES

### 10.1 Bedtime Mode
- Activates automatically at a set time each night
- Blocks all non-essential apps
- Dims overlay to dark/red theme (eye-friendly)
- Optionally blocks internet-connected apps entirely

### 10.2 Morning Lockout
- Prevents phone use for X minutes after waking alarm
- Designed to stop the "doom scroll in bed" habit
- Only allows alarm/clock app until morning lockout expires

---

## 11. APP SETTINGS & CUSTOMIZATION

### 11.1 Block Screen Customization
- Custom background color or image on block overlay
- Custom message shown when blocked
- Option to show motivational quote, countdown timer, or nothing

### 11.2 Theme
- Light / Dark / AMOLED dark mode
- Accent color picker

### 11.3 Language Support
- Arabic and English UI (minimum)

### 11.4 Backup & Restore
- Export all settings, profiles, and block rules to a file
- Restore from backup on new device

---

## 12. REQUIRED ANDROID PERMISSIONS

The following permissions are standard for this type of app and must be requested on first launch with clear explanation to the user:

| Permission | Why it's needed |
|------------|----------------|
| `PACKAGE_USAGE_STATS` | To monitor which apps are open and for how long |
| `BIND_ACCESSIBILITY_SERVICE` | To detect app launches and enforce blocks |
| `SYSTEM_ALERT_WINDOW` (Draw Over Other Apps) | To show block overlay on top of blocked apps |
| `DEVICE_ADMIN` | To prevent uninstall during locked sessions |
| `RECEIVE_BOOT_COMPLETED` | To re-activate blocks after phone restart |
| `FOREGROUND_SERVICE` | To keep the blocker running in the background |
| `VPN` (optional) | For website blocking feature |

---

## 13. TECHNICAL NOTES FOR DEVELOPER

- Language: **Kotlin**
- Minimum SDK: Android 8.0 (API 26), Target: Android 14
- Architecture: MVVM + Room database for storing rules/stats
- Background service: persistent ForegroundService to enforce blocks even when app is closed
- Block enforcement: AccessibilityService detects foreground app and triggers overlay
- Usage stats: UsageStatsManager API for tracking per-app screen time
- No internet required for core functionality (fully offline except optional backup)
- No ads, no analytics, no data collection — 100% local
- The app should survive: phone restart, force close, battery optimization — all must be handled

---

*This document covers all features as discussed. Additional features can be added iteratively after the core blocking engine is functional.*
