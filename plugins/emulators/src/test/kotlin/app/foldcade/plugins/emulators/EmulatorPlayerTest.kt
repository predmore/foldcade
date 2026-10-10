package app.foldcade.plugins.emulators

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.StartDisplay
import java.util.ServiceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorPlayerTest {
    private val entry = EmulatorsEntry()

    private fun player(id: String) = entry.players.single { it.id == id } as EmulatorPlayer

    private fun game(platformId: String) = Game(
        backendId = "local-folder",
        remoteKey = "content://games/title",
        platformId = platformId,
        availability = Availability.LocalOnly,
        label = "Title",
    )

    private fun launch(id: String, uri: String, packageName: String? = null) = player(id).let { player ->
        player.launchIntent(
            LaunchRequest(
                game = game(player.platformId),
                target = LaunchTarget.ContentUri(uri),
                resolvedPackage = packageName ?: player.packageNames.first(),
            ),
        )
    }

    @Test
    fun serviceFileLoadsTheEntryWithoutPlatforms() {
        val loaded = ServiceLoader.load(PluginEntry::class.java, EmulatorPlayerTest::class.java.classLoader)
            .filterIsInstance<EmulatorsEntry>()
            .single()
        assertTrue(loaded.platforms.isEmpty())
        assertEquals(entry.players.map { it.id }, loaded.players.map { it.id })
    }

    @Test
    fun oneUniquePlayerPerEmulatorAndPlatform() {
        val ids = entry.players.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(EMULATORS.sumOf { it.platformIds.size }, ids.size)
        assertEquals(EMULATORS.map { it.id }.distinct(), EMULATORS.map { it.id })
        assertTrue("dolphin.gamecube" in ids)
        assertTrue("dolphin.wii" in ids)
        assertTrue("pizza-boy-sc.game-gear" in ids)
    }

    @Test
    fun everyActivityIsAbsoluteAndEveryEmulatorHasAPackage() {
        for (emulator in EMULATORS) {
            assertTrue(emulator.id, emulator.activities.isNotEmpty())
            assertTrue(emulator.id, emulator.platformIds.isNotEmpty())
            emulator.activities.forEach { (packageName, activity) ->
                assertFalse(packageName, activity.startsWith("."))
                assertTrue(packageName, activity.contains('.'))
            }
            assertTrue(emulator.id, LaunchFlag.NewTask in emulator.flags)
        }
    }

    @Test
    fun playersTakeOneScreenAndKeepSavesInTheEmulator() {
        for (player in entry.players) {
            assertFalse(player.id, player.occupiesBothDisplays)
            assertEquals(player.id, StartDisplay.PickerChoice, player.startDisplay)
            assertTrue(player.id, player.needsLocalFile)
            assertFalse(player.id, player.requiresImportedGame)
            assertTrue(player.id, player.saveDeclarations(game(player.platformId)).isEmpty())
        }
    }

    @Test
    fun platformOrderFollowsTheCatalog() {
        fun order(platformId: String) = entry.players.filter { it.platformId == platformId }.map { it.displayName }
        assertEquals(listOf("My Boy!", "Pizza Boy GBA", "GBA.emu"), order(GBA))
        assertEquals(listOf("Dolphin", "Dolphin MMJR"), order(WII))
        assertEquals(listOf("NetherSX2", "ARMSX2"), order(PLAYSTATION_2))
        assertEquals(listOf("Flycast", "Redream"), order(DREAMCAST))
    }

    @Test
    fun dataHandoffPutsTheUriInDataWithARead() {
        val intent = launch("ppsspp.psp", "content://games/title.iso", "org.ppsspp.ppsspp")
        assertEquals("org.ppsspp.ppsspp", intent.packageName)
        assertEquals("org.ppsspp.ppsspp.PpssppActivity", intent.componentClass)
        assertEquals(ACTION_VIEW, intent.action)
        assertEquals("content://games/title.iso", intent.dataUri)
        assertTrue(intent.grantReadUri)
        assertTrue(intent.extras.isEmpty())
        assertEquals(setOf(LaunchFlag.NewTask), intent.flags)
    }

    @Test
    fun extraHandoffAlsoSetsDataSoTheGrantCoversIt() {
        val intent = launch("duckstation.playstation", "content://games/title.chd")
        assertEquals("com.github.stenzek.duckstation.EmulationActivity", intent.componentClass)
        assertEquals(ACTION_MAIN, intent.action)
        assertEquals("content://games/title.chd", intent.dataUri)
        assertTrue(intent.grantReadUri)
        assertEquals(
            listOf(
                PlayerExtra.Text("bootPath", "content://games/title.chd"),
                PlayerExtra.BooleanFlag("resumeState", false),
            ),
            intent.extras,
        )
        assertEquals(setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask), intent.flags)

        val dolphin = launch("dolphin.wii", "content://games/title.rvz")
        assertEquals("org.dolphinemu.dolphinemu.ui.main.TvMainActivity", dolphin.componentClass)
        assertEquals(listOf(PlayerExtra.Text("AutoStartFile", "content://games/title.rvz")), dolphin.extras)
    }

    @Test
    fun eachApplicationIdKeepsItsOwnActivity() {
        val pro = launch("pizza-boy-gba.game-boy-advance", "content://games/a.gba", "it.dbtecno.pizzaboygbapro")
        assertEquals("it.dbtecno.pizzaboygbapro.MainActivity", pro.componentClass)
        val free = launch("pizza-boy-gba.game-boy-advance", "content://games/a.gba", "it.dbtecno.pizzaboygba")
        assertEquals("it.dbtecno.pizzaboygba.MainActivity", free.componentClass)
        assertEquals(listOf(PlayerExtra.Text("rom_uri", "content://games/a.gba")), free.extras)
    }

    @Test
    fun pathsAppRefsAndOtherPackagesAreRefused() {
        val player = player("my-boy.game-boy-advance")
        listOf(
            "file:///storage/emulated/0/title.gba",
            "/storage/emulated/0/title.gba",
        ).forEach { uri ->
            val refused = runCatching { launch(player.id, uri) }
            assertTrue(uri, refused.exceptionOrNull() is PluginException.NotFound)
        }
        val other = runCatching { launch(player.id, "content://games/title.gba", "com.fastemulator.gbc") }
        assertTrue(other.exceptionOrNull() is PluginException.NotFound)
        val appRef = runCatching {
            player.launchIntent(
                LaunchRequest(
                    game = game(player.platformId),
                    target = LaunchTarget.AppRef(mapOf("app_id" to "1")),
                    resolvedPackage = player.packageNames.first(),
                ),
            )
        }
        assertTrue(appRef.exceptionOrNull() is PluginException.NotFound)
    }
}
