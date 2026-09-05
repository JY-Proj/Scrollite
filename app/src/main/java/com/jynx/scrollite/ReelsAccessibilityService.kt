package com.jynx.scrollite

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ReelsAccessibilityService : AccessibilityService() {

    // TEMP: UI exploration, remove before production
    private var lastDumpMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d(TAG, "eventType=${AccessibilityEvent.eventTypeToString(event.eventType)} pkg=${event.packageName}")

        // TEMP: UI exploration, remove before production
        if (event.packageName == INSTAGRAM_PKG) {
            val now = System.currentTimeMillis()
            if (now - lastDumpMs >= DUMP_INTERVAL_MS) {
                lastDumpMs = now
                dumpNodeTree(rootInActiveWindow, depth = 0)
            }
        }
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

    companion object {
        private const val TAG = "ReelsService"

        // TEMP: UI exploration, remove before production
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private const val DUMP_INTERVAL_MS = 3_000L
    }
}