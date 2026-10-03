package com.jynx.scrollite.ui.screentime

import org.junit.Assert.assertEquals
import org.junit.Test

class LooseningToneTest {

    @Test
    fun getsSadderWithEachLooseningToday() {
        assertEquals(LooseningTone.FIRST, LooseningTone.forCount(0))
        assertEquals(LooseningTone.AGAIN, LooseningTone.forCount(1))
        assertEquals(LooseningTone.SAD, LooseningTone.forCount(2))
        assertEquals(LooseningTone.SAD, LooseningTone.forCount(7))
    }
}
