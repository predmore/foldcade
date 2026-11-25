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
        assertEquals(EDEN_PACKAGES.map { "Android/data/$it/files" }, player.saveFolderPlaces)
        assertEquals(setOf("config", "keys", "nand"), player.saveFolderMarkers)
        assertFalse(player.saveFolderPickedInPlayer)
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

    @Test fun readsEdenStorageSettingsAndDefaultProfileFlag() {
        val folder = Folder()
        folder.config("[Data Storage]\nsave_directory=\nnand_directory=\n[System]\ncurrent_user=0")
        assertEquals(EdenStorageConfig(null, null, 0), readEdenStorageConfig(folder))
        // Representative Android config emitted by Eden, with test folder names.
        folder.config("""
            [Data%20Storage]
            nand_directory\default=false
            nand_directory=/storage/emulated/0/Android/data/dev.eden.eden_emulator/files/nand
            save_directory\default=false
            save_directory="/storage/ABCD-1234/Eden #1"

            [System]
            current_user\default=false
            current_user=1

            [UI]
            Paths\gamedirs\size=0
            Paths\nand_directory\default=false
            Paths\nand_directory=/storage/emulated/0/Android/data/dev.eden.eden_emulator/files/nand
            Paths\save_directory\default=false
            Paths\save_directory="/storage/ABCD-1234/Eden #1"
        """.trimIndent())
        assertEquals(EdenStorageConfig("/storage/emulated/0/Android/data/dev.eden.eden_emulator/files/nand", "/storage/ABCD-1234/Eden #1", 1), readEdenStorageConfig(folder))
        folder.config("[System]\ncurrent_user\\default=true\ncurrent_user=1")
        assertEquals(0, readEdenStorageConfig(folder)?.currentUser)
    }

    @Test fun uiPathsMustAgreeWithAnyDataStoragePaths() {
        val folder = Folder()
        val data = "[Data%20Storage]\nnand_directory=/sdcard/NAND\nsave_directory=/sdcard/NAND"
        val ui = "[UI]\nPaths\\nand_directory=/sdcard/NAND\nPaths\\save_directory=\nPaths\\save_directory\\default=true"
        folder.config("$data\n$ui")
        assertEquals(EdenStorageConfig("/sdcard/NAND", null, 0), readEdenStorageConfig(folder))
        folder.config(ui)
        assertEquals(EdenStorageConfig("/sdcard/NAND", null, 0), readEdenStorageConfig(folder))
        folder.config("$data\n${ui.replace("/sdcard/NAND", "/sdcard/OtherNand")}")
        assertNull(readEdenStorageConfig(folder))
        folder.config("${data.replace("save_directory=/sdcard/NAND", "save_directory=/sdcard/OldSave")}\n$ui")
        assertNull(readEdenStorageConfig(folder))
        folder.config("$data\n${ui.replace("Paths\\save_directory=", "Paths\\save_directory=/sdcard/OtherSave")}")
        assertNull(readEdenStorageConfig(folder))
    }

    @Test fun staleNumericProfileIndexesAreClamped() {
        val folder = Folder()
        for ((configured, index) in listOf(-4 to 0, 22 to 7)) {
            folder.config("[System]\ncurrent_user\\default=false\ncurrent_user=$configured")
            assertEquals(index, readEdenStorageConfig(folder)?.currentUser)
        }
        folder.config("[System]\ncurrent_user\\default=false\ncurrent_user=broken")
        assertNull(readEdenStorageConfig(folder)?.currentUser)
    }

    @Test fun missingAndCorruptConfigsCannotEstablishASaveLocation() {
        val folder = Folder()
        assertNull(readEdenStorageConfig(folder))
        for (config in listOf("", "just text", "[System", "[Data Storage]\nsave_directory", "[Other]\ncurrent_user=0",
            "[Data Storage]\nsave_directory=/sdcard/A\nsave_directory=/sdcard/B")) {
            folder.config(config)
            assertNull(readEdenStorageConfig(folder))
        }
        folder.files["config/config.ini"] = byteArrayOf(0xC0.toByte())
        assertNull(readEdenStorageConfig(folder))
    }

    @Test fun profileRecordsUseTheSelectedIndexAndSkipUnusedEntries() {
        val folder = Folder()
        assertEquals(EdenProfile.Missing, readEdenProfile(folder, "nand", 0))
        val profiles = ByteArray(0x650)
        val first = "51f78681a52439047d5dc1fb74d9260d".hexToByteArray()
        val second = "0102030405060708090a0b0c0d0e0f10".hexToByteArray()
        first.copyInto(profiles, 0x10)
        second.copyInto(profiles, 0x10 + 2 * 0xC8)
        folder.files["nand/$PROFILES"] = profiles
        assertEquals(EdenProfile.User(first.toHexString()), readEdenProfile(folder, "nand", 0))
        assertEquals(EdenProfile.User(second.toHexString()), readEdenProfile(folder, "nand", 1))
        assertEquals(EdenProfile.User(first.toHexString()), readEdenProfile(folder, "nand", 2))
        assertEquals(EdenProfile.Unreadable, readEdenProfile(folder, "nand", null))
        first.copyInto(profiles, 0x10 + 0xC8)
        assertEquals(EdenProfile.User(second.toHexString()), readEdenProfile(folder, "nand", 2))
        for (size in listOf(0, 16, 0xD8, 0x64F, 0x651, 0x650)) {
            folder.files["nand/$PROFILES"] = ByteArray(size)
            assertEquals(EdenProfile.Unreadable, readEdenProfile(folder, "nand", 0))
        }
    }

    @Test fun layoutPrefersTheNewAccountDirectoryOnlyWhenItExists() {
        val folder = Folder()
        val reader = object : GameFileReader {
            override val fileName = "Fantasy Life [0100755017EE0800].nsp"
            override fun read(offset: Long, length: Int): ByteArray? = error("Tag needs no file read")
        }
        val profile = EdenProfile.User("51f78681a52439047d5dc1fb74d9260d")
        val legacy = "nand/user/save/0000000000000000/0D26D974FBC15D7D043924A58186F751/$TITLE"
        val modern = "nand/user/save/account/${profile.uuid}/$TITLE/0"
        assertEquals(SaveFolderPart(TITLE, legacy), edenAccountSave(reader, folder, folder, "nand", profile))
        folder.directories.add(modern.substringBeforeLast('/'))
        assertEquals(SaveFolderPart(TITLE, legacy), edenAccountSave(reader, folder, folder, "nand", profile))
        folder.directories.add(modern)
        assertEquals(SaveFolderPart(TITLE, modern), edenAccountSave(reader, folder, folder, "nand", profile))
        folder.directories.add(legacy)
        assertEquals(SaveFolderPart(TITLE, modern), edenAccountSave(reader, folder, folder, "nand", profile))
    }

    private fun Folder.config(text: String) {
        files["config/config.ini"] = text.toByteArray()
    }

    private companion object {
        const val TITLE = "0100755017EE0000"
        const val PROFILES = "system/save/8000000000000010/su/avators/profiles.dat"
    }
}
