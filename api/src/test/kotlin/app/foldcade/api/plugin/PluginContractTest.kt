// SPDX-License-Identifier: Apache-2.0

package app.foldcade.api.plugin

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
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
                target = LaunchTarget.ContentUri(uri = "content://games/1"),
                resolvedPackage = "app.sample",
            ),
        )
        assertEquals("app.sample", intent.packageName)
        assertEquals("app.sample.PlayActivity", intent.componentClass)
        assertEquals("app.sample.PLAY", intent.action)
        assertEquals("content://games/1", intent.dataUri)
        assertEquals("application/octet-stream", intent.mimeType)
        assertEquals(setOf(LaunchFlag.NewTask), intent.flags)
        assertTrue(intent.extras.filterIsInstance<PlayerExtra.LongValue>().single().value == 7L)
        assertTrue(LaunchFlag.ClearTop in LaunchFlag.entries)
        assertTrue(LaunchFlag.ClearTask in LaunchFlag.entries)
        assertTrue(intent.grantReadUri)
        assertTrue(player.needsLocalFile)
        assertEquals("slot", player.saveDeclarations(game).single().slot)
        assertFalse(player.occupiesBothDisplays)
    }

    @Test
    fun appRefCarriesGameNativeAndMoonlightWithoutAFile() {
        val gameNative = LaunchTarget.AppRef(
            values = mapOf("app_id" to "42", "game_source" to "STEAM"),
        )
        val moonlight = LaunchTarget.AppRef(
            values = mapOf("host_uuid" to "host-1", "app_id" to "123"),
        )
        val player = IdlePlayer(needsLocalFile = false)
        val intent = player.launchIntent(
            LaunchRequest(game = game, target = gameNative, resolvedPackage = "app.sample"),
        )
        assertNull(intent.dataUri)
        assertFalse(player.needsLocalFile)
        assertEquals("42", gameNative.values["app_id"])
        assertEquals("STEAM", gameNative.values["game_source"])
        assertEquals("host-1", moonlight.values["host_uuid"])
        assertEquals("123", moonlight.values["app_id"])
    }

    @Test
    fun nintendo3dsAliasesResolveToTheSamePlayers() {
        val platform = object : Platform {
            override val id = "nintendo-3ds"
            override val displayName = "Nintendo 3DS"
            override val extensions = setOf("cci")
            override val aliases = setOf("3ds", "n3ds")
        }
        val player = IdlePlayer(platformId = "nintendo-3ds")
        val platforms = listOf(platform)
        val players = listOf(player)
        assertEquals("nintendo-3ds", canonicalPlatformId(platforms, "nintendo-3ds"))
        assertEquals("nintendo-3ds", canonicalPlatformId(platforms, "Nintendo-3DS"))
        assertEquals("nintendo-3ds", canonicalPlatformId(platforms, "3ds"))
        assertEquals("nintendo-3ds", canonicalPlatformId(platforms, "3DS"))
        assertEquals("nintendo-3ds", canonicalPlatformId(platforms, "n3ds"))
        assertEquals("nintendo-3ds", canonicalPlatformId(platforms, "N3DS"))
        assertEquals(listOf(player.id), playersForPlatform(platforms, players, "3ds").map { it.id })
        assertEquals(listOf(player.id), playersForPlatform(platforms, players, "3DS").map { it.id })
        assertEquals(listOf(player.id), playersForPlatform(platforms, players, "n3ds").map { it.id })
        assertNull(canonicalPlatformId(platforms, "nds"))
    }

    @Test
    fun aliasesAreAJvmDefaultMethod() {
        val aliases = Platform::class.java.methods.single { it.name == "getAliases" }
        assertTrue(aliases.isDefault)
        assertTrue(IdlePlatform().aliases.isEmpty())
        assertEquals(1, PLUGIN_API_VERSION)
        assertEquals(3, PLUGIN_API_MINOR)
        val bind = PluginEntry::class.java.methods.single { it.name == "bind" }
        assertTrue(bind.isDefault)
        val saveFileName = Player::class.java.methods.single { it.name == "saveFileName" }
        assertTrue(saveFileName.isDefault)
    }

    @Test
    fun artworkUriLoadsWithoutCredentials() {
        val clean = Artwork(ArtworkRole.Cover, "https://cdn.example/cover.jpg?w=200")
        assertEquals("https://cdn.example/cover.jpg?w=200", clean.uri)
        assertEquals(
            "https://cdn.example/cover.jpg?w=200",
            credentialFreeArtworkUri(
                "https://user:s3cret-token@cdn.example/cover.jpg?access_token=s3cret-token&w=200",
            ),
        )
        assertEquals(
            "https://cdn.example/cover.jpg",
            credentialFreeArtworkUri("https://cdn.example/cover.jpg?token=s3cret-token#auth=s3cret-token"),
        )
        val userinfo = runCatching {
            Artwork(ArtworkRole.Cover, "https://user:s3cret-token@cdn.example/cover.jpg")
        }.exceptionOrNull()
        assertTrue(userinfo is IllegalArgumentException)
        assertFalse(userinfo?.message?.contains("s3cret") == true)
        val token = runCatching {
            Artwork(ArtworkRole.Cover, "https://cdn.example/cover.jpg?access_token=s3cret-token")
        }.exceptionOrNull()
        assertTrue(token is IllegalArgumentException)
        assertFalse(token?.message?.contains("s3cret") == true)
        assertEquals(null, credentialFreeArtworkUri("javascript:alert(1)"))
    }

    @Test
    fun fetchMayReturnNullAndAPageMayOmitTotal() = runBlocking {
        assertNull(IdleMetadata(cachedMeta = null).fetch(game))
        val page = GamePage(games = emptyList(), nextOffset = null)
        assertNull(page.total)
        assertEquals(4, page.copy(total = 4).total)
        assertEquals(
            setOf(
                ArtworkRole.Cover,
                ArtworkRole.Background,
                ArtworkRole.Icon,
                ArtworkRole.Logo,
                ArtworkRole.Screenshot,
            ),
            ArtworkRole.entries.toSet(),
        )
    }

    @Test
    fun pluginFailuresNameTheCaseAndKeepTheServerVersion() {
        val mismatch = PluginException.ProtocolMismatch(serverVersion = "5.4.0", message = "newer")
        assertEquals("5.4.0", mismatch.serverVersion)
        assertEquals("newer", mismatch.message)
        assertEquals("sign in", PluginException.NotAuthenticated("sign in").message)
        assertEquals("offline", PluginException.Unavailable("offline").message)
        assertEquals("missing", PluginException.NotFound("missing").message)
        assertFalse(CancellationException("stopped") is PluginException)
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

private class IdlePlayer(
    override val platformId: String = "sample",
    override val needsLocalFile: Boolean = true,
) : Player {
    override val id = "player.sample"
    override val displayName = "Sample player"
    override val packageNames = listOf("app.sample")
    override val startDisplay = StartDisplay.PickerChoice
    override val occupiesBothDisplays = false
    override val requiresImportedGame = false

    override fun saveDeclarations(game: Game): List<SaveDeclaration> =
        listOf(SaveDeclaration(slot = "slot", locationUri = null))

    override fun launchIntent(request: LaunchRequest): PlayerIntent {
        val uri = (request.target as? LaunchTarget.ContentUri)?.uri
        return PlayerIntent(
            packageName = request.resolvedPackage,
            componentClass = "app.sample.PlayActivity",
            action = "app.sample.PLAY",
            dataUri = uri,
            extras = listOf(PlayerExtra.LongValue(key = "bytes", value = 7L)),
            grantReadUri = uri != null,
            mimeType = if (uri == null) null else "application/octet-stream",
            flags = setOf(LaunchFlag.NewTask),
        )
    }
}

private class IdleLibrary : LibraryBackend {
    override val id = "library.sample"
    override val displayName = "Sample library"

    override suspend fun connect() = Unit

    override suspend fun disconnect() = Unit

    override suspend fun listPlatforms(): List<ListedPlatform> = emptyList()

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage =
        GamePage(games = emptyList(), nextOffset = null)

    override suspend fun ensureLocal(game: Game): LaunchTarget =
        LaunchTarget.ContentUri(uri = game.remoteKey)

    override suspend fun saves(game: Game): SaveSet = SaveSet(id, emptyList())

    override suspend fun prepareLaunch(game: Game, player: Player): Placement =
        Placement(target = LaunchTarget.ContentUri(uri = game.remoteKey))

    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult =
        SyncResult(SyncOutcome.Unchanged)
}

private open class IdleMetadata(private val cachedMeta: GameMeta?) : MetadataProvider {
    override val id = "metadata.sample"
    override val displayName = "Sample metadata"

    override fun cached(game: Game): GameMeta? = cachedMeta

    override suspend fun fetch(game: Game): GameMeta? = cachedMeta
}

private class HangingLibrary : LibraryBackend {
    val entered = Channel<Unit>(Channel.UNLIMITED)

    override val id = "hanging.library"
    override val displayName = "Hanging"

    override suspend fun connect() = hang()

    override suspend fun disconnect() = hang()

    override suspend fun listPlatforms(): List<ListedPlatform> = hang()

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage = hang()

    override suspend fun ensureLocal(game: Game): LaunchTarget = hang()

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

    override suspend fun fetch(game: Game): GameMeta? {
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

    override suspend fun ensureLocal(game: Game): LaunchTarget =
        LaunchTarget.ContentUri(uri = game.remoteKey)

    override suspend fun saves(game: Game): SaveSet = SaveSet(id, emptyList())

    override suspend fun prepareLaunch(game: Game, player: Player): Placement =
        Placement(target = LaunchTarget.ContentUri(uri = game.remoteKey))

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

    override suspend fun fetch(game: Game): GameMeta? {
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
