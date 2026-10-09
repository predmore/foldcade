package app.foldcade.api.plugin

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginContractTest {
    private val game = Game(
        backendId = "library.sample",
        remoteKey = "game-1",
        platformId = "sample",
        availability = Availability.LocalOnly,
        label = "Sample",
    )

    @Test
    fun identityCachedAndIntentStaySynchronous() {
        val library = IdleLibrary()
        val metadata = IdleMetadata(cachedMeta = GameMeta(title = "Already"))
        val platform = IdlePlatform()
        val player = IdlePlayer()

        assertEquals("library.sample", library.id)
        assertEquals("Sample library", library.displayName)
        assertEquals("Already", metadata.cached(game)?.title)
        assertNull(IdleMetadata(cachedMeta = null).cached(game))
        assertEquals("sample", platform.id)
        assertEquals("Sample", platform.displayName)
        assertEquals(setOf("sample"), platform.extensions)

        val intent = player.launchIntent(
            LaunchRequest(
                game = game,
                local = LocalCopy(contentUri = "content://games/1"),
                resolvedPackage = "app.sample",
            ),
        )
        assertEquals("app.sample", intent.packageName)
        assertEquals("app.sample.PlayActivity", intent.componentClass)
        assertEquals("app.sample.PLAY", intent.action)
        assertEquals("content://games/1", intent.dataUri)
        assertTrue(intent.grantReadUri)
        assertEquals("slot", player.saveDeclarations(game).single().slot)
        assertFalse(player.occupiesBothDisplays)
    }

    @Test
    fun libraryAndMetadataStaySeparateTypes() {
        assertFalse(LibraryBackend::class.java.isAssignableFrom(MetadataProvider::class.java))
        assertFalse(MetadataProvider::class.java.isAssignableFrom(LibraryBackend::class.java))

        val library: LibraryBackend = IdleLibrary()
        val metadata: MetadataProvider = IdleMetadata(cachedMeta = null)
        assertFalse(library is MetadataProvider)
        assertFalse(metadata is LibraryBackend)
    }

    @Test
    fun onlyBackendIoAndMetadataFetchAreSuspend() {
        assertSuspendMethods(
            LibraryBackend::class.java,
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
        )
        assertSuspendMethods(MetadataProvider::class.java, setOf("fetch"))
        assertSuspendMethods(Player::class.java, emptySet())
        assertSuspendMethods(Platform::class.java, emptySet())
    }

    @Test(timeout = 5_000)
    fun libraryIoStopsWhenCancelled() = runBlocking {
        val library = HangingLibrary()
        val player = IdlePlayer()
        val calls = listOf<suspend () -> Unit>(
            { library.connect() },
            { library.disconnect() },
            { library.listPlatforms() },
            { library.listGames("sample", GameQuery()) },
            { library.ensureLocal(game) },
            { library.saves(game) },
            { library.prepareLaunch(game, player) },
            { library.reconcile(game, player, ObservedSaves(emptyList())) },
        )
        for (call in calls) {
            withTimeout(2_000) {
                val job = launch { call() }
                library.entered.receive()
                job.cancel()
                job.join()
                assertTrue(job.isCancelled)
            }
        }
    }

    @Test(timeout = 5_000)
    fun cancellingAScanStopsTheWork() = runBlocking {
        val library = ScanningLibrary()
        val job = launch { library.listGames("sample", GameQuery()) }
        withTimeout(2_000) {
            while (library.steps.get() == 0) yield()
        }
        job.cancel()
        job.join()
        val stoppedAt = library.steps.get()
        yield()
        assertTrue(job.isCancelled)
        assertFalse(library.running)
        assertEquals(stoppedAt, library.steps.get())
    }

    @Test(timeout = 5_000)
    fun metadataFetchStopsWhenCancelled() = runBlocking {
        val metadata = HangingMetadata()
        withTimeout(2_000) {
            val job = launch { metadata.fetch(game) }
            metadata.entered.receive()
            job.cancel()
            job.join()
            assertTrue(job.isCancelled)
        }

        val scanning = ScanningMetadata()
        val job = launch { scanning.fetch(game) }
        withTimeout(2_000) {
            while (scanning.steps.get() == 0) yield()
        }
        job.cancel()
        job.join()
        val stoppedAt = scanning.steps.get()
        yield()
        assertTrue(job.isCancelled)
        assertFalse(scanning.running)
        assertEquals(stoppedAt, scanning.steps.get())
    }

    private fun assertSuspendMethods(type: Class<*>, suspendNames: Set<String>) {
        val declared = type.methods.filter { it.declaringClass == type }
        assertTrue(declared.isNotEmpty())
        for (method in declared) {
            val isSuspend = method.parameterTypes.any { it.name == "kotlin.coroutines.Continuation" }
            assertEquals(method.name, method.name in suspendNames, isSuspend)
        }
        assertEquals(suspendNames, declared.map { it.name }.filter { it in suspendNames }.toSet())
    }
}

private class IdlePlatform : Platform {
    override val id = "sample"
    override val displayName = "Sample"
    override val extensions = setOf("sample")
}

private class IdlePlayer : Player {
    override val id = "player.sample"
    override val displayName = "Sample player"
    override val platformId = "sample"
    override val packageNames = listOf("app.sample")
    override val handoff = GameHandoff.ContentUri
    override val startDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays = false
    override val requiresImportedGame = false

    override fun saveDeclarations(game: Game): List<SaveDeclaration> =
        listOf(SaveDeclaration(slot = "slot", locationUri = null))

    override fun launchIntent(request: LaunchRequest): PlayerIntent = PlayerIntent(
        packageName = request.resolvedPackage,
        componentClass = "app.sample.PlayActivity",
        action = "app.sample.PLAY",
        dataUri = request.local.contentUri,
        grantReadUri = true,
    )
}

private class IdleLibrary : LibraryBackend {
    override val id = "library.sample"
    override val displayName = "Sample library"

    override suspend fun connect() = Unit

    override suspend fun disconnect() = Unit

    override suspend fun listPlatforms(): List<ListedPlatform> = emptyList()

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage =
        GamePage(games = emptyList(), nextOffset = null)

    override suspend fun ensureLocal(game: Game): LocalCopy = LocalCopy(game.remoteKey)

    override suspend fun saves(game: Game): SaveSet = SaveSet(id, emptyList())

    override suspend fun prepareLaunch(game: Game, player: Player): Placement =
        Placement(local = LocalCopy(game.remoteKey))

    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult =
        SyncResult(SyncOutcome.Unchanged)
}

private class IdleMetadata(private val cachedMeta: GameMeta?) : MetadataProvider {
    override val id = "metadata.sample"
    override val displayName = "Sample metadata"

    override fun cached(game: Game): GameMeta? = cachedMeta

    override suspend fun fetch(game: Game): GameMeta = GameMeta(title = game.label)
}

private class HangingLibrary : LibraryBackend {
    val entered = Channel<Unit>(Channel.UNLIMITED)

    override val id = "hanging.library"
    override val displayName = "Hanging"

    override suspend fun connect() = hang()

    override suspend fun disconnect() = hang()

    override suspend fun listPlatforms(): List<ListedPlatform> = hang()

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage = hang()

    override suspend fun ensureLocal(game: Game): LocalCopy = hang()

    override suspend fun saves(game: Game): SaveSet = hang()

    override suspend fun prepareLaunch(game: Game, player: Player): Placement = hang()

    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult = hang()

    private suspend fun hang(): Nothing {
        entered.send(Unit)
        suspendCancellableCoroutine<Nothing> { pending ->
            pending.invokeOnCancellation { }
        }
    }
}

private class HangingMetadata : MetadataProvider {
    val entered = Channel<Unit>(Channel.UNLIMITED)

    override val id = "hanging.metadata"
    override val displayName = "Hanging"

    override fun cached(game: Game): GameMeta? = null

    override suspend fun fetch(game: Game): GameMeta {
        entered.send(Unit)
        suspendCancellableCoroutine<Nothing> { pending ->
            pending.invokeOnCancellation { }
        }
    }
}

private class ScanningLibrary : LibraryBackend {
    val steps = AtomicInteger(0)

    @Volatile
    var running = false

    override val id = "scanning.library"
    override val displayName = "Scanning"

    override suspend fun connect() = Unit

    override suspend fun disconnect() = Unit

    override suspend fun listPlatforms(): List<ListedPlatform> = emptyList()

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage {
        running = true
        try {
            while (true) {
                coroutineContext.ensureActive()
                steps.incrementAndGet()
                yield()
            }
        } finally {
            running = false
        }
    }

    override suspend fun ensureLocal(game: Game): LocalCopy = LocalCopy(game.remoteKey)

    override suspend fun saves(game: Game): SaveSet = SaveSet(id, emptyList())

    override suspend fun prepareLaunch(game: Game, player: Player): Placement =
        Placement(local = LocalCopy(game.remoteKey))

    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult =
        SyncResult(SyncOutcome.Unchanged)
}

private class ScanningMetadata : MetadataProvider {
    val steps = AtomicInteger(0)

    @Volatile
    var running = false

    override val id = "scanning.metadata"
    override val displayName = "Scanning"

    override fun cached(game: Game): GameMeta? = null

    override suspend fun fetch(game: Game): GameMeta {
        running = true
        try {
            while (true) {
                coroutineContext.ensureActive()
                steps.incrementAndGet()
                yield()
            }
        } finally {
            running = false
        }
    }
}
