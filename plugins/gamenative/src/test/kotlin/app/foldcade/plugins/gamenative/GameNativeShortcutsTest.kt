package app.foldcade.plugins.gamenative

import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PlayerExtra
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GameNativeShortcutsTest {
    @Test fun pinsKeepTheirPublisherAndLaunchTheOriginalIntentWithoutGuessingStore() = runBlocking {
        val pin = gameNativeShortcut(GameNativePlayer.GOLD_PACKAGE, "game_42", "A GOG game", true, true)!!
        assertNull(steamAppId(pin.remoteKey))
        assertEquals(pin, catalogGameFromLine(catalogLine(pin)))
        val library = GameNativeLibrary().apply { confirm(listOf(pin)) }
        val game = library.listGames(PcPlatform.ID, GameQuery()).games.single()
        val target = library.ensureLocal(game) as LaunchTarget.AppRef
        val launch = GameNativePlayer().launchIntent(LaunchRequest(game, target, GameNativePlayer.VANILLA_PACKAGE))
        assertEquals(GameNativePlayer.GOLD_PACKAGE, launch.packageName)
        assertEquals(listOf(PlayerExtra.Text(GameNativePlayer.EXTRA_SHORTCUT_ID, "game_42")), launch.extras)
    }

    @Test fun disabledUnpinnedOrMalformedShortcutsAreIgnored() {
        for (id in listOf("game_0", "game_-2", "game_2147483648", "other_42", "game_42_extra")) {
            assertNull(gameNativeShortcut(GameNativePlayer.VANILLA_PACKAGE, id, "Game", true, true))
        }
        assertNull(gameNativeShortcut("other.app", "game_42", "Game", true, true))
        assertNull(gameNativeShortcut(GameNativePlayer.VANILLA_PACKAGE, "game_42", "Game", true, false))
        assertNull(gameNativeShortcut(GameNativePlayer.VANILLA_PACKAGE, "game_42", "Game", false, true))
    }

    @Test fun onlyAnExportWithTheSameIdAndNameIsDeduplicated() {
        val pin = gameNativeShortcut(GameNativePlayer.VANILLA_PACKAGE, "game_42", "A Game", true, true)!!
        val same = CatalogGame("A Game", 42, "GOG")
        val other = CatalogGame("Another game", 42, "STEAM")
        assertEquals(listOf(same.copy(shortcutPackage = pin.shortcutPackage), other), mergeGameNativeGames(listOf(pin), listOf(same, other)))
        assertEquals(listOf(same, other), mergeGameNativeGames(emptyList(), listOf(same, other)))
    }

    @Test fun aMatchingPinKeepsTheSteamExportIdentityAndArtButLaunchesItsActualPublisher() = runBlocking {
        val pin = gameNativeShortcut(GameNativePlayer.GOLD_PACKAGE, "game_42", "Game", true, true)!!
        val export = CatalogGame("Game", 42, "STEAM")
        val merged = mergeGameNativeGames(listOf(pin), listOf(export)).single()
        assertEquals(export.remoteKey, merged.remoteKey)
        assertEquals(42, steamAppId(merged.remoteKey))
        assertEquals(merged, catalogGameFromLine(catalogLine(merged)))
        val library = GameNativeLibrary().apply { confirm(listOf(merged)) }
        val game = library.listGames(PcPlatform.ID, GameQuery()).games.single()
        val launch = GameNativePlayer().launchIntent(LaunchRequest(game, library.ensureLocal(game), GameNativePlayer.VANILLA_PACKAGE))
        assertEquals(GameNativePlayer.GOLD_PACKAGE, launch.packageName)
        assertEquals(listOf(PlayerExtra.Text(GameNativePlayer.EXTRA_SHORTCUT_ID, "game_42")), launch.extras)
    }

    @Test fun aPinThatPredatesAnExportKeepsItsIdentityAcrossRefreshesAndExportRemoval() {
        val pin = gameNativeShortcut(GameNativePlayer.VANILLA_PACKAGE, "game_42", "Game", true, true)!!
        val stored = listOf(catalogGameFromLine(catalogLine(pin))!!)
        val live = restoreGameNativePins(listOf(pin), stored)
        val merged = mergeGameNativeGames(live, listOf(CatalogGame("Game", 42, "STEAM"))).single()
        assertEquals(pin.remoteKey, merged.remoteKey)
        val library = GameNativeLibrary().apply { confirm(listOf(merged)) }
        assertEquals(42, library.steamArtId(pin.remoteKey))
        val reloaded = restoreGameNativePins(listOf(pin), listOf(catalogGameFromLine(catalogLine(merged))!!))
        assertEquals(merged, reloaded.single())
        assertEquals(merged, mergeGameNativeGames(reloaded, emptyList()).single())
        assertEquals(reloaded, restoreGameNativePins(null, reloaded))
        assertTrue(restoreGameNativePins(emptyList(), reloaded).isEmpty())
    }

    @Test fun everyExportSourceKeepsItsStoreInTheLaunchIntent() = runBlocking {
        for (source in GAME_SOURCES) {
            val library = GameNativeLibrary().apply { confirm(listOf(CatalogGame("Game", 42, source))) }
            val game = library.listGames(PcPlatform.ID, GameQuery()).games.single()
            val intent = GameNativePlayer().launchIntent(LaunchRequest(game, library.ensureLocal(game), GameNativePlayer.VANILLA_PACKAGE))
            assertTrue(PlayerExtra.Text(GameNativePlayer.EXTRA_GAME_SOURCE, source) in intent.extras)
            assertEquals(if (source == "STEAM") 42 else null, steamAppId(game.remoteKey))
        }
    }
}
