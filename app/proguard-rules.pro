# Keep Compose runtime internals
-keep class androidx.compose.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Keep all app classes (safe for a debug/personal build)
-keep class com.allinone.blocker.** { *; }

# Keep Android framework classes
-keep class android.** { *; }
-keep class androidx.** { *; }

# Keep JSON parsing (used by BlockerRepository)
-keep class org.json.** { *; }

# Standard Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-dontwarn kotlin.**
-dontwarn kotlinx.**
