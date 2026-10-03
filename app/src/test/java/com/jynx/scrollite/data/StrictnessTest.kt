package com.jynx.scrollite.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictnessTest {

    @Test
    fun moreExtensions_isLooser() {
        assertTrue(Strictness.FLEXIBLE.isLooserThan(Strictness.STRICT))
        assertTrue(Strictness.STRICT.isLooserThan(Strictness.LOCKED))
    }

    @Test
    fun sameOrFewerExtensions_isNotLooser() {
        assertFalse(Strictness.STRICT.isLooserThan(Strictness.STRICT))
        assertFalse(Strictness.LOCKED.isLooserThan(Strictness.FLEXIBLE))
    }
}
