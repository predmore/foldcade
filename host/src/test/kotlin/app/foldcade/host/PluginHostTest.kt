package app.foldcade.host

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameMeta
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.MetadataProvider
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.api.plugin.SyncResult
import app.foldcade.plugins.sample.SampleEntry
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginHostTest {
    private val game = Game(
        backendId = "sample.library",
        remoteKey = "content://sample/1",
        platformId = "sample.platform",
        availability = Availability.LocalOnly,
        label = "Sample game",
    )

    @Test
    fun inTreeSampleRegistersFourInterfacesInSeparateSlots() {
        val host = PluginHost(Dispatchers.Unconfined)
        host.register(SampleEntry())

        assertEquals("sample.platform", host.platform("sample.platform")?.id)
        assertEquals("sample.player", host.player("sample.player")?.id)
        assertEquals(listOf("sample.player"), host.playersFor("sample.platform").map { it.id })
        assertEquals("sample.library", host.library("sample.library")?.id)
        assertEquals("sample.metadata", host.metadata("sample.metadata")?.id)
        assertNull(host.library("sample.metadata"))
        assertNull(host.metadata("sample.library"))
        assertFalse(host.library("sample.library") is MetadataProvider)
        assertFalse(host.metadata("sample.metadata") is LibraryBackend)

        val intent = host.player("sample.player")!!.launchIntent(
            LaunchRequest(
                game = game,
                target = LaunchTarget.ContentUri(uri = game.remoteKey),
                resolvedPackage = "app.foldcade.sample",
            ),
        )
        assertEquals("app.foldcade.sample.PLAY", intent.action)
        assertEquals(game.remoteKey, intent.dataUri)
    }

    @Test
    fun inTreeSampleLoadsFromTheClasspathEntryPoint() {
        val host = PluginHost(Dispatchers.Unconfined)
        host.load(SampleEntry::class.java.classLoader)
        assertEquals("Sample library", host.library("sample.library")?.displayName)
        assertEquals("Sample metadata", host.metadata("sample.metadata")?.displayName)
        assertNotNull(host.platform("sample.platform"))
        assertNotNull(host.player("sample.player"))
    }

    @Test
    fun outOfTreeJarRegistersLibraryAndMetadataAsSeparateSlots() {
        val jar = File(System.getProperty("foldcade.outOfTreeJar") ?: error("missing out-of-tree jar"))
        assertTrue(jar.isFile)
        val parent = PluginHost::class.java.classLoader
        URLClassLoader(arrayOf(jar.toURI().toURL()), parent).use { loader ->
            val host = PluginHost(Dispatchers.Unconfined)
            assertNull(host.library("outoftree.library"))
            host.load(loader)

            val library = host.library("outoftree.library")
            val metadata = host.metadata("outoftree.metadata")
            assertEquals("OutOfTreeLibrary", library?.javaClass?.simpleName)
            assertEquals("OutOfTreeMetadata", metadata?.javaClass?.simpleName)
            assertEquals(loader, library?.javaClass?.classLoader)
            assertEquals(loader, metadata?.javaClass?.classLoader)
            assertNull(host.library("outoftree.metadata"))
            assertNull(host.metadata("outoftree.library"))
            assertFalse(library is MetadataProvider)
            assertFalse(metadata is LibraryBackend)
            assertEquals("outoftree.platform", host.platform("outoftree.platform")?.id)
            assertEquals("outoftree.player", host.player("outoftree.player")?.id)
            assertEquals(listOf("outoftree.player"), host.playersFor("outoftree.platform").map { it.id })
        }
    }

    @Test
    fun theSameIdMayOccupyLibraryAndMetadataBecauseTheSlotsAreSeparate() {
        val host = PluginHost(Dispatchers.Unconfined)
        val library = LibraryFake("shared-id")
        val metadata = MetadataFake("shared-id")
        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val libraries = listOf(library)
            override val metadataProviders = listOf(metadata)
        })
        assertTrue(host.library("shared-id") === library)
        assertTrue(host.metadata("shared-id") === metadata)
    }

    @Test
    fun incompatiblePluginMajorIsRejected() {
        val host = PluginHost(Dispatchers.Unconfined)
        val failure = runCatching {
            host.register(object : PluginEntry {
                override val apiVersion = PLUGIN_API_VERSION + 1
            })
        }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
        assertNull(host.library("sample.library"))
    }

    @Test
    fun playersForResolvesNintendo3dsAliases() {
        val host = PluginHost(Dispatchers.Unconfined)
        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val platforms = listOf(object : Platform {
                override val id = "nintendo-3ds"
                override val displayName = "Nintendo 3DS"
                override val extensions = setOf("cci")
                override val aliases = setOf("3ds", "n3ds")
            })
            override val players = listOf(SampleAliasPlayer())
        })
        assertEquals(listOf("n3ds-player"), host.playersFor("nintendo-3ds").map { it.id })
        assertEquals(listOf("n3ds-player"), host.playersFor("3ds").map { it.id })
        assertEquals(listOf("n3ds-player"), host.playersFor("n3ds").map { it.id })
        assertTrue(host.playersFor("nds").isEmpty())
    }

    @Test
    fun pluginEntryListsAreJvmDefaultMethods() {
        val lists = PluginEntry::class.java.methods.single { it.name == "getPlatforms" }
        val major = PluginEntry::class.java.methods.single { it.name == "getApiVersion" }
        assertTrue(lists.isDefault)
        assertFalse(major.isDefault)
    }

    @Test
    fun platformIdAndAliasCollisionsAreRejected() {
        val host = PluginHost(Dispatchers.Unconfined)
        host.register(platformEntry("nintendo-3ds", setOf("3ds", "n3ds")))

        val aliasHitsCanonicalId = runCatching {
            host.register(platformEntry("folder", setOf("Nintendo-3DS")))
        }
        assertTrue(aliasHitsCanonicalId.exceptionOrNull() is IllegalStateException)
        assertNull(host.platform("folder"))
        assertEquals("nintendo-3ds", host.platform("nintendo-3ds")?.id)

        val aliasHitsAlias = runCatching {
            host.register(platformEntry("romm", setOf("N3DS")))
        }
        assertTrue(aliasHitsAlias.exceptionOrNull() is IllegalStateException)
        assertNull(host.platform("romm"))

        val repeatsOwnId = runCatching {
            PluginHost(Dispatchers.Unconfined).register(platformEntry("3ds", setOf("3DS")))
        }
        assertTrue(repeatsOwnId.exceptionOrNull() is IllegalStateException)

        val sharedAlias = runCatching {
            PluginHost(Dispatchers.Unconfined).register(object : PluginEntry {
                override val apiVersion = PLUGIN_API_VERSION
                override val platforms = listOf(
                    namedPlatform("ds", setOf("nds")),
                    namedPlatform("other", setOf("NDS")),
                )
            })
        }
        assertTrue(sharedAlias.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun duplicateIdInsideOneSlotIsRejected() {
        val host = PluginHost(Dispatchers.Unconfined)
        host.register(SampleEntry())
        val failure = runCatching { host.register(SampleEntry()) }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
    }

    @Test(timeout = 5_000)
    fun libraryIoRunsOffTheCallerAndDoesNotBlockIt() = runBlocking {
        val caller = namedDispatcher("caller")
        val io = namedDispatcher("foldcade-io")
        try {
            val backend = object : LibraryFake("io.library") {
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()

                @Volatile
                var threadName: String? = null

                override suspend fun listGames(platformId: String, query: GameQuery): GamePage {
                    threadName = Thread.currentThread().name
                    started.complete(Unit)
                    release.await()
                    return GamePage(emptyList(), null)
                }
            }
            val host = PluginHost(io)
            host.register(object : PluginEntry {
                override val apiVersion = PLUGIN_API_VERSION
                override val libraries = listOf(backend)
            })
            val listed = launch(caller) { host.listGames(backend.id, "sample", GameQuery()) }
            withTimeout(2_000) { backend.started.await() }
            assertTrue(backend.threadName!!.startsWith("foldcade-io"))
            val callerName = withContext(caller) { Thread.currentThread().name }
            assertTrue(callerName.startsWith("caller"))
            backend.release.complete(Unit)
            withTimeout(2_000) { listed.join() }
        } finally {
            caller.close()
            io.close()
        }
    }

    @Test(timeout = 5_000)
    fun cachedMetadataStaysOnTheCaller() = runBlocking {
        val caller = namedDispatcher("caller")
        val io = namedDispatcher("foldcade-io")
        try {
            val provider = object : MetadataFake("meta.sync") {
                @Volatile
                var threadName: String? = null

                override fun cached(game: Game): GameMeta? {
                    threadName = Thread.currentThread().name
                    return GameMeta(title = "Already")
                }
            }
            val host = PluginHost(io)
            host.register(object : PluginEntry {
                override val apiVersion = PLUGIN_API_VERSION
                override val metadataProviders = listOf(provider)
            })
            val meta = withContext(caller) { host.cachedMetadata(provider.id, game) }
            assertEquals("Already", meta?.title)
            assertTrue(provider.threadName!!.startsWith("caller"))
        } finally {
            caller.close()
            io.close()
        }
    }

    @Test(timeout = 5_000)
    fun cancellingAScanOrHttpCallStopsTheWork() = runBlocking {
        val scan = object : LibraryFake("scan.library") {
            val steps = AtomicInteger(0)

            @Volatile
            var running = false

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
        }
        val http = object : LibraryFake("http.library") {
            val started = CompletableDeferred<Unit>()

            @Volatile
            var finished = false

            override suspend fun connect() {
                started.complete(Unit)
                delay(60_000)
                finished = true
            }
        }
        val metadata = object : MetadataFake("http.metadata") {
            val started = CompletableDeferred<Unit>()

            @Volatile
            var finished = false

            override suspend fun fetch(game: Game): GameMeta {
                started.complete(Unit)
                delay(60_000)
                finished = true
                return GameMeta(title = game.label)
            }
        }
        val host = PluginHost(Dispatchers.Default)
        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val libraries = listOf(scan, http)
            override val metadataProviders = listOf(metadata)
        })

        val scanning = launch { host.listGames(scan.id, "sample", GameQuery()) }
        withTimeout(2_000) { while (scan.steps.get() == 0) yield() }
        scanning.cancel()
        scanning.join()
        val stoppedAt = scan.steps.get()
        yield()
        assertTrue(scanning.isCancelled)
        assertFalse(scan.running)
        assertEquals(stoppedAt, scan.steps.get())

        val connecting = launch { host.connect(http.id) }
        withTimeout(2_000) { http.started.await() }
        connecting.cancel()
        connecting.join()
        assertTrue(connecting.isCancelled)
        assertFalse(http.finished)

        val fetching = launch { host.fetchMetadata(metadata.id, game) }
        withTimeout(2_000) { metadata.started.await() }
        fetching.cancel()
        fetching.join()
        assertTrue(fetching.isCancelled)
        assertFalse(metadata.finished)
    }
}

private fun platformEntry(id: String, aliases: Set<String>) = object : PluginEntry {
    override val apiVersion = PLUGIN_API_VERSION
    override val platforms = listOf(namedPlatform(id, aliases))
}

private fun namedPlatform(id: String, aliases: Set<String>) = object : Platform {
    override val id = id
    override val displayName = id
    override val extensions = emptySet<String>()
    override val aliases = aliases
}

private fun namedDispatcher(name: String) = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, name).apply { isDaemon = true }
}.asCoroutineDispatcher()

private class SampleAliasPlayer : Player {
    override val id = "n3ds-player"
    override val displayName = "3DS"
    override val platformId = "nintendo-3ds"
    override val packageNames = listOf("org.azahar_emu.azahar")
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.Primary
    override val occupiesBothDisplays = true
    override val requiresImportedGame = false

    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()

    override fun launchIntent(request: LaunchRequest) = error("unused")
}

private open class LibraryFake(override val id: String) : LibraryBackend {
    override val displayName = id

    open override suspend fun connect() = Unit

    override suspend fun disconnect() = Unit

    override suspend fun listPlatforms(): List<ListedPlatform> = emptyList()

    open override suspend fun listGames(platformId: String, query: GameQuery): GamePage =
        GamePage(emptyList(), null)

    override suspend fun ensureLocal(game: Game): LaunchTarget =
        LaunchTarget.ContentUri(uri = game.remoteKey)

    override suspend fun saves(game: Game): SaveSet = SaveSet(id, emptyList())

    override suspend fun prepareLaunch(game: Game, player: Player): Placement =
        Placement(target = LaunchTarget.ContentUri(uri = game.remoteKey))

    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult =
        error("unused")
}

private open class MetadataFake(override val id: String) : MetadataProvider {
    override val displayName = id

    open override fun cached(game: Game): GameMeta? = null

    open override suspend fun fetch(game: Game): GameMeta? = GameMeta(title = game.label)
}
