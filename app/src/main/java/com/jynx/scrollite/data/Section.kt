package com.jynx.scrollite.data

/**
 * The kind of place inside an app a usage session was spent in — generic
 * across apps, so "all short video" is one query whether it came from
 * Instagram Reels, YouTube Shorts or TikTok. Each [Platform] supplies its own
 * user-facing name for a section (see [Platform.labelFor]).
 *
 * Stored by name; [Converters.toSection] maps unknown names to [OTHER], so
 * removing or renaming a value can never crash reads of old rows.
 */
enum class Section(val isShortForm: Boolean, val defaultLabel: String) {
    SHORT_VIDEO(isShortForm = true, defaultLabel = "Short videos"),
    FEED(isShortForm = false, defaultLabel = "Feed"),
    STORIES(isShortForm = false, defaultLabel = "Stories"),
    MESSAGES(isShortForm = false, defaultLabel = "Messages"),
    EXPLORE(isShortForm = false, defaultLabel = "Explore"),

    /** Anything not (yet) recognized. Detectors fall back to this when unsure. */
    OTHER(isShortForm = false, defaultLabel = "Other");

    companion object {
        /** Sections that count toward the daily short-form limit. */
        val shortForm: List<Section> = entries.filter { it.isShortForm }
    }
}
