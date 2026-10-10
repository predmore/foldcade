package app.foldcade.plugins.gamenative

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.ObservedSlot
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SyncOutcome
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameNativeLibraryTest {
    private val player = GameNativePlayer()

    @Test
    fun shortcutsAndAConfirmedListBecomeTheCatalog() = runBlocking {
        val library = GameNativeLibrary()
        val fromShortcut = catalogGameFromShortcut(
            ShortcutLaunch(
                action = GameNativePlayer.ACTION_LAUNCH_GAME,
                appIdExtra = 730,
                gameSourceExtra = "steam",
                viewAppId = null,
                viewGameSource = null,
                label = "Counter-Strike",
            ),
        )
        val fromView = catalogGameFromShortcut(
            ShortcutLaunch(
                action = "android.intent.action.VIEW",
                appIdExtra = -1,
                gameSourceExtra = null,
                viewAppId = "12",
                viewGameSource = "gog",
                label = "A GOG game",
            ),
        )
        val typed = CatalogGame(title = "Custom", appId = 4, gameSource = "custom_game")
        library.confirm(listOfNotNull(fromShortcut, fromView, typed))
        val page = library.listGames(PcPlatform.ID, GameQuery())
        assertEquals(listOf("Counter-Strike", "A GOG game", "Custom"), page.games.map { it.label })
        assertEquals(listOf("STEAM_730", "GOG_12", "CUSTOM_GAME_4"), page.games.map { it.remoteKey })
        assertEquals(Availability.LocalOnly, page.games.first().availability)
        assertNull(page.nextOffset)
        assertEquals(3, page.total)
        assertTrue(library.listGames("nintendo-3ds", GameQuery()).games.isEmpty())
    }

    @Test
    fun missingLaunchSourceDefaultsToSteamAndUnknownActionsAreDropped() {
        val steam = catalogGameFromShortcut(
            ShortcutLaunch(
                action = GameNativePlayer.ACTION_LAUNCH_GAME,
                appIdExtra = 9,
                gameSourceExtra = null,
                viewAppId = null,
                viewGameSource = null,
                label = "Nameless",
            ),
        )
        assertEquals("STEAM", steam?.gameSource)
        val unknown = catalogGameFromShortcut(
            ShortcutLaunch(
                action = GameNativePlayer.ACTION_LAUNCH_GAME,
                appIdExtra = 9,
                gameSourceExtra = "ITCH",
                viewAppId = null,
                viewGameSource = null,
                label = "Nameless",
            ),
        )
        assertEquals("STEAM", unknown?.gameSource)
        assertNull(
            catalogGameFromShortcut(
                ShortcutLaunch(
                    action = "android.intent.action.VIEW",
                    appIdExtra = 9,
                    gameSourceExtra = "STEAM",
                    viewAppId = "9",
                    viewGameSource = null,
                    label = "View",
                ),
            ),
        )
        assertNull(
            catalogGameFromShortcut(
                ShortcutLaunch(
                    action = "other",
                    appIdExtra = 9,
                    gameSourceExtra = "STEAM",
                    viewAppId = null,
                    viewGameSource = null,
                    label = "No",
                ),
            ),
        )
        assertNull(catalogGameFromShortcut(ShortcutLaunch("app.gamenative.LAUNCH_GAME", 0, "STEAM", null, null, "Zero")))
    }

    @Test
    fun aBlankShortcutLabelUsesTheSourceAndId() {
        val game = catalogGameFromShortcut(
            ShortcutLaunch(
                action = GameNativePlayer.ACTION_LAUNCH_GAME,
                appIdExtra = 3,
                gameSourceExtra = "EPIC",
                viewAppId = null,
                viewGameSource = null,
                label = "  ",
            ),
        )
        assertEquals("EPIC 3", game?.title)
    }

    @Test
    fun linesRoundTripAndDropABadSource() {
        val game = CatalogGame("A title", 15, "AMAZON")
        assertEquals(game, catalogGameFromLine(catalogLine(game)))
        assertNull(catalogGameFromLine("WINDOWS\t15\tA title"))
        assertNull(catalogGameFromLine("STEAM\t0\tA title"))
        assertNull(catalogGameFromLine("not a row"))
    }

    @Test
    fun ensureLocalReturnsAnAppRefAndDoesNotDownload() = runBlocking {
        val library = confirmed()
        val game = library.listGames(PcPlatform.ID, GameQuery()).games.single()
        val target = library.ensureLocal(game) as LaunchTarget.AppRef
        assertEquals(mapOf("app_id" to "730", "game_source" to "STEAM"), target.values)
        val placement = library.prepareLaunch(game, player)
        assertEquals(target, placement.target)
        assertTrue(placement.savesToPlace.isEmpty())
        assertEquals(SyncOutcome.Unchanged, placement.sync.outcome)
        assertEquals(GameNativeLibrary.PROGRESS_NOTE, placement.sync.note)
        assertTrue(library.saves(game).slots.isEmpty())
    }

    @Test
    fun reconcileIsANoOpAndIgnoresObservedSaves() = runBlocking {
        val library = confirmed()
        val game = library.listGames(PcPlatform.ID, GameQuery()).games.single()
        val result = library.reconcile(
            game,
            player,
            ObservedSaves(
                listOf(
                    ObservedSlot(
                        slot = "save",
                        contentHash = "aa",
                        playerId = player.id,
                        contentUri = "content://saves/aa",
                    ),
                ),
            ),
        )
        assertEquals(SyncOutcome.Unchanged, result.outcome)
        assertEquals(GameNativeLibrary.PROGRESS_NOTE, result.note)
        assertTrue(library.saves(game).slots.isEmpty())
    }

    @Test
    fun anUnknownGameIsNotFound() = runBlocking {
        val library = GameNativeLibrary()
        val missing = Game(
            backendId = GameNativeLibrary.ID,
            remoteKey = "STEAM_1",
            platformId = PcPlatform.ID,
            availability = Availability.LocalOnly,
            label = "Missing",
        )
        val failure = try {
            library.ensureLocal(missing)
            error("expected a plugin failure")
        } catch (caught: PluginException) {
            caught
        }
        assertTrue(failure is PluginException.NotFound)
    }

    @Test
    fun pagesStopAtTheEnd() = runBlocking {
        val library = GameNativeLibrary()
        library.confirm(
            listOf(
                CatalogGame("One", 1, "STEAM"),
                CatalogGame("Two", 2, "STEAM"),
                CatalogGame("Three", 3, "EPIC"),
            ),
        )
        val first = library.listGames(PcPlatform.ID, GameQuery(text = "", offset = 0, limit = 2))
        assertEquals(listOf("One", "Two"), first.games.map { it.label })
        assertEquals(2, first.nextOffset)
        val last = library.listGames(PcPlatform.ID, GameQuery(offset = 2, limit = 2))
        assertEquals(listOf("Three"), last.games.map { it.label })
        assertNull(last.nextOffset)
        val named = library.listGames(PcPlatform.ID, GameQuery(text = "thr"))
        assertEquals("Three", named.games.single().label)
    }

    @Test
    fun theLibraryIsNotAMetadataProvider() {
        assertFalse(MetadataProvider::class.java.isAssignableFrom(GameNativeLibrary::class.java))
    }

    @Test
    fun aCancelledListRethrows() = runBlocking {
        val library = GameNativeLibrary()
        val failure = AtomicReference<Throwable>()
        val job = launch {
            try {
                cancel()
                library.listGames(PcPlatform.ID, GameQuery())
            } catch (thrown: Throwable) {
                failure.set(thrown)
                throw thrown
            }
        }
        job.join()
        assertTrue(failure.get() is CancellationException)
        assertFalse(failure.get() is PluginException)
    }

    private fun confirmed(): GameNativeLibrary = GameNativeLibrary().also { library ->
        library.confirm(listOf(CatalogGame("Counter-Strike", 730, "STEAM")))
    }

    @Test
    fun onlyASteamKeyHasASteamAppId() {
        org.junit.Assert.assertEquals(1245620, steamAppId("STEAM_1245620"))
        org.junit.Assert.assertEquals(null, steamAppId("GOG_1245620"))
        org.junit.Assert.assertEquals(null, steamAppId("STEAM_x"))
        org.junit.Assert.assertEquals(null, steamAppId("STEAM"))
    }
}
