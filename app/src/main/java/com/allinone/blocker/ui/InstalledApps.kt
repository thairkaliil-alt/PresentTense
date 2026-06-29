package com.allinone.blocker.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

data class DeviceApp(val packageName: String, val label: String)

/**
 * Lists launchable, user-facing apps so the user can pick what to block.
 *
 * PERFORMANCE CHANGES vs old version:
 *  - Icons are stored in a separate ConcurrentHashMap<String, ImageBitmap> so
 *    iconFor() is an O(1) hash lookup instead of a linear scan through the whole
 *    app list on every row render.
 *  - DeviceApp no longer carries the icon bitmap, so the StateFlow list is tiny
 *    (just strings) and emitting a new list never copies megabytes of bitmap data.
 *  - labelFor() and iconFor() never touch StateFlow — they read directly from the
 *    hash maps, which are safe to call from any thread at any time.
 */
object InstalledApps {

    // Package fragments treated as short-form video apps for the Reels kill switch.
    private val REELS_PACKAGES = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically", // TikTok
        "com.facebook.katana",
        "com.snapchat.android"
    )

    /** True if this app CAN show Reels/Shorts — does NOT mean it's showing them right now. */
    fun isReelsCapable(pkg: String): Boolean = REELS_PACKAGES.contains(pkg)

    /** @deprecated Use isReelsCapable — kept for any legacy call sites. */
    fun isReels(pkg: String): Boolean = isReelsCapable(pkg)

    private val _apps = MutableStateFlow<List<DeviceApp>>(emptyList())
    val apps: StateFlow<List<DeviceApp>> = _apps

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    @Volatile private var loadedOnce = false

    // O(1) icon lookup — keyed by package name, never forces a list scan.
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    // Fallback label cache for packages not in the main list (e.g. uninstalled apps
    // that still have usage history). Looked up via PackageManager once, then cached.
    private val fallbackLabelCache = ConcurrentHashMap<String, String>()

    /** Loads the list in the background if it hasn't been loaded yet. Safe to call repeatedly. */
    fun ensureLoaded(context: Context) {
        if (loadedOnce || _loading.value) return
        refresh(context)
    }

    /** Forces a fresh scan — e.g. after the user installs a new app. */
    fun refresh(context: Context) {
        _loading.value = true
        val appContext = context.applicationContext
        thread {
            val (apps, icons) = scan(appContext)
            iconCache.putAll(icons)
            _apps.value = apps
            loadedOnce = true
            _loading.value = false
        }
    }

    /**
     * O(1) icon lookup — reads directly from the hash map, no list scan.
     * Returns null for packages not yet loaded or since uninstalled.
     */
    fun iconFor(pkg: String): ImageBitmap? = iconCache[pkg]

    /**
     * Display name for [pkg]. Checks the loaded list first (fast path), then
     * falls back to a cached PackageManager call for rare unlisted packages.
     */
    fun labelFor(context: Context, pkg: String): String {
        _apps.value.firstOrNull { it.packageName == pkg }?.let { return it.label }
        return fallbackLabelCache.getOrPut(pkg) {
            runCatching {
                val pm = context.applicationContext.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
        }
    }

    private fun scan(context: Context): Pair<List<DeviceApp>, Map<String, ImageBitmap>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        val seen = HashSet<String>()
        val apps = ArrayList<DeviceApp>()
        val icons = HashMap<String, ImageBitmap>()
        val iconSizePx = (40 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)

        for (info in resolved) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue
            if (!seen.add(pkg)) continue
            val label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
            val icon = loadIcon(pm, pkg, iconSizePx)
            apps.add(DeviceApp(pkg, label))
            if (icon != null) icons[pkg] = icon
        }
        apps.sortBy { it.label.lowercase() }
        return apps to icons
    }

    private fun loadIcon(pm: PackageManager, pkg: String, sizePx: Int): ImageBitmap? =
        runCatching {
            drawableToBitmap(pm.getApplicationIcon(pkg), sizePx).asImageBitmap()
        }.getOrNull()

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val bmp = drawable.bitmap
            if (bmp.width == sizePx && bmp.height == sizePx) return bmp
            return Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true)
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}
