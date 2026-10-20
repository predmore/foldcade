package app.foldcade.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class SliderTouchTest {
    @Test
    fun theMusicSliderTouchTargetIsAtLeastFortyEightDp() {
        assertTrue(SLIDER_TOUCH_TARGET_DP >= 48)
    }
}
