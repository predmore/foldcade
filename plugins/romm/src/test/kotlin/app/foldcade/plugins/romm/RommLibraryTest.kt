package app.foldcade.plugins.romm

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.DeviceCodePoll
import app.foldcade.api.plugin.FirmwareOffer
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.Setup
import app.foldcade.api.plugin.GameActivity
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.ArtworkRole
import app.foldcade.api.plugin.Artwork
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.api.plugin.SyncOutcome
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import app.foldcade.romm.DeviceAuthChallenge
import app.foldcade.romm.FirmwareSummary
import app.foldcade.romm.DeviceTokenPoll
import app.foldcade.romm.Heartbeat
import app.foldcade.romm.KeptBoth
import app.foldcade.romm.LocalSave
import app.foldcade.romm.RomProgress
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
import app.foldcade.romm.RommUser
import app.foldcade.romm.RaAchievement
import app.foldcade.romm.RaGameProgress
import app.foldcade.romm.SaveSyncReport
import app.foldcade.romm.SaveLedger
import app.foldcade.romm.SyncOperation
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RommLibraryTest {
    @Test
    fun catalogSnapshotStripsArtworkCredentialsAndCorruptSnapshotsCanRefresh() = runBlocking {
        val ops = ScriptedOps()
        ops.onPlatforms = { listOf(platform(3, "3ds")) }
        ops.onRoms = { page(rom("3ds", 3).copy(
            pathCoverLarge = "/cover.png?token=rmm_secret&ts=1",
            screenshots = listOf("https://user:rmm_secret@romm.example/scene.png?api_key=rmm_secret"),
        )) }
        val harness = harness(ops)
        try {
            harness.library.listPlatforms()
            harness.library.listGames("3ds", GameQuery())
            assertFalse(cacheContains(harness.wiring.dataRoot, "rmm_secret"))
            val file = harness.wiring.data.catalogFile()
            Files.writeString(file, "{broken")
            val fresh = RommLibrary(RommGate { harness.wiring })
            fresh.listPlatforms()
            assertEquals("Super Mario", fresh.listGames("3ds", GameQuery()).games.single().label)
            ops.onHeartbeat = { throw RommUnavailable("offline") }
            val cold = RommLibrary(RommGate { harness.wiring })
            cold.connect()
            assertEquals("Super Mario", cold.listGames("3ds", GameQuery()).games.single().label)
        } finally {
            harness.wiring.cacheRoot.toFile().deleteRecursively()
            harness.wiring.dataRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun cancellingDownloadClearsItsActivityAndDoesNotProduceATarget() = runBlocking {
        val ops = ScriptedOps()
        ops.onPlatforms = { listOf(platform(3, "3ds")) }
        ops.onRoms = { page(rom("3ds", 3)) }
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        ops.onDownload = { _, _, _, _, _, _ ->
            started.complete(Unit)
            try { CompletableDeferred<Path>().await() } finally { finished.complete(Unit) }
        }
        val harness = harness(ops)
        try {
            val game = harness.library.listGames("3ds", GameQuery()).games.single()
            val activities = mutableListOf<GameActivity>()
            harness.library.observeActivity { _, activity -> activities += activity }
            var delivered = false
            val job = launch {
                harness.library.ensureLocal(game)
                delivered = true
            }
            withTimeout(2_000) { started.await() }
            job.cancelAndJoin()
            assertTrue(finished.isCompleted)
            assertFalse(delivered)
            assertEquals(GameActivity.Idle, activities.last())
        } finally {
            harness.wiring.cacheRoot.toFile().deleteRecursively()
            harness.wiring.dataRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun coldOfflineCatalogRetainsTitlesArtAndVerifiedLaunchesThenRefreshes() = runBlocking {
        val ops = ScriptedOps()
        val listed = rom("3ds", 3).copy(pathCoverLarge = "/assets/cover.jpg")
        ops.onPlatforms = { listOf(platform(3, "3ds")) }
        ops.onRoms = { page(listed) }
        val harness = harness(ops)
        try {
            harness.library.listPlatforms()
            val game = harness.library.listGames("3ds", GameQuery()).games.single()
            val spec = listed.bytes()
            val target = RommClient.romCacheFile(harness.wiring.romRoot, listed.id, spec.fileName, spec.fileIds)
            Files.createDirectories(target.parent)
            Files.write(target, ByteArray(9))
            Files.writeString(target.resolveSibling(target.fileName.toString() + ".verified"),
                """{"size":9,"md5":"${app.foldcade.romm.md5File(target)}"}""")
            writeRomSpec(harness.wiring.romRoot, listed.id, spec)
            ops.onHeartbeat = { throw RommUnavailable("offline") }
            ops.onPlatforms = { error("offline listing must use disk") }
            ops.onRoms = { error("offline listing must use disk") }
            // A new gate simulates a new process using the same data roots.
            val gate = RommGate { harness.wiring }
            val cold = RommLibrary(gate)
            cold.connect()
            assertEquals("3ds", cold.listPlatforms().single().platformId)
            val cached = cold.listGames("3ds", GameQuery()).games.single()
            assertEquals(game.label, cached.label)
            assertEquals(Availability.Cached, cached.availability)
            assertEquals("Super Mario", RommMetadata(gate).cached(cached)?.title)
            assertTrue(RommMetadata(gate).cached(cached)!!.artwork.isNotEmpty())
            assertTrue(cold.ensureLocal(cached) is LaunchTarget.ContentUri)

            ops.onHeartbeat = { Heartbeat("5.4.0") }
            ops.onPlatforms = { listOf(platform(3, "3ds")) }
            ops.onRoms = { page(listed.copy(name = "Renamed online")) }
            cold.connect()
            cold.listPlatforms()
            assertEquals("Renamed online", cold.listGames("3ds", GameQuery()).games.single().label)
        } finally {
            harness.library.disconnect()
            harness.wiring.cacheRoot.toFile().deleteRecursively()
            harness.wiring.dataRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun aFinishedRomWithoutItsMarkerStillLaunchesOffline() = runBlocking {
        val ops = ScriptedOps()
        val listed = rom("3ds", 3)
        ops.onPlatforms = { listOf(platform(3, "3ds")) }
        ops.onRoms = { page(listed) }
        val harness = harness(ops)
        try {
            harness.library.listPlatforms()
            val game = harness.library.listGames("3ds", GameQuery()).games.single()
            val spec = listed.bytes()
            val target = RommClient.romCacheFile(harness.wiring.romRoot, listed.id, spec.fileName, spec.fileIds)
            Files.createDirectories(target.parent)
            Files.write(target, ByteArray(9))
            writeRomSpec(harness.wiring.romRoot, listed.id, spec)
            ops.onHeartbeat = { throw RommUnavailable("offline") }
            ops.onDownload = { _, _, _, _, _, _ -> error("offline launch must not download") }
            val cold = RommLibrary(RommGate { harness.wiring })
            cold.connect()
            assertTrue(cold.ensureLocal(game) is LaunchTarget.ContentUri)
            assertTrue(RommClient.isVerifiedRom(target, spec.expectedSize, spec.md5))

            Files.write(target, ByteArray(4))
            Files.deleteIfExists(target.resolveSibling(target.fileName.toString() + ".verified"))
            assertTrue(runCatching { cold.ensureLocal(game) }.isFailure)
            assertFalse(RommClient.isVerifiedRom(target, spec.expectedSize, spec.md5))
        } finally {
            harness.library.disconnect()
            harness.wiring.cacheRoot.toFile().deleteRecursively()
            harness.wiring.dataRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun aMismatchedFileDoesNotShowAsOnDevice() {
        val root = Files.createTempDirectory("rom-badge")
        try {
            val listed = rom("3ds", 3)
            val spec = listed.bytes()
            val target = RommClient.romCacheFile(root, listed.id, spec.fileName, spec.fileIds)
            Files.createDirectories(target.parent)
            Files.write(target, ByteArray(4))
            assertEquals(Availability.RemoteOnly, cacheAvailability(root, listed))
            Files.write(target, ByteArray(9))
            assertEquals(Availability.Cached, cacheAvailability(root, listed))
            val wrongHash = listed.copy(files = listOf(RomFileSummary(7, "mario.cci", 9, md5Hash = "0".repeat(32))))
            assertEquals(Availability.RemoteOnly, cacheAvailability(root, wrongHash))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun aListedGameCarriesRommsSizeForItsDownload() {
        val root = Files.createTempDirectory("rom-size")
        try {
            val listed = rom("3ds", 3).copy(fsSizeBytes = 512L * 1024 * 1024)
            assertEquals(512L * 1024 * 1024, listed.toGame(root, emptyList()).sizeBytes)
            // RomM reports nothing for a ROM it has not sized; the hero then shows no size.
            assertEquals(null, listed.copy(fsSizeBytes = 0).toGame(root, emptyList()).sizeBytes)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun removingADownloadPutsTheGameBackOnTheServerAndKeepsItsSaves() = runBlocking {
        val ops = ScriptedOps()
        val listed = rom("3ds", 3)
        ops.onPlatforms = { listOf(platform(3, "3ds")) }
        ops.onRoms = { page(listed) }
        val harness = harness(ops)
        try {
            harness.library.listPlatforms()
            val spec = listed.bytes()
            val target = RommClient.romCacheFile(harness.wiring.romRoot, listed.id, spec.fileName, spec.fileIds)
            Files.createDirectories(target.parent)
            Files.write(target, ByteArray(9))
            writeRomSpec(harness.wiring.romRoot, listed.id, spec)
            val save = harness.wiring.data.slotFile(listed.id, "main")
            Files.createDirectories(save.parent)
            Files.write(save, "progress".toByteArray())
            val game = harness.library.listGames("3ds", GameQuery()).games.single()
            assertEquals(Availability.Cached, game.availability)

            harness.library.removeLocal(game)

            assertFalse(Files.exists(target.parent))
            assertNull(readRomSpec(harness.wiring.romRoot, listed.id))
            assertEquals("progress", String(Files.readAllBytes(save)))
            assertEquals(Availability.RemoteOnly, harness.library.listGames("3ds", GameQuery()).games.single().availability)
            // Nothing left to delete is not a failure.
            harness.library.removeLocal(game)
        } finally {
            harness.library.disconnect()
            harness.wiring.cacheRoot.toFile().deleteRecursively()
            harness.wiring.dataRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun aDeleteWaitsForTheDownloadUnderWay() = runBlocking {
        val ops = ScriptedOps()
        val listed = rom("3ds", 3)
        ops.onPlatforms = { listOf(platform(3, "3ds")) }
        ops.onRoms = { page(listed) }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        ops.onDownload = { _, fileName, root, ids, _, _ ->
            started.complete(Unit)
            release.await()
            RommClient.romCacheFile(root, listed.id, fileName, ids).also {
                Files.createDirectories(it.parent)
                Files.write(it, ByteArray(9))
            }
        }
        val harness = harness(ops)
        try {
            harness.library.listPlatforms()
            val game = harness.library.listGames("3ds", GameQuery()).games.single()
            val download = launch { harness.library.ensureLocal(game) }
            withTimeout(2_000) { started.await() }
            var deleted = false
            val delete = launch {
                harness.library.removeLocal(game)
                deleted = true
            }
            yield()
            assertFalse(deleted)
            release.complete(Unit)
            withTimeout(2_000) { download.join(); delete.join() }
            assertTrue(deleted)
            assertEquals(Availability.RemoteOnly, harness.library.listGames("3ds", GameQuery()).games.single().availability)
        } finally {
            harness.library.disconnect()
            harness.wiring.cacheRoot.toFile().deleteRecursively()
            harness.wiring.dataRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun offlineFallbackDoesNotHideAuthenticationFailureOrMixServers() = runBlocking {
        val ops = ScriptedOps()
        ops.onPlatforms = { listOf(platform(3, "3ds")) }
        ops.onRoms = { page(rom("3ds", 3)) }
        val harness = harness(ops)
        try {
            harness.library.listPlatforms()
            harness.library.listGames("3ds", GameQuery())
            ops.onHeartbeat = { throw RommUnauthorized("expired") }
            assertTrue(runCatching { RommLibrary(RommGate { harness.wiring }).connect() }.exceptionOrNull() is PluginException.NotAuthenticated)
            val other = RommWiring("https://different.example", RommTokenSource { "rmm_other" }, harness.wiring.cacheRoot, harness.wiring.dataRoot)
            other.open = { _, _ -> ops }
            ops.onHeartbeat = { throw RommUnavailable("offline") }
            assertTrue(runCatching { RommLibrary(RommGate { other }).connect() }.exceptionOrNull() is PluginException.Unavailable)
        } finally {
            harness.wiring.cacheRoot.toFile().deleteRecursively()
            harness.wiring.dataRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun searchesAndInterruptedPaginationKeepThePreviousCompleteOfflineListing() = runBlocking {
        val ops = ScriptedOps()
        val first = rom("3ds", 3)
        val second = first.copy(id = 5678, name = "Luigi")
        ops.onPlatforms = { listOf(platform(3, "3ds").copy(romCount = 2)) }
        ops.onRoms = { RomPage(listOf(first, second), 2, 50, 0) }
        val harness = harness(ops)
        try {
            harness.library.listPlatforms()
            harness.library.listGames("3ds", GameQuery())
            ops.onRoms = { page(first) }
            harness.library.listGames("3ds", GameQuery(text = "Mario"))
            ops.onRoms = { RomPage(listOf(first), 2, 1, 0) }
            harness.library.listGames("3ds", GameQuery(limit = 1))
            ops.onHeartbeat = { throw RommUnavailable("offline") }
            val cold = RommLibrary(RommGate { harness.wiring })
            cold.connect()
            val page1 = cold.listGames("3ds", GameQuery(limit = 1))
            assertEquals(listOf("1234"), page1.games.map { it.remoteKey })
            assertEquals(1, page1.nextOffset)
            assertEquals(2, page1.total)
            assertEquals("5678", cold.listGames("3ds", GameQuery(offset = 1)).games.single().remoteKey)
            assertEquals("Luigi", cold.listGames("3ds", GameQuery(text = "luigi")).games.single().label)
        } finally {
            harness.wiring.cacheRoot.toFile().deleteRecursively()
            harness.wiring.dataRoot.toFile().deleteRecursively()
        }
    }

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
        // RomM 5.3.1 stamps covers with an unencoded date and time.
        assertEquals(
            "https://romm.example/assets/romm/resources/roms/1/9/cover/big.png?ts=2026-10-03%2004:20:36",
            absoluteHttp("https://romm.example", "/assets/romm/resources/roms/1/9/cover/big.png?ts=2026-10-03 04:20:36"),
        )
        assertEquals(
            "http://192.168.1.20/assets/Pok%C3%A9mon%20Blue.png?a=%2Fb&c=d%25",
            absoluteHttp("http://192.168.1.20", "/assets/Pokémon%20Blue.png?a=%2Fb&c=d%"),
        )
        // The server's own cover wins over its scraper's address on another host.
        val stamped = rom("3ds", 3).copy(
            pathCoverLarge = "/assets/cover/big.png?ts=2026-10-03 04:20:36",
            urlCover = "https://images.igdb.com/igdb/image/upload/t_1080p/co1xpx.jpg",
        ).toMeta("https://romm.example")
        assertEquals(
            "https://romm.example/assets/cover/big.png?ts=2026-10-03%2004:20:36",
            stamped.artwork.single { it.role == ArtworkRole.Cover }.uri,
        )
        val stripped = rom("3ds", 3).copy(
            pathCoverLarge = null,
            urlCover = "https://cdn.example/cover.jpg?token=rmm_secret",
        ).toMeta("http://192.168.1.20")
        assertEquals("https://cdn.example/cover.jpg", stripped.artwork.single().uri)
        assertFalse(stripped.artwork.single().uri.contains("rmm_"))
        assertFalse(stripped.artwork.single().uri.contains("s3cret"))
        val shot = rom("3ds", 3).copy(screenshots = listOf("/assets/shots/1.jpg", "/assets/shots/2.jpg"))
            .toMeta("http://192.168.1.20")
        assertEquals(
            listOf(Artwork(ArtworkRole.Screenshot, "http://192.168.1.20/assets/shots/1.jpg")),
            shot.artwork.filter { it.role == ArtworkRole.Screenshot },
        )
        val other = game.copy(backendId = "local-folder")
        assertNull(harness.metadata.cached(other))
        assertNull(harness.metadata.fetch(other))
        val moonlight = game.copy(backendId = "moonlight")
        assertNull(harness.metadata.cached(moonlight))
        assertNull(harness.metadata.fetch(moonlight))
    }

    @Test
    fun achievementsJoinTheUsersProgressOnTheBadgeId() = runBlocking {
        val ops = ScriptedOps()
        ops.onRom = { rom("genesis", 2).copy(raId = 120, achievements = twoAchievements) }
        ops.onMe = {
            RommUser(
                raUsername = "player",
                progress = listOf(RaGameProgress(120, 2, 1, 1, earnedBadgeIds = setOf("25175"))),
            )
        }
        val game = Game(ROMM_LIBRARY_ID, "1234", "genesis", Availability.LocalOnly, "Gunstar")
        val found = harness(ops).metadata.achievements(game)!!
        assertTrue(found.progressKnown)
        assertEquals(listOf("Stage One", "Stage Two"), found.achievements.map { it.title })
        assertEquals(listOf(true, false), found.achievements.map { it.earned })
        // Earned badges in colour, the rest locked, both from the RomM server.
        assertEquals("http://romm.example/badges/25175.png", found.achievements[0].badge)
        assertEquals("http://romm.example/badges/25176_lock.png", found.achievements[1].badge)
    }

    @Test
    fun achievementsWithoutProgressStillListAndNeverAskForASignIn() = runBlocking {
        val ops = ScriptedOps()
        ops.onRom = { rom("genesis", 2).copy(raId = 120, achievements = twoAchievements) }
        val game = Game(ROMM_LIBRARY_ID, "1234", "genesis", Availability.LocalOnly, "Gunstar")
        // The default fake refuses /api/users/me the way a token without me.read is refused.
        val refused = harness(ops).metadata.achievements(game)!!
        assertFalse(refused.progressKnown)
        assertEquals(2, refused.achievements.size)
        assertFalse(refused.achievements.any { it.earned })
        assertEquals("http://romm.example/badges/25176.png", refused.achievements[1].badge)

        ops.onMe = { RommUser(raUsername = null, progress = emptyList()) }
        assertFalse(harness(ops).metadata.achievements(game)!!.progressKnown)

        // Linked, with nothing earned on this game yet, is known progress.
        ops.onMe = { RommUser(raUsername = "player", progress = emptyList()) }
        val none = harness(ops).metadata.achievements(game)!!
        assertTrue(none.progressKnown)
        assertFalse(none.achievements.any { it.earned })
    }

    @Test
    fun aRomWithoutAchievementsAndAnotherLibrarysGameHaveNone() = runBlocking {
        val ops = ScriptedOps()
        ops.onRom = { rom("3ds", 3) }
        val harness = harness(ops)
        assertNull(harness.metadata.achievements(Game(ROMM_LIBRARY_ID, "1234", "3ds", Availability.LocalOnly, "Mario")))
        assertNull(harness.metadata.achievements(Game("local-folder", "1234", "3ds", Availability.LocalOnly, "Mario")))
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
            keptBoth = listOf(KeptBoth(1, "main", 2L, 3, path)),
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
    fun aSwitchFolderDownloadsTheGameAndItsUpdateAsFilesInOneFolder() = runBlocking {
        val server = TinyServer()
        val cache = Files.createTempDirectory("romm-folder")
        val game = "base-game".toByteArray()
        val update = "upd".toByteArray()
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"switch","fs_slug":"switch","rom_count":1,"name":"Nintendo Switch"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, switchFolderPage(game, update)) }
            server.route("GET", "/api/roms/35/content/Fantasy Life i.nsp") { exchange, _ -> bytes(exchange, 200, game) }
            server.route("GET", "/api/roms/35/content/Fantasy Life i [Update v2.2.0].nsp") { exchange, _ ->
                bytes(exchange, 200, update)
            }
            val wiring = RommWiring(
                server.origin,
                RommTokenSource { "rmm_test" },
                cache,
                Files.createTempDirectory("romm-data"),
                contentUris = CachePathContentUri(cache, folderAuthority = "app.foldcade.romm.folders"),
            )
            val library = RommLibrary(RommGate { wiring })
            val heard = java.util.Collections.synchronizedList(mutableListOf<GameActivity>())
            library.observeActivity { _, activity -> heard += activity }
            val listed = library.listGames("switch", GameQuery()).games.single()
            assertEquals(Availability.RemoteOnly, listed.availability)

            val target = library.ensureLocal(listed) as LaunchTarget.ContentUri

            // Each file on its own: asked for both at once, RomM sends a zip.
            val gameHit = server.recorded.single { it.path.endsWith("/Fantasy Life i.nsp") }
            val updateHit = server.recorded.single { it.path.endsWith("[Update v2.2.0].nsp") }
            assertTrue(gameHit.query.orEmpty().contains("file_ids=35"))
            assertFalse(gameHit.query.orEmpty().contains("36"))
            assertTrue(updateHit.query.orEmpty().contains("file_ids=36"))
            assertTrue(target.uri.startsWith("content://app.foldcade.romm.folders/tree/"))
            val gameFile = wiring.contentUris.pathFor(target.uri)!!
            assertEquals("Fantasy Life i.nsp", gameFile.fileName.toString())
            assertArrayEquals(game, Files.readAllBytes(gameFile))
            assertArrayEquals(update, Files.readAllBytes(gameFile.resolveSibling("Fantasy Life i [Update v2.2.0].nsp")))
            // One bar for both files.
            assertEquals(GameActivity.Downloading(12L, 12L), heard.last { it is GameActivity.Downloading })
            assertEquals(Availability.Cached, library.listGames("switch", GameQuery()).games.single().availability)

            // Offline, the kept file list still knows the update.
            val spec = readRomSpec(wiring.romRoot, 35)!!
            assertEquals(listOf(AddOn(36, "Fantasy Life i [Update v2.2.0].nsp", 3, md5Hex(update))), spec.addOns)
            assertEquals(gameFile, verifiedRom(wiring.romRoot, 35, spec))
            val hits = server.recorded.size
            assertEquals(target, library.ensureLocal(listed))
            assertEquals(hits, server.recorded.size)
        } finally {
            server.close()
        }
    }

    @Test
    fun aDownloadAndALaunchSyncTellTheHostWhatTheyAreDoing() = runBlocking {
        val server = TinyServer()
        val cache = Files.createTempDirectory("romm-activity")
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/roms/1234/content/mario.cci") { exchange, _ ->
                bytes(exchange, 200, "hello-rom".toByteArray())
            }
            server.route("POST", "/api/devices") { exchange, _ -> json(exchange, 201, """{"device_id":"device-1"}""") }
            server.route("GET", "/openapi.json") { exchange, _ ->
                json(exchange, 200, """{"openapi":"3.1.0","info":{"title":"RomM API","version":"5.3.1"}}""")
            }
            val library = live(server, cache)
            val heard = java.util.Collections.synchronizedList(mutableListOf<Pair<String, GameActivity>>())
            library.observeActivity { key, activity -> heard += key to activity }
            val game = library.listGames("3ds", GameQuery()).games.single()

            library.ensureLocal(game)
            val download = heard.map { it.second }
            assertTrue(heard.all { it.first == "1234" })
            assertEquals(GameActivity.Downloading(0L, null), download.first())
            assertEquals(GameActivity.Downloading(9L, 9L), download.last { it is GameActivity.Downloading })
            assertEquals(GameActivity.Checking, download.last())

            heard.clear()
            val placement = library.prepareLaunch(game, NamedPlayer("azahar"))
            assertEquals(
                listOf(GameActivity.SyncingSaves, GameActivity.Synced(placement.sync)),
                heard.map { it.second },
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun aFailedDownloadLeavesTheTileIdle() = runBlocking {
        val server = TinyServer()
        val cache = Files.createTempDirectory("romm-activity-fail")
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/roms/1234/content/mario.cci") { exchange, _ -> json(exchange, 500, "{}") }
            val library = live(server, cache)
            val heard = java.util.Collections.synchronizedList(mutableListOf<GameActivity>())
            library.observeActivity { _, activity -> heard += activity }
            val game = library.listGames("3ds", GameQuery()).games.single()
            assertTrue(runCatching { library.ensureLocal(game) }.isFailure)
            assertEquals(GameActivity.Idle, heard.last())
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
            val roms = cache.resolve("servers").resolve(RommData.serverKey(server.origin))
            val cached = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
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

    @Test
    fun aLaunchBringsTheFirmwareThePlayerAsksForAndKeepsItForOffline() = runBlocking {
        val server = TinyServer()
        val cache = Files.createTempDirectory("romm-firmware")
        val bios = "the-platform-bios".toByteArray()
        val firmwareUp = AtomicBoolean(true)
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/firmware") { exchange, _ ->
                if (!firmwareUp.get()) return@route json(exchange, 503, "{}")
                json(
                    exchange,
                    200,
                    """[{"id":9,"platform_id":3,"file_name":"boot9.bin","file_size_bytes":${bios.size},"md5_hash":"${md5Hex(bios)}"},""" +
                        """{"id":10,"platform_id":3,"file_name":"readme.txt","file_size_bytes":4,"md5_hash":null},""" +
                        """{"id":11,"platform_id":3,"file_name":"../boot9.bin","file_size_bytes":4,"md5_hash":null}]""",
                )
            }
            server.route("GET", "/api/firmware/9/content/boot9.bin") { exchange, _ -> bytes(exchange, 200, bios) }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val spec = rom("3ds", 3).bytes()
            val roms = cache.resolve("servers").resolve(RommData.serverKey(server.origin))
            val cached = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
            Files.createDirectories(cached.parent)
            Files.write(cached, "hello-rom".toByteArray())
            val player = BiosPlayer(setOf("boot9.bin"))

            val online = (library.prepareLaunch(game, player).target as LaunchTarget.ContentUri).firmware
            assertEquals(listOf("boot9.bin"), online.map { it.name })
            val kept = CachePathContentUri(cache).pathFor(online.single().contentUri)!!
            assertEquals(roms.resolve("firmware/3/boot9.bin"), kept)
            assertArrayEquals(bios, Files.readAllBytes(kept))
            assertTrue(server.recorded.none { it.path.contains("/firmware/10/") || it.path.contains("/firmware/11/") })
            // A player that asks for none gets none.
            assertTrue((library.prepareLaunch(game, NamedPlayer("azahar")).target as LaunchTarget.ContentUri).firmware.isEmpty())

            firmwareUp.set(false)
            val offline = (library.prepareLaunch(game, player).target as LaunchTarget.ContentUri).firmware
            assertEquals(online, offline)
        } finally {
            server.close()
        }
    }

    @Test
    fun aZipOfFirmwareIsOfferedAsItsFilesAndKeptForOffline() = runBlocking {
        val server = TinyServer()
        val cache = Files.createTempDirectory("romm-firmware-zip")
        val zip = ByteArrayOutputStream().also { raw ->
            ZipOutputStream(raw).use { archive ->
                archive.putNextEntry(ZipEntry("boot.bin"))
                archive.write("the-bios".toByteArray())
                archive.closeEntry()
                archive.putNextEntry(ZipEntry("note.txt"))
                archive.write("skip".toByteArray())
                archive.closeEntry()
            }
        }.toByteArray()
        val firmwareUp = AtomicBoolean(true)
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/firmware") { exchange, _ ->
                if (!firmwareUp.get()) return@route json(exchange, 503, "{}")
                json(
                    exchange,
                    200,
                    """[{"id":9,"platform_id":3,"file_name":"bios.zip","file_size_bytes":${zip.size},"md5_hash":"${md5Hex(zip)}"}]""",
                )
            }
            server.route("GET", "/api/firmware/9/content/bios.zip") { exchange, _ -> bytes(exchange, 200, zip) }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val spec = rom("3ds", 3).bytes()
            val roms = cache.resolve("servers").resolve(RommData.serverKey(server.origin))
            val cached = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
            Files.createDirectories(cached.parent)
            Files.write(cached, "hello-rom".toByteArray())
            val player = BiosPlayer(setOf("bios.zip", "bios.zip/boot.bin"))

            val online = (library.prepareLaunch(game, player).target as LaunchTarget.ContentUri).firmware.map { it.name }
            assertEquals(listOf("bios.zip", "bios.zip/boot.bin"), online.sorted())
            val kept = roms.resolve("firmware/3/bios.zip.unpacked/boot.bin")
            assertArrayEquals("the-bios".toByteArray(), Files.readAllBytes(kept))
            // The other file is unpacked too. The player did not ask for it, so it is not offered.
            assertTrue(Files.exists(roms.resolve("firmware/3/bios.zip.unpacked/note.txt")))

            firmwareUp.set(false)
            val offline = (library.prepareLaunch(game, player).target as LaunchTarget.ContentUri).firmware.map { it.name }
            assertEquals(online.sorted(), offline.sorted())
        } finally {
            server.close()
        }
    }

    @Test(timeout = 30_000)
    fun aFirstLaunchWaitsOutAFirmwareFetch() = runBlocking {
        val server = TinyServer(concurrent = true)
        val cache = Files.createTempDirectory("romm-firmware-first")
        val bios = "the-platform-bios".toByteArray()
        val release = AtomicBoolean(false)
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/firmware") { exchange, _ ->
                json(exchange, 200, """[{"id":9,"platform_id":3,"file_name":"boot9.bin","file_size_bytes":${bios.size},"md5_hash":"${md5Hex(bios)}"}]""")
            }
            server.route("GET", "/api/firmware/9/content/boot9.bin") { exchange, _ ->
                while (!release.get()) Thread.sleep(20)
                bytes(exchange, 200, bios)
            }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val spec = rom("3ds", 3).bytes()
            val roms = cache.resolve("servers").resolve(RommData.serverKey(server.origin))
            val cached = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
            Files.createDirectories(cached.parent)
            Files.write(cached, "hello-rom".toByteArray())
            val player = BiosPlayer(setOf("boot9.bin"))
            val heard = java.util.Collections.synchronizedList(mutableListOf<GameActivity>())
            val synced = CompletableDeferred<Unit>()
            library.observeActivity { _, activity ->
                heard += activity
                if (activity is GameActivity.Synced && heard.count { it is GameActivity.Synced } == 1) synced.complete(Unit)
            }

            // Nothing is checked yet. The download is held past the short wait, and the launch stays with it.
            val pending = async { library.prepareLaunch(game, player) }
            withTimeout(10_000) { synced.await() }
            delay(6_000)
            assertFalse(pending.isCompleted)
            val sync = heard[1] as GameActivity.Synced
            // The tile shows the firmware download through the wait, and has not finished yet.
            assertEquals(
                listOf(GameActivity.SyncingSaves, sync, GameActivity.GettingFirmware(0L, bios.size.toLong())),
                heard.toList(),
            )
            release.set(true)
            val placement = withTimeout(15_000) { pending.await() }
            val firmware = (placement.target as LaunchTarget.ContentUri).firmware
            assertEquals(listOf("boot9.bin"), firmware.map { it.name })
            assertArrayEquals(bios, Files.readAllBytes(roms.resolve("firmware/3/boot9.bin")))
            assertEquals(GameActivity.Synced(placement.sync), sync)
            // Bytes as they arrive, then the hash check, then the same sync mark again.
            assertEquals(
                listOf(
                    GameActivity.SyncingSaves,
                    sync,
                    GameActivity.GettingFirmware(0L, bios.size.toLong()),
                    GameActivity.GettingFirmware(bios.size.toLong(), bios.size.toLong()),
                    GameActivity.Checking,
                    sync,
                ),
                heard.toList(),
            )
        } finally {
            release.set(true)
            server.close()
        }
    }

    @Test(timeout = 20_000)
    fun aPlayerThatWantsNoFirmwareDoesNotWaitOutAStalledListing() = runBlocking {
        val server = TinyServer(concurrent = true)
        val cache = Files.createTempDirectory("romm-firmware-none")
        val release = AtomicBoolean(false)
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/firmware") { exchange, _ ->
                while (!release.get()) Thread.sleep(20)
                json(exchange, 200, """[{"id":9,"platform_id":3,"file_name":"boot9.bin","file_size_bytes":4,"md5_hash":null}]""")
            }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val spec = rom("3ds", 3).bytes()
            val roms = cache.resolve("servers").resolve(RommData.serverKey(server.origin))
            val cached = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
            Files.createDirectories(cached.parent)
            Files.write(cached, "hello-rom".toByteArray())
            // Eden, Azahar, and melonDS name no RomM firmware file.
            val player = NamedPlayer("azahar")
            val heard = java.util.Collections.synchronizedList(mutableListOf<GameActivity>())
            val syncedAt = AtomicLong(0)
            library.observeActivity { _, activity ->
                heard += activity
                if (activity is GameActivity.Synced) syncedAt.compareAndSet(0, System.nanoTime())
            }

            val placement = library.prepareLaunch(game, player)
            val waitedMs = (System.nanoTime() - syncedAt.get()) / 1_000_000
            // The listing never answers. The launch keeps the short wait and shows no download.
            assertTrue("returned after ${waitedMs}ms", waitedMs in 4_000..8_000)
            assertTrue((placement.target as LaunchTarget.ContentUri).firmware.isEmpty())
            assertEquals(
                listOf(GameActivity.SyncingSaves, GameActivity.Synced(placement.sync)),
                heard.toList(),
            )
        } finally {
            release.set(true)
            server.close()
        }
    }

    @Test(timeout = 20_000)
    fun aWaitedFirmwareZipReportsCheckingWhileItUnpacks() = runBlocking {
        val server = TinyServer(concurrent = true)
        val cache = Files.createTempDirectory("romm-firmware-zip-wait")
        val zip = ByteArrayOutputStream().also { raw ->
            ZipOutputStream(raw).use { archive ->
                archive.putNextEntry(ZipEntry("boot.bin"))
                archive.write("the-bios".toByteArray())
                archive.closeEntry()
            }
        }.toByteArray()
        val release = AtomicBoolean(false)
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/firmware") { exchange, _ ->
                json(exchange, 200, """[{"id":9,"platform_id":3,"file_name":"bios.zip","file_size_bytes":${zip.size},"md5_hash":"${md5Hex(zip)}"}]""")
            }
            server.route("GET", "/api/firmware/9/content/bios.zip") { exchange, _ ->
                while (!release.get()) Thread.sleep(20)
                bytes(exchange, 200, zip)
            }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val spec = rom("3ds", 3).bytes()
            val roms = cache.resolve("servers").resolve(RommData.serverKey(server.origin))
            val cached = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
            Files.createDirectories(cached.parent)
            Files.write(cached, "hello-rom".toByteArray())
            val player = BiosPlayer(setOf("bios.zip", "bios.zip/boot.bin"))
            val heard = java.util.Collections.synchronizedList(mutableListOf<GameActivity>())
            val sawDownload = CompletableDeferred<Unit>()
            library.observeActivity { _, activity ->
                heard += activity
                if (activity is GameActivity.GettingFirmware && heard.any { it is GameActivity.Synced }) sawDownload.complete(Unit)
            }

            val pending = async { library.prepareLaunch(game, player) }
            withTimeout(10_000) { sawDownload.await() }
            release.set(true)
            val placement = withTimeout(15_000) { pending.await() }
            val sync = GameActivity.Synced(placement.sync)
            assertEquals(
                listOf(
                    GameActivity.SyncingSaves,
                    sync,
                    GameActivity.GettingFirmware(0L, zip.size.toLong()),
                    GameActivity.GettingFirmware(zip.size.toLong(), zip.size.toLong()),
                    GameActivity.Checking,
                    GameActivity.Checking,
                    sync,
                ),
                heard.toList(),
            )
            assertEquals(listOf("bios.zip", "bios.zip/boot.bin"), (placement.target as LaunchTarget.ContentUri).firmware.map { it.name }.sorted())
        } finally {
            release.set(true)
            server.close()
        }
    }

    @Test(timeout = 25_000)
    fun aSlowFirmwareFetchFallsBackToTheCheckedCopy() = runBlocking {
        val server = TinyServer(concurrent = true)
        val cache = Files.createTempDirectory("romm-firmware-checked")
        val bios = "already-checked".toByteArray()
        val replacement = "a-newer-bios".toByteArray()
        val release = AtomicBoolean(false)
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/firmware") { exchange, _ ->
                json(exchange, 200, """[{"id":9,"platform_id":3,"file_name":"boot9.bin","file_size_bytes":${replacement.size},"md5_hash":"${md5Hex(replacement)}"}]""")
            }
            server.route("GET", "/api/firmware/9/content/boot9.bin") { exchange, _ ->
                while (!release.get()) Thread.sleep(20)
                bytes(exchange, 200, replacement)
            }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val spec = rom("3ds", 3).bytes()
            val roms = cache.resolve("servers").resolve(RommData.serverKey(server.origin))
            val cached = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
            Files.createDirectories(cached.parent)
            Files.write(cached, "hello-rom".toByteArray())
            val kept = roms.resolve("firmware/3/boot9.bin")
            Files.createDirectories(kept.parent)
            Files.write(kept, bios)
            Files.write(kept.resolveSibling("boot9.bin.verified"), """{"size":${bios.size},"md5":"${md5Hex(bios)}"}""".toByteArray())
            // chooseFirmware stays the default, so any one checked file keeps the short wait.
            val player = BiosPlayer(setOf("boot9.bin"))
            val heard = java.util.Collections.synchronizedList(mutableListOf<GameActivity>())
            val syncedAt = AtomicLong(0)
            library.observeActivity { _, activity ->
                heard += activity
                if (activity is GameActivity.Synced) syncedAt.compareAndSet(0, System.nanoTime())
            }

            val placement = library.prepareLaunch(game, player)
            val waitedMs = (System.nanoTime() - syncedAt.get()) / 1_000_000
            // The short wait, not the download. The checked copy is what launches.
            assertTrue("fell back after ${waitedMs}ms", waitedMs in 4_000..15_000)
            val firmware = (placement.target as LaunchTarget.ContentUri).firmware
            assertEquals(listOf("boot9.bin"), firmware.map { it.name })
            assertArrayEquals(bios, Files.readAllBytes(CachePathContentUri(cache).pathFor(firmware.single().contentUri)!!))
            // A checked copy keeps the sync mark and does not show the firmware download.
            assertEquals(
                listOf(GameActivity.SyncingSaves, GameActivity.Synced(placement.sync)),
                heard.toList(),
            )
        } finally {
            release.set(true)
            server.close()
        }
    }

    @Test(timeout = 30_000)
    fun keyFilesCheckedStillWaitForTheBiosDownload() = runBlocking {
        val server = TinyServer(concurrent = true)
        val cache = Files.createTempDirectory("romm-firmware-keys")
        val keys = listOf("civ.bin", "cks.bin", "eks.bin", "kek.bin").associateWith { "$it-key".toByteArray() }
        val bios = "BIOS-usa".toByteArray()
        val member = "ps2-0230a-20080220.bin"
        val zip = zipOf(mapOf(member to bios))
        val release = AtomicBoolean(false)
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/firmware") { exchange, _ ->
                val listed = keys.entries.mapIndexed { index, (name, bytes) -> firmwareItem(index + 1L, name, bytes) }
                    .plus(firmwareItem(9, "PS2_BIOS.zip", zip))
                    .joinToString(",")
                json(exchange, 200, "[$listed]")
            }
            server.route("GET", "/api/firmware/9/content/PS2_BIOS.zip") { exchange, _ ->
                while (!release.get()) Thread.sleep(20)
                bytes(exchange, 200, zip)
            }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val spec = rom("3ds", 3).bytes()
            val roms = cache.resolve("servers").resolve(RommData.serverKey(server.origin))
            val cached = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
            Files.createDirectories(cached.parent)
            Files.write(cached, "hello-rom".toByteArray())
            val firmwareDir = roms.resolve("firmware/3")
            keys.forEach { (name, bytes) -> plantCheckedFile(firmwareDir, name, bytes) }
            val player = ChoosingBiosPlayer(
                wants = keys.keys + setOf("PS2_BIOS.zip", "PS2_BIOS.zip/$member"),
                marker = "BIOS".toByteArray(),
            )
            val heard = java.util.Collections.synchronizedList(mutableListOf<GameActivity>())
            val synced = CompletableDeferred<Unit>()
            library.observeActivity { _, activity ->
                heard += activity
                if (activity is GameActivity.Synced && heard.count { it is GameActivity.Synced } == 1) synced.complete(Unit)
            }

            // The key files are checked, and none of them is a BIOS. The launch stays with the zip.
            val pending = async { library.prepareLaunch(game, player) }
            withTimeout(10_000) { synced.await() }
            delay(6_000)
            assertFalse(pending.isCompleted)
            val total = keys.values.sumOf { it.size.toLong() } + zip.size
            assertTrue(heard.any { it is GameActivity.GettingFirmware && it.totalBytes == total })
            release.set(true)
            val placement = withTimeout(15_000) { pending.await() }
            val firmware = (placement.target as LaunchTarget.ContentUri).firmware
            assertEquals(
                (keys.keys + setOf("PS2_BIOS.zip", "PS2_BIOS.zip/$member")).sorted(),
                firmware.map { it.name }.sorted(),
            )
            val written = firmware.single { it.name == "PS2_BIOS.zip/$member" }
            assertArrayEquals(bios, Files.readAllBytes(CachePathContentUri(cache).pathFor(written.contentUri)!!))
            assertEquals(GameActivity.Synced(placement.sync), heard.last())
        } finally {
            release.set(true)
            server.close()
        }
    }

    @Test(timeout = 25_000)
    fun aCheckedBiosTakesTheShortWait() = runBlocking {
        val server = TinyServer(concurrent = true)
        val cache = Files.createTempDirectory("romm-firmware-bios")
        val keys = listOf("civ.bin", "cks.bin", "eks.bin", "kek.bin").associateWith { "$it-key".toByteArray() }
        val bios = "BIOS-usa".toByteArray()
        val member = "ps2-0230a-20080220.bin"
        val zip = zipOf(mapOf(member to bios))
        val replacement = "a-newer-bios-zip".toByteArray()
        val release = AtomicBoolean(false)
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/firmware") { exchange, _ ->
                val listed = keys.entries.mapIndexed { index, (name, bytes) -> firmwareItem(index + 1L, name, bytes) }
                    .plus(firmwareItem(9, "PS2_BIOS.zip", replacement))
                    .joinToString(",")
                json(exchange, 200, "[$listed]")
            }
            server.route("GET", "/api/firmware/9/content/PS2_BIOS.zip") { exchange, _ ->
                while (!release.get()) Thread.sleep(20)
                bytes(exchange, 200, replacement)
            }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val spec = rom("3ds", 3).bytes()
            val roms = cache.resolve("servers").resolve(RommData.serverKey(server.origin))
            val cached = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
            Files.createDirectories(cached.parent)
            Files.write(cached, "hello-rom".toByteArray())
            val firmwareDir = roms.resolve("firmware/3")
            keys.forEach { (name, bytes) -> plantCheckedFile(firmwareDir, name, bytes) }
            plantCheckedZip(firmwareDir, "PS2_BIOS.zip", zip, mapOf(member to bios))
            val player = ChoosingBiosPlayer(
                wants = keys.keys + setOf("PS2_BIOS.zip", "PS2_BIOS.zip/$member"),
                marker = "BIOS".toByteArray(),
            )
            val heard = java.util.Collections.synchronizedList(mutableListOf<GameActivity>())
            val syncedAt = AtomicLong(0)
            library.observeActivity { _, activity ->
                heard += activity
                if (activity is GameActivity.Synced) syncedAt.compareAndSet(0, System.nanoTime())
            }

            val placement = library.prepareLaunch(game, player)
            val waitedMs = (System.nanoTime() - syncedAt.get()) / 1_000_000
            // The unpacked BIOS is one this player would write, so the short wait stands.
            assertTrue("fell back after ${waitedMs}ms", waitedMs in 4_000..15_000)
            val firmware = (placement.target as LaunchTarget.ContentUri).firmware
            val written = firmware.single { it.name == "PS2_BIOS.zip/$member" }
            assertArrayEquals(bios, Files.readAllBytes(CachePathContentUri(cache).pathFor(written.contentUri)!!))
            assertEquals(
                listOf(GameActivity.SyncingSaves, GameActivity.Synced(placement.sync)),
                heard.toList(),
            )
        } finally {
            release.set(true)
            server.close()
        }
    }

    @Test
    fun aThrowWhileChoosingFirmwareStillLaunchesTheCheckedCopy() = runBlocking {
        val server = TinyServer()
        val cache = Files.createTempDirectory("romm-firmware-choose-throws")
        val key = "civ-key".toByteArray()
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/firmware") { exchange, _ -> json(exchange, 503, "{}") }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val spec = rom("3ds", 3).bytes()
            val roms = cache.resolve("servers").resolve(RommData.serverKey(server.origin))
            val cached = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
            Files.createDirectories(cached.parent)
            Files.write(cached, "hello-rom".toByteArray())
            plantCheckedFile(roms.resolve("firmware/3"), "civ.bin", key)
            val player = ChoosingBiosPlayer(setOf("civ.bin"), "BIOS".toByteArray(), fail = true)

            val placement = library.prepareLaunch(game, player)
            val firmware = (placement.target as LaunchTarget.ContentUri).firmware
            assertEquals(listOf("civ.bin"), firmware.map { it.name })
            assertArrayEquals(key, Files.readAllBytes(CachePathContentUri(cache).pathFor(firmware.single().contentUri)!!))
        } finally {
            server.close()
        }
    }

    @Test(timeout = 20_000)
    fun aFailedFirmwareFetchStillLaunches() = runBlocking {
        val server = TinyServer()
        val cache = Files.createTempDirectory("romm-firmware-failed")
        try {
            server.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, """[{"id":3,"slug":"3ds","fs_slug":"3ds","rom_count":1,"name":"Nintendo 3DS"}]""")
            }
            server.route("GET", "/api/roms") { exchange, _ -> json(exchange, 200, ROM_PAGE) }
            server.route("GET", "/api/firmware") { exchange, _ -> json(exchange, 503, "{}") }
            val library = live(server, cache)
            val game = library.listGames("3ds", GameQuery()).games.single()
            val spec = rom("3ds", 3).bytes()
            val roms = cache.resolve("servers").resolve(RommData.serverKey(server.origin))
            val cached = RommClient.romCacheFile(roms, 1234, spec.fileName, spec.fileIds)
            Files.createDirectories(cached.parent)
            Files.write(cached, "hello-rom".toByteArray())

            val placement = library.prepareLaunch(game, BiosPlayer(setOf("boot9.bin")))
            assertTrue(placement.target is LaunchTarget.ContentUri)
            assertTrue((placement.target as LaunchTarget.ContentUri).firmware.isEmpty())
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
        val wiring = RommWiring("http://romm.example", RommTokenSource { token }, cache, Files.createTempDirectory("romm-data"))
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
        val wiring = RommWiring("http://romm.example", RommTokenSource { "rmm_test" }, cache, Files.createTempDirectory("romm-data"))
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
    fun eachSlotIsOneFileAndTrashIsNotASave() {
        val root = Files.createTempDirectory("romm-trash")
        try {
            val data = RommData(root, "http://romm.example")
            val file = data.slotFile(7, "autosave")
            Files.createDirectories(file.parent.resolve(".trash"))
            Files.writeString(file, "keep")
            Files.writeString(file.parent.resolve("stray.srm"), "stray")
            Files.writeString(file.parent.resolve(".trash").resolve("gone.srm"), "gone")
            val odd = data.slotFile(7, "../up")
            assertTrue(odd.normalize().startsWith(data.romSaves(7)))
            val listed = localSaves(data, 7, "azahar")
            assertEquals(listOf("autosave.sav"), listed.map { it.fileName })
            assertEquals("autosave", listed.single().slot)
        } finally {
            root.toFile().deleteRecursively()
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
            val data = Files.createTempDirectory("romm-pair-data")
            val wiring = RommWiring(server.origin, RommTokenSource { null }, cache, data)
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
            assertFalse(cacheContains(data, "rmm_secret"))
            val restarted = RommWiring(server.origin, RommTokenSource { null }, cache, data)
            assertEquals("device-1", restarted.rememberedDevice?.deviceId)
        } finally {
            server.close()
        }
    }

    @Test
    fun setupIsOriginThenDeviceCodeAndTheSecondInstanceKeepsItsKey() {
        val library = RommLibrary(RommGate { null })
        val steps = library.setup()
        assertEquals(Setup.Text(ROMM_ORIGIN_KEY, "Server"), steps[0])
        assertTrue(steps.none { it is Setup.Secret })
        assertEquals("access-token", (steps[1] as Setup.DeviceCode).credentialKey)
        val second = RommLibrary.open("romm#2")
        assertEquals("romm", second.id)
        assertEquals("romm#2", second.instanceId)
        assertEquals("access-token@romm#2", (second.setup()[1] as Setup.DeviceCode).credentialKey)
        assertEquals("access-token", rommCredentialKey("romm"))
        assertEquals("access-token@romm#2", rommCredentialKey("romm#2"))
    }

    @Test
    fun deviceCodePollMapsTheTokenAndKeepsTheDevice() = runBlocking {
        val cache = Files.createTempDirectory("romm-code")
        val data = Files.createTempDirectory("romm-code-data")
        try {
            val wiring = RommWiring("http://romm.example", RommTokenSource { null }, cache, data)
            val ops = ScriptedOps()
            var seenId: String? = null
            ops.onBegin = { id, _, _ ->
                seenId = id
                DeviceAuthChallenge("dev", "WDJB-MJHT", "/pair", "/pair?user_code=WDJB-MJHT", 600, 5)
            }
            ops.onUrl = { "http://romm.example/pair?user_code=WDJB-MJHT" }
            val polls = ArrayDeque(
                listOf(
                    DeviceTokenPoll.Pending,
                    DeviceTokenPoll.SlowDown,
                    DeviceTokenPoll.Denied,
                    DeviceTokenPoll.Expired,
                    DeviceTokenPoll.Approved("rmm_secret", "device-1", listOf("roms.read"), null),
                ),
            )
            ops.onPoll = { polls.removeFirst() }
            wiring.open = { _, _ -> ops }
            val library = RommLibrary(RommGate { wiring })
            val challenge = library.beginDeviceCode()
            assertEquals("WDJB-MJHT", challenge.userCode)
            assertEquals(Files.readString(data.resolve("install-id")).trim(), seenId)
            assertTrue(challenge.verificationUrl.contains("WDJB-MJHT"))
            assertEquals(DeviceCodePoll.Pending, library.pollDeviceCode(challenge.deviceCode))
            assertEquals(DeviceCodePoll.SlowDown, library.pollDeviceCode(challenge.deviceCode))
            assertEquals(DeviceCodePoll.Denied, library.pollDeviceCode(challenge.deviceCode))
            assertEquals(DeviceCodePoll.Expired, library.pollDeviceCode(challenge.deviceCode))
            val approved = library.pollDeviceCode(challenge.deviceCode) as DeviceCodePoll.Approved
            assertEquals("rmm_secret", approved.token)
            assertEquals("device-1", wiring.rememberedDevice?.deviceId)
            assertNull(wiring.tokenSource.accessToken())
        } finally {
            cache.toFile().deleteRecursively()
            data.toFile().deleteRecursively()
        }
    }

    @Test(timeout = 8_000)
    fun anInFlightFirmwareNotePublishesNothingAfterStop() {
        assertNothingPublishesAfterStop { progress, sink ->
            progress.follow(sink)
            listOf(Thread { progress.note(GameActivity.Downloading(1L, 10L)) }.also { it.start() })
        }
    }

    @Test(timeout = 8_000)
    fun replayingFirmwareProgressPublishesNothingAfterStop() {
        assertNothingPublishesAfterStop { progress, sink ->
            progress.note(GameActivity.Downloading(1L, 10L))
            listOf(Thread { progress.follow(sink) }.also { it.start() })
        }
    }

    /**
     * [start] begins a delivery that blocks inside [sink]. [LaunchProgress.stop]
     * takes the same lock as that delivery, so it must not return while the
     * delivery can still publish. A note after [stop] publishes nothing.
     */
    private fun assertNothingPublishesAfterStop(
        start: (progress: LaunchProgress, sink: (GameActivity) -> Unit) -> List<Thread>,
    ) {
        val progress = LaunchProgress()
        val deliveryEntered = CountDownLatch(1)
        val releaseDelivery = CountDownLatch(1)
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val sink = { _: GameActivity ->
            deliveryEntered.countDown()
            if (releaseDelivery.await(5, TimeUnit.SECONDS)) events += "published" else events += "delivery-timed-out"
        }
        val workers = start(progress, sink)
        assertTrue(deliveryEntered.await(5, TimeUnit.SECONDS))
        val stopped = CountDownLatch(1)
        val stopping = Thread {
            progress.stop()
            events += "stopped"
            stopped.countDown()
        }
        stopping.start()
        // The delivery holds the lock. Give stop long enough to return if it does not wait.
        assertFalse(
            "stop returned while a delivery was still in flight",
            stopped.await(200, TimeUnit.MILLISECONDS),
        )
        releaseDelivery.countDown()
        assertTrue(stopped.await(5, TimeUnit.SECONDS))
        (workers + stopping).forEach { thread ->
            thread.join(5_000)
            assertFalse(thread.isAlive)
        }
        assertEquals(listOf("published", "stopped"), events.toList())
        progress.note(GameActivity.Checking)
        assertEquals(listOf("published", "stopped"), events.toList())
    }
}

private fun harness(ops: ScriptedOps, platforms: List<Platform> = emptyList()): Harness {
    val cache = Files.createTempDirectory("romm-script")
    val wiring = RommWiring(
        origin = "http://romm.example",
        tokenSource = RommTokenSource { "rmm_test" },
        cacheRoot = cache,
        dataRoot = Files.createTempDirectory("romm-script-data"),
        platforms = platforms,
    )
    wiring.open = { _, _ -> ops }
    val gate = RommGate { wiring }
    return Harness(wiring, RommLibrary(gate), RommMetadata(gate))
}

private fun live(server: TinyServer, cache: Path): RommLibrary {
    val wiring = RommWiring(server.origin, RommTokenSource { "rmm_test" }, cache, Files.createTempDirectory("romm-data"))
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

private val twoAchievements = listOf(
    RaAchievement("Stage Two", "Clear Stage 2", 5, "25176", "/badges/25176.png", "/badges/25176_lock.png", null, null, 2),
    RaAchievement("Stage One", "Clear Stage 1", 4, "25175", "/badges/25175.png", "/badges/25175_lock.png", null, null, 1),
).sortedBy { it.displayOrder }

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

/** A player that looks for each firmware file in [wants] in its `bios` folder. */
private class BiosPlayer(private val wants: Set<String>) : Player {
    override val id = "bios-player"
    override val displayName = id
    override val platformId = "nintendo-3ds"
    override val packageNames = emptyList<String>()
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.Primary
    override val occupiesBothDisplays = false
    override val requiresImportedGame = false
    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()
    override fun firmwareFileName(name: String): String? = if (name in wants) "bios/$name" else null
    override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
}

/**
 * A player that names [wants] and writes a file whose header starts with [marker].
 * Anything else it names is offered and then left out.
 */
private class ChoosingBiosPlayer(
    private val wants: Set<String>,
    private val marker: ByteArray,
    private val fail: Boolean = false,
) : Player {
    override val id = "choosing-bios"
    override val displayName = id
    override val platformId = "nintendo-3ds"
    override val packageNames = emptyList<String>()
    override val needsLocalFile = true
    override val startDisplay = StartDisplay.Primary
    override val occupiesBothDisplays = false
    override val requiresImportedGame = false
    override val firmwareHeaderSize: Int = marker.size
    override fun saveDeclarations(game: Game): List<SaveDeclaration> = emptyList()
    override fun firmwareFileName(name: String): String? = if (name in wants) "bios/${name.substringAfterLast('/')}" else null
    override fun chooseFirmware(offered: List<FirmwareOffer>): List<FirmwareOffer> {
        if (fail) error("no")
        return offered.filter { it.header.size >= marker.size && it.header.copyOfRange(0, marker.size).contentEquals(marker) }
    }
    override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
}

internal class ScriptedOps : RommOps {
    var onHeartbeat: suspend () -> Heartbeat = { Heartbeat("5.4.0") }
    var onSearchCovers: suspend (String) -> List<app.foldcade.romm.RomCoverMatch> = { emptyList() }
    var onPlatforms: suspend () -> List<PlatformSummary> = { emptyList() }
    var onRoms: suspend (RomQuery) -> RomPage = { query -> RomPage(emptyList(), 0, query.limit, query.offset) }
    var onRom: suspend (Long) -> RomSummary = { id -> error("rom $id") }
    var onMe: suspend () -> RommUser = { throw RommForbidden("no me.read") }
    var onClose: () -> Unit = {}
    var onArtwork: suspend (String) -> ByteArray = { error("artwork") }
    var onRegister: suspend (RegisteredDevice?, String, String) -> RegisteredDevice =
        { _, _, version -> RegisteredDevice("device-1", version) }
    var hostnames = mutableListOf<String?>()
    var onSync: suspend (String, List<LocalSave>, List<Long>, List<String>?) -> SaveSyncReport =
        { _, _, _, _ -> error("sync") }
    var onDownload: suspend (Long, String, Path, List<Long>, Long?, String?) -> Path =
        { _, _, _, _, _, _ -> error("download") }

    /** What a download reports before [onDownload] answers. */
    var downloadSteps: List<RomProgress> = emptyList()
    var onFirmware: suspend (Long) -> List<app.foldcade.romm.FirmwareSummary> = { emptyList() }
    var onDownloadFirmware: suspend (app.foldcade.romm.FirmwareSummary, Path) -> Path = { _, _ -> error("firmware") }

    /** What a firmware download reports before [onDownloadFirmware] answers. */
    var firmwareSteps: List<RomProgress> = emptyList()

    override suspend fun heartbeat() = onHeartbeat()
    override suspend fun artwork(uri: String) = onArtwork(uri)
    override suspend fun platforms() = onPlatforms()

    override suspend fun roms(query: RomQuery) = onRoms(query)
    override suspend fun rom(id: Long) = onRom(id)
    override suspend fun me() = onMe()
    override suspend fun searchCovers(title: String) = onSearchCovers(title)
    override suspend fun downloadRom(
        romId: Long,
        fileName: String,
        cacheRoot: Path,
        fileIds: List<Long>,
        expectedSize: Long?,
        expectedMd5: String?,
        progress: ((RomProgress) -> Unit)?,
        target: Path?,
    ): Path {
        downloadSteps.forEach { progress?.invoke(it) }
        return onDownload(romId, fileName, cacheRoot, fileIds, expectedSize, expectedMd5)
    }

    override suspend fun firmware(platformId: Long) = onFirmware(platformId)
    override suspend fun downloadFirmware(
        firmware: app.foldcade.romm.FirmwareSummary,
        target: Path,
        progress: ((RomProgress) -> Unit)?,
    ): Path {
        firmwareSteps.forEach { progress?.invoke(it) }
        return onDownloadFirmware(firmware, target)
    }

    override suspend fun registerDevice(
        stored: RegisteredDevice?,
        name: String,
        clientVersion: String,
        hostname: String?,
    ): RegisteredDevice {
        hostnames += hostname
        return onRegister(stored, name, clientVersion)
    }

    override suspend fun syncSaves(
        deviceId: String,
        saves: List<LocalSave>,
        romIds: List<Long>,
        emulators: List<String>?,
        destination: (SyncOperation) -> Path,
        ledger: SaveLedger,
    ) = onSync(deviceId, saves, romIds, emulators)

    var onBegin: suspend (String, String, String) -> DeviceAuthChallenge = { _, _, _ -> error("begin") }
    var onPoll: suspend (String) -> DeviceTokenPoll = { error("poll") }
    var onUrl: (DeviceAuthChallenge) -> String = { error("url") }

    override suspend fun beginDeviceAuth(clientDeviceIdentifier: String, name: String, clientVersion: String) =
        onBegin(clientDeviceIdentifier, name, clientVersion)

    override suspend fun pollDeviceToken(deviceCode: String) = onPoll(deviceCode)
    override fun verificationUrl(challenge: DeviceAuthChallenge) = onUrl(challenge)
    override fun close() = onClose()
}

/** Answers one request at a time, or each on its own thread when [concurrent], so a held answer stops no other. */
private class TinyServer(concurrent: Boolean = false) : AutoCloseable {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val pool = if (concurrent) java.util.concurrent.Executors.newCachedThreadPool() else null
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
        pool?.let(server::setExecutor)
        server.start()
        origin = "http://127.0.0.1:${server.address.port}"
    }

    fun route(method: String, path: String, handler: (HttpExchange, ByteArray) -> Unit) {
        routes += Route(method, path, handler)
    }

    override fun close() {
        server.stop(0)
        pool?.shutdownNow()
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

/** Fantasy Life i as RomM files it: the game at the top of its folder, the update in `updates/`. */
private fun switchFolderPage(game: ByteArray, update: ByteArray): String = """
{"items":[{"id":35,"name":"Fantasy Life i","fs_name":"Fantasy Life i","fs_extension":"","fs_size_bytes":${game.size + update.size},"platform_id":3,"platform_slug":"switch","platform_fs_slug":"switch","platform_display_name":"Nintendo Switch","alternative_names":[],"path_cover_small":null,"path_cover_large":null,"url_cover":null,"has_simple_single_file":false,"has_nested_single_file":true,"has_multiple_files":false,"files":[
{"id":36,"file_name":"Fantasy Life i [Update v2.2.0].nsp","file_size_bytes":${update.size},"md5_hash":"${md5Hex(update)}","category":"update","is_top_level":false},
{"id":35,"file_name":"Fantasy Life i.nsp","file_size_bytes":${game.size},"md5_hash":"${md5Hex(game)}","category":"game","is_top_level":true}
]}],"total":1,"limit":50,"offset":0}
"""

private fun md5Hex(bytes: ByteArray): String =
    java.security.MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

private fun firmwareItem(id: Long, name: String, bytes: ByteArray): String =
    """{"id":$id,"platform_id":3,"file_name":"$name","file_size_bytes":${bytes.size},"md5_hash":"${md5Hex(bytes)}"}"""

private fun zipOf(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { raw ->
    ZipOutputStream(raw).use { archive ->
        entries.forEach { (name, bytes) ->
            archive.putNextEntry(ZipEntry(name))
            archive.write(bytes)
            archive.closeEntry()
        }
    }
}.toByteArray()

private fun plantCheckedFile(dir: Path, name: String, bytes: ByteArray) {
    val file = dir.resolve(name)
    Files.createDirectories(file.parent)
    Files.write(file, bytes)
    Files.write(file.resolveSibling("$name.verified"), """{"size":${bytes.size},"md5":"${md5Hex(bytes)}"}""".toByteArray())
}

/** A checked zip and its unpack, stamped with the same marker so the members count as checked. */
private fun plantCheckedZip(dir: Path, name: String, bytes: ByteArray, members: Map<String, ByteArray>) {
    plantCheckedFile(dir, name, bytes)
    val stamp = Files.readAllBytes(dir.resolve("$name.verified"))
    val unpacked = dir.resolve("$name.unpacked")
    Files.createDirectories(unpacked)
    members.forEach { (member, memberBytes) -> Files.write(unpacked.resolve(member), memberBytes) }
    Files.write(dir.resolve("$name.unpacked.verified"), stamp)
}

private const val ROM_PAGE = """
{"items":[{"id":1234,"name":"Super Mario","fs_name":"mario.cci","fs_extension":"cci","fs_size_bytes":9,"platform_id":3,"platform_slug":"3ds","platform_fs_slug":"3ds","platform_display_name":"Nintendo 3DS","alternative_names":[],"path_cover_small":"/assets/cover/small.jpg","path_cover_large":"/assets/cover/large.jpg","url_cover":null,"has_simple_single_file":true,"has_nested_single_file":false,"has_multiple_files":false,"files":[{"id":7,"file_name":"mario.cci","file_size_bytes":9}]}],"total":1,"limit":50,"offset":0}
"""
