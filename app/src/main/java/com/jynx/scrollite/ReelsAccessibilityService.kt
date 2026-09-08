package com.jynx.scrollite

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jynx.scrollite.data.AppDatabase
import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.ReelEntry
import com.jynx.scrollite.data.ReelsRepository
import com.jynx.scrollite.data.RoomReelsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReelsAccessibilityService : AccessibilityService() {

    companion object {

        // TEMP: UI exploration, remove before production
        private const val TAG = "ReelsService"
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private const val DUMP_INTERVAL_MS = 3000L

        // Signals discovered via Layout Inspector / the temp dump function
        private const val ID_CLIPS_PAGER = "com.instagram.android:id/clips_viewer_view_pager"
        private const val ID_AUTHOR_USERNAME = "com.instagram.android:id/clips_author_username"
        private const val ID_TAB_CLIPS = "com.instagram.android:id/clips_tab"

        // button group
        private const val ID_LIKE_BUTTON = "com.instagram.android:id/like_button"
        private const val ID_COMMENT_BUTTON = "com.instagram.android:id/comment_button"
        private const val ID_DIRECT_SHARE_BUTTON = "com.instagram.android:id/direct_share_button"
        private const val ID_CLIPS_UFI_MORE_BUTTON_COMPONENT = "com.instagram.android:id/clips_ufi_more_button_component"
    }


    // TEMP: UI exploration, remove before production
    private var lastDumpMs = 0L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: ReelsRepository by lazy {
        RoomReelsRepository(AppDatabase.getInstance(applicationContext).reelDao())
    }

    // Interim de-dup heuristic: insert only when the username changes from the
    // last inserted one. Not real debounce (won't handle scrolling back to a
    // previous Reel, rapid re-scrolls, etc.) — a placeholder until a proper
    // "confirmed new Reel" trigger exists.
    private var lastInsertedUsername: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d(TAG, "eventType=${AccessibilityEvent.eventTypeToString(event.eventType)} pkg=${event.packageName}")

        if (event.packageName != INSTAGRAM_PKG) return // TODO: add more later as we are not only limited to Instagram

        val root = rootInActiveWindow ?: return

        // Detection runs first — dumpNodeTree recycles root, so it must go last
        val (isReels, matchCount, totalSignals) = isReelsScreen(root)
        Log.d(TAG, "isReels=$isReels matchCount=$matchCount totalSignals=$totalSignals")

        if (isReels) {
            val username = extractUsername(root)
            Log.d(TAG, "REELS DETECTED username=$username") // TEMP: verify username extraction, remove before production

            if (username != null && username != lastInsertedUsername) {
                lastInsertedUsername = username
                val entry = ReelEntry(
                    username = username,
                    caption = null, // not yet extracted (Person 1)
                    hashtags = emptyList(), // not yet extracted (Person 1)
                    screenshotPath = null, // capture not yet built
                    platform = Platform.INSTAGRAM, // only Instagram is detected today
                    timestamp = System.currentTimeMillis(),
                )
                serviceScope.launch { repository.insert(entry) }
            }
        }

        // TEMP: UI exploration, remove before production
        val now = System.currentTimeMillis()
        if (now - lastDumpMs >= DUMP_INTERVAL_MS) {
            lastDumpMs = now
            dumpNodeTree(root, depth = 0) // recycles root
        } else {
            root.recycle()
        }
    }

    /**
     * Checks the current screen for known Reels signals and returns
     * true if enough of them match (so one Instagram update breaking
     * one signal doesn't break detection entirely).
     */
    private fun isReelsScreen(root: AccessibilityNodeInfo): Triple<Boolean, Int, Int> {
        val signals = listOf(
            hasViewId(root, ID_CLIPS_PAGER),
            hasViewId(root, ID_AUTHOR_USERNAME),
            hasViewId(root, ID_LIKE_BUTTON) &&
            hasViewId(root, ID_COMMENT_BUTTON) &&
            hasViewId(root, ID_DIRECT_SHARE_BUTTON) &&
            hasViewId(root, ID_CLIPS_UFI_MORE_BUTTON_COMPONENT),
            isTabSelected(root, ID_TAB_CLIPS),
            // add more checks here as we nail down more signals,
            // e.g. hasContentDescription(root, "Like"), hasContentDescription(root, "Share reel")
        )

        val matchCount = signals.count { it }
        val threshold = 2 // start low with few signals; raise as we add more
        return Triple(matchCount >= threshold, matchCount, signals.size)
    }

    /**
     * Searches the entire UI tree for an element with a specific view ID
     */
    private fun hasViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        return root.findAccessibilityNodeInfosByViewId(viewId)
            .any { it.isVisibleToUser }
    }

    /**
     * Reads the author username text off the clips_author_username node.
     * recycle() is a no-op on API 33+ (pool removed) but still matters on
     * this app's minSdk 26-32 range, so it's called unconditionally.
     */
    private fun extractUsername(root: AccessibilityNodeInfo): String? {
        val nodes = root.findAccessibilityNodeInfosByViewId(ID_AUTHOR_USERNAME)
        try {
            return nodes.firstOrNull { it.isVisibleToUser }
                ?.text?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
        } finally {
            nodes.forEach { it.recycle() }
        }
    }

    /**
     * Recursively search for UI elements with specific text content descriptions
     *
     * Can be used to find buttons/icons by text description
     */
    private fun hasContentDescription(root: AccessibilityNodeInfo, text: String): Boolean {
        if (root.contentDescription?.contains(text, ignoreCase = true) == true) return true
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (hasContentDescription(child, text)) return true
        }
        return false
    }

    /**
     * Check if the user is on a certain tab
     */
    private fun isTabSelected(root: AccessibilityNodeInfo, tabId: String): Boolean {
        return root.findAccessibilityNodeInfosByViewId(tabId)
            .any { it.isSelected }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // TEMP: UI exploration, remove before production
    private fun dumpNodeTree(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null) return

        val indent = "  ".repeat(depth)
        val cls = node.className ?: ""
        val id = node.viewIdResourceName ?: ""
        val text = node.text ?: ""
        val desc = node.contentDescription ?: ""

        Log.d(TAG, "$indent[$depth] cls=$cls id=$id text=$text desc=$desc")

        for (i in 0 until node.childCount) {
            dumpNodeTree(node.getChild(i), depth + 1)
        }

        node.recycle()
    }

}