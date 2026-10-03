package com.jynx.scrollite.tracking.apps

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.Section
import com.jynx.scrollite.tracking.AppDetector
import com.jynx.scrollite.tracking.NodeQueries
import com.jynx.scrollite.tracking.ShortVideoInfo

/**
 * YouTube: recognizes Shorts; everything else (home feed, watch page,
 * Subscriptions, Library) is OTHER. The `reel_*` view IDs below are
 * Shorts-exclusive in the YouTube app and language-independent, which makes
 * them better signals than the localized button content-descriptions. They
 * were sourced from Shorts accessibility tooling and break when YouTube
 * renames them — candidates for remote config before launch, same as the
 * Instagram IDs.
 */
object YouTubeDetector : AppDetector {

    private const val TAG = "YouTubeDetector"

    override val platform = Platform.YOUTUBE
    override val packageName = "com.google.android.youtube"

    // Shorts-only containers. Any one present means the Shorts player is on
    // screen; none of these appear on the regular watch page or feeds.
    private const val ID_REEL_RECYCLER = "com.google.android.youtube:id/reel_recycler"
    private const val ID_REEL_PLAYER_PAGE_CONTAINER = "com.google.android.youtube:id/reel_player_page_container"
    private const val ID_REEL_PLAYER_UNDERLAY = "com.google.android.youtube:id/reel_player_underlay"

    // Best-effort author extraction. The channel on a Short is rendered as a
    // "@handle"; these are the candidate IDs for it. Extraction is a
    // nice-to-have for the watch history, so an unreadable/renamed ID just
    // yields null rather than affecting section timing.
    private val AUTHOR_ID_CANDIDATES = listOf(
        "com.google.android.youtube:id/reel_channel_bar_channel_name",
        "com.google.android.youtube:id/reel_channel_name",
    )

    /**
     * One Shorts-specific container is already a strong signal, but keep the
     * list multi-signal so a single YouTube rename doesn't break detection
     * entirely — time must never be dropped just because one ID changed.
     */
    private const val SHORTS_SIGNAL_THRESHOLD = 1

    override fun detectSection(root: AccessibilityNodeInfo): Section =
        if (isShortsScreen(root)) Section.SHORT_VIDEO else Section.OTHER

    override fun extractShortVideo(root: AccessibilityNodeInfo): ShortVideoInfo? {
        val author = AUTHOR_ID_CANDIDATES.firstNotNullOfOrNull { NodeQueries.firstVisibleText(root, it) }
            ?: return null
        return ShortVideoInfo(author = author, caption = null)
    }

    private fun isShortsScreen(root: AccessibilityNodeInfo): Boolean {
        val signals = listOf(
            NodeQueries.hasVisibleViewId(root, ID_REEL_RECYCLER),
            NodeQueries.hasVisibleViewId(root, ID_REEL_PLAYER_PAGE_CONTAINER),
            NodeQueries.hasVisibleViewId(root, ID_REEL_PLAYER_UNDERLAY),
        )
        val matchCount = signals.count { it }
        Log.d(TAG, "shorts signals $matchCount/${signals.size}")
        return matchCount >= SHORTS_SIGNAL_THRESHOLD
    }
}
