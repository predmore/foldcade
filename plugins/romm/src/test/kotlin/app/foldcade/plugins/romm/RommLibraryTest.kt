package app.foldcade.plugins.romm

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.romm.FlushResult
import app.foldcade.romm.Heartbeat
import app.foldcade.romm.KeptBoth
import app.foldcade.romm.LocalSave
import app.foldcade.romm.PlatformSummary
import app.foldcade.romm.RegisteredDevice
import app.foldcade.romm.RomFileSummary
import app.foldcade.romm.RomPage
import app.foldcade.romm.RomQuery
import app.foldcade.romm.RomSummary
import app.foldcade.romm.RommClient
import app.foldcade.romm.RommException
import app.foldcade.romm.RommForbidden
import app.foldcade.romm.RommHttpException
import app.foldcade.romm.RommUnauthorized
import app.foldcade.romm.RommProtocolMismatch
import app.foldcade.romm.RommUnauthenticated
import app.foldcade.romm.RommUnavailable
import app.foldcade.romm.RommUnsupportedServer
import app.foldcade.romm.SaveSyncReport
import app.foldcade.romm.SaveUploadQueue
import app.foldcade.romm.SyncOperation
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RommLibraryTest {
    @Test
    fun slugsStayPutUnlessABuiltInAliasAlreadyClaimsThem() = runBlocking {
        val ops = ScriptedOps()
        ops.onPlatforms = { listOf(platform(3, "3ds"), platform(4, "snes"), platform(5, "nes")) }
        ops.onRoms = { query ->
            val slug = when (query.platformIds.single()) {
                3L -> "3ds"
                4L -> "snes"
                else -> "nes"
            }
            page(rom(slug, query.platformIds.single()))
        }
        val definitions = listOf(aliasPlatform("nintendo-3ds", setOf("3ds", "n3ds")))
        val harness = harness(ops, definitions)
        val listed = harness.library.listPlatforms().map { it.platformId }
        assertEquals(listOf("nintendo-3ds", "snes", "nes"), listed)
        assertEquals("nintendo-3ds", harness.library.listGames("nintendo-3ds", GameQuery()).games.single().platformId)
        assertEquals("snes", harness.library.listGames("snes", GameQuery()).games.single().platformId)
        assertEquals("nes", harness.library.listGames("nes", GameQuery()).games.single().platformId)
        assertTrue(harness.library.listGames("super-nintendo", GameQuery()).games.isEmpty())
    }

    @Test
    fun metadataComesFromTheListedRomAndNotFromAnotherBackend() = runBlocking {
        val ops = ScriptedOps()
        val mario = rom("3ds", 3).copy(
            pathCoverLarge = "/assets/cover/large.jpg",
            pathCoverSmall = "/assets/cover/small.jpg",
        )
        ops.onPlatforms = { listOf(platform(3, "3ds")) }
        ops.onRoms = { page(mario) }
        val harness = harness(ops)
        val game = harness.library.listGames("3ds", GameQuery(text = "mario")).games.single()
        assertEquals("1234", game.remoteKey)
        val meta = harness.metadata.cached(game)
        assertEquals("Super Mario", meta?.title)
        assertEquals(
            "http://romm.example/assets/cover/large.jpg",
            meta?.artwork?.first()?.uri,
        )
        assertFalse(meta!!.artwork.any { it.uri.contains("rmm_") })
        assertEquals(
            "https://cdn.example/cover.jpg?w=200",
            absoluteHttp(
                "http://192.168.1.20",
                "https://user:s3cret-token@cdn.example/cover.jpg?access_token=s3cret-token&w=200",
            ),
        )
        assertEquals(
            "http://192.168.1.20/assets/cover.jpg",
            absoluteHttp("http://192.168.1.20", "/assets/cover.jpg?token=rmm_secret"),
        )
        val stripped = rom("3ds", 3).copy(
            pathCoverLarge = null,
            urlCover = "https://cdn.example/cover.jpg?token=rmm_secret",
        ).toMeta("http://192.168.1.20")
        assertEquals("https://cdn.example/cover.jpg", stripped.artwork.single().uri)
        assertFalse(stripped.artwork.single().uri.contains("rmm_"))
        assertFalse(stripped.artwork.single().uri.contains("s3cret"))
        val other = game.copy(backendId = "local-folder")
        assertNull(harness.metadata.cached(other))
        assertNull(harness.metadata.fetch(other))
        val moonlight = game.copy(backendId = "moonlight")
        assertNull(harness.metadata.cached(moonlight))
        assertNull(harness.metadata.fetch(moonlight))
    }

    @Test
    fun metadataUsesTheRomIdAndSearchesByTitleOnlyWhenThatIdIsUnknown() = runBlocking {
        val ops = ScriptedOps()
        var lookedUp: Long? = null
        var searched: String? = null
        ops.onRom = { id ->
            lookedUp = id
            rom("3ds", 3).copy(id = id, name = "By Id", pathCoverLarge = "/assets/by-id.jpg")
        }
        ops.onRoms = { query ->
            searched = query.searchTerm
            page(rom("3ds", 3).copy(id = 99, name = "By Title", fsName = "by-title.cci"))
        }
        val harness = harness(ops)
        val known = Game(ROMM_LIBRARY_ID, "1234", "3ds", Availability.RemoteOnly, "Wrong Title")
        val byId = harness.metadata.fetch(known)
        assertEquals("By Id", byId?.title)
        assertEquals(1234L, lookedUp)
        assertNull(searched)
        assertEquals(
            "http://romm.example/assets/by-id.jpg",
            byId?.artwork?.first()?.uri,
        )

        val unknown = known.copy(remoteKey = "shelf-copy", label = "By Title")
        val byTitle = harness.metadata.fetch(unknown)
        assertEquals("By Title", byTitle?.title)
        assertEquals("By Title", searched)

        ops.onRom = { throw RommHttpException(404, "missing") }
        val missing = runCatching {
            harness.metadata.fetch(known.copy(remoteKey = "404", label = "Still By Id"))
        }.exceptionOrNull()
        assertTrue(missing is PluginException.NotFound)
        assertEquals("By Title", searched)
    }

    @Test
    fun titleSearchAcceptsOnlyAnExactNormalizedNameOrFsName() = runBlocking {
        val ops = ScriptedOps()
        val harness = harness(ops)
        val shelf = Game(ROMM_LIBRARY_ID, "shelf-copy", "3ds", Availability.RemoteOnly, "By Title")

        ops.onRoms = {
            page(rom("3ds", 3).copy(id = 99, name = "By  Title", fsName = "other.cci"))
        }
        assertEquals("By  Title", harness.metadata.fetch(shelf)?.title)

        ops.onRoms = {
            page(rom("3ds", 3).copy(id = 100, name = "Cafe\u0301", fsName = "cafe.cci"))
        }
        assertEquals("Cafe\u0301", harness.metadata.fetch(shelf.copy(label = "Caf\u00e9"))?.title)

        ops.onRoms = {
            page(rom("3ds", 3).copy(id = 101, name = "Something Else", fsName = "by-title.cci"))
        }
        assertEquals("Something Else", harness.metadata.fetch(shelf.copy(label = "By-Title.CCI"))?.title)

        ops.onRoms = {
            page(rom("3ds", 3).copy(id = 102, name = "Super Mario", fsName = "mario.cci"))
        }
        assertNull(harness.metadata.fetch(shelf.copy(label = "Mario")))
        assertNull(harness.metadata.fetch(shelf.copy(label = "Super Mario Bros")))
        assertNull(harness.metadata.fetch(shelf.copy(label = "mario")))
        assertNull(harness.metadata.cached(shelf.copy(remoteKey = "102")))

        ops.onRoms = {
            RomPage(
                listOf(
                    rom("3ds", 3).copy(id = 103, name = "By Title Deluxe", fsName = "deluxe.cci"),
                    rom("3ds", 3).copy(
                        id = 104,
                        name = "By Title",
                        fsName = "exact.cci",
                        pathCoverLarge = "/assets/exact.jpg",
                    ),
                ),
                total = 2,
                limit = 50,
                offset = 0,
            )
        }
        val exact = harness.metadata.fetch(shelf)
        assertEquals("By Title", exact?.title)
        assertEquals("http://romm.example/assets/exact.jpg", exact?.artwork?.single()?.uri)
        assertNull(harness.metadata.cached(shelf.copy(remoteKey = "103")))

        ops.onRoms = {
            page(
                rom("3ds", 3).copy(
                    id = 105,
                    name = "Other",
                    fsName = "other.cci",
                    alternativeNames = listOf("By Title"),
                ),
            )
        }
        assertNull(harness.metadata.fetch(shelf))
    }

    @Test
    fun clientErrorsMapAndCancellationIsRethrown() = runBlocking {
        val mismatch = RommProtocolMismatch("6.0.0", 5).toPluginException()
        assertTrue(mismatch is PluginException.ProtocolMismatch)
        assertEquals("6.0.0", (mismatch as PluginException.ProtocolMismatch).serverVersion)
        assertTrue(RommHttpException(404, "missing").toPluginException() is PluginException.NotFound)
        assertTrue(RommHttpException(401, "no").toPluginException() is PluginException.NotAuthenticated)
        assertTrue(RommUnauthorized("no").toPluginException() is PluginException.NotAuthenticated)
        assertTrue(RommForbidden("no").toPluginException() is PluginException.NotAuthenticated)
        assertTrue(RommUnauthenticated().toPluginException() is PluginException.NotAuthenticated)
        assertTrue(RommUnavailable("down").toPluginException() is PluginException.Unavailable)
        val old = RommUnsupportedServer("5.3.1", "too old").toPluginException()
        assertEquals("5.3.1", (old as PluginException.ProtocolMismatch).serverVersion)

        val ops = ScriptedOps()
        val harness = harness(ops)
        ops.onPlatforms = { throw RommUnavailable("offline") }
        val failure = runCatching { harness.library.listPlatforms() }.exceptionOrNull()
        assertTrue(failure is PluginException.Unavailable)
        assertFalse(failure is CancellationException)

        val started = CompletableDeferred<Unit>()
        ops.onPlatforms = {
            started.complete(Unit)
            kotlinx.coroutines.suspendCancellableCoroutine<List<PlatformSummary>> { }
        }
        var seen: Throwable? = null
        val job = launch {
            try {
                harness.library.listPlatforms()
            } catch (error: Throwable) {
                seen = error
            }
        }
        withTimeout(2_000) { started.await() }
        job.cancelAndJoin()
        assertTrue(seen is CancellationException)
        assertFalse(seen is PluginException)
    }

    @Test
    fun keptBothAndQueuedMapOntoSyncResults() {
        val path = Files.createTempFile("save", ".sav")
        val kept = SaveSyncReport(
            sessionId = 1,
            serverVersion = "5.4.0",
            downloaded = emptyList(),
            uploadedSaveIds = emptyList(),
            keptBoth = listOf(KeptBoth(1, "main", 2, 3, path)),
            deleted = emptyList(),
            queued = emptyList(),
            failed = emptyList(),
            completedSession = true,
        )
        assertEquals(SyncOutcome.KeptBoth, kept.toResult().outcome)
        assertEquals("Kept both copies", kept.toResult().note)
        val queued = kept.copy(keptBoth = emptyList(), queued = listOf("main.sav"))
        assertEquals(SyncOutcome.Queued, queued.toResult().outcome)
    }

    @Test
    fun downloadUsesPurposePlayAndAContentUri() = runBlocking {
        val server = TinyServer()
        val cache = Files.createTempDirectory("romm-play")
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS","display_name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/roms/1234/content/mario.cci") { exchange, _ ->
                bytes(exchange, 200, "hello-rom".toByteArray())
            }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val target = library.ensureLocal(game) as LaunchTarget.ContentUri
            assertTrue(target.uri.startsWith("content://app.foldcade.romm.cache/romm/"))
            assertTrue(target.uri.endsWith("mario.cci"))
            val hit = server.recorded.single { it.path.endsWith("/mario.cci") }
            assertTrue(hit.query.orEmpty().contains("purpose=play"))
            assertFalse(hit.query.orEmpty().contains("format"))
            assertEquals("Bearer rmm_test", hit.header("Authorization"))
            assertFalse(cacheContains(cache, "rmm_test"))
        } finally {
            server.close()
        }
    }

    @Test
    fun olderServerStillLaunchesAndSaveSyncStaysInsideTheClient() = runBlocking {
        val server = TinyServer()
        val cache = Files.createTempDirectory("romm-old")
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("POST", "/api/devices") { exchange, _ ->
                json(exchange, 201, """{"device_id":"device-1"}""")
            }
            server.route("GET", "/openapi.json") { exchange, _ ->
                json(exchange, 200, """{"openapi":"3.1.0","info":{"title":"RomM API","version":"5.3.1"}}""")
            }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val spec = rom("3ds", 3).bytes()
            val cached = RommClient.romCacheFile(cache, 1234, spec.fileName, spec.fileIds)
            Files.createDirectories(cached.parent)
            Files.write(cached, "hello-rom".toByteArray())
            val placement = library.prepareLaunch(game, NamedPlayer("azahar"))
            assertTrue(placement.target is LaunchTarget.ContentUri)
            assertEquals(SyncOutcome.Unchanged, placement.sync.outcome)
            assertTrue(placement.sync.note.orEmpty().contains("5.3.1"))
            assertTrue(server.recorded.none { it.path.contains("/sync/negotiate") })
            assertTrue(server.recorded.none { it.path.contains("/content/") })
        } finally {
            server.close()
        }
    }

    @Test(timeout = 8_000)
    fun cancellingABrowseStopsTheClientCall() = runBlocking {
        val server = TinyServer()
        val release = AtomicBoolean(false)
        val started = CompletableDeferred<Unit>()
        server.route("GET", "/api/platforms") { exchange, _ ->
            started.complete(Unit)
            while (!release.get()) Thread.sleep(20)
            json(exchange, 200, "[]")
        }
        val cache = Files.createTempDirectory("romm-cancel")
        try {
            val library = live(server, cache)
            var seen: Throwable? = null
            val job = launch {
                try {
                    library.listPlatforms()
                } catch (error: Throwable) {
                    seen = error
                }
            }
            withTimeout(2_000) { started.await() }
            job.cancelAndJoin()
            assertTrue(seen is CancellationException)
            assertFalse(seen is PluginException)
        } finally {
            release.set(true)
            server.close()
        }
    }

    @Test
    fun aReplacedConnectionStaysOpenUntilTheInFlightCallFinishes() = runBlocking {
        val closes = AtomicInteger()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = ScriptedOps()
        first.onClose = { closes.incrementAndGet() }
        first.onPlatforms = {
            started.complete(Unit)
            release.await()
            emptyList()
        }
        val second = ScriptedOps()
        second.onClose = { closes.incrementAndGet() }
        var token = "rmm_test"
        val cache = Files.createTempDirectory("romm-race")
        val wiring = RommWiring("http://romm.example", RommTokenSource { token }, cache)
        var opened = 0
        wiring.open = { _, _ ->
            opened += 1
            if (opened == 1) first else second
        }
        val library = RommLibrary(RommGate { wiring })
        val job = launch { library.listPlatforms() }
        withTimeout(2_000) { started.await() }
        token = "rmm_next"
        assertTrue(library.listPlatforms().isEmpty())
        assertEquals(0, closes.get())
        release.complete(Unit)
        job.join()
        assertEquals(1, closes.get())
        library.disconnect()
        assertEquals(2, closes.get())
    }

    @Test
    fun disconnectClosesOnceAfterEveryInFlightCallDrains() = runBlocking {
        val closes = AtomicInteger()
        val arrived = AtomicInteger()
        val both = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val ops = ScriptedOps()
        ops.onClose = { closes.incrementAndGet() }
        ops.onPlatforms = {
            if (arrived.incrementAndGet() == 2) both.complete(Unit)
            release.await()
            emptyList()
        }
        val cache = Files.createTempDirectory("romm-drain")
        val wiring = RommWiring("http://romm.example", RommTokenSource { "rmm_test" }, cache)
        wiring.open = { _, _ -> ops }
        val library = RommLibrary(RommGate { wiring })
        val first = launch { library.listPlatforms() }
        val second = launch { library.listPlatforms() }
        withTimeout(2_000) { both.await() }
        library.disconnect()
        assertEquals(0, closes.get())
        release.complete(Unit)
        first.join()
        second.join()
        assertEquals(1, closes.get())
    }

    @Test
    fun aTrashedFileInsideASlotIsNotASave() {
        val cache = Files.createTempDirectory("romm-trash")
        try {
            val slot = cache.resolve("saves").resolve("7").resolve("autosave")
            Files.createDirectories(slot.resolve(".trash"))
            Files.writeString(slot.resolve("keep.srm"), "keep")
            Files.writeString(slot.resolve(".trash").resolve("gone.srm"), "gone")
            val listed = localSaves(cache, 7, "azahar")
            assertEquals(listOf("keep.srm"), listed.map { it.fileName })
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @Test
    fun pairingReturnsTheTokenAndDoesNotWriteIt() = runBlocking {
        val server = TinyServer()
        val cache = Files.createTempDirectory("romm-pair")
        try {
            server.route("GET", "/api/heartbeat") { exchange, _ ->
                json(exchange, 200, """{"SYSTEM":{"VERSION":"5.4.0"}}""")
            }
            server.route("POST", "/api/auth/device/init") { exchange, _ ->
                json(
                    exchange,
                    201,
                    """{"device_code":"dev","user_code":"WDJB-MJHT","verification_path":"/pair","verification_path_complete":"/pair?user_code=WDJB-MJHT","expires_in":600,"interval":5}""",
                )
            }
            server.route("POST", "/api/auth/device/token") { exchange, _ ->
                json(
                    exchange,
                    200,
                    """{"access_token":"rmm_secret","device_id":"device-1","scopes":["roms.read","platforms.read"]}""",
                )
            }
            val wiring = RommWiring(server.origin, RommTokenSource { null }, cache)
            val pairing = RommPairing(wiring)
            val challenge = pairing.begin("device-identifier")
            assertEquals("WDJB-MJHT", challenge.userCode)
            assertTrue(challenge.verificationUrl.contains("WDJB-MJHT"))
            val approved = pairing.poll(challenge.deviceCode) as RommPairingPoll.Approved
            assertEquals("rmm_secret", approved.accessToken)
            assertFalse(approved.scopes.contains("roms.user.read"))
            assertFalse(approved.scopes.contains("collections.read"))
            val init = server.recorded.single { it.path == "/api/auth/device/init" }
            val body = init.body.toString(Charsets.UTF_8)
            assertFalse(body.contains("roms.user.read"))
            assertFalse(body.contains("collections.read"))
            assertFalse(body.contains("password"))
            assertFalse(cacheContains(cache, "rmm_secret"))
            assertNull(wiring.tokenSource.accessToken())
            assertEquals("device-1", wiring.rememberedDevice?.deviceId)
            assertEquals(wiring.clientVersion, wiring.rememberedDevice?.clientVersion)
            assertFalse(cacheContains(cache, "device-1"))
        } finally {
            server.close()
        }
    }
}

private fun harness(ops: ScriptedOps, platforms: List<Platform> = emptyList()): Harness {
    val cache = Files.createTempDirectory("romm-script")
    val wiring = RommWiring(
        origin = "http://romm.example",
        tokenSource = RommTokenSource { "rmm_test" },
        cacheRoot = cache,
        platforms = platforms,
    )
    wiring.open = { _, _ -> ops }
    val gate = RommGate { wiring }
    return Harness(wiring, RommLibrary(gate), RommMetadata(gate))
}

private fun live(server: TinyServer, cache: Path): RommLibrary {
    val wiring = RommWiring(server.origin, RommTokenSource { "rmm_test" }, cache)
    return RommLibrary(RommGate { wiring })
}

private class Harness(
    val wiring: RommWiring,
    val library: RommLibrary,
    val metadata: RommMetadata,
)

private fun platform(id: Long, slug: String) = PlatformSummary(
    id = id,
    name = slug,
    displayName = slug,
    slug = slug,
    fsSlug = slug,
    abbreviation = slug,
    alternativeNames = emptyList(),
    romCount = 1,
)

private fun rom(slug: String, platformId: Long) = RomSummary(
    id = 1234,
    name = "Super Mario",
    fsName = "mario.cci",
    fsExtension = "cci",
    fsSizeBytes = 9,
    platformId = platformId,
    platformSlug = slug,
    platformFsSlug = slug,
    platformDisplayName = slug,
    alternativeNames = emptyList(),
    pathCoverSmall = null,
    pathCoverLarge = null,
    urlCover = null,
    hasSimpleSingleFile = true,
    hasNestedSingleFile = false,
    hasMultipleFiles = false,
    files = listOf(RomFileSummary(7, "mario.cci", 9)),
)

private fun page(rom: RomSummary) = RomPage(listOf(rom), total = 1, limit = 50, offset = 0)

private fun aliasPlatform(id: String, aliases: Set<String>) = object : Platform {
    override val id = id
    override val displayName = id
    override val extensions = emptySet<String>()
    override val aliases = aliases
}

private class NamedPlayer(override val id: String) : Player {
    override val displayName = id
    override val platformId = "nintendo-3ds"
    override val packageNames = emptyList<String>()
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.Primary
    override val occupiesBothDisplays = true
    override val requiresImportedGame = false
    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()
    override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
}

private class ScriptedOps : RommOps {
    var onPlatforms: suspend () -> List<PlatformSummary> = { emptyList() }
    var onRoms: suspend (RomQuery) -> RomPage = { query -> RomPage(emptyList(), 0, query.limit, query.offset) }
    var onRom: suspend (Long) -> RomSummary = { id -> error("rom $id") }
    var onClose: () -> Unit = {}
    var onRegister: suspend (RegisteredDevice?, String, String) -> RegisteredDevice =
        { _, _, version -> RegisteredDevice("device-1", version) }
    var onSync: suspend (String, List<LocalSave>, List<Long>, List<String>) -> SaveSyncReport =
        { _, _, _, _ -> error("sync") }

    override suspend fun heartbeat() = Heartbeat("5.4.0")
    override suspend fun platforms() = onPlatforms()
    override suspend fun roms(query: RomQuery) = onRoms(query)
    override suspend fun rom(id: Long) = onRom(id)
    override suspend fun downloadRom(
        romId: Long,
        fileName: String,
        cacheRoot: Path,
        fileIds: List<Long>,
        expectedSize: Long?,
    ): Path = error("download")

    override suspend fun registerDevice(stored: RegisteredDevice?, name: String, clientVersion: String) =
        onRegister(stored, name, clientVersion)

    override suspend fun syncSaves(
        deviceId: String,
        saves: List<LocalSave>,
        romIds: List<Long>,
        emulators: List<String>,
        destination: (SyncOperation) -> Path,
        queue: SaveUploadQueue,
    ) = onSync(deviceId, saves, romIds, emulators)

    override suspend fun beginDeviceAuth(clientDeviceIdentifier: String, name: String, clientVersion: String) =
        error("begin")

    override suspend fun pollDeviceToken(deviceCode: String) = error("poll")
    override fun verificationUrl(challenge: app.foldcade.romm.DeviceAuthChallenge) = error("url")
    override suspend fun flushUploads(queue: SaveUploadQueue) = FlushResult(0, 0)
    override fun close() = onClose()
}

private class TinyServer : AutoCloseable {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val routes = mutableListOf<Route>()
    val recorded: MutableList<Hit> = java.util.Collections.synchronizedList(mutableListOf())
    val origin: String

    init {
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes()
            recorded += Hit(
                exchange.requestMethod,
                exchange.requestURI.path,
                exchange.requestURI.rawQuery,
                body,
                exchange.requestHeaders.entries.associate { it.key to it.value.toList() },
            )
            val route = routes.lastOrNull { it.method == exchange.requestMethod && it.path == exchange.requestURI.path }
            if (route == null) {
                val msg = "unmapped ${exchange.requestMethod} ${exchange.requestURI.path}".toByteArray()
                exchange.sendResponseHeaders(500, msg.size.toLong())
                exchange.responseBody.use { it.write(msg) }
            } else {
                route.handler(exchange, body)
            }
        }
        server.start()
        origin = "http://127.0.0.1:${server.address.port}"
    }

    fun route(method: String, path: String, handler: (HttpExchange, ByteArray) -> Unit) {
        routes += Route(method, path, handler)
    }

    override fun close() {
        server.stop(0)
    }

    private data class Route(
        val method: String,
        val path: String,
        val handler: (HttpExchange, ByteArray) -> Unit,
    )
}

private class Hit(
    val path: String,
    val query: String?,
    val body: ByteArray,
    private val headers: Map<String, List<String>>,
) {
    constructor(
        method: String,
        path: String,
        query: String?,
        body: ByteArray,
        headers: Map<String, List<String>>,
    ) : this(path, query, body, headers)

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

private fun json(exchange: HttpExchange, status: Int, text: String) {
    val body = text.toByteArray(Charsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, body.size.toLong())
    exchange.responseBody.use { it.write(body) }
}

private fun bytes(exchange: HttpExchange, status: Int, body: ByteArray) {
    exchange.responseHeaders.add("Content-Type", "application/octet-stream")
    exchange.sendResponseHeaders(status, body.size.toLong())
    exchange.responseBody.use { it.write(body) }
}

private fun cacheContains(root: Path, needle: String): Boolean {
    if (!Files.exists(root)) return false
    val bytes = needle.toByteArray(Charsets.UTF_8)
    return Files.walk(root).use { stream ->
        stream.filter { Files.isRegularFile(it) }.anyMatch { file ->
            val data = Files.readAllBytes(file)
            indexOf(data, bytes) >= 0
        }
    }
}

private fun indexOf(data: ByteArray, needle: ByteArray): Int {
    if (needle.isEmpty() || data.size < needle.size) return -1
    for (start in 0..data.size - needle.size) {
        var matched = true
        for (offset in needle.indices) {
            if (data[start + offset] != needle[offset]) {
                matched = false
                break
            }
        }
        if (matched) return start
    }
    return -1
}

private const val ROM_PAGE = """
{"items":[{"id":1234,"name":"Super Mario","fs_name":"mario.cci","fs_extension":"cci","fs_size_bytes":9,"platform_id":3,"platform_slug":"3ds","platform_fs_slug":"3ds","platform_display_name":"Nintendo 3DS","alternative_names":[],"path_cover_small":"/assets/cover/small.jpg","path_cover_large":"/assets/cover/large.jpg","url_cover":null,"has_simple_single_file":true,"has_nested_single_file":false,"has_multiple_files":false,"files":[{"id":7,"file_name":"mario.cci","file_size_bytes":9}]}],"total":1,"limit":50,"offset":0}
"""
