package com.jynx.scrollite.tracking.apps

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.Section
import com.jynx.scrollite.tracking.AppDetector
import com.jynx.scrollite.tracking.NodeQueries
import com.jynx.scrollite.tracking.ShortVideoInfo

/**
 * Instagram: recognizes Reels; everything else is OTHER until feed/DM/stories
 * signals are added. View IDs were found via Layout Inspector and break when
 * Instagram renames them — candidates for remote config before launch.
 */
object InstagramDetector : AppDetector {

    private const val TAG = "InstagramDetector"

    override val platform = Platform.INSTAGRAM
    override val packageName = "com.instagram.android"

    private const val ID_CLIPS_PAGER = "com.instagram.android:id/clips_viewer_view_pager"
    private const val ID_AUTHOR_USERNAME = "com.instagram.android:id/clips_author_username"
    private const val ID_TAB_CLIPS = "com.instagram.android:id/clips_tab"

    // Reels action buttons — only meaningful together
    private const val ID_LIKE_BUTTON = "com.instagram.android:id/like_button"
    private const val ID_COMMENT_BUTTON = "com.instagram.android:id/comment_button"
    private const val ID_DIRECT_SHARE_BUTTON = "com.instagram.android:id/direct_share_button"
    private const val ID_CLIPS_UFI_MORE_BUTTON_COMPONENT = "com.instagram.android:id/clips_ufi_more_button_component"

    private const val ID_CAPTION_COMPONENT = "com.instagram.android:id/clips_caption_component"

    /** Signals that must match to call a screen Reels. Low while there are few; raise as more are added. */
    private const val REELS_SIGNAL_THRESHOLD = 2

    override fun detectSection(root: AccessibilityNodeInfo): Section =
        if (isReelsScreen(root)) Section.SHORT_VIDEO else Section.OTHER

    override fun extractShortVideo(root: AccessibilityNodeInfo): ShortVideoInfo? {
        val author = NodeQueries.firstVisibleText(root, ID_AUTHOR_USERNAME) ?: return null
        return ShortVideoInfo(author = author, caption = extractCaption(root))
    }

    /**
     * Several independent signals, so one Instagram update breaking one of
     * them doesn't break detection entirely.
     */
    private fun isReelsScreen(root: AccessibilityNodeInfo): Boolean {
        val signals = listOf(
            NodeQueries.hasVisibleViewId(root, ID_CLIPS_PAGER),
            NodeQueries.hasVisibleViewId(root, ID_AUTHOR_USERNAME),
            NodeQueries.hasVisibleViewId(root, ID_LIKE_BUTTON) &&
                NodeQueries.hasVisibleViewId(root, ID_COMMENT_BUTTON) &&
                NodeQueries.hasVisibleViewId(root, ID_DIRECT_SHARE_BUTTON) &&
                NodeQueries.hasVisibleViewId(root, ID_CLIPS_UFI_MORE_BUTTON_COMPONENT),
            NodeQueries.isSelected(root, ID_TAB_CLIPS),
        )
        val matchCount = signals.count { it }
        Log.d(TAG, "reels signals $matchCount/${signals.size}")
        return matchCount >= REELS_SIGNAL_THRESHOLD
    }

    /**
     * The caption container has no text of its own — the string is a content
     * description on a node nested inside it.
     */
    private fun extractCaption(root: AccessibilityNodeInfo): String? =
        root.findAccessibilityNodeInfosByViewId(ID_CAPTION_COMPONENT)
            .filter { it.isVisibleToUser }
            .firstNotNullOfOrNull { NodeQueries.firstContentDescription(it) }
}
