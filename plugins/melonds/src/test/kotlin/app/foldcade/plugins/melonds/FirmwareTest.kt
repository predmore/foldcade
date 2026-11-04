package app.foldcade.plugins.melonds

import app.foldcade.api.plugin.FirmwareOffer
import org.junit.Assert.*
import org.junit.Test

class FirmwareTest {
    private val player = MelonDsPlayer()
    @Test fun dsAndDsiSetsHaveSeparateDestinationsWithAndroidsRequiredSizes() {
        assertEquals("nds/firmware.bin", player.firmwareDestination(FirmwareOffer("firmware.bin", ByteArray(262144))))
        assertEquals("dsi/firmware.bin", player.firmwareDestination(FirmwareOffer("dsi_firmware.bin", ByteArray(131072))))
        assertEquals("dsi/firmware.bin", player.firmwareDestination(FirmwareOffer("bios.zip/dsi/firmware.bin", ByteArray(131072))))
        assertTrue(player.firmwareKeepsFolders)
        assertEquals("dsi/bios7.bin", player.firmwareDestination(FirmwareOffer("dsi_bios7.bin", ByteArray(65536))))
        assertEquals("dsi/bios9.bin", player.firmwareDestination(FirmwareOffer("DSi.zip/bios9.bin", ByteArray(65536))))
        assertNull(player.firmwareDestination(FirmwareOffer("bios7.bin", ByteArray(16384)))) // a familiar name is not a known DS BIOS
        assertNull(player.firmwareDestination(FirmwareOffer("dsi_firmware.bin", ByteArray(262144))))
        assertNull(player.firmwareDestination(FirmwareOffer("firmware.bin", ByteArray(10))))
        assertNull(player.firmwareFileName("dump.zip/../bios7.bin"))
    }
}
