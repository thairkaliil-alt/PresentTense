package com.allinone.blocker.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detects whether the user is currently viewing Reels / Shorts / TikTok
 * inside a supported app, by inspecting the accessibility node tree.
 *
 * HOW IT WORKS
 * ─────────────
 * Instagram, YouTube, Facebook, and Snapchat all expose their bottom
 * navigation bars as accessibility nodes with content-descriptions.
 * We walk the node tree looking for known labels that indicate the
 * Reels / Shorts tab is the one currently selected or focused.
 *
 * TikTok is short-form only — every screen is "Reels" — so we always
 * return true for it regardless of node content.
 *
 * YouTube Shorts are detected by looking for the "Shorts" tab being
 * selected in the bottom nav — same tab-selection logic as Instagram.
 */
object ReelsDetector {

    // TikTok — the entire app is short-form video
    private const val TIKTOK = "com.zhiliaoapp.musically"

    // Instagram Reels tab content-descriptions (varies by app version/locale)
    private val INSTAGRAM_REELS_LABELS = setOf(
        "reels", "reel"
    )

    // YouTube Shorts tab labels
    private val YOUTUBE_SHORTS_LABELS = setOf(
        "shorts"
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
            "com.google.android.youtube" -> isTabSelected(root, YOUTUBE_SHORTS_LABELS)
            "com.facebook.katana" -> isTabSelected(root, FACEBOOK_REELS_LABELS)
            "com.snapchat.android" -> isTabSelected(root, SNAPCHAT_SPOTLIGHT_LABELS)
            else -> false
        }
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
