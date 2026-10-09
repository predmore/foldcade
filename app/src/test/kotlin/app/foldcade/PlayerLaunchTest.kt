package app.foldcade

import app.foldcade.api.ExternalApp
import app.foldcade.api.Session
import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.language.Copy
import app.foldcade.plugins.azahar.AzaharPlayer
import app.foldcade.plugins.azahar.Nintendo3ds
import app.foldcade.plugins.gamenative.GameNativeLibrary
import app.foldcade.plugins.gamenative.GameNativePlayer
import app.foldcade.plugins.gamenative.MAIN_ACTIVITY
import app.foldcade.plugins.melonds.EMULATOR_ACTIVITY
import app.foldcade.plugins.melonds.MelonDsPlayer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLaunchTest {
    private val game = Game(
        backendId = "shelf",
        remoteKey = "nintendo-3ds.azahar",
        platformId = Nintendo3ds.ID,
        availability = Availability.LocalOnly,
        label = "3DS",
    )
    private val uri = LaunchTarget.ContentUri("content://games/title.cci")

    @Test
    fun missingInstallIsReportedBeforeTheFile() {
        val decision = planPlayerLaunch(
            player = AzaharPlayer(),
            game = game,
            target = null,
            installedPackages = emptySet(),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
        )
        val blocked = decision as PlayerLaunch.Blocked
        assertEquals(LaunchBlock.MissingPlayer, blocked.block)
        assertEquals("Azahar", blocked.playerName)
    }

    @Test
    fun installedPlayerWithoutAFileDoesNotAskForASaveFolder() {
        val decision = planPlayerLaunch(
            player = AzaharPlayer(),
            game = game,
            target = null,
            installedPackages = setOf(AzaharPlayer.PACKAGES.first()),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
        )
        assertEquals(LaunchBlock.NoLocalFile, (decision as PlayerLaunch.Blocked).block)
    }

    @Test
    fun saveFolderComesAfterAContentUri() {
        val player = AzaharPlayer()
        val ask = planPlayerLaunch(
            player = player,
            game = game,
            target = uri,
            installedPackages = setOf("io.github.lime3ds.android"),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
        )
        assertEquals(LaunchBlock.SaveFolder, (ask as PlayerLaunch.Blocked).block)

        player.bindSaveFolder("content://trees/saves")
        val ready = planPlayerLaunch(
            player = player,
            game = game,
            target = uri,
            installedPackages = setOf(AzaharPlayer.PACKAGES.first(), "io.github.lime3ds.android"),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
        ) as PlayerLaunch.Ready
        assertEquals("org.azahar_emu.azahar", ready.intent.packageName)
        assertEquals("content://games/title.cci", ready.intent.dataUri)
        assertTrue(ready.intent.grantReadUri)
        assertTrue(ready.occupiesBothDisplays)
        assertEquals(StartDisplay.Primary, ready.startDisplay)
        assertTrue(LaunchFlag.ClearTop in ready.intent.flags)
        assertEquals("content://trees/saves", player.saveDeclarations(game).single().locationUri)
    }

    @Test
    fun aSkippedSaveFolderStillLaunches() {
        val player = AzaharPlayer()
        val ready = planPlayerLaunch(
            player = player,
            game = game,
            target = uri,
            installedPackages = setOf(AzaharPlayer.PACKAGES.first()),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
            saveFolderSettled = true,
        ) as PlayerLaunch.Ready
        assertFalse(player.hasSaveFolder())
        assertNull(player.saveDeclarations(game).single().locationUri)
        assertEquals("content://games/title.cci", ready.intent.dataUri)
    }

    @Test
    fun anUnresolvedComponentIsTheMissingPlayerState() {
        val player = AzaharPlayer()
        val decision = planPlayerLaunch(
            player = player,
            game = game,
            target = uri,
            installedPackages = setOf("io.github.lime3ds.android"),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
            saveFolderSettled = true,
            componentResolves = { false },
        )
        val blocked = decision as PlayerLaunch.Blocked
        assertEquals(LaunchBlock.MissingPlayer, blocked.block)
        assertEquals("Azahar", blocked.playerName)
    }

    @Test
    fun aSecondBothPanelGameAsksToCloseTheFirst() {
        val session = Session().launch(ExternalApp("already", occupiesBothDisplays = true))
        assertTrue(anotherBothPanelRunning(session, "nintendo-3ds.azahar"))
        assertFalse(anotherBothPanelRunning(session, "already"))

        val player = AzaharPlayer().also { it.bindSaveFolder("content://trees/saves") }
        val ask = planPlayerLaunch(
            player = player,
            game = game,
            target = uri,
            installedPackages = setOf(AzaharPlayer.PACKAGES.first()),
            anotherBothPanelRunning = true,
            closeConfirmed = false,
        )
        assertEquals(LaunchBlock.CloseFirst, (ask as PlayerLaunch.Blocked).block)

        val ready = planPlayerLaunch(
            player = player,
            game = game,
            target = uri,
            installedPackages = setOf(AzaharPlayer.PACKAGES.first()),
            anotherBothPanelRunning = true,
            closeConfirmed = true,
        )
        assertTrue(ready is PlayerLaunch.Ready)
    }

    @Test
    fun platformDeclarationMatchesTheLocalFolderCatalog() {
        val folder = LocalFolderEntry().platforms.first { it.id == Nintendo3ds.ID }
        val player = AzaharPlayer()
        assertEquals(folder.id, player.platformId)
        assertEquals(folder.displayName, "Nintendo 3DS")
        assertEquals(folder.extensions, Nintendo3ds.extensions)
        assertEquals(folder.aliases, Nintendo3ds.aliases)
    }

    @Test
    fun manifestSeesBothPackagesAndDoesNotAskForAllFiles() {
        val xml = File("src/main/AndroidManifest.xml").readText()
        assertFalse(xml.contains("MANAGE_EXTERNAL_STORAGE"))
        AzaharPlayer.PACKAGES.forEach { name ->
            assertTrue(xml.contains("android:name=\"$name\""))
        }
    }

    @Test
    fun stableMelonDsPackageWinsAndKeepsTheMelonDsLaunch() {
        val ds = dsGame()
        val player = MelonDsPlayer().also { it.bindSaveFolder("content://trees/saves") }
        val ready = planPlayerLaunch(
            player = player,
            game = ds,
            target = LaunchTarget.ContentUri("content://games/title.nds"),
            installedPackages = setOf(MelonDsPlayer.NIGHTLY_PACKAGE, MelonDsPlayer.STABLE_PACKAGE),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
        ) as PlayerLaunch.Ready
        assertEquals(MelonDsPlayer.STABLE_PACKAGE, ready.intent.packageName)
        assertEquals(EMULATOR_ACTIVITY, ready.intent.componentClass)
        assertEquals("content://games/title.nds", ready.intent.dataUri)
        assertTrue(ready.intent.grantReadUri)
        assertTrue(ready.intent.extras.isEmpty())
        assertEquals(setOf(LaunchFlag.NewTask), ready.intent.flags)
        assertTrue(ready.occupiesBothDisplays)
        assertEquals(StartDisplay.Primary, ready.startDisplay)
    }

    @Test
    fun nightlyMelonDsIsUsedWhenItIsTheOnlyInstall() {
        val player = MelonDsPlayer().also { it.bindSaveFolder("content://trees/saves") }
        val ready = planPlayerLaunch(
            player = player,
            game = dsGame(),
            target = LaunchTarget.ContentUri("content://games/title.nds"),
            installedPackages = setOf(MelonDsPlayer.NIGHTLY_PACKAGE),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
        ) as PlayerLaunch.Ready
        assertEquals(MelonDsPlayer.NIGHTLY_PACKAGE, ready.intent.packageName)
        assertEquals(EMULATOR_ACTIVITY, ready.intent.componentClass)
    }

    @Test
    fun melonDsPlatformMatchesTheLocalFolderCatalog() {
        val folder = LocalFolderEntry().platforms.first { it.id == MelonDsPlayer.PLATFORM_ID }
        val player = MelonDsPlayer()
        assertEquals(folder.id, player.platformId)
        assertEquals("Nintendo DS", folder.displayName)
        assertTrue("nds" in folder.aliases)
        assertTrue("nds" in folder.extensions)
    }

    @Test
    fun gameNativeLaunchesOnThePickerScreenWithAnIntAppId() {
        val player = GameNativePlayer()
        val ready = planPlayerLaunch(
            player = player,
            game = pcGame(),
            target = LaunchTarget.AppRef(mapOf("app_id" to "730", "game_source" to "STEAM")),
            installedPackages = setOf(GameNativePlayer.VANILLA_PACKAGE, GameNativePlayer.GOLD_PACKAGE),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
        ) as PlayerLaunch.Ready
        assertFalse(ready.occupiesBothDisplays)
        assertEquals(StartDisplay.PickerChoice, ready.startDisplay)
        assertEquals(GameNativePlayer.VANILLA_PACKAGE, ready.intent.packageName)
        assertEquals(MAIN_ACTIVITY, ready.intent.componentClass)
        assertEquals(GameNativePlayer.ACTION_LAUNCH_GAME, ready.intent.action)
        val appId = ready.intent.extras.filterIsInstance<PlayerExtra.Integer>().single()
        assertEquals("app_id", appId.key)
        assertEquals(730, appId.value)
        assertEquals(
            setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop),
            ready.intent.flags,
        )
        assertEquals(Copy.progressInGameNative, GameNativeLibrary.PROGRESS_NOTE)
    }

    @Test
    fun gameNativeGoldIsUsedWhenItIsTheOnlyInstall() {
        val ready = planPlayerLaunch(
            player = GameNativePlayer(),
            game = pcGame(),
            target = LaunchTarget.AppRef(mapOf("app_id" to "4", "game_source" to "EPIC")),
            installedPackages = setOf(GameNativePlayer.GOLD_PACKAGE),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
        ) as PlayerLaunch.Ready
        assertEquals(GameNativePlayer.GOLD_PACKAGE, ready.intent.packageName)
        assertEquals(MAIN_ACTIVITY, ready.intent.componentClass)
    }

    @Test
    fun gameNativeMissingPackageIsTheMissingPlayerState() {
        val decision = planPlayerLaunch(
            player = GameNativePlayer(),
            game = pcGame(),
            target = LaunchTarget.AppRef(mapOf("app_id" to "1", "game_source" to "STEAM")),
            installedPackages = emptySet(),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
        )
        val blocked = decision as PlayerLaunch.Blocked
        assertEquals(LaunchBlock.MissingPlayer, blocked.block)
        assertEquals("GameNative", blocked.playerName)
    }

    @Test
    fun manifestSeesGameNativePackagesAndDoesNotAskForEveryPackage() {
        val xml = File("src/main/AndroidManifest.xml").readText()
        assertFalse(xml.contains("QUERY_ALL_PACKAGES"))
        assertFalse(xml.contains("MANAGE_EXTERNAL_STORAGE"))
        GameNativePlayer.PACKAGES.forEach { name ->
            assertTrue(xml.contains("android:name=\"$name\""))
        }
    }

    @Test
    fun manifestSeesMelonDsPackagesAndDoesNotAskForEveryPackage() {
        val xml = File("src/main/AndroidManifest.xml").readText()
        assertFalse(xml.contains("QUERY_ALL_PACKAGES"))
        assertFalse(xml.contains("MANAGE_EXTERNAL_STORAGE"))
        MelonDsPlayer.PACKAGES.forEach { name ->
            assertTrue(xml.contains("android:name=\"$name\""))
        }
    }

    private fun pcGame() = Game(
        backendId = GameNativeLibrary.ID,
        remoteKey = "STEAM_730",
        platformId = "pc",
        availability = Availability.LocalOnly,
        label = "Counter-Strike",
    )

    private fun dsGame() = Game(
        backendId = "shelf",
        remoteKey = "nintendo-ds.melonds",
        platformId = MelonDsPlayer.PLATFORM_ID,
        availability = Availability.LocalOnly,
        label = "DS",
    )
}
