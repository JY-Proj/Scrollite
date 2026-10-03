package com.jynx.scrollite.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformSectionTest {

    @Test
    fun appsUseTheirOwnNamesForSections() {
        assertEquals("Reels", Platform.INSTAGRAM.labelFor(Section.SHORT_VIDEO))
        assertEquals("For You", Platform.TIKTOK.labelFor(Section.SHORT_VIDEO))
        assertEquals("Feed", Platform.INSTAGRAM.labelFor(Section.FEED)) // falls back to the generic name
    }

    @Test
    fun onlyShortVideoCountsTowardTheLimit() {
        assertEquals(listOf(Section.SHORT_VIDEO), Section.shortForm)
    }

    @Test
    fun unknownStoredSection_readsAsOther_insteadOfCrashing() {
        val converters = Converters()
        assertEquals(Section.SHORT_VIDEO, converters.toSection("SHORT_VIDEO"))
        assertEquals(Section.OTHER, converters.toSection("REELS"))
        assertEquals(Section.OTHER, converters.toSection("SOMETHING_REMOVED"))
    }
}
