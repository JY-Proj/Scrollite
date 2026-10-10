package com.jynx.scrollite.data

/**
 * A tracked app. Declaration order is the fixed order apps appear in (and
 * get chart colors by), so it must only ever be appended to.
 */
enum class Platform(
    val displayName: String,
    private val sectionLabels: Map<Section, String> = emptyMap(),
) {
    INSTAGRAM(
        displayName = "Instagram",
        sectionLabels = mapOf(
            Section.SHORT_VIDEO to "Reels",
            Section.MESSAGES to "Messages",
        ),
    ),
    TIKTOK(
        displayName = "TikTok",
        sectionLabels = mapOf(
            Section.SHORT_VIDEO to "For You",
            Section.MESSAGES to "Inbox",
        ),
    ),
    YOUTUBE(
        displayName = "YouTube",
        sectionLabels = mapOf(
            Section.SHORT_VIDEO to "Shorts",
        ),
    ),
    REDNOTE(
        displayName = "RedNote",
        sectionLabels = mapOf(
            Section.SHORT_VIDEO to "Videos",
            Section.EXPLORE to "Explore",
            Section.MESSAGES to "Messages",
        ),
    );

    /** This app's own name for [section], e.g. Reels for Instagram's short videos. */
    fun labelFor(section: Section): String = sectionLabels[section] ?: section.defaultLabel
}
