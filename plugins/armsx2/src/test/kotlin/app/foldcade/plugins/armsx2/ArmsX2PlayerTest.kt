package app.foldcade.plugins.armsx2

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.FirmwareOffer
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.StartDisplay
import java.util.Locale
import java.util.ServiceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmsX2PlayerTest {
    private val player = ArmsX2Player()

    private val game = Game(
        backendId = "romm",
        remoteKey = "37",
        platformId = "playstation-2",
        availability = Availability.Cached,
        label = "Need for Speed Underground 2",
    )

    private fun launch(uri: String, packageName: String = ArmsX2Player.PACKAGES.first()) = player.launchIntent(
        LaunchRequest(game = game, target = LaunchTarget.ContentUri(uri), resolvedPackage = packageName),
    )

    @Test
    fun serviceFileLoadsTheEntry() {
        val loaded = ServiceLoader.load(PluginEntry::class.java, ArmsX2PlayerTest::class.java.classLoader)
            .filterIsInstance<ArmsX2Entry>()
            .single()
        assertEquals(listOf("armsx2"), loaded.players.map { it.id })
        assertTrue(loaded.platforms.isEmpty())
        val player = loaded.players.single()
        assertEquals("ARMSX2", player.displayName)
        assertEquals("playstation-2", player.platformId)
        assertEquals(listOf("com.armsx2", "come.nanodata.armsx2", "com.armsx2.nightly"), player.packageNames)
        assertEquals(StartDisplay.PickerChoice, player.startDisplay)
        assertFalse(player.occupiesBothDisplays)
        assertEquals(0x81000, player.firmwareHeaderSize)
    }

    @Test
    fun theFolderPromptOpensOnTheGithubBuildsDataFolder() {
        assertEquals(emptyList<SaveDeclaration>(), player.saveDeclarations(game))
        assertFalse(player.hasSaveFolder())
        player.bindSaveFolder("file:///sdcard/Android/data/com.armsx2/files")
        assertFalse(player.hasSaveFolder())
        player.bindSaveFolder("content://com.android.externalstorage.documents/tree/x")
        assertTrue(player.hasSaveFolder())
        assertEquals(
            listOf(SaveDeclaration("card1", "content://com.android.externalstorage.documents/tree/x")),
            player.saveDeclarations(game),
        )
        assertEquals(
            "content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata%2Fcom.armsx2%2Ffiles",
            player.suggestedSaveFolder,
        )
        assertEquals(setOf("memcards", "bios"), player.saveFolderMarkers)
    }

    @Test
    fun firmwareGoesInBiosUnderTheFilesOwnName() {
        assertEquals("bios/ps2-0230a-20080220.bin", player.firmwareFileName("ps2-0230a-20080220.bin"))
        assertEquals("bios/ps2-0230a-20080220.bin", player.firmwareFileName("PS2_BIOS.zip/ps2-0230a-20080220.bin"))
        assertEquals("bios/ps2-0101jd-20000217.bin", player.firmwareFileName("PS2_BIOS.zip/Misc BIOS/ps2-0101jd-20000217.bin"))
        for (name in listOf("", " ", ".", "..", "a/../bios.bin", "..\\bios.bin", "bios\u0000", "a//b.bin", "/bios.bin")) {
            assertNull(name, player.firmwareFileName(name))
        }
    }

    @Test
    fun eachPackageLaunchesTheGameAsTheViewData() {
        val uri = "content://app.foldcade.romm.folders/document/game.iso"
        for (packageName in ArmsX2Player.PACKAGES) {
            val intent = launch(uri, packageName)
            assertEquals(packageName, intent.packageName)
            assertEquals("com.armsx2.MainActivity", intent.componentClass)
            assertEquals("android.intent.action.VIEW", intent.action)
            assertEquals(uri, intent.dataUri)
            assertTrue(intent.grantReadUri)
            assertTrue(intent.extras.isEmpty())
            assertEquals(setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask), intent.flags)
        }
    }

    @Test
    fun pathsAndOtherPackagesAreRefused() {
        for (uri in listOf("file:///sdcard/Game.iso", "/sdcard/Game.iso")) {
            assertTrue(uri, runCatching { launch(uri) }.exceptionOrNull() is PluginException.NotFound)
        }
        assertTrue(runCatching { launch("content://games/a.iso", "xyz.aethersx2.android") }.exceptionOrNull() is PluginException.NotFound)
        val appRef = runCatching {
            player.launchIntent(
                LaunchRequest(game, LaunchTarget.AppRef(mapOf("app_id" to "1")), ArmsX2Player.PACKAGES.first()),
            )
        }
        assertTrue(appRef.exceptionOrNull() is PluginException.NotFound)
    }

    @Test
    fun aConsoleRomverIsReadFromTheRomDirectory() {
        val usa = parsePs2ConsoleBios(biosHeader("0230AC20080220"))
        assertEquals(230, usa?.version)
        assertEquals('A', usa?.region)
        assertEquals(20080220, usa?.date)
        assertEquals("0230AC20080220", usa?.text)
        // Asia and China are console BIOS too. The choice decides whether they are used.
        assertEquals('H', parsePs2ConsoleBios(biosHeader("0220HC20060210"))?.region)
        assertEquals('C', parsePs2ConsoleBios(biosHeader("0190CC20030623"))?.region)
        // A devkit, an arcade image, a DVD image, and a key file are not console BIOS.
        assertNull(parsePs2ConsoleBios(biosHeader("0101JD20000217")))
        assertNull(parsePs2ConsoleBios(biosHeader("0100TZ20000306")))
        assertNull(parsePs2ConsoleBios("DVD firmware".toByteArray()))
        assertNull(parsePs2ConsoleBios("civ".toByteArray()))
        // ROMVER past the bytes the shell reads is not a BIOS the player can see.
        val far = biosHeader("0230AC20080220", resetSize = 1_100_000)
        assertEquals('A', parsePs2ConsoleBios(far)?.region)
        assertNull(parsePs2ConsoleBios(far.copyOf(ArmsX2Player.BIOS_HEADER)))
        val near = biosHeader("0230EC20080220", resetSize = 0x7fe00)
        assertEquals('E', parsePs2ConsoleBios(near.copyOf(ArmsX2Player.BIOS_HEADER))?.region)
    }

    @Test
    fun oneConsoleBiosIsChosenByRegionThenVersionThenDateThenName() {
        val offers = listOf(
            offer("PS2_BIOS.zip/ps2-0220e-20060905.bin", "0220EC20060905"),
            offer("PS2_BIOS.zip/ps2-0230a-20080220.bin", "0230AC20080220"),
            offer("PS2_BIOS.zip/ps2-0220a-20060210.bin", "0220AC20060210"),
            offer("PS2_BIOS.zip/ps2-0230a-earlier.bin", "0230AC20050620"),
            offer("PS2_BIOS.zip/ps2-0250j-20100415.bin", "0250JC20100415"),
            offer("PS2_BIOS.zip/ps2-0190c-20030623.bin", "0190CC20030623"),
            offer("PS2_BIOS.zip/ps2-0101jd-20000217.bin", "0101JD20000217"),
            offer("keys/civ.bin", "not-a-bios"),
            offer("PS2_BIOS.zip", "PK\u0003\u0004zip"),
            FirmwareOffer("DVD Firmware/ps2-dvd-110u.bin", "DVD firmware".toByteArray()),
        )
        // The US takes the newest Americas BIOS, not the newer Japan one.
        assertEquals("PS2_BIOS.zip/ps2-0230a-20080220.bin", choosePs2Bios(offers, "US").single().name)
        assertEquals("PS2_BIOS.zip/ps2-0230a-20080220.bin", choosePs2Bios(offers, "CA").single().name)
        assertEquals("PS2_BIOS.zip/ps2-0230a-20080220.bin", choosePs2Bios(offers, "MX").single().name)
        assertEquals("PS2_BIOS.zip/ps2-0230a-20080220.bin", choosePs2Bios(offers, "BR").single().name)
        // Japan takes Japan even when Europe and the Americas are present.
        assertEquals("PS2_BIOS.zip/ps2-0250j-20100415.bin", choosePs2Bios(offers, "JP").single().name)
        // Anywhere else takes Europe.
        assertEquals("PS2_BIOS.zip/ps2-0220e-20060905.bin", choosePs2Bios(offers, "DE").single().name)
        assertEquals("PS2_BIOS.zip/ps2-0220e-20060905.bin", choosePs2Bios(offers, "AU").single().name)
        // No Americas BIOS: Europe, then Japan, then any.
        val noAmericas = offers.filterNot { it.name.contains("0230a") || it.name.contains("0220a") }
        assertEquals("PS2_BIOS.zip/ps2-0220e-20060905.bin", choosePs2Bios(noAmericas, "US").single().name)
        val japanOnly = offers.filter { it.name.contains("0250j") || it.name.contains("0101jd") }
        assertEquals("PS2_BIOS.zip/ps2-0250j-20100415.bin", choosePs2Bios(japanOnly, "FR").single().name)
        val china = listOf(offer("PS2_BIOS.zip/ps2-0190c-20030623.bin", "0190CC20030623"))
        assertEquals("PS2_BIOS.zip/ps2-0190c-20030623.bin", choosePs2Bios(china, "US").single().name)
        // Same version and region: the later date, then the earliest name.
        val tied = listOf(
            offer("b.bin", "0220AC20060210"),
            offer("a.bin", "0220AC20060210"),
            offer("c.bin", "0220AC20050620"),
        )
        assertEquals("a.bin", choosePs2Bios(tied, "US").single().name)
        assertTrue(choosePs2Bios(listOf(offer("civ.bin", "key")), "US").isEmpty())
        assertTrue(choosePs2Bios(emptyList(), "US").isEmpty())
    }

    @Test
    fun theDeviceLocalePicksTheRegion() {
        val offers = listOf(
            offer("japan.bin", "0250JC20100415"),
            offer("usa.bin", "0230AC20080220"),
            offer("europe.bin", "0230EC20080220"),
        )
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPAN)
            assertEquals("japan.bin", player.chooseFirmware(offers).single().name)
            Locale.setDefault(Locale.US)
            assertEquals("usa.bin", player.chooseFirmware(offers).single().name)
            Locale.setDefault(Locale.GERMANY)
            assertEquals("europe.bin", player.chooseFirmware(offers).single().name)
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun offer(name: String, romver: String) = FirmwareOffer(name, biosHeader(romver))

    /**
     * A small image: `RESET`'s bytes, then a ROM directory (`RESET`, `ROMDIR`,
     * `ROMVER`, and an empty entry), then the `ROMVER` text.
     */
    private fun biosHeader(romver: String, resetSize: Int = 64): ByteArray {
        val text = romver.toByteArray(Charsets.US_ASCII)
        if (text.size != 14) return text
        val tableSize = 16 * 4
        val romverAt = resetSize + tableSize
        val bytes = ByteArray(romverAt + 16)
        fun entry(at: Int, name: String, size: Long) {
            name.toByteArray(Charsets.US_ASCII).copyInto(bytes, at)
            bytes[at + 12] = size.toByte()
            bytes[at + 13] = (size shr 8).toByte()
            bytes[at + 14] = (size shr 16).toByte()
            bytes[at + 15] = (size shr 24).toByte()
        }
        entry(resetSize, "RESET", resetSize.toLong())
        entry(resetSize + 16, "ROMDIR", tableSize.toLong())
        entry(resetSize + 32, "ROMVER", 14)
        text.copyInto(bytes, romverAt)
        return bytes
    }
}
