package app.foldcade.ui

import app.foldcade.R
import app.foldcade.language.Copy
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

    @Test
    fun statusUsesIconsNotPercentOrNetworkText() {
        assertEquals(R.drawable.ic_status_battery_100, batteryStatusIcon(100, charging = false))
        assertEquals(R.drawable.ic_status_battery_100_charging, batteryStatusIcon(97, charging = true))
        assertEquals(R.drawable.ic_status_wifi_4, wifiStatusIcon(Copy.wifi))
        assertEquals(R.drawable.ic_status_wifi_off, wifiStatusIcon(Copy.offline))
        assertEquals(R.drawable.ic_status_bell_dot, bellStatusIcon(0))
        assertEquals(R.drawable.ic_status_bell_3, bellStatusIcon(3))
        assertEquals(R.drawable.ic_status_bell_9plus, bellStatusIcon(12))
    }
}