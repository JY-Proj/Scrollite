package com.jynx.scrollite.tracking.apps

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.Section
import com.jynx.scrollite.tracking.AppDetector
import com.jynx.scrollite.tracking.NodeQueries
import com.jynx.scrollite.tracking.ShortVideoInfo

/**
 * RedNote / Xiaohongshu (小红书): recognizes the immersive, full-screen
 * vertical video player (the short-form feed). The waterfall Explore grid,
 * image notes, profile, messages and the comment sheet are all OTHER.
 *
 * Unlike many Chinese apps, RedNote ships readable (un-obfuscated) resource
 * IDs, so the view-ID signals below are as stable as Instagram's or YouTube's.
 * They were read off on-device layout dumps and, as with the other detectors,
 * break if RedNote renames them — candidates for remote config before launch.
 *
 * The chosen signals are all video-exclusive: the video-feed list container,
 * the danmaku (bullet-comment) toggle, the scrubber, and the player's spoken
 * playback-progress description. Image notes and the Explore grid carry none
 * of them.
 */
object RedNoteDetector : AppDetector {

    private const val TAG = "RedNoteDetector"

    override val platform = Platform.REDNOTE
    override val packageName = "com.xingin.xhs"

    // Immersive video-player containers/controls. Each is video-only.
    private const val ID_VIDEO_FEED_LIST = "com.xingin.xhs:id/matrix_video_feed_note_detail_list"
    private const val ID_DANMAKU_BUTTON = "com.xingin.xhs:id/portraitDanmakuBtn"
    private const val ID_SEEKBAR = "com.xingin.xhs:id/seekBar"
    private const val ID_AREA_PROGRESS = "com.xingin.xhs:id/area_progress"

    // The player narrates playback position, e.g. "已播放到0分40秒，共1分15秒"
    // ("played to 0m40s of 1m15s") — a content description that only the video
    // player renders, so it survives the controls auto-hiding mid-playback.
    private const val PLAYBACK_PROGRESS_MARKER = "已播放到"

    // Best-effort author/caption for the watch history. A renamed/unreadable
    // ID just yields null, which never affects section timing.
    private const val ID_AUTHOR = "com.xingin.xhs:id/matrixNickNameView"
    private const val ID_CAPTION = "com.xingin.xhs:id/noteContentText"

    /**
     * One video-only signal is enough: the feed-list container alone is
     * conclusive. Kept multi-signal with a low threshold so a single renamed
     * ID in a RedNote update can't drop detection (and the user's time).
     */
    private const val VIDEO_SIGNAL_THRESHOLD = 1

    override fun detectSection(root: AccessibilityNodeInfo): Section =
        if (isVideoScreen(root)) Section.SHORT_VIDEO else Section.OTHER

    override fun extractShortVideo(root: AccessibilityNodeInfo): ShortVideoInfo? {
        val author = NodeQueries.firstVisibleText(root, ID_AUTHOR) ?: return null
        return ShortVideoInfo(author = author, caption = NodeQueries.firstVisibleText(root, ID_CAPTION))
    }

    private fun isVideoScreen(root: AccessibilityNodeInfo): Boolean {
        val signals = listOf(
            NodeQueries.hasVisibleViewId(root, ID_VIDEO_FEED_LIST),
            NodeQueries.hasVisibleViewId(root, ID_DANMAKU_BUTTON),
            NodeQueries.hasVisibleViewId(root, ID_SEEKBAR) &&
                NodeQueries.hasVisibleViewId(root, ID_AREA_PROGRESS),
            NodeQueries.hasContentDescription(root, PLAYBACK_PROGRESS_MARKER),
        )
        val matchCount = signals.count { it }
        Log.d(TAG, "video signals $matchCount/${signals.size}")
        return matchCount >= VIDEO_SIGNAL_THRESHOLD
    }
}
