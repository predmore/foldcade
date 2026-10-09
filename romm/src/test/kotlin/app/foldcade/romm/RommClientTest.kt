package app.foldcade.romm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RommClientTest {
    private lateinit var server: MockRomm
    private lateinit var dir: java.nio.file.Path

    @Before
    fun setUp() {
        server = MockRomm()
        dir = Files.createTempDirectory("romm-client")
    }

    @After
    fun tearDown() {
        server.assertKnownPaths()
        server.close()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun parsesThePinnedSpecVersion() {
        val version = RommVersion.parse("5.4.0-alpha.2")
        assertEquals(5, version?.major)
        assertEquals(4, version?.minor)
        assertEquals(0, version?.patch)
        assertTrue(version!!.isAtLeast(5, 0, 0))
        assertTrue(version.isAtLeast(5, 4, 0))
        assertFalse(RommVersion.parse("5.3.1")!!.isAtLeast(5, 4, 0))
        assertFalse(RommVersion.parse("4.9.9")!!.isAtLeast(5, 0, 0))
        assertEquals(RommContract.TESTED_MAJOR, 5)
        assertEquals(RommContract.TESTED_SPEC, "5.4.0-alpha.2")
        assertNull(RommVersion.parse("v5.4.0"))
    }

    @Test
    fun compiledClassesDoNotReferenceJavaNetHttp() {
        val root = java.nio.file.Paths.get(RommClient::class.java.protectionDomain.codeSource.location.toURI())
        val banned = listOf(
            "java/net/http".toByteArray(Charsets.US_ASCII),
            "java.net.http".toByteArray(Charsets.US_ASCII),
        )
        val hits = mutableListOf<String>()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }.forEach { path ->
                val bytes = Files.readAllBytes(path)
                if (banned.any { needle -> indexOf(bytes, needle) >= 0 }) hits += path.toString()
            }
        }
        assertTrue("compiled classes reference java.net.http, which Android does not provide: $hits", hits.isEmpty())
    }

    @Test
    fun deviceAuthAsksForFoldcadeScopesAndNotAPassword() {
        server.route("GET", "/api/heartbeat") { exchange, _ ->
            json(exchange, 200, fixture("heartbeat-5.4.0-alpha.2.json"))
        }
        server.route("POST", "/api/auth/device/init") { exchange, _ ->
            json(exchange, 201, fixture("device-auth-init.json"))
        }
        runClient(token = { "rmm_should_not_be_sent" }) { client ->
            val challenge = client.beginDeviceAuth("device-1", "Thor", "0.1.0")
            assertEquals("WDJB-MJHT", challenge.userCode)
            assertEquals(5, challenge.intervalSeconds)
            assertEquals(
                "${server.origin}/pair/device?user_code=WDJB-MJHT",
                client.verificationUrl(challenge),
            )
        }
        val init = server.recorded.first { it.path == "/api/auth/device/init" }
        assertNull(header(init, "Authorization"))
        val body = Json.parseToJsonElement(init.body.toString(Charsets.UTF_8)).jsonObject
        assertEquals("foldcade", body.req("client"))
        assertEquals("android", body.req("platform"))
        assertEquals("0.1.0", body.req("client_version"))
        assertEquals(
            RommContract.DEVICE_AUTH_SCOPES,
            body.getValue("requested_scopes").jsonArray.map { it.jsonPrimitive.content },
        )
        assertFalse(RommContract.DEVICE_AUTH_SCOPES.contains("tasks.run"))
        assertFalse(init.body.toString(Charsets.UTF_8).contains("password"))
    }

    @Test
    fun deviceAuthRefusesAServerOlderThan5() {
        server.route("GET", "/api/heartbeat") { exchange, _ ->
            json(exchange, 200, """{"SYSTEM":{"VERSION":"4.9.0","GIT_BRANCH":null,"SHOW_SETUP_WIZARD":false}}""")
        }
        runClient { client ->
            val error = suspendCatching { client.beginDeviceAuth("device-1", "Thor", "0.1.0") }
            assertTrue(error.exceptionOrNull() is RommUnsupportedServer)
        }
        assertEquals(listOf("/api/heartbeat"), server.recorded.map { it.path })
    }

    @Test
    fun pollsUntilTheServerIssuesOrDeniesTheToken() {
        server.route("POST", "/api/auth/device/token") { exchange, body ->
            val code = Json.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject.req("device_code")
            when (code) {
                "wait" -> json(exchange, 400, """{"detail":"authorization_pending"}""")
                "slow" -> json(exchange, 400, """{"detail":"slow_down"}""")
                "no" -> json(exchange, 400, """{"detail":"access_denied"}""")
                "old" -> json(exchange, 400, """{"detail":"expired_token"}""")
                else -> json(exchange, 200, fixture("device-token.json"))
            }
        }
        runClient { client ->
            assertEquals(DeviceTokenPoll.Pending, client.pollDeviceToken("wait"))
            assertEquals(DeviceTokenPoll.SlowDown, client.pollDeviceToken("slow"))
            assertEquals(DeviceTokenPoll.Denied, client.pollDeviceToken("no"))
            assertEquals(DeviceTokenPoll.Expired, client.pollDeviceToken("old"))
            val approved = client.pollDeviceToken("yes") as DeviceTokenPoll.Approved
            assertEquals("rmm_issued", approved.accessToken)
            assertEquals("3f1c2b9e-8a4d-4c7e-9f21-6d0b5a7e1c34", approved.deviceId)
        }
    }

    @Test
    fun listsPlatformsAndRomsWithTheDocumentedQuery() {
        server.route("GET", "/api/platforms") { exchange, _ ->
            json(exchange, 200, fixture("platforms.json"))
        }
        server.route("GET", "/api/roms") { exchange, _ ->
            json(exchange, 200, fixture("roms.json"))
        }
        runClient(token = { "rmm_test" }) { client ->
            val platforms = client.platforms()
            assertEquals("3DS", platforms.single().abbreviation)
            assertEquals("3ds", platforms.single().fsSlug)
            val page = client.roms(RomQuery(platformIds = listOf(3), searchTerm = "mario"))
            val rom = page.items.single()
            assertEquals(1234L, rom.id)
            assertEquals("mario.cci", rom.files.single().fileName)
            assertEquals("/assets/cover/large.jpg", rom.pathCoverLarge)
            assertNull(rom.urlCover)
        }
        val roms = server.recorded.first { it.path == "/api/roms" }
        assertEquals("Bearer rmm_test", header(roms, "Authorization"))
        val query = roms.query.orEmpty()
        assertTrue(query.contains("platform_ids=3"))
        assertTrue(query.contains("search_term=mario"))
        assertTrue(query.contains("with_files=true"))
        assertTrue(query.contains("order_by="))
        assertFalse(query.contains("format="))
        assertFalse(query.contains("rmm_test"))
    }

    @Test
    fun readsOneRomById() {
        server.route("GET", "/api/roms/1234/simple") { exchange, _ ->
            json(exchange, 200, fixture("rom-simple.json"))
        }
        runClient(token = { "rmm_test" }) { client ->
            val rom = client.rom(1234)
            assertEquals(1234L, rom.id)
            assertEquals("Super Mario", rom.name)
            assertEquals("/assets/cover/large.jpg", rom.pathCoverLarge)
        }
        val hit = server.recorded.single { it.path == "/api/roms/1234/simple" }
        assertEquals("Bearer rmm_test", header(hit, "Authorization"))
        assertEquals("GET", hit.method)
    }

    @Test
    fun listsAStable531PlatformThatOmitsAbbreviation() {
        server.route("GET", "/api/platforms") { exchange, _ ->
            json(exchange, 200, fixture("platforms-5.3.1.json"))
        }
        runClient(token = { "rmm_test" }) { client ->
            val platform = client.platforms().single()
            assertEquals("3ds", platform.slug)
            assertEquals("3ds", platform.abbreviation)
            assertTrue(platform.alternativeNames.isEmpty())
            assertEquals("Nintendo 3DS", platform.displayName)
        }
        assertEquals(listOf("/api/platforms"), server.recorded.map { it.path })
    }

    @Test
    fun saveSyncRefuses531WithoutOpeningASession() {
        server.route("GET", "/openapi.json") { exchange, _ ->
            json(exchange, 200, fixture("openapi-5.3.1.json"))
        }
        val save = localSave("local")
        runClient(token = { "rmm_test" }) { client ->
            val error = suspendCatching {
                client.syncSaves("device-1", listOf(save), destination = { save.file })
            }.exceptionOrNull()
            val unsupported = error as RommUnsupportedServer
            assertEquals("5.3.1", unsupported.serverVersion)
            assertTrue(unsupported.message!!.contains("server too old for save sync"))
        }
        assertEquals(listOf("/openapi.json"), server.recorded.map { it.path })
        assertEquals("local", Files.readString(save.file))
    }

    @Test
    fun downloadsWithPurposePlayAndResumesAPartial() {
        val rom = "hello-rom".toByteArray()
        server.route("GET", "/api/roms/1234/content/mario.cci") { exchange, _ ->
            val range = header(exchange)
            val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            bytes(exchange, if (range == null) 200 else 206, rom.copyOfRange(start, rom.size))
        }
        val cache = dir.resolve("cache")
        val partial = cache.resolve("roms/1234/mario.cci.partial")
        Files.createDirectories(partial.parent)
        Files.write(partial, "hello".toByteArray())
        runClient(token = { "rmm_test" }) { client ->
            val file = client.downloadRom(1234, "mario.cci", cache, expectedSize = rom.size.toLong())
            assertEquals("hello-rom", Files.readString(file))
            val again = client.downloadRom(1234, "mario.cci", cache, expectedSize = rom.size.toLong())
            assertEquals(file, again)
        }
        val hits = server.recorded.filter { it.path.contains("/content/") }
        assertEquals(1, hits.size)
        assertEquals("bytes=5-", header(hits.single(), "Range"))
        assertTrue(hits.single().query.orEmpty().contains("purpose=play"))
        assertFalse(hits.single().query.orEmpty().contains("format"))
    }

    @Test
    fun multiFileDownloadPassesFileIdsAndDoesNotConvert() {
        server.route("GET", "/api/roms/1234/content/game.zip") { exchange, _ ->
            bytes(exchange, 200, "zip".toByteArray())
        }
        runClient(token = { "rmm_test" }) { client ->
            val file = client.downloadRom(1234, "game.zip", dir.resolve("cache"), fileIds = listOf(7, 8))
            assertTrue(file.toString().contains("f7-8"))
            assertEquals("zip", Files.readString(file))
        }
        val query = server.recorded.single().query.orEmpty()
        assertTrue(query.contains("file_ids=7%2C8") || query.contains("file_ids=7,8"))
        assertTrue(query.contains("purpose=play"))
        assertFalse(query.contains("format"))
    }

    @Test
    fun skipsRegistrationWhenTheStoredClientVersionMatches() {
        runClient(origin = "http://127.0.0.1:9", token = { "rmm_test" }) { client ->
            val stored = RegisteredDevice("device-1", "0.1.0")
            assertEquals(stored, client.registerDevice(stored, "Thor", "0.1.0"))
        }
        assertTrue(server.recorded.isEmpty())
    }

    @Test
    fun updatesADeviceWhenTheClientVersionChanged() {
        server.route("PUT", "/api/devices/device-1") { exchange, _ ->
            json(exchange, 200, """{"id":"device-1","user_id":1,"sync_enabled":true}""")
        }
        runClient(token = { "rmm_test" }) { client ->
            val updated = client.registerDevice(RegisteredDevice("device-1", "0.1.0"), "Thor", "0.2.0")
            assertEquals("0.2.0", updated.clientVersion)
            assertEquals("device-1", updated.deviceId)
        }
        val body = Json.parseToJsonElement(server.recorded.single().body.toString(Charsets.UTF_8)).jsonObject
        assertEquals("foldcade", body.req("client"))
        assertEquals("android", body.req("platform"))
        assertEquals("api", body.req("sync_mode"))
        assertEquals("0.2.0", body.req("client_version"))
        assertFalse(body.containsKey("capabilities"))
    }

    @Test
    fun postsADeviceWhenTheStoredIdIsGone() {
        server.route("PUT", "/api/devices/gone") { exchange, _ ->
            json(exchange, 404, """{"detail":"Device with ID gone not found"}""")
        }
        server.route("POST", "/api/devices") { exchange, _ ->
            json(
                exchange,
                201,
                """{"device_id":"3f1c2b9e-8a4d-4c7e-9f21-6d0b5a7e1c34","name":"Thor","created_at":"2026-04-18T09:00:00Z"}""",
            )
        }
        runClient(token = { "rmm_test" }) { client ->
            val created = client.registerDevice(RegisteredDevice("gone", "0.1.0"), "Thor", "0.2.0")
            assertEquals("3f1c2b9e-8a4d-4c7e-9f21-6d0b5a7e1c34", created.deviceId)
        }
        assertEquals(listOf("PUT", "POST"), server.recorded.map { it.method })
    }

    @Test
    fun refusesToNegotiateWhenTheOpenApiMajorIsNewer() {
        server.route("GET", "/openapi.json") { exchange, _ ->
            json(exchange, 200, fixture("openapi-6.0.0.json"))
        }
        val save = localSave("local")
        runClient(token = { "rmm_test" }) { client ->
            val error = suspendCatching {
                client.negotiate("device-1", listOf(save), romIds = listOf(1234), emulators = listOf("azahar"))
            }.exceptionOrNull()
            val mismatch = error as RommProtocolMismatch
            assertEquals("6.0.0", mismatch.serverVersion)
            assertEquals(5, mismatch.testedMajor)
            assertTrue(mismatch.message!!.contains("6.0.0"))
            assertTrue(mismatch.message!!.contains("tested with RomM 5"))
        }
        assertEquals(listOf("/openapi.json"), server.recorded.map { it.path })
        assertEquals("local", Files.readString(save.file))
    }

    @Test
    fun negotiatesANewerMinorOfThePinnedMajor() {
        server.route("GET", "/openapi.json") { exchange, _ ->
            json(exchange, 200, """{"openapi":"3.1.0","info":{"title":"RomM API","version":"5.9.0"}}""")
        }
        server.route("POST", "/api/sync/negotiate") { exchange, _ ->
            json(exchange, 200, fixture("negotiate-download.json"))
        }
        val save = localSave("local")
        runClient(token = { "rmm_test" }) { client ->
            val result = client.negotiate(
                "3f1c2b9e-8a4d-4c7e-9f21-6d0b5a7e1c34",
                listOf(save),
                romIds = listOf(1234),
                emulators = listOf("azahar"),
            )
            assertEquals(42L, result.sessionId)
            assertEquals("download", result.operations.single().action)
            assertEquals(0, result.totalDelete)
        }
        val body = Json.parseToJsonElement(
            server.recorded.first { it.path == "/api/sync/negotiate" }.body.toString(Charsets.UTF_8),
        ).jsonObject
        val sent = body.getValue("saves").jsonArray.single().jsonObject
        assertEquals(1234L, sent.getValue("rom_id").jsonPrimitive.long)
        assertEquals("autosave", sent.req("slot"))
        assertEquals("azahar", sent.req("emulator"))
        assertEquals(md5Hex("local".toByteArray()), sent.req("content_hash"))
        assertEquals(5L, sent.getValue("file_size_bytes").jsonPrimitive.long)
        assertEquals(listOf("azahar"), body.getValue("emulators").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf(1234L), body.getValue("rom_ids").jsonArray.map { it.jsonPrimitive.long })
        assertEquals(false, body.getValue("restore_unlisted").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun conflictArchivesTheLocalBytesAndWritesTheServerCopy() {
        server.route("GET", "/openapi.json") { exchange, _ ->
            json(exchange, 200, fixture("openapi-5.4.0-alpha.2.json"))
        }
        server.route("POST", "/api/sync/negotiate") { exchange, _ ->
            json(
                exchange,
                200,
                fixture("negotiate-download.json").replace(""""action": "download"""", """"action": "conflict""""),
            )
        }
        server.route("POST", "/api/saves") { exchange, _ ->
            json(exchange, 200, """{"id":50,"rom_id":1234,"file_name":"mario.srm"}""")
        }
        server.route("GET", "/api/saves/99/content") { exchange, _ ->
            bytes(exchange, 200, "server".toByteArray())
        }
        server.route("POST", "/api/saves/99/downloaded") { exchange, _ ->
            json(exchange, 200, """{"id":99}""")
        }
        server.route("POST", "/api/sync/sessions/42/complete") { exchange, _ ->
            json(exchange, 200, fixture("sync-complete.json"))
        }
        val save = localSave("local")
        runClient(token = { "rmm_test" }) { client ->
            val report = client.syncSaves(
                deviceId = "3f1c2b9e-8a4d-4c7e-9f21-6d0b5a7e1c34",
                saves = listOf(save),
                romIds = listOf(1234),
                emulators = listOf("azahar"),
                destination = { save.file },
            )
            assertEquals(1, report.keptBoth.size)
            assertEquals(50L, report.keptBoth.single().archivedSaveId)
            assertTrue(report.completedSession)
            assertEquals("server", Files.readString(save.file))
        }
        val upload = server.recorded.first { it.method == "POST" && it.path == "/api/saves" }
        assertFalse(upload.query.orEmpty().contains("slot="))
        assertFalse(upload.query.orEmpty().contains("overwrite=true"))
        assertTrue(upload.query.orEmpty().contains("overwrite=false"))
        assertTrue(upload.body.toString(Charsets.ISO_8859_1).contains("local"))
        val download = server.recorded.first { it.path == "/api/saves/99/content" }
        assertTrue(download.query.orEmpty().contains("optimistic=false"))
        val confirm = server.recorded.first { it.path == "/api/saves/99/downloaded" }
        assertTrue(confirm.body.toString(Charsets.UTF_8).contains(md5Hex("server".toByteArray())))
        val complete = server.recorded.first { it.path == "/api/sync/sessions/42/complete" }
        assertFalse(complete.body.toString(Charsets.UTF_8).contains("play_sessions"))
    }

    @Test
    fun aMovedSlotIsNotOverwritten() {
        server.route("GET", "/openapi.json") { exchange, _ ->
            json(exchange, 200, fixture("openapi-5.4.0-alpha.2.json"))
        }
        server.route("POST", "/api/sync/negotiate") { exchange, _ ->
            json(
                exchange,
                200,
                """
                {
                  "session_id": 42,
                  "operations": [{
                    "action": "upload",
                    "rom_id": 1234,
                    "save_id": null,
                    "file_name": "mario.srm",
                    "slot": "autosave",
                    "emulator": "azahar",
                    "reason": "client is newer"
                  }],
                  "total_upload": 1,
                  "total_download": 0,
                  "total_conflict": 0,
                  "total_no_op": 0,
                  "total_delete": 0
                }
                """.trimIndent(),
            )
        }
        server.route("POST", "/api/saves") { exchange, _ ->
            json(exchange, 409, """{"detail":"slot changed"}""")
        }
        server.route("POST", "/api/sync/sessions/42/complete") { exchange, _ ->
            json(exchange, 200, fixture("sync-complete.json"))
        }
        val save = localSave("local")
        runClient(token = { "rmm_test" }) { client ->
            val report = client.syncSaves(
                "device-1",
                listOf(save),
                romIds = listOf(1234),
                emulators = listOf("azahar"),
                destination = { save.file },
            )
            assertTrue(report.failed.single().contains("slot changed"))
            assertTrue(report.uploadedSaveIds.isEmpty())
        }
        assertEquals("local", Files.readString(save.file))
        assertEquals(1, server.recorded.count { it.path == "/api/saves" })
        assertFalse(server.recorded.any { it.query.orEmpty().contains("overwrite=true") })
    }

    @Test
    fun queuedUploadIsRetriedWithoutASession() {
        val queue = SaveUploadQueue(dir.resolve("queue"))
        val save = localSave("local")
        queue.enqueue(save, "device-1", md5Hex("local".toByteArray()), "local".toByteArray(), "autosave")
        server.route("POST", "/api/saves") { exchange, _ ->
            json(exchange, 200, """{"id":8,"rom_id":1234,"file_name":"mario.srm"}""")
        }
        runClient(token = { "rmm_test" }) { client ->
            val flushed = queue.flush(client)
            assertEquals(1, flushed.sent)
            assertEquals(0, flushed.kept)
        }
        assertTrue(queue.pending().isEmpty())
        val upload = server.recorded.single()
        assertFalse(upload.query.orEmpty().contains("session_id"))
        assertTrue(upload.query.orEmpty().contains("slot=autosave"))
    }

    @Test
    fun aMajorFiveResponseMissingTheTestedFieldsIsNotApplied() {
        server.route("GET", "/openapi.json") { exchange, _ ->
            json(exchange, 200, fixture("openapi-5.4.0-alpha.2.json"))
        }
        server.route("POST", "/api/sync/negotiate") { exchange, _ ->
            json(
                exchange,
                200,
                """{"session_id":1,"operations":[],"total_upload":0,"total_download":0,"total_conflict":0,"total_no_op":0}""",
            )
        }
        val save = localSave("local")
        runClient(token = { "rmm_test" }) { client ->
            val error = suspendCatching {
                client.syncSaves("device-1", listOf(save), destination = { save.file })
            }.exceptionOrNull()
            assertTrue(error is RommResponseException)
            assertTrue(error!!.message!!.contains("total_delete"))
        }
        assertEquals("local", Files.readString(save.file))
        assertFalse(server.recorded.any { it.path.contains("/complete") })
    }

    @Test
    fun anUnreadableOpenApiVersionDoesNotNegotiate() {
        server.route("GET", "/openapi.json") { exchange, _ ->
            json(exchange, 200, """{"openapi":"3.1.0","info":{"title":"RomM API"}}""")
        }
        val save = localSave("local")
        runClient(token = { "rmm_test" }) { client ->
            assertTrue(suspendCatching { client.negotiate("device-1", listOf(save)) }.exceptionOrNull() is RommProtocolUnreadable)
        }
        assertEquals(listOf("/openapi.json"), server.recorded.map { it.path })
    }

    @Test
    fun httpStatusesStayOnTheException() {
        var count = 0
        server.route("GET", "/api/heartbeat") { exchange, _ ->
            count += 1
            when (count) {
                1 -> json(exchange, 401, """{"detail":"unauthorized"}""")
                2 -> json(exchange, 404, """{"detail":"missing"}""")
                else -> json(exchange, 503, """{"detail":"down"}""")
            }
        }
        runClient { client ->
            val unauthorized = suspendCatching { client.heartbeat() }.exceptionOrNull() as RommHttpException
            assertEquals(401, unauthorized.status)
            val missing = suspendCatching { client.heartbeat() }.exceptionOrNull() as RommHttpException
            assertEquals(404, missing.status)
            val down = suspendCatching { client.heartbeat() }.exceptionOrNull() as RommHttpException
            assertEquals(503, down.status)
        }
    }

    @Test
    fun aReadTimeoutFailsInsteadOfHanging() {
        val release = CountDownLatch(1)
        server.route("GET", "/api/platforms") { exchange, _ ->
            release.await(30, TimeUnit.SECONDS)
            exchange.close()
        }
        val started = System.nanoTime()
        try {
            val error = runClient(token = { "rmm_test" }, readTimeout = Duration.ofMillis(400)) { client ->
                suspendCatching { client.platforms() }.exceptionOrNull()
            }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue(error is RommUnavailable)
            assertTrue("timed out too slowly: ${elapsedMs}ms", elapsedMs < 5_000)
        } finally {
            release.countDown()
        }
    }

    @Test
    fun cancelStopsARomDownloadBeforeItIsFinalized() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.route("GET", "/api/roms/1234/content/slow.bin") { exchange, _ ->
            exchange.responseHeaders.add("Content-Type", "application/octet-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.write(byteArrayOf(1))
            exchange.responseBody.flush()
            started.countDown()
            release.await(30, TimeUnit.SECONDS)
            exchange.close()
        }
        val cache = dir.resolve("cache")
        val client = RommClient(server.origin, { "rmm_test" })
        try {
            runBlocking {
                val job = launch(Dispatchers.IO) {
                    client.downloadRom(1234, "slow.bin", cache)
                }
                assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
                val cancelStarted = System.nanoTime()
                job.cancel()
                job.join()
                val cancelMs = (System.nanoTime() - cancelStarted) / 1_000_000
                assertTrue(job.isCancelled)
                assertTrue(
                    "download cancel took ${cancelMs}ms; the OkHttp call stayed open until the read timeout",
                    cancelMs < 2_000,
                )
            }
        } finally {
            release.countDown()
            client.close()
        }
        assertFalse(Files.exists(cache.resolve("roms/1234/slow.bin")))
    }

    @Test
    fun cancelDuringUploadDoesNotQueueOrCompleteTheSession() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.route("GET", "/openapi.json") { exchange, _ ->
            json(exchange, 200, fixture("openapi-5.4.0-alpha.2.json"))
        }
        server.route("POST", "/api/sync/negotiate") { exchange, _ ->
            json(
                exchange,
                200,
                """
                {
                  "session_id": 42,
                  "operations": [{
                    "action": "upload",
                    "rom_id": 1234,
                    "save_id": null,
                    "file_name": "mario.srm",
                    "slot": "autosave",
                    "emulator": "azahar",
                    "reason": "client is newer"
                  }],
                  "total_upload": 1,
                  "total_download": 0,
                  "total_conflict": 0,
                  "total_no_op": 0,
                  "total_delete": 0
                }
                """.trimIndent(),
            )
        }
        server.route("POST", "/api/saves") { exchange, _ ->
            started.countDown()
            release.await(30, TimeUnit.SECONDS)
            exchange.close()
        }
        val queue = SaveUploadQueue(dir.resolve("queue"))
        val save = localSave("local")
        val client = RommClient(server.origin, { "rmm_test" })
        try {
            runBlocking {
                val job = launch(Dispatchers.IO) {
                    client.syncSaves(
                        deviceId = "device-1",
                        saves = listOf(save),
                        romIds = listOf(1234),
                        emulators = listOf("azahar"),
                        destination = { save.file },
                        queue = queue,
                    )
                }
                assertTrue(withContext(Dispatchers.IO) { started.await(5, TimeUnit.SECONDS) })
                job.cancel()
                job.join()
                assertTrue(job.isCancelled)
            }
        } finally {
            release.countDown()
            client.close()
        }
        assertTrue(queue.pending().isEmpty())
        assertFalse(server.recorded.any { it.path.contains("/complete") })
        assertEquals("local", Files.readString(save.file))
    }

    @Test
    fun normalizeOriginFoldsSchemeAndHostCase() {
        assertEquals("https://romm.example", RommClient.normalizeOrigin("HTTPS://Romm.Example"))
        assertEquals("http://romm.example:8080", RommClient.normalizeOrigin("HTTP://Romm.Example:8080/api"))
        assertEquals("http://romm.example/Games", RommClient.normalizeOrigin("http://Romm.Example/Games/"))
        assertEquals("https://romm.example", RommClient.normalizeOrigin("HTTPS://Romm.Example/API"))
        assertEquals("http://romm.example/Games", RommClient.normalizeOrigin("http://romm.example/Games/Api"))
        assertEquals("http://[::1]", RommClient.normalizeOrigin("HTTP://[::1]/api"))
        val userinfo = runCatching { RommClient.normalizeOrigin("HTTP://User:s3cret-token@Romm.Example/API") }
        val userinfoError = userinfo.exceptionOrNull()
        assertTrue(userinfoError is IllegalArgumentException)
        assertTrue(userinfoError?.message?.contains("password") == true)
        assertFalse(userinfoError?.message?.contains("s3cret-token") == true)
        assertEquals(null, normalizeSetupOrigin("http://User:s3cret-token@romm.example"))
        val rejected = runCatching { RommClient.normalizeOrigin("FTP://romm.example") }
        assertTrue(rejected.exceptionOrNull() is IllegalArgumentException)
        assertEquals(
            "http://romm.example",
            normalizeSetupOrigin("  HTTP://Romm.Example/api/  "),
        )
    }

    @Test
    fun cleartextGuardRejectsAnotherHttpHostAndStillHintsHttps() {
        assertTrue(httpCleartextAllowed("http://192.168.1.20:8080", "http://192.168.1.20:8080/api/heartbeat"))
        assertTrue(httpCleartextAllowed("http://192.168.1.20", "http://192.168.1.20:80/api/heartbeat"))
        assertFalse(httpCleartextAllowed("http://192.168.1.20:8080", "http://evil.example/api/heartbeat"))
        assertFalse(httpCleartextAllowed("http://192.168.1.20:8080", "http://192.168.1.20:9090/api/heartbeat"))
        assertTrue(httpCleartextAllowed("http://192.168.1.20:8080", "https://192.168.1.20/api/heartbeat"))

        server.route("GET", "/api/heartbeat") { exchange, _ ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:9/api/heartbeat")
            exchange.sendResponseHeaders(302, -1)
            exchange.responseBody.close()
        }
        val rejected = runCatching { runClient { it.heartbeat() } }.exceptionOrNull()
        assertTrue(rejected is RommUnavailable)
        assertTrue(rejected?.message?.contains("Cleartext") == true)

        server.route("GET", "/api/heartbeat") { exchange, _ ->
            exchange.responseHeaders.add("Location", "https://romm.example/api/heartbeat")
            exchange.sendResponseHeaders(301, -1)
            exchange.responseBody.close()
        }
        runClient { client ->
            assertEquals(TRY_HTTPS_HINT, client.redirectHint())
        }
    }

    @Test
    fun confirmSignInStaysOpenOn401OrUnreachable() {
        server.route("GET", "/api/heartbeat") { exchange, _ ->
            json(exchange, 200, fixture("heartbeat-5.4.0-alpha.2.json"))
        }
        server.route("GET", "/api/platforms") { exchange, _ ->
            json(exchange, 401, """{"detail":"unauthorized"}""")
        }
        runClient(token = { "rmm_test" }) { client ->
            val denied = client.confirmSignIn()
            assertTrue(denied is RommSignInResult.StayOnForm)
        }
        val unreachable = runBlocking {
            RommClient(
                origin = "http://127.0.0.1:1",
                accessToken = { "rmm_test" },
                connectTimeout = Duration.ofMillis(400),
                readTimeout = Duration.ofMillis(400),
            ).use { it.confirmSignIn() }
        }
        assertTrue(unreachable is RommSignInResult.StayOnForm)

        server.route("GET", "/api/platforms") { exchange, _ ->
            json(exchange, 200, "[]")
        }
        runClient(token = { "rmm_test" }) { client ->
            val accepted = client.confirmSignIn()
            assertTrue(accepted is RommSignInResult.Accepted)
        }
    }

    @Test
    fun logsAndToStringHideTheTokenAndHttpRedirectsHintHttps() {
        val logs = mutableListOf<String>()
        server.route("GET", "/api/platforms") { exchange, _ ->
            json(exchange, 200, "[]")
        }
        runClient(token = { "rmm_supersecret" }, httpLog = { logs += it }) { client ->
            client.platforms()
        }
        val text = logs.joinToString("\n")
        assertFalse(text.contains("rmm_supersecret"))
        assertTrue(text.contains("Authorization: ***"))
        assertEquals(
            "Authorization: ***\nBearer ***",
            redactSensitive("Authorization: Bearer rmm_supersecret\nBearer rmm_supersecret"),
        )

        val approved = DeviceTokenPoll.Approved(
            accessToken = "rmm_supersecret",
            deviceId = "device-1",
            scopes = listOf("roms.read"),
            expiresAt = null,
        )
        assertFalse(approved.toString().contains("rmm_supersecret"))
        assertTrue(approved.toString().contains("***"))
        val challenge = DeviceAuthChallenge(
            deviceCode = "device-secret",
            userCode = "ABCD-EFGH",
            verificationPath = "/auth/device",
            verificationPathComplete = "/auth/device?user_code=ABCD-EFGH",
            expiresInSeconds = 600,
            intervalSeconds = 5,
        )
        assertFalse(challenge.toString().contains("device-secret"))
        assertTrue(challenge.toString().contains("ABCD-EFGH"))

        server.route("GET", "/api/heartbeat") { exchange, _ ->
            exchange.responseHeaders.add("Location", "https://romm.example/api/heartbeat")
            exchange.sendResponseHeaders(301, -1)
            exchange.responseBody.close()
        }
        runClient { client ->
            assertEquals(TRY_HTTPS_HINT, client.redirectHint())
            assertEquals(CLEARTEXT_CREDENTIAL_WARNING, cleartextCredentialWarning(client.origin))
            assertEquals(CLEARTEXT_CREDENTIAL_WARNING, cleartextCredentialWarning("  HTTP://192.168.1.20:8080"))
            assertEquals(null, cleartextCredentialWarning("https://romm.example"))
            assertEquals(null, httpsRedirectHint(200, "https://romm.example"))
        }
        assertTrue(server.recorded.none { it.path == "/api/heartbeat" && it.headers.keys.any { name -> name.equals("Authorization", true) } })
    }

    private suspend fun <T> suspendCatching(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

    private fun <T> runClient(
        origin: String = server.origin,
        token: () -> String? = { null },
        readTimeout: Duration = Duration.ofSeconds(30),
        httpLog: ((String) -> Unit)? = null,
        block: suspend (RommClient) -> T,
    ): T = runBlocking {
        RommClient(
            origin = origin,
            accessToken = token,
            readTimeout = readTimeout,
            writeTimeout = readTimeout,
            httpLog = httpLog,
        ).use { block(it) }
    }

    private fun localSave(text: String): LocalSave {
        val file = dir.resolve("mario.srm")
        Files.writeString(file, text)
        return LocalSave(
            romId = 1234,
            fileName = "mario.srm",
            slot = "autosave",
            emulator = "azahar",
            updatedAt = Instant.parse("2026-04-18T09:42:01Z"),
            file = file,
        )
    }

    private fun fixture(name: String): String =
        javaClass.classLoader.getResource("fixtures/$name")!!.readText()

    private fun header(recorded: Recorded, name: String): String? =
        recorded.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    private fun header(exchange: com.sun.net.httpserver.HttpExchange): String? =
        exchange.requestHeaders.getFirst("Range")

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        for (i in 0..haystack.size - needle.size) {
            var matched = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
        }
        return -1
    }
}

private fun kotlinx.serialization.json.JsonObject.req(name: String): String =
    getValue(name).jsonPrimitive.content
