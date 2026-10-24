package app.foldcade.plugins.duckstation

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.StartDisplay
import java.util.ServiceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckStationPlayerTest {
    private val player = DuckStationPlayer()

    private val game = Game(
        backendId = "romm",
        remoteKey = "36",
        platformId = "playstation",
        availability = Availability.Cached,
        label = "Castlevania",
    )

    private fun launch(uri: String, packageName: String = DuckStationPlayer.PACKAGE) = player.launchIntent(
        LaunchRequest(game = game, target = LaunchTarget.ContentUri(uri), resolvedPackage = packageName),
    )

    @Test
    fun serviceFileLoadsTheEntry() {
        val loaded = ServiceLoader.load(PluginEntry::class.java, DuckStationPlayerTest::class.java.classLoader)
            .filterIsInstance<DuckStationEntry>()
            .single()
        assertEquals(listOf("duckstation"), loaded.players.map { it.id })
        assertTrue(loaded.platforms.isEmpty())
        val player = loaded.players.single()
        assertEquals("playstation", player.platformId)
        assertEquals(listOf("com.github.stenzek.duckstation"), player.packageNames)
        assertEquals(StartDisplay.PickerChoice, player.startDisplay)
        assertFalse(player.occupiesBothDisplays)
    }

    @Test
    fun cardsAreInTheFolderFoldcadeHostsOnceDuckStationHasIt() {
        assertEquals(listOf(SaveDeclaration("card1", null)), player.saveDeclarations(game))
        assertFalse(player.hasSaveFolder())
        player.bindSaveFolder("file:///sdcard/Android/data/com.github.stenzek.duckstation/files")
        assertFalse(player.hasSaveFolder())
        player.bindSaveFolder("content://com.android.externalstorage.documents/tree/x")
        assertTrue(player.hasSaveFolder())
        // The folder picked is for the BIOS: DuckStation's cards there are private to it.
        assertEquals(listOf(SaveDeclaration("card1", null)), player.saveDeclarations(game))
        val hosted = "content://app.foldcade.saves/tree/duckstation"
        player.bindHostedFolder(hosted, inUse = false)
        assertFalse(player.hostedFolderInUse())
        assertEquals(listOf(SaveDeclaration("card1", null)), player.saveDeclarations(game))
        player.bindHostedFolder(hosted, inUse = true)
        assertTrue(player.hostedFolderInUse())
        assertEquals(listOf(SaveDeclaration("card1", hosted)), player.saveDeclarations(game))
        assertEquals("DuckStation memory cards", player.hostedFolderTitle)
        // The picker opens at DuckStation's own data folder, which holds both.
        assertEquals(
            "content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata%2Fcom.github.stenzek.duckstation%2Ffiles",
            player.suggestedSaveFolder,
        )
        assertEquals(setOf("memcards", "bios"), player.saveFolderMarkers)
    }

    @Test
    fun aGamesCardIsNamedAsDuckStationNamesItsCardPerGame() {
        assertEquals(
            "Castlevania - Symphony of the Night (USA)_1.mcd",
            player.saveFileName("card1", "Castlevania - Symphony of the Night (USA).cue"),
        )
        // A disc set's playlist carries the set's name.
        assertEquals("Final Fantasy VII (USA)_1.mcd", player.saveFileName("card1", "Final Fantasy VII (USA).m3u"))
        assertEquals("Game.v2_1.mcd", player.saveFileName("card1", "Game.v2.chd"))
        assertEquals("Game_1.mcd", player.saveFileName("card1", "Game"))
        // Path::SanitizeFileName on Android: control characters and `*` become `_`.
        assertEquals("Star_Ocean__1.mcd", player.saveFileName("card1", "Star*Ocean\u0001.cue"))
        assertNull(player.saveFileName("card1", ".cue"))
        assertNull(player.saveFileName("card1", ""))
        assertNull(player.saveFileName("sav", "Game.cue"))
    }

    @Test
    fun anyFirmwareGoesInTheBiosFolderUnderItsOwnName() {
        assertEquals("bios/SCPH1001.BIN", player.firmwareFileName("SCPH1001.BIN"))
        assertEquals("bios/scph5501.bin", player.firmwareFileName("scph5501.bin"))
        for (name in listOf("", " ", ".", "..", "psx/SCPH1001.BIN", "..\\SCPH1001.BIN", "bios\u0000")) {
            assertNull(name, player.firmwareFileName(name))
        }
    }

    @Test
    fun theGameGoesAsTheDocumentItsFileLayerNames() {
        // A RomM download, in its folder: the provider's ids are paths with no volume.
        val romm = "content://app.foldcade.romm.folders/tree/romm-media%2Froms%2F36%2Ff39" +
            "/document/romm-media%2Froms%2F36%2Ff39%2FCastlevania%20%28USA%29.cue"
        val intent = launch(romm)
        assertEquals("com.github.stenzek.duckstation", intent.packageName)
        assertEquals("com.github.stenzek.duckstation.EmulationActivity", intent.componentClass)
        assertEquals("android.intent.action.MAIN", intent.action)
        assertEquals(romm, intent.dataUri)
        assertTrue(intent.grantReadUri)
        assertEquals(
            listOf(
                PlayerExtra.Text(
                    "bootPath",
                    "/android_content/app.foldcade.romm.folders/tree/romm-media/roms/36/f39" +
                        "/document/romm-media/roms/36/f39/Castlevania (USA).cue",
                ),
                PlayerExtra.BooleanFlag("resumeState", false),
            ),
            intent.extras,
        )
        assertEquals(setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask), intent.flags)

        // A local folder: Android's external storage ids are `volume:path`.
        val local = "content://com.android.externalstorage.documents/tree/primary%3AROMs" +
            "/document/primary%3AROMs%2Fps1%2FGame.chd"
        assertEquals(
            "/android_content/com.android.externalstorage.documents/tree@primary/ROMs/document@primary/ROMs/ps1/Game.chd",
            duckStationPath(local),
        )
        assertEquals(
            "/android_content/com.android.externalstorage.documents/tree@primary/document@primary/Game.chd",
            duckStationPath("content://com.android.externalstorage.documents/tree/primary%3A/document/primary%3AGame.chd"),
        )
        // A colon in a file's name is part of the name, not a volume.
        assertEquals(
            "/android_content/app.foldcade.romm.folders/tree/romm-media/roms/37/f40" +
                "/document/romm-media/roms/37/f40/Star Wars: Rebel Assault (USA).cue",
            duckStationPath(
                "content://app.foldcade.romm.folders/tree/romm-media%2Froms%2F37%2Ff40" +
                    "/document/romm-media%2Froms%2F37%2Ff40%2FStar%20Wars%3A%20Rebel%20Assault%20(USA).cue",
            ),
        )
        assertEquals(
            "/android_content/com.android.externalstorage.documents/tree@primary/ROMs/document@primary/ROMs/Game: Two.cue",
            duckStationPath(
                "content://com.android.externalstorage.documents/tree/primary%3AROMs/document/primary%3AROMs%2FGame%3A%20Two.cue",
            ),
        )
    }

    @Test
    fun aUriDuckStationCannotNameGoesAsItIs() {
        // Not a document in a tree.
        assertNull(duckStationPath("content://app.foldcade.romm.cache/romm/roms/1/Game.chd"))
        assertNull(duckStationPath("content://com.android.externalstorage.documents/document/primary%3AGame.chd"))
        assertNull(duckStationPath("file:///sdcard/Game.chd"))
        // Names that would read back as another document.
        assertNull(duckStationPath("content://a/tree/r/document/r%2F%2FGame.cue"))
        assertNull(duckStationPath("content://a/tree/r/document/r%2Fdocument%2FGame.cue"))
        assertNull(duckStationPath("content://a/tree/r/document/r%2Ftree%40x%2FGame.cue"))
        assertNull(duckStationPath("content://a/tree/r/document/r%2FGame%2"))

        val intent = launch("content://games/title.chd")
        assertEquals(PlayerExtra.Text("bootPath", "content://games/title.chd"), intent.extras.first())
    }

    @Test
    fun pathsAndOtherPackagesAreRefused() {
        for (uri in listOf("file:///sdcard/Game.chd", "/sdcard/Game.chd")) {
            assertTrue(uri, runCatching { launch(uri) }.exceptionOrNull() is PluginException.NotFound)
        }
        assertTrue(runCatching { launch("content://games/a.chd", "org.ppsspp.ppsspp") }.exceptionOrNull() is PluginException.NotFound)
        val appRef = runCatching {
            player.launchIntent(
                LaunchRequest(game, LaunchTarget.AppRef(mapOf("app_id" to "1")), DuckStationPlayer.PACKAGE),
            )
        }
        assertTrue(appRef.exceptionOrNull() is PluginException.NotFound)
    }
}
