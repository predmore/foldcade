package app.foldcade.localfolder

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.ObservedSlot
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.api.plugin.SyncOutcome
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFolderBackendTest {
    @Test
    fun identityIsSynchronousAndThereIsNoMetadataProvider() {
        val backend = newBackend(folderDir("content://roms", "roms"))
        assertEquals("local-folder", backend.id)
        assertEquals("Local folder", backend.displayName)
        assertFalse(MetadataProvider::class.java.isAssignableFrom(LocalFolderBackend::class.java))

        val declared = LocalFolderBackend::class.java.methods
            .filter { it.declaringClass == LocalFolderBackend::class.java }
        val suspendNames = declared
            .filter { method -> method.parameterTypes.any { it.name == "kotlin.coroutines.Continuation" } }
            .map { it.name }
            .filterNot { it.startsWith("access$") }
            .toSet()
        assertEquals(
            setOf(
                "connect",
                "disconnect",
                "listPlatforms",
                "listGames",
                "ensureLocal",
                "saves",
                "prepareLaunch",
                "reconcile",
            ),
            suspendNames,
        )
        assertFalse(suspendNames.contains("getId"))
        assertFalse(suspendNames.contains("getDisplayName"))
    }

    @Test
    fun gamesUseCanonicalIdsAndTheDocumentUri() = runBlocking {
        val backend = newBackend(
            folderDir(
                "content://roms",
                "roms",
                listOf(
                    folderDir(
                        "content://3ds",
                        "3ds",
                        listOf(folderFile("content://3ds/game", "Game.cci")),
                    ),
                    folderDir(
                        "content://nds",
                        "Nintendo DS",
                        listOf(folderFile("content://nds/game", "Cart.nds")),
                    ),
                ),
            ),
        )

        val platforms = backend.listPlatforms()
        assertEquals(listOf("nintendo-3ds", "nintendo-ds"), platforms.map { it.platformId })
        assertEquals(listOf("Nintendo 3DS", "Nintendo DS"), platforms.map { it.displayName })

        val aliasPage = backend.listGames("3ds", GameQuery())
        assertTrue(aliasPage.games.isEmpty())
        assertEquals(0, aliasPage.total)
        assertNull(aliasPage.nextOffset)
        assertEquals(0, backend.listGames("Nintendo-3DS", GameQuery()).total)

        val page = backend.listGames("nintendo-3ds", GameQuery())
        val game = page.games.single()
        assertEquals("local-folder", game.backendId)
        assertEquals("content://3ds/game", game.remoteKey)
        assertEquals("nintendo-3ds", game.platformId)
        assertEquals(Availability.LocalOnly, game.availability)
        assertEquals("Game", game.label)
        assertEquals("content://3ds/game", (backend.ensureLocal(game) as LaunchTarget.ContentUri).uri)
    }

    @Test
    fun pagesReadTheCachedScan() = runBlocking {
        val calls = AtomicInteger()
        val backend = newBackend(
            folderDir(
                "content://snes",
                "snes",
                listOf(
                    folderFile("content://c", "C.sfc"),
                    folderFile("content://a", "A.sfc"),
                    folderFile("content://b", "B.sfc"),
                    folderFile("content://other", "Other.nds"),
                ),
            ),
            childrenOf = countWalks(calls),
        )

        val first = backend.listGames("snes", GameQuery(limit = 2))
        assertEquals(listOf("A", "B"), first.games.map { it.label })
        assertEquals(2, first.nextOffset)
        assertEquals(3, first.total)
        val afterFirst = calls.get()
        assertTrue(afterFirst > 0)

        val second = backend.listGames("snes", GameQuery(offset = 2, limit = 2))
        assertEquals(listOf("C"), second.games.map { it.label })
        assertNull(second.nextOffset)
        assertEquals(3, second.total)
        assertEquals(afterFirst, calls.get())

        val found = backend.listGames("snes", GameQuery(text = "b"))
        assertEquals(listOf("B"), found.games.map { it.label })
        assertEquals(1, found.total)
        val blank = backend.listGames("snes", GameQuery(text = "   "))
        assertEquals(3, blank.total)
        assertEquals(afterFirst, calls.get())

        val ds = backend.listGames("nintendo-ds", GameQuery(text = "other"))
        assertEquals(listOf("Other"), ds.games.map { it.label })
        assertEquals("content://other", ds.games.single().remoteKey)
    }

    @Test
    fun disconnectDropsTheCacheAndKeepsRecordedSaves() = runBlocking {
        val calls = AtomicInteger()
        val backend = newBackend(
            folderDir("content://nds", "nds", listOf(folderFile("content://game", "Game.nds"))),
            childrenOf = countWalks(calls),
        )
        val game = backend.listGames("nintendo-ds", GameQuery()).games.single()
        val player = unusedPlayer("player.a")
        backend.reconcile(game, player, oneSlot("slot", "aa", player.id))
        val scanned = calls.get()

        backend.disconnect()
        val again = backend.listGames("nintendo-ds", GameQuery()).games.single()
        assertTrue(calls.get() > scanned)
        assertEquals("aa", backend.saves(again).slots.single().contentHash)
    }

    @Test(timeout = 5_000)
    fun cancellationStopsTheScanAndDoesNotPublishIt() = runBlocking {
        val calls = AtomicInteger()
        val started = CompletableFuture<Unit>()
        val release = CountDownLatch(1)
        val root = folderDir(
            "content://roms",
            "roms",
            listOf(
                folderDir("content://nds", "nds", listOf(folderFile("content://game", "Game.nds"))),
            ),
        )
        val list = indexTree(root)
        val backend = LocalFolderBackend(root.entry()) { entry ->
            if (calls.incrementAndGet() == 1) {
                started.complete(Unit)
                release.await()
            }
            list(entry)
        }
        val failure = AtomicReference<Throwable>()
        val job = launch(Dispatchers.Default) {
            try {
                backend.connect()
            } catch (thrown: Throwable) {
                failure.set(thrown)
                throw thrown
            }
        }
        try {
            started.get(2, TimeUnit.SECONDS)
            job.cancel()
        } finally {
            release.countDown()
        }
        job.join()
        assertTrue(failure.get() is CancellationException)
        assertFalse(failure.get() is PluginException)
        val stopped = calls.get()
        yield()
        assertEquals(1, stopped)
        assertEquals(stopped, calls.get())

        val platforms = backend.listPlatforms()
        assertEquals(listOf("nintendo-ds"), platforms.map { it.platformId })
        assertTrue(calls.get() > stopped)
    }

    @Test
    fun anUnknownGameIsNotFound() = runBlocking {
        val backend = newBackend(folderDir("content://roms", "roms"))
        val missing = Game(
            backendId = "local-folder",
            remoteKey = "content://missing",
            platformId = "nintendo-3ds",
            availability = Availability.LocalOnly,
            label = "Missing",
        )
        val other = missing.copy(backendId = "romm")
        assertTrue(runCatching { backend.ensureLocal(missing) }.exceptionOrNull() is PluginException.NotFound)
        assertTrue(runCatching { backend.ensureLocal(other) }.exceptionOrNull() is PluginException.NotFound)
    }

    @Test
    fun launchLeavesSavesWhereThePlayerWritesThem() = runBlocking {
        val backend = newBackend(
            folderDir("content://3ds", "3ds", listOf(folderFile("content://game", "Game.cci"))),
        )
        val game = backend.listGames("nintendo-3ds", GameQuery()).games.single()
        val player = unusedPlayer("player.a")
        val placement = backend.prepareLaunch(game, player)
        val target = placement.target as LaunchTarget.ContentUri
        assertEquals("content://game", target.uri)
        assertTrue(target.firmware.isEmpty())
        assertTrue(placement.savesToPlace.isEmpty())
        assertEquals(SyncOutcome.Unchanged, placement.sync.outcome)
        assertTrue(backend.saves(game).slots.isEmpty())
    }

    @Test
    fun reconcileKeepsBothWhenTwoPlayersChangeTheSameSlot() = runBlocking {
        val backend = newBackend(
            folderDir("content://nds", "nds", listOf(folderFile("content://game", "Game.nds"))),
        )
        val game = backend.listGames("nintendo-ds", GameQuery()).games.single()
        val first = unusedPlayer("player.a")
        val second = unusedPlayer("player.b")

        val recorded = backend.reconcile(game, first, oneSlot("slot", "AA", first.id))
        assertEquals(SyncOutcome.Unchanged, recorded.outcome)
        assertEquals("aa", backend.saves(game).slots.single().contentHash)

        val same = backend.reconcile(game, first, oneSlot("slot", "bb", first.id))
        assertEquals(SyncOutcome.Unchanged, same.outcome)
        assertEquals("bb", backend.saves(game).slots.single().contentHash)

        val conflict = backend.reconcile(game, second, oneSlot("slot", "cc", second.id))
        assertEquals(SyncOutcome.KeptBoth, conflict.outcome)
        assertEquals("Both saves were kept.", conflict.note)
        assertEquals(
            listOf("slot" to "cc", "slot.kept" to "bb"),
            backend.saves(game).slots.map { it.slot to it.contentHash },
        )
        assertEquals(listOf("player.b", "player.a"), backend.saves(game).slots.map { it.lastPlayerId })
    }

    @Test(timeout = 5_000)
    fun bindTreeDropsAnInFlightScanOfThePreviousFolder() = runBlocking {
        val started = CompletableFuture<Unit>()
        val release = CountDownLatch(1)
        val previous = folderDir(
            "content://old",
            "nds",
            listOf(folderFile("content://old/game", "Old.nds")),
        )
        val next = folderDir(
            "content://new",
            "snes",
            listOf(folderFile("content://new/game", "New.sfc")),
        )
        val previousChildren = indexTree(previous)
        val backend = LocalFolderBackend()
        val scanning = launch(Dispatchers.Default) {
            backend.bindTree(previous.entry()) { entry ->
                started.complete(Unit)
                release.await()
                previousChildren(entry)
            }
            backend.connect()
        }
        try {
            started.get(2, TimeUnit.SECONDS)
            backend.bindTree(next.entry(), indexTree(next))
        } finally {
            release.countDown()
        }
        scanning.join()
        assertEquals(listOf("New"), backend.listGames("snes", GameQuery()).games.map { it.label })
        assertTrue(backend.listGames("nintendo-ds", GameQuery()).games.isEmpty())
    }
}

private class TreeNode(
    val uri: String,
    val name: String,
    val directory: Boolean,
    val children: List<TreeNode> = emptyList(),
) {
    fun entry(): FolderEntry = FolderEntry(
        documentUri = uri,
        displayName = name,
        mimeType = if (directory) DOCUMENT_DIRECTORY_MIME else "application/octet-stream",
    )
}

private fun folderDir(uri: String, name: String, children: List<TreeNode> = emptyList()): TreeNode =
    TreeNode(uri, name, directory = true, children = children)

private fun folderFile(uri: String, name: String): TreeNode =
    TreeNode(uri, name, directory = false)

private fun indexTree(root: TreeNode): (FolderEntry) -> List<FolderEntry> {
    val byUri = HashMap<String, TreeNode>()
    val pending = ArrayDeque<TreeNode>()
    pending.add(root)
    while (pending.isNotEmpty()) {
        val node = pending.removeFirst()
        check(byUri.put(node.uri, node) == null) { "duplicate fixture uri ${node.uri}" }
        pending.addAll(node.children)
    }
    return { entry -> byUri[entry.documentUri]?.children?.map { it.entry() }.orEmpty() }
}

private fun countWalks(calls: AtomicInteger): (TreeNode) -> (FolderEntry) -> List<FolderEntry> = { root ->
    val list = indexTree(root)
    fun children(entry: FolderEntry): List<FolderEntry> {
        calls.incrementAndGet()
        return list(entry)
    }
    ::children
}

private fun newBackend(
    root: TreeNode,
    childrenOf: (TreeNode) -> (FolderEntry) -> List<FolderEntry> = { indexTree(it) },
): LocalFolderBackend = LocalFolderBackend(root.entry(), childrenOf(root))

private fun oneSlot(slot: String, hash: String, playerId: String): ObservedSaves = ObservedSaves(
    slots = listOf(
        ObservedSlot(slot = slot, contentHash = hash, playerId = playerId, contentUri = null),
    ),
)

private fun unusedPlayer(id: String): Player = object : Player {
    override val id = id
    override val displayName = id
    override val platformId = "nintendo-3ds"
    override val packageNames = listOf("app.unused")
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.Primary
    override val occupiesBothDisplays = true
    override val requiresImportedGame = false

    override fun saveDeclarations(game: Game): List<SaveDeclaration> = error("not asked")

    override fun launchIntent(request: LaunchRequest): PlayerIntent = error("not asked")
}
