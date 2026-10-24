package app.foldcade.plugins.retroarch

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
import org.junit.Assert.fail
import org.junit.Test

class RetroArchPlayerTest {
    private val entry = RetroArchEntry()

    private fun player(platformId: String) = entry.players.single { it.platformId == platformId } as RetroArchPlayer

    private fun game(platformId: String) = Game(
        backendId = "local-folder",
        remoteKey = "content://games/title",
        platformId = platformId,
        availability = Availability.LocalOnly,
        label = "Title",
    )

    private fun launch(platformId: String, target: LaunchTarget, packageName: String = AARCH64) =
        player(platformId).launchIntent(LaunchRequest(game(platformId), target, packageName))

    private val shared = LaunchTarget.SharedFile(
        path = "/storage/emulated/0/ROMs/gba/Puzzle (USA).gba",
        uri = "content://com.android.externalstorage.documents/document/primary%3AROMs%2Fgba%2FPuzzle%20(USA).gba",
    )

    @Test
    fun serviceFileLoadsTheEntryWithoutPlatforms() {
        val loaded = ServiceLoader.load(PluginEntry::class.java, RetroArchPlayerTest::class.java.classLoader)
            .filterIsInstance<RetroArchEntry>()
            .single()
        assertTrue(loaded.platforms.isEmpty())
        assertEquals(entry.players.map { it.id }, loaded.players.map { it.id })
    }

    @Test
    fun onePlayerPerSystemWithCores() {
        val ids = entry.players.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(SYSTEMS.map { it.platformId }.distinct(), SYSTEMS.map { it.platformId })
        assertTrue("retroarch.game-boy-advance" in ids)
        for (system in SYSTEMS) {
            assertTrue(system.platformId, system.cores.isNotEmpty())
            assertEquals(system.platformId, system.cores.map { it.file }.distinct(), system.cores.map { it.file })
            for (core in system.cores) {
                assertTrue(core.file, core.file.matches(Regex("[a-z0-9_]+")))
                assertFalse(core.file, core.libraryName.isBlank() || '/' in core.libraryName)
            }
        }
    }

    @Test
    fun playersOpenGamesByPathOnOneScreen() {
        for (player in entry.players) {
            assertEquals(PACKAGES, player.packageNames)
            assertEquals("RetroArch", player.displayName)
            assertTrue(player.needsLocalFile)
            assertTrue(player.opensByPath)
            assertFalse(player.occupiesBothDisplays)
            assertFalse(player.requiresImportedGame)
            assertEquals(StartDisplay.PickerChoice, player.startDisplay)
        }
    }

    @Test
    fun onlyTheDsKeepsStandaloneFirst() {
        assertEquals(listOf("nintendo-ds", "playstation"), SYSTEMS.filterNot { it.aheadOfStandalone }.map { it.platformId })
    }

    @Test
    fun launchPassesThePathTheCoreAndRetroArchsFolders() {
        entry.cores.update(mapOf(AARCH64 to setOf("mgba_libretro_android.so")))
        val intent = launch("game-boy-advance", shared)
        assertEquals(AARCH64, intent.packageName)
        assertEquals(RETRO_ACTIVITY, intent.componentClass)
        assertEquals(ACTION_MAIN, intent.action)
        assertNull(intent.dataUri)
        assertFalse(intent.grantReadUri)
        assertEquals(setOf(LaunchFlag.NewTask, LaunchFlag.ReplaceRunning), intent.flags)
        assertEquals(
            listOf(
                PlayerExtra.Text("ROM", shared.path),
                PlayerExtra.Text("LIBRETRO", "/data/user/0/com.retroarch.aarch64/cores/mgba_libretro_android.so"),
                PlayerExtra.Text(
                    "CONFIGFILE",
                    "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg",
                ),
                PlayerExtra.Text("DATADIR", "/data/user/0/com.retroarch.aarch64"),
                PlayerExtra.Text("SDCARD", "/storage/emulated/0"),
                PlayerExtra.Text("EXTERNAL", "/storage/emulated/0/Android/data/com.retroarch.aarch64/files"),
            ),
            intent.extras,
        )
    }

    @Test
    fun theFolderExtrasFollowTheResolvedPackage() {
        entry.cores.update(mapOf("com.retroarch" to null))
        val extras = launch("snes", shared, packageName = "com.retroarch").extras
            .filterIsInstance<PlayerExtra.Text>()
            .associate { it.key to it.value }
        assertEquals("/data/user/0/com.retroarch/cores/snes9x_libretro_android.so", extras["LIBRETRO"])
        assertEquals("/data/user/0/com.retroarch", extras["DATADIR"])
    }

    @Test
    fun aContentUriOrAnotherAppIsRefused() {
        for ((target, packageName) in listOf(
            LaunchTarget.ContentUri("content://games/title.gba") to AARCH64,
            LaunchTarget.AppRef(mapOf("app_id" to "1")) to AARCH64,
            LaunchTarget.SharedFile("relative/title.gba", "content://x") to AARCH64,
            shared to "org.example.other",
        )) {
            try {
                launch("game-boy-advance", target, packageName)
                fail("$target through $packageName should not launch")
            } catch (_: PluginException.NotFound) {
                Unit
            }
        }
    }

    @Test
    fun theFirstInstalledCoreWins() {
        val gba = player("game-boy-advance")
        entry.cores.update(mapOf(AARCH64 to setOf("gpsp_libretro_android.so", "vbam_libretro_android.so")))
        assertEquals(CoreChoice.Ready(Core("vbam", "VBA-M")), gba.coreChoice(AARCH64))
        assertTrue(gba.ready(AARCH64))
    }

    @Test
    fun coresAreMissingOnlyWhenTheShellCanSeeThem() {
        val gba = player("game-boy-advance")
        entry.cores.update(mapOf(AARCH64 to setOf("snes9x_libretro_android.so")))
        assertEquals(CoreChoice.Missing(Core("mgba", "mGBA")), gba.coreChoice(AARCH64))
        assertFalse(gba.ready(AARCH64))
        entry.cores.update(mapOf(AARCH64 to null))
        assertEquals(CoreChoice.Unknown(Core("mgba", "mGBA")), gba.coreChoice(AARCH64))
        assertFalse(gba.ready(AARCH64))
        // An unknown package is never a RetroArch install.
        entry.cores.update(mapOf("org.example.other" to setOf("mgba_libretro_android.so")))
        assertNull(entry.cores.activePackage())
    }

    @Test
    fun theActivePackageIsTheFirstInstalledOne() {
        entry.cores.update(mapOf("com.retroarch.ra32" to null, "com.retroarch" to emptySet()))
        assertEquals("com.retroarch", entry.cores.activePackage())
        entry.cores.update(mapOf("com.retroarch.ra32" to null, AARCH64 to null))
        assertEquals(AARCH64, entry.cores.activePackage())
    }

    @Test
    fun savesAreSortedByTheCoreNameAndLoseOnlyTheLastExtension() {
        val genesis = player("genesis")
        entry.cores.update(mapOf(AARCH64 to setOf("picodrive_libretro_android.so")))
        assertEquals("PicoDrive/Sonic.Hack (v1.1).srm", genesis.saveFileName("srm", "Sonic.Hack (v1.1).md"))
        assertEquals("PicoDrive/Game.srm", genesis.saveFileName("srm", "Game.zip"))
        assertEquals("PicoDrive/NoExtension.srm", genesis.saveFileName("srm", "NoExtension"))
        assertNull(genesis.saveFileName("sav", "Game.md"))
        assertNull(genesis.saveFileName("srm", " "))
        // Unknown cores save under the core a launch would try.
        entry.cores.update(mapOf(AARCH64 to null))
        assertEquals("Genesis Plus GX/Game.srm", genesis.saveFileName("srm", "Game.md"))
    }

    @Test
    fun everySystemSharesOneSaveFolder() {
        val players = entry.players.map { it as RetroArchPlayer }
        assertTrue(players.none { it.hasSaveFolder() })
        assertEquals(setOf("retroarch"), players.map { it.saveFolderKey }.toSet())
        assertEquals(setOf(DEFAULT_SAVE_FOLDER), players.map { it.suggestedSaveFolder }.toSet())
        players.first().bindSaveFolder("content://tree/saves")
        assertTrue(players.all { it.hasSaveFolder() })
        assertEquals(
            listOf(SaveDeclaration("srm", "content://tree/saves")),
            players.last().saveDeclarations(game(players.last().platformId)),
        )
        players.last().bindSaveFolder("/storage/emulated/0/RetroArch/saves")
        assertTrue(players.none { it.hasSaveFolder() })
    }

    @Test
    fun aFreshStartNamesTheSameCoreAnotherWay() {
        entry.cores.update(mapOf(AARCH64 to setOf("mgba_libretro_android.so")))
        val usual = launch("game-boy-advance", shared)
        val fresh = player("game-boy-advance").freshStart(usual)
        val core = { intent: app.foldcade.api.plugin.PlayerIntent ->
            intent.extras.filterIsInstance<PlayerExtra.Text>().single { it.key == "LIBRETRO" }.value
        }
        assertEquals("/data/data/com.retroarch.aarch64/cores/mgba_libretro_android.so", core(fresh))
        assertEquals(usual.extras.filterNot { (it as PlayerExtra.Text).key == "LIBRETRO" }, fresh.extras.filterNot { (it as PlayerExtra.Text).key == "LIBRETRO" })
        assertTrue(fresh != usual)
        assertEquals(usual, player("game-boy-advance").freshStart(fresh))
    }

    private companion object {
        const val AARCH64 = "com.retroarch.aarch64"
    }
}
