package com.allinone.blocker.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detects whether the user is currently viewing Reels / Shorts / TikTok
 * inside a supported app, by inspecting the accessibility node tree.
 *
 * HOW IT WORKS
 * ─────────────
 * Instagram, Facebook, and Snapchat expose their bottom navigation bars
 * as accessibility nodes with content-descriptions, and we walk the node
 * tree looking for known labels that indicate the Reels/Spotlight tab is
 * the one currently selected.
 *
 * YouTube is handled differently. YouTube's "Shorts" bottom-nav tab does
 * NOT reliably report isSelected/isChecked — that's why Shorts used to
 * slip through. Instead, the Shorts PLAYER itself (the full-screen
 * vertical video view you land in, whether you tapped the Shorts tab,
 * tapped a Shorts shelf thumbnail on the homepage, or opened a shared
 * Shorts link) uses a distinct, stable set of resource IDs across recent
 * YouTube versions — e.g. "reel_player_page_container", "reel_recycler",
 * "reel_player_overlay_actions". We check for ANY node with one of those
 * IDs first, since that catches Shorts however the user got there. The
 * old tab-label check is kept as a secondary fallback for older/odd
 * YouTube builds where those IDs might differ.
 *
 * TikTok is short-form only — every screen is "Reels" — so we always
 * return true for it regardless of node content.
 */
object ReelsDetector {

    // TikTok — the entire app is short-form video
    private const val TIKTOK = "com.zhiliaoapp.musically"
    private const val YOUTUBE = "com.google.android.youtube"

    // Instagram Reels tab content-descriptions (varies by app version/locale)
    private val INSTAGRAM_REELS_LABELS = setOf(
        "reels", "reel"
    )

    // YouTube Shorts tab labels — used only as a fallback, see class doc above
    private val YOUTUBE_SHORTS_LABELS = setOf(
        "shorts"
    )

    // Resource-ID fragments that appear on the YouTube Shorts PLAYER itself.
    // These are checked with "contains" against the node's viewIdResourceName
    // (e.g. "com.google.android.youtube:id/reel_player_page_container"),
    // so minor ID renames/suffix changes between app versions still match.
    private val YOUTUBE_SHORTS_PLAYER_ID_FRAGMENTS = setOf(
        "reel_player",
        "reel_recycler",
        "reel_progress",
        "shorts_player",
        "reel_watch_player"
    )

    // Facebook Reels tab labels
    private val FACEBOOK_REELS_LABELS = setOf(
        "reels", "reel", "watch"
    )

    // Snapchat Spotlight tab labels
    private val SNAPCHAT_SPOTLIGHT_LABELS = setOf(
        "spotlight", "discover"
    )

    /**
     * Returns true if the current screen inside [pkg] is a Reels/Shorts feed.
     * [root] is the root AccessibilityNodeInfo for the active window —
     * pass null if unavailable, in which case we fall back to package-level
     * detection for TikTok only.
     */
    fun isOnReelsScreen(pkg: String, root: AccessibilityNodeInfo?): Boolean {
        // TikTok is always short-form — no need to inspect nodes
        if (pkg == TIKTOK) return true

        if (root == null) return false

        return when (pkg) {
            "com.instagram.android" -> isTabSelected(root, INSTAGRAM_REELS_LABELS)
            YOUTUBE -> isOnYouTubeShorts(root)
            "com.facebook.katana" -> isTabSelected(root, FACEBOOK_REELS_LABELS)
            "com.snapchat.android" -> isTabSelected(root, SNAPCHAT_SPOTLIGHT_LABELS)
            else -> false
        }
    }

    /**
     * YouTube-specific check: first looks for the Shorts player's own
     * resource IDs (catches Shorts opened from the tab, the homepage
     * shelf, or a shared link), then falls back to the old tab-selected
     * label check for safety.
     */
    private fun isOnYouTubeShorts(root: AccessibilityNodeInfo): Boolean {
        if (hasMatchingResourceId(root, YOUTUBE_SHORTS_PLAYER_ID_FRAGMENTS)) return true
        return isTabSelected(root, YOUTUBE_SHORTS_LABELS)
    }

    /**
     * BFS traversal looking for any node whose viewIdResourceName contains
     * one of [idFragments]. Resource IDs are far more stable than visible
     * text/content-description labels, which get localized and reworded.
     */
    private fun hasMatchingResourceId(
        root: AccessibilityNodeInfo,
        idFragments: Set<String>
    ): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            val resId = node.viewIdResourceName?.lowercase()
            if (resId != null && idFragments.any { resId.contains(it) }) {
                return true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return false
    }

    /**
     * Walks the full node tree looking for a node whose content-description
     * or text matches one of [labels] (case-insensitive), AND which is either:
     *  - marked as selected (isSelected = true), or
     *  - the only clickable tab node matching the label (active tab pattern)
     *
     * This targets the bottom navigation bar tab that is currently active.
     */
    private fun isTabSelected(root: AccessibilityNodeInfo, labels: Set<String>): Boolean {
        return findMatchingNodes(root, labels).any { node ->
            node.isSelected || isLikelyActiveTab(node)
        }
    }

    /**
     * Some apps don't mark the active tab as isSelected but instead give it
     * a different checked/focused state or put it inside a selected parent.
     */
    private fun isLikelyActiveTab(node: AccessibilityNodeInfo): Boolean {
        if (node.isChecked) return true
        // Walk up to 3 levels up looking for a selected/checked parent
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 3) {
            if (parent.isSelected || parent.isChecked) return true
            parent = parent.parent
            depth++
        }
        return false
    }

    /**
     * BFS traversal of the node tree, collecting all nodes whose
     * content-description or text matches any label in [labels].
     */
    private fun findMatchingNodes(
        root: AccessibilityNodeInfo,
        labels: Set<String>
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            val desc = node.contentDescription?.toString()?.lowercase()
            val text = node.text?.toString()?.lowercase()

            if ((desc != null && labels.any { desc.contains(it) }) ||
                (text != null && labels.any { text.contains(it) })
            ) {
                results.add(node)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return results
    }
}
