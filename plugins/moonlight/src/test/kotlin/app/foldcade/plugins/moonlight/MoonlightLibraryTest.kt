package app.foldcade.plugins.moonlight

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.ObservedSlot
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SyncOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonlightLibraryTest {
    private val host = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
    private val desktop = moonlightApp(host, "881448767", "Desktop")!!
    private val other = moonlightApp(host, "123", "Celeste")!!

    @Test
    fun pinnedShortcutsAreTheCatalogUntilTheUserConfirmsAnImport() = runBlocking {
        val library = MoonlightLibrary()
        assertFalse(library.hasConfirmedImport())
        library.replacePinned(listOf(desktop, other))
        val page = library.listGames(MoonlightPlatform.ID, GameQuery())
        assertEquals(listOf("Desktop", "Celeste"), page.games.map { it.label })
        assertEquals(2, page.total)
        assertNull(page.nextOffset)
        assertEquals(Availability.LocalOnly, page.games.first().availability)
        assertEquals(MoonlightLibrary.ID, page.games.first().backendId)

        library.confirmImport(listOf(other))
        assertTrue(library.hasConfirmedImport())
        assertEquals(listOf("Celeste"), library.listGames(MoonlightPlatform.ID, GameQuery()).games.map { it.label })
        library.replacePinned(listOf(desktop))
        assertEquals(listOf("Celeste"), library.listGames(MoonlightPlatform.ID, GameQuery()).games.map { it.label })
    }

    @Test
    fun theSourceSwitchKeepsBothLists() = runBlocking {
        val library = MoonlightLibrary()
        library.replacePinned(listOf(desktop, other))
        library.confirmImport(listOf(other))
        assertEquals(MoonlightCatalog.Imported, library.catalog())
        assertEquals(listOf("Celeste"), library.importedApps().map { it.label })

        library.useCatalog(MoonlightCatalog.Pinned)
        library.replacePinned(listOf(desktop))
        assertEquals(listOf("Desktop"), library.listGames(MoonlightPlatform.ID, GameQuery()).games.map { it.label })
        assertEquals(listOf("Celeste"), library.importedApps().map { it.label })

        library.useCatalog(MoonlightCatalog.Imported)
        assertEquals(listOf("Celeste"), library.listGames(MoonlightPlatform.ID, GameQuery()).games.map { it.label })
        assertEquals(listOf(desktop), library.pinnedApps())

        val fresh = MoonlightLibrary()
        fresh.useCatalog(MoonlightCatalog.Imported)
        assertTrue(fresh.listGames(MoonlightPlatform.ID, GameQuery()).games.isEmpty())
        assertFalse(fresh.hasConfirmedImport())
    }

    @Test
    fun aConfirmedEmptyImportStaysEmpty() = runBlocking {
        val library = MoonlightLibrary()
        library.replacePinned(listOf(desktop))
        library.confirmImport(emptyList())
        val page = library.listGames(MoonlightPlatform.ID, GameQuery())
        assertTrue(page.games.isEmpty())
        assertEquals(0, page.total)
    }

    @Test
    fun ensureLocalReturnsTheAppRefAndReconcileIsANoOp() = runBlocking {
        val library = MoonlightLibrary()
        library.replacePinned(listOf(desktop))
        val game = library.listGames(MoonlightPlatform.ID, GameQuery()).games.single()
        val target = library.ensureLocal(game) as LaunchTarget.AppRef
        assertEquals(host, target.values[MoonlightPlayer.HOST_UUID])
        assertEquals("881448767", target.values[MoonlightPlayer.APP_ID])
        val placement = library.prepareLaunch(game, MoonlightPlayer())
        assertEquals(target, placement.target)
        assertTrue(placement.savesToPlace.isEmpty())
        assertTrue(library.saves(game).slots.isEmpty())

        val observed = ObservedSaves(
            listOf(
                ObservedSlot(
                    slot = "save",
                    contentHash = "abc",
                    playerId = "moonlight",
                    contentUri = "content://moonlight/private",
                ),
            ),
        )
        val result = library.reconcile(game, MoonlightPlayer(), observed)
        assertEquals(SyncOutcome.Unchanged, result.outcome)
        assertNull(result.note)
        assertTrue(library.saves(game).slots.isEmpty())
    }

    @Test
    fun anotherBackendIsNotThisCatalog() = runBlocking {
        val library = MoonlightLibrary()
        library.replacePinned(listOf(desktop))
        val game = library.listGames(MoonlightPlatform.ID, GameQuery()).games.single()
        val missing = runCatching { library.ensureLocal(game.copy(backendId = "romm")) }
        assertTrue(missing.exceptionOrNull() is PluginException.NotFound)
        assertTrue(library.listGames("nintendo-3ds", GameQuery()).games.isEmpty())
        val filtered = library.listGames(MoonlightPlatform.ID, GameQuery(text = "desk"))
        assertEquals("Desktop", filtered.games.single().label)
    }

    @Test
    fun pagesStopAtTheLimit() = runBlocking {
        val library = MoonlightLibrary()
        library.replacePinned(listOf(desktop, other))
        val first = library.listGames(MoonlightPlatform.ID, GameQuery(limit = 1))
        assertEquals(listOf("Desktop"), first.games.map { it.label })
        assertEquals(1, first.nextOffset)
        assertEquals(2, first.total)
        val second = library.listGames(MoonlightPlatform.ID, GameQuery(offset = 1, limit = 1))
        assertEquals(listOf("Celeste"), second.games.map { it.label })
        assertNull(second.nextOffset)
    }
}
