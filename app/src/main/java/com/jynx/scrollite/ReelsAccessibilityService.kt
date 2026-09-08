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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d(TAG, "eventType=${AccessibilityEvent.eventTypeToString(event.eventType)} pkg=${event.packageName}")

        if (event.packageName != INSTAGRAM_PKG) return // TODO: add more later as we are not only limited to Instagram

        val root = rootInActiveWindow ?: return

        // Detection runs first — dumpNodeTree recycles root, so it must go last
        val (isReels, matchCount, totalSignals) = isReelsScreen(root)
        Log.d(TAG, "isReels=$isReels matchCount=$matchCount totalSignals=$totalSignals")

        // TEMP: UI exploration, remove before production
        val now = System.currentTimeMillis()
        if (now - lastDumpMs >= DUMP_INTERVAL_MS) {
            lastDumpMs = now
            dumpNodeTree(root, depth = 0) // recycles root
        } else {
            root.recycle()
        }

        if (isReels) {
            scheduleExtraction()
        } else {
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
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
            val freshRoot = rootInActiveWindow ?: return@Runnable // re-fetch, don't reuse old root
            val caption = extractCaption(freshRoot)
            val hashtags = extractHashtags(caption)
            Log.d(TAG, "caption=$caption hashtags=$hashtags")
            triggerScreenshot()
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
    private fun triggerScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "Skipping screenshot: takeScreenshot() requires API 30+, device is ${Build.VERSION.SDK_INT}")
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
                        result.hardwareBuffer.close() // must always close, success or failure
                    }

                    if (bitmap == null) return
                    Log.d(TAG, "Screenshot captured: ${bitmap.width}x${bitmap.height}")
                    // Hand off to Person 2's storage code here, e.g.:
                    // onScreenshotCaptured(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    val reason = when (errorCode) {
                        ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "called too soon after previous screenshot"
                        ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "invalid display"
                        ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "internal error"
                        else -> "unknown error code=$errorCode"
                    }
                    Log.w(TAG, "Screenshot failed: $reason")
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