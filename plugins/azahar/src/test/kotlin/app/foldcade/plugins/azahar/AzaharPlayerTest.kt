package app.foldcade.plugins.azahar

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.StartDisplay
import java.util.ServiceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AzaharPlayerTest {
    private val game = Game(
        backendId = "local-folder",
        remoteKey = "content://games/title",
        platformId = Nintendo3ds.ID,
        availability = Availability.LocalOnly,
        label = "Title.cci",
    )

    @Test
    fun serviceFileLoadsTheEntry() {
        val entry = ServiceLoader.load(
            app.foldcade.api.plugin.PluginEntry::class.java,
            AzaharPlayerTest::class.java.classLoader,
        ).filterIsInstance<AzaharEntry>().single()
        assertEquals(Nintendo3ds.ID, entry.platforms.single().id)
        assertEquals(AzaharPlayer.ID, entry.players.single().id)
    }

    @Test
    fun bothApplicationIdsAndBothPanels() {
        val player = AzaharPlayer()
        assertEquals(
            listOf("org.azahar_emu.azahar", "io.github.lime3ds.android"),
            player.packageNames,
        )
        assertTrue(player.occupiesBothDisplays)
        assertEquals(StartDisplay.Primary, player.startDisplay)
        assertTrue(player.needsLocalFile)
        assertFalse(player.requiresImportedGame)
        assertEquals(Nintendo3ds.ID, player.platformId)
    }

    @Test
    fun contentUriLaunchGrantsReadAndDoesNotPassAPath() {
        val intent = AzaharPlayer().launchIntent(
            LaunchRequest(
                game = game,
                target = LaunchTarget.ContentUri("content://games/title.cci"),
                resolvedPackage = "org.azahar_emu.azahar",
            ),
        )
        assertEquals(VANILLA_PACKAGE, intent.packageName)
        assertEquals(EMULATION_ACTIVITY, intent.componentClass)
        assertEquals(AzaharPlayer.ACTION_VIEW, intent.action)
        assertEquals("content://games/title.cci", intent.dataUri)
        assertTrue(intent.grantReadUri)
        assertTrue(intent.extras.isEmpty())
        assertEquals(AzaharPlayer.CONTENT_MIME, intent.mimeType)
        assertEquals(
            setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask),
            intent.flags,
        )
    }

    @Test
    fun playPackageIsAccepted() {
        val intent = AzaharPlayer().launchIntent(
            LaunchRequest(
                game = game,
                target = LaunchTarget.ContentUri("content://games/title.zcci"),
                resolvedPackage = "io.github.lime3ds.android",
            ),
        )
        assertEquals(PLAY_PACKAGE, intent.packageName)
        assertEquals(EMULATION_ACTIVITY, intent.componentClass)
        assertEquals(activityClass(VANILLA_PACKAGE), activityClass(PLAY_PACKAGE))
        assertNull(activityClass("io.github.lime3ds.android.fork"))
    }

    @Test
    fun filePathsAndOtherPackagesAreRefused() {
        val player = AzaharPlayer()
        val file = runCatching {
            player.launchIntent(
                LaunchRequest(
                    game = game,
                    target = LaunchTarget.ContentUri("file:///storage/emulated/0/title.cci"),
                    resolvedPackage = AzaharPlayer.PACKAGES.first(),
                ),
            )
        }
        assertTrue(file.exceptionOrNull() is PluginException.NotFound)

        val storage = runCatching {
            player.launchIntent(
                LaunchRequest(
                    game = game,
                    target = LaunchTarget.ContentUri("/storage/emulated/0/title.cci"),
                    resolvedPackage = AzaharPlayer.PACKAGES.first(),
                ),
            )
        }
        assertTrue(storage.exceptionOrNull() is PluginException.NotFound)

        val sdmc = runCatching {
            player.launchIntent(
                LaunchRequest(
                    game = game,
                    target = LaunchTarget.ContentUri("sdmc:/Nintendo 3DS/title.cci"),
                    resolvedPackage = AzaharPlayer.PACKAGES.first(),
                ),
            )
        }
        assertTrue(sdmc.exceptionOrNull() is PluginException.NotFound)

        val other = runCatching {
            player.launchIntent(
                LaunchRequest(
                    game = game,
                    target = LaunchTarget.ContentUri("content://games/title.cci"),
                    resolvedPackage = "app.foldcade.sample",
                ),
            )
        }
        assertTrue(other.exceptionOrNull() is PluginException.NotFound)

        val appRef = runCatching {
            player.launchIntent(
                LaunchRequest(
                    game = game,
                    target = LaunchTarget.AppRef(mapOf("app_id" to "1")),
                    resolvedPackage = AzaharPlayer.PACKAGES.first(),
                ),
            )
        }
        assertTrue(appRef.exceptionOrNull() is PluginException.NotFound)
    }

    @Test
    fun cciAndZcciArePreferredAnd3dsIsBestEffort() {
        assertEquals(ExtensionAcceptance.Preferred, Nintendo3ds.acceptance("cci"))
        assertEquals(ExtensionAcceptance.Preferred, Nintendo3ds.acceptance(".ZCCI"))
        assertEquals(ExtensionAcceptance.BestEffort, Nintendo3ds.acceptance("3ds"))
        assertEquals(ExtensionAcceptance.Rejected, Nintendo3ds.acceptance("cia"))
        assertEquals(ExtensionAcceptance.Rejected, Nintendo3ds.acceptance("zip"))
        assertEquals(setOf("3ds", "n3ds", "new-nintendo-3ds"), Nintendo3ds.aliases)
    }

    @Test
    fun saveProbeAsksUntilTheUserPicksAContentTree() {
        val player = AzaharPlayer()
        assertTrue(saveLocation(null).askForFolder)
        assertNull(saveLocation(null).locationUri)
        assertTrue(saveLocation("sdmc:/Nintendo 3DS").askForFolder)
        assertTrue(saveLocation("/storage/emulated/0/azahar").askForFolder)
        assertTrue(saveLocation("file:///storage/emulated/0/azahar").askForFolder)
        assertNull(player.saveDeclarations(game).single().locationUri)

        val tree = "content://com.android.externalstorage.documents/tree/primary%3ASaves"
        player.bindSaveFolder("sdmc:/Nintendo 3DS")
        assertFalse(player.hasSaveFolder())
        player.bindSaveFolder(tree)
        val declared = player.saveDeclarations(game).single()
        assertEquals(AzaharPlayer.TITLE_SLOT, declared.slot)
        assertEquals(tree, declared.locationUri)
        assertFalse(saveLocation(tree).askForFolder)
    }
}
