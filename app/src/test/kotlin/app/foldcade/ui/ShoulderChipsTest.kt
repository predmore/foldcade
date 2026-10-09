package app.foldcade.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoulderChipsTest {
    @Test
    fun chipsUseTheArtKitDrawables() {
        assertEquals("ic_btn_l1", ShoulderChips.l1)
        assertEquals("ic_btn_l1_filled", ShoulderChips.l1Filled)
        assertEquals("ic_btn_r1", ShoulderChips.r1)
        assertEquals("ic_btn_r1_filled", ShoulderChips.r1Filled)
    }
}