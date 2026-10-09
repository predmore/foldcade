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
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialAccess
import app.foldcade.api.plugin.CredentialDenied
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.api.plugin.SyncResult
import app.foldcade.plugins.sample.SampleEntry
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.Collections
import java.util.Enumeration
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
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
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

        val intent = host.launchIntent(
            "sample.player",
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
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        runBlocking { host.load(SampleEntry::class.java.classLoader) }
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
            val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
            assertNull(host.library("outoftree.library"))
            runBlocking { host.load(loader) }

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
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
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
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
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
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
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
        assertEquals("nintendo-3ds", host.platform("3DS")?.id)
        assertEquals("nintendo-3ds", host.platform("N3DS")?.id)
        assertNull(host.platform("nds"))
        assertTrue(host.playersFor("nds").isEmpty())
    }

    @Test
    fun pluginEntryListsAreJvmDefaultMethods() {
        val lists = PluginEntry::class.java.methods.single { it.name == "getPlatforms" }
        val major = PluginEntry::class.java.methods.single { it.name == "getApiVersion" }
        val minor = PluginEntry::class.java.methods.single { it.name == "getApiMinor" }
        assertTrue(lists.isDefault)
        assertFalse(major.isDefault)
        assertTrue(minor.isDefault)
    }

    @Test
    fun platformIdAndAliasCollisionsAreRejected() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
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
            PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).register(platformEntry("3ds", setOf("3DS")))
        }
        assertTrue(repeatsOwnId.exceptionOrNull() is IllegalStateException)

        val sharedAlias = runCatching {
            PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).register(object : PluginEntry {
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
    fun thirdPartyCannotClaimReservedRommIds() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val library = runCatching {
            host.register(object : PluginEntry {
                override val apiVersion = PLUGIN_API_VERSION
                override val libraries = listOf(LibraryFake("romm"))
            })
        }
        assertTrue(library.exceptionOrNull() is IllegalStateException)
        assertNull(host.library("romm"))

        val metadata = runCatching {
            host.register(object : PluginEntry {
                override val apiVersion = PLUGIN_API_VERSION
                override val metadataProviders = listOf(MetadataFake("romm.metadata"))
            })
        }
        assertTrue(metadata.exceptionOrNull() is IllegalStateException)
        assertNull(host.metadata("romm.metadata"))

        val player = runCatching {
            host.register(object : PluginEntry {
                override val apiVersion = PLUGIN_API_VERSION
                override val players = listOf(object : Player by SampleAliasPlayer() {
                    override val id = "romm"
                })
            })
        }
        assertTrue(player.exceptionOrNull() is IllegalStateException)
        assertNull(host.player("romm"))

        val mixed = runCatching {
            host.register(object : PluginEntry {
                override val apiVersion = PLUGIN_API_VERSION
                override val libraries = listOf(LibraryFake("kept.out"))
                override val metadataProviders = listOf(MetadataFake("romm.metadata"))
            })
        }
        assertTrue(mixed.exceptionOrNull() is IllegalStateException)
        assertNull(host.library("kept.out"))

        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val libraries = listOf(LibraryFake("local.folder"))
        })
        assertEquals("local.folder", host.library("local.folder")?.id)
    }

    @Test
    fun olderMinorLoadsAndANewerMinorIsRejected() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val older = object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val libraries = listOf(LibraryFake("older.minor"))
        }
        assertEquals(0, older.apiMinor)
        host.register(older)
        assertEquals("older.minor", host.library("older.minor")?.id)

        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val libraries = listOf(LibraryFake("same.minor"))
        })
        assertEquals("same.minor", host.library("same.minor")?.id)

        val newer = runCatching {
            host.register(object : PluginEntry {
                override val apiVersion = PLUGIN_API_VERSION
                override val apiMinor = PLUGIN_API_MINOR + 1
                override val libraries = listOf(LibraryFake("newer.minor"))
            })
        }
        assertTrue(newer.exceptionOrNull() is IllegalStateException)
        assertNull(host.library("newer.minor"))
        assertEquals("older.minor", host.library("older.minor")?.id)
    }

    @Test
    fun aThrowingGetterDoesNotLeaveThePluginHalfRegistered() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val failure = runCatching {
            host.register(object : PluginEntry {
                override val apiVersion = PLUGIN_API_VERSION
                override val players = listOf(SampleAliasPlayer())
                override val libraries: List<LibraryBackend>
                    get() = throw IllegalStateException("library list failed")
            })
        }
        assertTrue(failure.exceptionOrNull() is IllegalStateException)
        assertNull(host.player("n3ds-player"))
        assertTrue(host.library("unused") == null)
    }

    @Test(timeout = 5_000)
    fun loadKeepsLaterPluginsWhenOneIsRejected() {
        val service = File.createTempFile("foldcade-plugins", ".services")
        service.writeText(
            listOf(
                IsolatedGood::class.java.name,
                "missing.plugin.DoesNotExist",
                IsolatedBadMajor::class.java.name,
                IsolatedThrows::class.java.name,
                IsolatedGoodTwo::class.java.name,
            ).joinToString("\n"),
        )
        val loader = object : ClassLoader(IsolatedGood::class.java.classLoader) {
            override fun getResources(name: String): Enumeration<URL> {
                if (name == "META-INF/services/${PluginEntry::class.java.name}") {
                    return Collections.enumeration(listOf(service.toURI().toURL()))
                }
                return super.getResources(name)
            }
        }
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        runBlocking { host.load(loader) }
        assertEquals("good.one", host.library("good.one")?.id)
        assertEquals("good.two", host.library("good.two")?.id)
        assertNull(host.library("bad.major"))
        assertNull(host.player("throws.player"))
        assertTrue(host.rejected.any { it.reason.contains("DoesNotExist") })
        assertTrue(host.rejected.any { it.plugin == IsolatedBadMajor::class.java.name })
        assertTrue(host.rejected.any { it.plugin == IsolatedThrows::class.java.name })
        assertEquals(3, host.rejected.size)
    }

    @Test
    fun pluginCallsKeepPluginExceptionAndCancellationAndWrapTheRest() = runBlocking {
        val backend = object : LibraryFake("boom.library") {
            override suspend fun connect() {
                throw PluginException.NotFound("gone")
            }

            override suspend fun listGames(platformId: String, query: GameQuery): GamePage {
                throw IllegalStateException("disk")
            }
        }
        val provider = object : MetadataFake("boom.meta") {
            override fun cached(game: Game): GameMeta = throw IllegalStateException("cache")
        }
        val player = object : Player by SampleAliasPlayer() {
            override val id = "boom.player"
            override fun launchIntent(request: LaunchRequest) = throw IllegalStateException("intent")
        }
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val libraries = listOf(backend)
            override val metadataProviders = listOf(provider)
            override val players = listOf(player)
        })

        val missing = runCatching { host.connect(backend.id) }
        assertTrue(missing.exceptionOrNull() is PluginException.NotFound)

        val listed = runCatching { host.listGames(backend.id, "sample", GameQuery()) }
        val wrapped = listed.exceptionOrNull() as PluginCallException
        assertEquals(backend.id, wrapped.pluginId)
        assertTrue(wrapped.cause is IllegalStateException)

        val cached = runCatching { host.cachedMetadata(provider.id, game) }
        assertEquals(provider.id, (cached.exceptionOrNull() as PluginCallException).pluginId)

        val intent = runCatching {
            host.launchIntent(
                player.id,
                LaunchRequest(
                    game = game,
                    target = LaunchTarget.ContentUri(uri = game.remoteKey),
                    resolvedPackage = "app.sample",
                ),
            )
        }
        assertEquals(player.id, (intent.exceptionOrNull() as PluginCallException).pluginId)
    }

    @Test
    fun platformAndPlayersForUseNamesStoredAtRegistration() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val reads = AtomicInteger()
        val platform = object : Platform {
            override val id: String
                get() {
                    reads.incrementAndGet()
                    return "nintendo-3ds"
                }
            override val displayName = "Nintendo 3DS"
            override val extensions = setOf("cci")
            override val aliases: Set<String>
                get() {
                    reads.incrementAndGet()
                    return setOf("3ds", "n3ds")
                }
        }
        val player = object : Player by SampleAliasPlayer() {
            override val platformId: String
                get() {
                    reads.incrementAndGet()
                    return "nintendo-3ds"
                }
        }
        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val platforms = listOf(platform)
            override val players = listOf(player)
        })
        val afterRegister = reads.get()
        assertTrue(afterRegister > 0)
        assertTrue(host.platform("3DS") === platform)
        assertTrue(host.platform("n3ds") === platform)
        assertNull(host.platform("nds"))
        assertEquals(listOf(player), host.playersFor("3ds"))
        assertEquals(listOf(player), host.playersFor("NINTENDO-3DS"))
        assertTrue(host.playersFor("nds").isEmpty())
        assertEquals(afterRegister, reads.get())
    }

    @Test
    fun outOfMemoryFromAPluginCallIsRethrown() = runBlocking {
        val backend = object : LibraryFake("oom.library") {
            override suspend fun connect() {
                throw OutOfMemoryError("simulated")
            }
        }
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val libraries = listOf(backend)
        })
        val failure = runCatching { host.connect(backend.id) }
        assertTrue(failure.exceptionOrNull() is OutOfMemoryError)
    }

    @Test
    fun outOfMemoryFromLoadIsRethrown() = runBlocking {
        val loader = object : ClassLoader(null) {
            override fun getResources(name: String): Enumeration<URL> {
                throw OutOfMemoryError("simulated")
            }
        }
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val failure = runCatching { host.load(loader) }
        assertTrue(failure.exceptionOrNull() is OutOfMemoryError)
        assertTrue(host.rejected.isEmpty())
    }

    @Test
    fun wrappedOutOfMemoryFromAProviderIsRethrown() = runBlocking {
        val service = File.createTempFile("foldcade-oom", ".services")
        service.writeText(IsolatedOutOfMemory::class.java.name)
        val loader = object : ClassLoader(IsolatedOutOfMemory::class.java.classLoader) {
            override fun getResources(name: String): Enumeration<URL> {
                if (name == "META-INF/services/${PluginEntry::class.java.name}") {
                    return Collections.enumeration(listOf(service.toURI().toURL()))
                }
                return super.getResources(name)
            }
        }
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val failure = runCatching { host.load(loader) }
        assertTrue(failure.exceptionOrNull() is OutOfMemoryError)
        assertTrue(host.rejected.isEmpty())
    }

    @Test(timeout = 5_000)
    fun loadRunsOnTheHostDispatcher() = runBlocking {
        val caller = namedDispatcher("caller")
        val io = namedDispatcher("foldcade-io")
        try {
            val host = PluginHost(io, MemoryCredentialStore())
            val loader = object : ClassLoader(IsolatedGood::class.java.classLoader) {
                @Volatile
                var threadName: String? = null

                override fun getResources(name: String): Enumeration<URL> {
                    if (name == "META-INF/services/${PluginEntry::class.java.name}") {
                        threadName = Thread.currentThread().name
                        return Collections.enumeration(emptyList())
                    }
                    return super.getResources(name)
                }
            }
            withContext(caller) { host.load(loader) }
            assertTrue(loader.threadName!!.startsWith("foldcade-io"))
        } finally {
            caller.close()
            io.close()
        }
    }

    @Test
    fun duplicateIdInsideOneSlotIsRejected() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
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
            val host = PluginHost(io, MemoryCredentialStore())
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
            val host = PluginHost(io, MemoryCredentialStore())
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
        val host = PluginHost(Dispatchers.Default, MemoryCredentialStore())
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

    @Test
    fun bindRefusesAnotherPluginsCredentials() = runBlocking {
        val store = MemoryCredentialStore()
        val host = PluginHost(Dispatchers.Unconfined, store)
        val entry = object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val libraries: List<LibraryBackend> = listOf(LibraryFake("library.a"))
            var access: CredentialAccess? = null
            override fun bind(access: CredentialAccess) {
                this.access = access
            }
        }
        host.register(entry)
        val access = entry.access ?: error("bind was not called")
        access.open("library.a").put("token", Credential.ApiToken("rmm_a"))
        try {
            access.open("library.b")
            error("opened another plugin")
        } catch (denied: CredentialDenied) {
            assertEquals("library.b", denied.pluginId)
        }
        val found = store.lookup("library.a", "token") as CredentialLookup.Present
        assertEquals("rmm_a", (found.credential as Credential.ApiToken).value)
        assertFalse(found.credential.toString().contains("rmm_a"))
    }

    @Test
    fun aThirdPartyEntryCannotClaimReservedRommIds() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val intruder = object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val libraries: List<LibraryBackend> = listOf(LibraryFake("romm"))
            override val metadataProviders: List<MetadataProvider> = listOf(MetadataFake("romm.metadata"))
        }
        val failure = runCatching { host.register(intruder) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("Reserved") == true)
        assertNull(host.library("romm"))
        assertNull(host.metadata("romm.metadata"))
    }
}

class IsolatedGood : PluginEntry {
    override val apiVersion = PLUGIN_API_VERSION
    override val libraries: List<LibraryBackend> = listOf(LibraryFake("good.one"))
}

class IsolatedGoodTwo : PluginEntry {
    override val apiVersion = PLUGIN_API_VERSION
    override val libraries: List<LibraryBackend> = listOf(LibraryFake("good.two"))
}

class IsolatedBadMajor : PluginEntry {
    override val apiVersion = PLUGIN_API_VERSION + 1
    override val libraries: List<LibraryBackend> = listOf(LibraryFake("bad.major"))
}

class IsolatedOutOfMemory : PluginEntry {
    init {
        throw OutOfMemoryError("simulated")
    }

    override val apiVersion = PLUGIN_API_VERSION
}

class IsolatedThrows : PluginEntry {
    override val apiVersion = PLUGIN_API_VERSION
    override val players: List<Player> = listOf(SampleAliasPlayer().let { source ->
        object : Player by source {
            override val id = "throws.player"
        }
    })
    override val libraries: List<LibraryBackend>
        get() = throw IllegalStateException("library list failed")
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
