package app.foldcade.plugins.melonds

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

class MelonDsPlayerTest {
    private val game = Game(
        backendId = "local-folder",
        remoteKey = "content://games/title",
        platformId = MelonDsPlayer.PLATFORM_ID,
        availability = Availability.LocalOnly,
        label = "Title.nds",
    )

    @Test
    fun serviceFileLoadsTheEntry() {
        val entry = ServiceLoader.load(
            app.foldcade.api.plugin.PluginEntry::class.java,
            MelonDsPlayerTest::class.java.classLoader,
        ).filterIsInstance<MelonDsEntry>().single()
        assertTrue(entry.platforms.isEmpty())
        assertEquals(MelonDsPlayer.ID, entry.players.single().id)
    }

    @Test
    fun bothPackagesTakeBothPanels() {
        val player = MelonDsPlayer()
        assertEquals(
            listOf("me.magnum.melonds", "me.magnum.melonds.nightly"),
            player.packageNames,
        )
        assertEquals("melonds", player.id)
        assertTrue(player.occupiesBothDisplays)
        assertEquals(StartDisplay.Primary, player.startDisplay)
        assertTrue(player.needsLocalFile)
        assertFalse(player.requiresImportedGame)
        assertEquals("nintendo-ds", player.platformId)
    }

    @Test
    fun contentUriLaunchGrantsReadAndDoesNotPassDeprecatedExtras() {
        val intent = MelonDsPlayer().launchIntent(
            LaunchRequest(
                game = game,
                target = LaunchTarget.ContentUri("content://games/title.nds"),
                resolvedPackage = MelonDsPlayer.STABLE_PACKAGE,
            ),
        )
        assertEquals(MelonDsPlayer.STABLE_PACKAGE, intent.packageName)
        assertEquals(EMULATOR_ACTIVITY, intent.componentClass)
        assertEquals(MelonDsPlayer.ACTION_VIEW, intent.action)
        assertEquals("content://games/title.nds", intent.dataUri)
        assertTrue(intent.grantReadUri)
        assertNull(intent.mimeType)
        assertTrue(intent.extras.isEmpty())
        assertEquals(setOf(LaunchFlag.NewTask), intent.flags)
        assertTrue(intent.extras.none { it.key == "uri" || it.key == "PATH" })
    }

    @Test
    fun nightlyUsesTheSameActivity() {
        val intent = MelonDsPlayer().launchIntent(
            LaunchRequest(
                game = game,
                target = LaunchTarget.ContentUri("content://games/title.dsi"),
                resolvedPackage = MelonDsPlayer.NIGHTLY_PACKAGE,
            ),
        )
        assertEquals(MelonDsPlayer.NIGHTLY_PACKAGE, intent.packageName)
        assertEquals(EMULATOR_ACTIVITY, intent.componentClass)
    }

    @Test
    fun pathsAndOtherPackagesAreRefused() {
        val player = MelonDsPlayer()
        listOf(
            "file:///storage/emulated/0/title.nds",
            "/storage/emulated/0/title.nds",
            "content://games/title.nds",
        ).forEach { uri ->
            val refused = runCatching {
                player.launchIntent(
                    LaunchRequest(
                        game = game,
                        target = LaunchTarget.ContentUri(uri),
                        resolvedPackage = if (uri.startsWith("content:")) "app.foldcade.sample" else player.packageNames.first(),
                    ),
                )
            }
            assertTrue(refused.exceptionOrNull() is PluginException.NotFound)
        }
        val appRef = runCatching {
            player.launchIntent(
                LaunchRequest(
                    game = game,
                    target = LaunchTarget.AppRef(mapOf("app_id" to "1")),
                    resolvedPackage = player.packageNames.first(),
                ),
            )
        }
        assertTrue(appRef.exceptionOrNull() is PluginException.NotFound)
    }

    @Test
    fun saveFolderAcceptsOnlyAContentTree() {
        val player = MelonDsPlayer()
        assertFalse(player.hasSaveFolder())
        assertNull(player.saveDeclarations(game).single().locationUri)
        assertEquals(MelonDsPlayer.SAVE_SLOT, player.saveDeclarations(game).single().slot)

        player.bindSaveFolder("/storage/emulated/0/melonds")
        assertFalse(player.hasSaveFolder())
        player.bindSaveFolder("file:///storage/emulated/0/melonds")
        assertFalse(player.hasSaveFolder())

        val tree = "content://com.android.externalstorage.documents/tree/primary%3ASaves"
        player.bindSaveFolder(tree)
        assertTrue(player.hasSaveFolder())
        assertEquals(tree, player.saveDeclarations(game).single().locationUri)
    }

    @Test
    fun theSaveIsTheGameFileNameWithASavExtension() {
        val player = MelonDsPlayer()
        assertEquals("Mario Kart DS (USA).sav", player.saveFileName(MelonDsPlayer.SAVE_SLOT, "Mario Kart DS (USA).nds"))
        assertEquals("game.v2.sav", player.saveFileName(MelonDsPlayer.SAVE_SLOT, "game.v2.nds"))
        assertEquals("noext.sav", player.saveFileName(MelonDsPlayer.SAVE_SLOT, "noext"))
        assertNull(player.saveFileName("other", "game.nds"))
        assertNull(player.saveFileName(MelonDsPlayer.SAVE_SLOT, " "))
    }
}
