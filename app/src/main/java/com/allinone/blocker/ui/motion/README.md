# Motion — micro-interaction system

A small, consistent toolkit for subtle motion across Present Tense. The goal is
motion that's *felt, not seen*: tiny displacements, calm easings, everything
under the ~400ms Doherty threshold so the app always feels responsive. Built as
components so any screen can adopt them and the whole feel can be retuned from
one place.

## Files

| File | What it gives you |
|------|-------------------|
| `Motion.kt` | The tokens — durations, easings, springs, constants. Edit here to retune the whole app. |
| `Pressable.kt` | `Modifier.pressable { }` and `Modifier.pressScale(src)` — tactile press feedback. |
| `Appearance.kt` | `AnimatedAppearance`, `StaggeredColumn`, `AppearWhen` — content entrances. |
| `ScreenTransition.kt` | `ScreenSwitch` — calm cross-fade between screens/tabs. |
| `AnimatedValues.kt` | `AnimatedCount`, `animatedCountAsState` — numbers that roll up. |

## Recipes

**Make any card/row feel tactile** — replace `clickable` with `pressable`:
```kotlin
Modifier.pressable { onClick() }          // 4% squeeze, no ripple
Modifier.pressable(pressedScale = MotionTokens.PressScaleSmall) { … } // icons/chips
```

**Keep an existing Material onClick, just add the squeeze:**
```kotlin
val src = rememberPressInteraction()
Card(interactionSource = src, modifier = Modifier.pressScale(src)) { … }
```

**Gentle entrance for a section:**
```kotlin
AnimatedAppearance { StatsCard(...) }
```

**A list that settles in:**
```kotlin
StaggeredColumn(rows) { row -> RowCard(row) }
```

**Toggle content in/out softly:**
```kotlin
AppearWhen(visible = expanded) { Details() }
```

**Animate between screens** (already wired in `MainActivity.AppRoot`):
```kotlin
ScreenSwitch(targetState = screen) { current -> when (current) { … } }
```

**Roll a number up:**
```kotlin
AnimatedCount(value = streak, suffix = " day streak", style = …)
```

## Principles (why it's built this way)

- **Consistency over cleverness.** Every animation pulls from the same tokens,
  so the app moves with one rhythm. Never hand-roll `tween(237)` in a screen.
- **Subtle by default.** ~4% scale, ~12dp travel. Big motion grabs attention;
  we want the opposite.
- **Enter decelerates, exit accelerates.** Arrivals settle; departures get out
  of the way. (Material 3 / iOS HIG.)
- **Honest dopamine.** Rolling counters reward *real* progress (streaks, time
  saved) — no fake urgency, no dark patterns.
- **Accessible.** Everything reads `LocalReducedMotion` (wired from the OS
  "remove animations" setting in `BlockerTheme`) and collapses to instant /
  cross-fade when the user asks for less motion.
</content_placeholder>
