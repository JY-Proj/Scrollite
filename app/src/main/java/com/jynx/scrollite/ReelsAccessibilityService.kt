package com.jynx.scrollite

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.os.Build
import android.view.Display

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

// for later
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK

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

        // caption extraction
        private const val ID_CAPTION_COMPONENT = "com.instagram.android:id/clips_caption_component"
        private const val DEBOUNCE_MS = 400L
    }

    // Debounce
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null

    // TEMP: UI exploration, remove before production
    private var lastDumpMs = 0L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: ReelsRepository by lazy {
        RoomReelsRepository(AppDatabase.getInstance(applicationContext).reelDao(), applicationContext)
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
                scheduleExtraction()
            }
        }  else {
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
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
     * Resets a short timer every time this is called. Only fires the actual
     * extraction once no new events have come in for DEBOUNCE_MS — i.e. once
     * the screen has "settled" instead of still mid-transition.
     */
    private fun scheduleExtraction() {
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        val runnable = Runnable {
            val freshRoot = rootInActiveWindow ?: return@Runnable

            // Re-check everything against the settled screen — the username
            // that triggered this might have already changed if the user
            // kept scrolling during the 400ms debounce window.
            val (stillReels, _, _) = isReelsScreen(freshRoot)
            if (!stillReels) { freshRoot.recycle(); return@Runnable }

            val username = extractUsername(freshRoot)
            val caption = extractCaption(freshRoot)
            val hashtags = extractHashtags(caption)
            Log.d(TAG, "extracted username=$username caption=$caption hashtags=$hashtags")

            if (username != null) {
                val entry = ReelEntry(
                    username = username,
                    caption = caption,
                    hashtags = hashtags,
                    screenshotPath = null, // filled in after screenshot resolves
                    platform = Platform.INSTAGRAM,
                    timestamp = System.currentTimeMillis(),
                )
                serviceScope.launch {
                    val rowId = repository.insert(entry) // needs to return the new row's id
                    triggerScreenshot(rowId)
                }
            }

            freshRoot.recycle()
        }
        debounceRunnable = runnable
        debounceHandler.postDelayed(runnable, DEBOUNCE_MS)
    }

    /**
     * Takes a screenshot of the current screen, guarded for API level and
     * handled asynchronously via callback. Never throws — failures are
     * logged and skipped, since this is a nice-to-have, not core detection.
     */
    private fun triggerScreenshot(rowId: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "Skipping screenshot: requires API 30+")
            return
        }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to wrap screenshot buffer: ${e.message}")
                        null
                    } finally {
                        result.hardwareBuffer.close()
                    }
                    if (bitmap == null) return
                    serviceScope.launch {
                        val path = repository.saveScreenshot(rowId, bitmap) // Person 2's function
                        repository.updateScreenshotPath(rowId, path)
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Screenshot failed, errorCode=$errorCode")
                }
            }
        )
    }

    /**
     * Finds the caption text inside clips_caption_component. Per the dump,
     * the container itself has no visible text — the actual string shows up
     * as a contentDescription on a node nested inside it.
     */
    private fun extractCaption(root: AccessibilityNodeInfo): String? {
        val containers = root.findAccessibilityNodeInfosByViewId(ID_CAPTION_COMPONENT)
            .filter { it.isVisibleToUser }
        if (containers.isEmpty()) return null

        return containers.firstNotNullOfOrNull { findFirstContentDescription(it) }
    }

    private fun findFirstContentDescription(node: AccessibilityNodeInfo): String? {
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) return it }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findFirstContentDescription(child)?.let { return it }
        }
        return null
    }

    private fun extractHashtags(caption: String?): List<String> {
        if (caption.isNullOrBlank()) return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
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