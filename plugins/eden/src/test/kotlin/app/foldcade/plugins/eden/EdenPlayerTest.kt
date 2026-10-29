package app.foldcade.plugins.eden

import app.foldcade.api.plugin.*
import app.foldcade.plugins.emulators.Emulator
import app.foldcade.plugins.emulators.EmulatorPlayer
import java.util.ServiceLoader
import org.junit.Assert.*
import org.junit.Test

class EdenPlayerTest {
    private val game = Game("romm", "35", "nintendo-switch", Availability.Cached, "Fantasy Life i")

    @Test fun serviceAndIdentity() {
        assertEquals("eden", ServiceLoader.load(PluginEntry::class.java).filterIsInstance<EdenEntry>().single().player.id)
        val player = EdenPlayer()
        assertEquals(EDEN_PACKAGES, player.packageNames)
        assertEquals("nintendo-switch", player.platformId)
        assertEquals("Eden", player.displayName)
        assertTrue(player.needsLocalFile)
        assertEquals(StartDisplay.PickerChoice, player.startDisplay)
        assertFalse(player.occupiesBothDisplays)
        assertFalse(player.requiresImportedGame)
        assertFalse(player.hasSaveFolder())
        assertTrue(player.supportsSaveSync(game))
        assertTrue(player.saveDeclarations(game).isEmpty())
        player.bindSaveFolder("content://eden/tree/root")
        assertEquals(listOf(SaveDeclaration("savedata", "content://eden/tree/root")), player.saveDeclarations(game))
        player.bindSaveFolder("")
        assertFalse(player.hasSaveFolder())
    }

    @Test fun intentMatchesTheFormerTableEntryForBothPackages() {
        val old = EmulatorPlayer(Emulator(
            id = "eden", displayName = "Eden", platformIds = listOf("nintendo-switch"),
            activities = EDEN_PACKAGES.associateWith { "org.yuzu.yuzu_emu.activities.EmulationActivity" },
            action = "android.nfc.action.TECH_DISCOVERED",
        ), "nintendo-switch")
        for (pkg in EDEN_PACKAGES) {
            val request = LaunchRequest(game, LaunchTarget.ContentUri("content://romm/fantasy.nsp"), pkg)
            assertEquals(old.launchIntent(request), EdenPlayer().launchIntent(request))
        }
        assertThrows(PluginException.NotFound::class.java) {
            EdenPlayer().launchIntent(LaunchRequest(game, LaunchTarget.ContentUri("file:///rom.nsp"), EDEN_PACKAGES.first()))
        }
        assertThrows(PluginException.NotFound::class.java) {
            EdenPlayer().launchIntent(LaunchRequest(game, LaunchTarget.ContentUri("content://rom"), "missing"))
        }
    }

    @Test fun profilesUseTheCurrentIndexAndReverseTheUuidBytes() {
        val folder = Folder()
        val profiles = ByteArray(0x650)
        val uuid = "51f78681a52439047d5dc1fb74d9260d".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        uuid.copyInto(profiles, 0x10)
        ByteArray(16) { (it + 1).toByte() }.copyInto(profiles, 0x10 + 0xC8)
        folder.files[EdenPlayer.PROFILES] = profiles
        assertEquals("0D26D974FBC15D7D043924A58186F751", edenUser(folder))
        folder.files["config/config.ini"] = "[Other]\ncurrent_user=7\n[System]\ncurrent_user\\default=true\ncurrent_user=1\n".toByteArray()
        assertEquals("100F0E0D0C0B0A090807060504030201", edenUser(folder))
        folder.files["config/config.ini"] = "[System]\ncurrent_user\\default=7\n".toByteArray()
        assertEquals("0D26D974FBC15D7D043924A58186F751", edenUser(folder))
    }

    @Test fun zeroShortOddAndOutOfRangeProfilesDoNotInventAUser() {
        val folder = Folder()
        for (size in listOf(0, 16, 0xD8, 0x64F, 0x651, 0x650)) {
            folder.files[EdenPlayer.PROFILES] = ByteArray(size)
            assertNull(edenUser(folder))
        }
        folder.files[EdenPlayer.PROFILES] = ByteArray(0x650) { 1 }
        for (index in listOf("-1", "8", "garbage")) {
            folder.files["config/config.ini"] = "[System]\ncurrent_user=$index".toByteArray()
            assertNull(edenUser(folder))
        }
    }

    @Test fun fallbackRequiresExactlyOneNonzeroUserDirectory() {
        val folder = Folder()
        val user = SaveFolderEntry("0D26D974FBC15D7D043924A58186F751", true, 0)
        folder.users = listOf(user, SaveFolderEntry("cache", true, 0), SaveFolderEntry("0".repeat(32), true, 0), SaveFolderEntry("a".repeat(32), false, 0))
        assertEquals(user.name, edenUser(folder))
        folder.users = folder.users!! + SaveFolderEntry("1".repeat(32), true, 0)
        assertNull(edenUser(folder))
        folder.users = emptyList()
        assertNull(edenUser(folder))
    }

    @Test fun searchKeepsUserOutsideTheZipAndDoesNotRequireTheTitleFolder() {
        val folder = Folder().apply { files[EdenPlayer.PROFILES] = ByteArray(0x650).apply { this[0x10] = 1 } }
        val reader = object : GameFileReader {
            override val fileName = "Fantasy Life [0100755017EE0800].nsp"
            override fun read(offset: Long, length: Int): ByteArray? = error("Tag needs no file read")
        }
        val player = EdenPlayer()
        for (launch in listOf(false, true)) assertEquals(
            SaveFolderSearch("${EdenPlayer.SAVES}/${"0".repeat(30)}01", "0100755017EE0000"),
            player.findSaveFolders("savedata", reader, folder, launch),
        )
        assertNull(player.findSaveFolders("bcat", reader, folder, true))
        folder.files.clear()
        assertNull(player.findSaveFolders("savedata", reader, folder, true))
        assertNull(player.setupSteps(null))
    }
}
