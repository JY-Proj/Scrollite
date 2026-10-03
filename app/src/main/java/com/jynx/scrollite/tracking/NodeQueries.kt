package com.jynx.scrollite.tracking

import android.view.accessibility.AccessibilityNodeInfo

/** Small accessibility-tree helpers shared by app detectors. */
internal object NodeQueries {

    /** Whether any visible element in the tree has [viewId]. */
    fun hasVisibleViewId(root: AccessibilityNodeInfo, viewId: String): Boolean =
        root.findAccessibilityNodeInfosByViewId(viewId).any { it.isVisibleToUser }

    /** Whether the element with [viewId] (e.g. a bottom-nav tab) is selected. */
    fun isSelected(root: AccessibilityNodeInfo, viewId: String): Boolean =
        root.findAccessibilityNodeInfosByViewId(viewId).any { it.isSelected }

    /**
     * Trimmed text of the first visible element with [viewId]. recycle() is a
     * no-op on API 33+ but still matters on this app's minSdk 26–32 range.
     */
    fun firstVisibleText(root: AccessibilityNodeInfo, viewId: String): String? {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        try {
            return nodes.firstOrNull { it.isVisibleToUser }
                ?.text?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
        } finally {
            nodes.forEach { it.recycle() }
        }
    }

    /** First non-blank content description at or below [node]. */
    fun firstContentDescription(node: AccessibilityNodeInfo): String? {
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) return it }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            firstContentDescription(child)?.let { return it }
        }
        return null
    }

    /** Whether any element at or below [root] has a content description containing [text]. */
    fun hasContentDescription(root: AccessibilityNodeInfo, text: String): Boolean {
        if (root.contentDescription?.contains(text, ignoreCase = true) == true) return true
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (hasContentDescription(child, text)) return true
        }
        return false
    }
}
