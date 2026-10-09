package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.api.plugin.SyncResult
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.language.DialogKind
import app.foldcade.language.GridKind
import app.foldcade.language.Meaning
import app.foldcade.language.reduce
import app.foldcade.localfolder.DOCUMENT_DIRECTORY_MIME
import app.foldcade.localfolder.FolderEntry
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.localfolder.LocalFolderEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryGridTest {
    @Test
    fun aBoundFolderBecomesPlatformsThenGames() = runBlocking {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val entry = LocalFolderEntry()
        host.register(entry)
        val root = FolderEntry("content://roms", "roms", DOCUMENT_DIRECTORY_MIME)
        val nds = FolderEntry("content://nds", "nds", DOCUMENT_DIRECTORY_MIME)
        val game = FolderEntry("content://nds/cart", "Cart.nds", "application/octet-stream")
        entry.folder.bindTree(root) { parent ->
            when (parent.documentUri) {
                root.documentUri -> listOf(nds)
                nds.documentUri -> listOf(game)
                else -> emptyList()
            }
        }

        val loaded = loadPlatforms(
            host,
            LocalFolderBackend.ID,
            folderName = entry.folder::folderName,
        )
        val platforms = loaded as LoadedLibrary.Platforms
        assertEquals("nintendo-ds", platforms.entries.single().id)
        assertEquals("Nintendo DS", platforms.entries.single().title)
        assertEquals("1", platforms.entries.single().shortText)
        assertNull(platforms.entries.single().availabilityLabel)

        val games = loadGames(
            host,
            LocalFolderBackend.ID,
            "nintendo-ds",
            folderName = entry.folder::folderName,
        )
        assertEquals("Cart", games.single().title)
        assertEquals("nds", games.single().shortText)
        assertEquals(Copy.inFolder, games.single().availabilityLabel)
        assertEquals("content://nds/cart", games.single().game?.remoteKey)
    }

    @Test
    fun anEmptyFolderHasNoPlatforms() = runBlocking {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val entry = LocalFolderEntry()
        host.register(entry)
        val root = FolderEntry("content://roms", "roms", DOCUMENT_DIRECTORY_MIME)
        entry.folder.bindTree(root) { emptyList() }
        assertEquals(LoadedLibrary.NoPlatforms, loadPlatforms(host, LocalFolderBackend.ID))
    }

    @Test
    fun aFailedLibraryIsUnreachable() = runBlocking {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val libraries = listOf(object : QuietLibrary("broken") {
                override suspend fun connect() {
                    throw PluginException.Unavailable("offline")
                }
            })
        })
        assertEquals(LoadedLibrary.Unreachable, loadPlatforms(host, "broken"))
    }

    @Test
    fun gamesFollowNextOffsetUntilThePagesEnd() = runBlocking {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val libraries = listOf(PagingLibrary())
        })
        val games = loadGames(host, "paged", "snes")
        assertEquals(listOf("One", "Two", "Three"), games.map { it.title })
    }

    @Test
    fun noRegisteredPlayerOpensTheContentUri() {
        val target = LaunchTarget.ContentUri("content://tree/game")
        val plan = planLaunch(emptyList(), target) { null }
        assertEquals(GameLaunch.Dummy("content://tree/game"), plan)
    }

    @Test
    fun aMissingInstallNamesThePlayerAndItsPackages() {
        val player = quietPlayer("player.a", "Azahar", listOf("org.example.azahar", "org.example.play"))
        val plan = planLaunch(listOf(player), LaunchTarget.ContentUri("content://game")) { null }
        val missing = plan as GameLaunch.Missing
        assertEquals("Azahar", missing.playerName)
        assertEquals(listOf("org.example.azahar", "org.example.play"), missing.packages)
    }

    @Test
    fun oneInstalledPlayerIsChosenAndTwoAreNot() {
        val first = quietPlayer("player.a", "Azahar", listOf("org.example.a"))
        val second = quietPlayer("player.b", "Other", listOf("org.example.b"))
        val target = LaunchTarget.ContentUri("content://game")
        val one = planLaunch(listOf(first, second), target) { player ->
            if (player.id == "player.a") "org.example.a" else null
        }
        assertEquals("player.a", (one as GameLaunch.Installed).player.id)
        assertEquals("org.example.a", one.packageName)
        val two = planLaunch(listOf(first, second), target) { it.packageNames.single() }
        assertEquals(GameLaunch.SeveralInstalled, two)
    }

    @Test
    fun noLibraryFocusesAddFolderThenExplainsTheGrant() {
        val shell = ShellController(
            SessionStore(MemoryPrefs()),
            PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
        )
        shell.showNoLibrary()
        assertEquals(GridKind.NoLibrary, shell.model.gridKind)
        assertEquals(2, shell.model.count)
        assertEquals(0, shell.model.focus.cellIndex)
        val explained = reduce(shell.model, Meaning.Activate).first
        assertEquals(DialogKind.Folder, explained.dialog?.kind)
    }

    @Test
    fun theManifestDoesNotRequestAllFilesAccess() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("WRITE_EXTERNAL_STORAGE"))
    }
}

private open class QuietLibrary(override val id: String) : LibraryBackend {
    override val displayName = id
    override suspend fun connect() = Unit
    override suspend fun disconnect() = Unit
    override suspend fun listPlatforms(): List<ListedPlatform> = emptyList()
    override suspend fun listGames(platformId: String, query: GameQuery): GamePage =
        GamePage(emptyList(), null, 0)
    override suspend fun ensureLocal(game: Game): LaunchTarget = LaunchTarget.ContentUri(game.remoteKey)
    override suspend fun saves(game: Game): SaveSet = SaveSet(id, emptyList())
    override suspend fun prepareLaunch(game: Game, player: Player): Placement =
        Placement(LaunchTarget.ContentUri(game.remoteKey))
    override suspend fun reconcile(
        game: Game,
        player: Player,
        observed: ObservedSaves,
    ): SyncResult = SyncResult(app.foldcade.api.plugin.SyncOutcome.Unchanged)
}

private class PagingLibrary : QuietLibrary("paged") {
    override suspend fun listGames(platformId: String, query: GameQuery): GamePage {
        val labels = listOf("One", "Two", "Three")
        val offset = query.offset.coerceAtLeast(0)
        if (offset >= labels.size) return GamePage(emptyList(), null, labels.size)
        val label = labels[offset]
        return GamePage(
            games = listOf(
                Game("paged", "content://$label", platformId, Availability.LocalOnly, label),
            ),
            nextOffset = if (offset + 1 < labels.size) offset + 1 else null,
            total = labels.size,
        )
    }
}

private fun quietPlayer(id: String, name: String, packages: List<String>) = object : Player {
    override val id = id
    override val displayName = name
    override val platformId = "nintendo-3ds"
    override val packageNames = packages
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.Primary
    override val occupiesBothDisplays = true
    override val requiresImportedGame = false
    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()
    override fun launchIntent(request: LaunchRequest): PlayerIntent = error("not asked")
}
