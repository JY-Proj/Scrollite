package com.jynx.scrollite.tracking

import android.view.accessibility.AccessibilityNodeInfo
import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.Section
import com.jynx.scrollite.tracking.apps.InstagramDetector
import com.jynx.scrollite.tracking.apps.YouTubeDetector

/**
 * Everything app-specific about tracking one app: how to recognize it and
 * which section of it is on screen. The service, session tracking, limits
 * and stats are app-neutral, so supporting a new app means writing one
 * detector and registering it in [AppDetectors].
 */
interface AppDetector {
    val platform: Platform
    val packageName: String

    /**
     * Which section [root] (this app's active window) shows. Return
     * [Section.OTHER] when unsure — time must never be dropped just because
     * a screen wasn't recognized.
     */
    fun detectSection(root: AccessibilityNodeInfo): Section

    /** The short video on screen, for the watch history; null if unsupported or unreadable. */
    fun extractShortVideo(root: AccessibilityNodeInfo): ShortVideoInfo? = null
}

data class ShortVideoInfo(val author: String, val caption: String?)

/** Registry of supported apps. */
object AppDetectors {
    val all: List<AppDetector> = listOf(InstagramDetector, YouTubeDetector)

    private val byPackage = all.associateBy { it.packageName }

    fun forPackage(packageName: CharSequence?): AppDetector? = packageName?.let { byPackage[it.toString()] }
}
