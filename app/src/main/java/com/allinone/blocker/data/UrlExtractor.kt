package com.allinone.blocker.data

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Pulls the current website's domain out of a browser's on-screen UI.
 *
 * HOW THIS WORKS (plain-English version):
 * Android's Accessibility service can "look at" the screen of any app and
 * read text out of it — that's the same permission that lets the rest of
 * this app detect when a blocked app is in the foreground. A browser's
 * address bar is just a text box drawn somewhere near the top of the
 * screen, and every major browser gives that text box an internal ID we can
 * search for (like a label on a drawer telling you what's inside it).
 *
 * So: when the foreground app is a known browser, we ask Android for that
 * browser's "address bar" element by ID, read whatever text is in it (the
 * URL), and pull just the domain out of it (e.g. "https://reddit.com/r/all"
 * -> "reddit.com").
 *
 * WHY THIS SOMETIMES FAILS, ON PURPOSE:
 * Private/incognito tabs are deliberately built by browser makers to hide
 * page content from accessibility tools like this one, including the URL
 * bar text. That's a privacy feature working as intended, not a bug here.
 * When that happens, [extractDomain] returns null and the caller treats it
 * as "unknown site" rather than guessing.
 */
object UrlExtractor {

    /**
     * Every browser package this app knows how to read a URL out of, or at
     * least attempt to via the generic fallback. Shared between the
     * accessibility service (decides whether to look for a URL at all) and
     * the Stats screen (decides whether to show something under "Browsing
     * Time"). Keeping ONE list here means a browser only needs to be added
     * in one place to be recognized everywhere.
     */
    val BROWSER_PACKAGES: Set<String> = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus",
        "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
        "com.brave.browser", "com.brave.browser_beta", "com.duckduckgo.mobile.android",
        "com.UCMobile.intl", "com.uc.browser.en", "com.kiwibrowser.browser", "com.vivaldi.browser",
        "com.sec.android.app.sbrowser", "org.bromite.bromite",
        "com.stoutner.privacybrowser.standard", "io.github.forkmaintainers.iceraven",
        "com.yandex.browser"
    )

    /**
     * Known address-bar element IDs per browser, keyed by package name.
     * If a browser isn't listed here, [findUrlBarGeneric] below is used as
     * a best-effort fallback instead of just giving up.
     */
    private val URL_BAR_IDS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
        "com.chrome.beta" to listOf("com.chrome.beta:id/url_bar"),
        "com.chrome.dev" to listOf("com.chrome.dev:id/url_bar"),
        "com.chrome.canary" to listOf("com.chrome.canary:id/url_bar"),
        "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox:id/url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view"),
        "org.mozilla.fenix" to listOf("org.mozilla.fenix:id/mozac_browser_toolbar_url_view"),
        "org.mozilla.focus" to listOf("org.mozilla.focus:id/mozac_browser_toolbar_url_view", "org.mozilla.focus:id/display_url"),
        "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
        "com.opera.browser" to listOf("com.opera.browser:id/url_field", "com.opera.browser:id/address_bar"),
        "com.opera.gx" to listOf("com.opera.gx:id/url_field"),
        "com.opera.mini.native" to listOf("com.opera.mini.native:id/url_field"),
        "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
        "com.brave.browser_beta" to listOf("com.brave.browser_beta:id/url_bar"),
        "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
        "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
        "com.vivaldi.browser" to listOf("com.vivaldi.browser:id/url_bar"),
        "com.kiwibrowser.browser" to listOf("com.kiwibrowser.browser:id/url_bar"),
        "com.yandex.browser" to listOf("com.yandex.browser:id/bro_omnibox_address_title_text", "com.yandex.browser:id/bro_omnibox_address_edit_text")
    )

    /**
     * Returns the normalized domain currently shown in [pkg]'s address bar,
     * or null if it can't be read (browser not supported, private tab,
     * no matching node found, etc.).
     *
     * @param rootNode the active window's root accessibility node — pass in
     *   AccessibilityService.rootInActiveWindow.
     */
    fun extractDomain(pkg: String, rootNode: AccessibilityNodeInfo?): String? {
        if (rootNode == null) return null

        val rawText = runCatching {
            val ids = URL_BAR_IDS[pkg]
            var text: String? = null
            if (ids != null) {
                for (id in ids) {
                    text = findTextByViewId(rootNode, id)
                    if (!text.isNullOrBlank()) break
                }
            }
            // Fall back to a generic scan for any browser we don't explicitly
            // recognize — better to try than to silently never support it.
            if (text.isNullOrBlank()) {
                text = findUrlBarGeneric(rootNode)
            }
            text
        }.getOrNull()

        return rawText?.let { normalizeDomain(it) }
    }

    /** Searches the node tree for the first node matching [viewId] and returns its text. */
    private fun findTextByViewId(root: AccessibilityNodeInfo, viewId: String): String? {
        val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }.getOrNull()
        if (nodes.isNullOrEmpty()) return null
        for (node in nodes) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                node.recycle()
                return text
            }
            node.recycle()
        }
        return null
    }

    /**
     * Best-effort fallback for browsers we don't have a specific ID for.
     * Walks the visible tree (shallow — capped depth to stay fast) looking
     * for any text node that looks like a URL or bare domain. This is a
     * heuristic, not a guarantee — some browsers will simply not be
     * detectable this way, which is fine; they just won't support per-site
     * blocking until added to the list above.
     */
    private fun findUrlBarGeneric(root: AccessibilityNodeInfo, maxDepth: Int = 6): String? {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)

        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            val text = node.text?.toString()
            if (!text.isNullOrBlank() && looksLikeUrlOrDomain(text)) {
                return text
            }
            if (depth < maxDepth) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    queue.add(child to depth + 1)
                }
            }
        }
        return null
    }

    private fun looksLikeUrlOrDomain(text: String): Boolean {
        val t = text.trim()
        if (t.length < 4 || t.length > 256) return false
        if (t.contains(" ")) return false // address bars don't show spaces; search boxes do
        return t.startsWith("http://") ||
            t.startsWith("https://") ||
            Regex("^(www\\.)?[a-z0-9-]+\\.[a-z]{2,}(/.*)?$", RegexOption.IGNORE_CASE).matches(t)
    }

    /**
     * Turns a raw URL or address-bar string into a bare, comparable domain:
     * "https://www.Reddit.com/r/all?x=1" -> "reddit.com"
     * Strips scheme, "www.", path/query/fragment, port, and lowercases.
     */
    fun normalizeDomain(raw: String): String? {
        var s = raw.trim().lowercase()
        if (s.isBlank()) return null

        // Strip scheme
        s = s.removePrefix("https://").removePrefix("http://")

        // Cut everything from the first '/', '?', or '#' onward (path/query/fragment)
        val cutIndex = s.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (cutIndex >= 0) s = s.substring(0, cutIndex)

        // Strip userinfo if present (user:pass@host)
        s = s.substringAfterLast('@')

        // Strip port
        s = s.substringBefore(':')

        // Strip leading "www."
        s = s.removePrefix("www.")

        if (s.isBlank() || !s.contains('.')) return null
        // Reject anything that still has obviously invalid characters for a host.
        if (!Regex("^[a-z0-9.-]+$").matches(s)) return null

        return s
    }
}
