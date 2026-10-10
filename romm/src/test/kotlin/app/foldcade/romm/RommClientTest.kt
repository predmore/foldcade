package app.foldcade.romm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    fun downloadsWithPurposePlayAndResumesAPartialOnlyWithItsValidator() {
        val rom = "hello-rom".toByteArray()
        server.route("GET", "/api/roms/1234/content/mario.cci") { exchange, _ ->
            val range = header(exchange)
            val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            exchange.responseHeaders.add("ETag", "\"v1\"")
            if (range != null) exchange.responseHeaders.add("Content-Range", "bytes $start-${rom.size - 1}/${rom.size}")
            bytes(exchange, if (range == null) 200 else 206, rom.copyOfRange(start, rom.size))
        }
        val cache = dir.resolve("cache")
        val partial = cache.resolve("roms/1234/mario.cci.partial")
        Files.createDirectories(partial.parent)
        Files.write(partial, "hello".toByteArray())
        Files.writeString(cache.resolve("roms/1234/mario.cci.partial.validator"), "\"v1\"")
        runClient(token = { "rmm_test" }) { client ->
            val file = client.downloadRom(1234, "mario.cci", cache, expectedSize = rom.size.toLong(), expectedMd5 = md5Hex(rom))
            assertEquals("hello-rom", Files.readString(file))
            val again = client.downloadRom(1234, "mario.cci", cache, expectedSize = rom.size.toLong(), expectedMd5 = md5Hex(rom))
            assertEquals(file, again)
        }
        val hits = server.recorded.filter { it.path.contains("/content/") }
        assertEquals(1, hits.size)
        assertEquals("bytes=5-", header(hits.single(), "Range"))
        assertEquals("\"v1\"", header(hits.single(), "If-Range"))
        assertTrue(hits.single().query.orEmpty().contains("purpose=play"))
        assertFalse(hits.single().query.orEmpty().contains("format"))
        assertFalse(Files.exists(cache.resolve("roms/1234/mario.cci.partial.validator")))
    }

    @Test
    fun aPartialWithoutAValidatorStartsOver() {
        server.route("GET", "/api/roms/1234/content/mario.cci") { exchange, _ ->
            bytes(exchange, 200, "fresh-rom".toByteArray())
        }
        val cache = dir.resolve("cache")
        val partial = cache.resolve("roms/1234/mario.cci.partial")
        Files.createDirectories(partial.parent)
        Files.write(partial, "stale".toByteArray())
        runClient(token = { "rmm_test" }) { client ->
            assertEquals("fresh-rom", Files.readString(client.downloadRom(1234, "mario.cci", cache)))
        }
        assertNull(header(server.recorded.single(), "Range"))
    }

    @Test
    fun aResumeAtTheWrongOffsetStartsOver() {
        val rom = "hello-rom".toByteArray()
        server.route("GET", "/api/roms/1234/content/mario.cci") { exchange, _ ->
            if (header(exchange) != null) {
                exchange.responseHeaders.add("Content-Range", "bytes 0-${rom.size - 1}/${rom.size}")
                bytes(exchange, 206, rom)
            } else {
                bytes(exchange, 200, rom)
            }
        }
        val cache = dir.resolve("cache")
        val partial = cache.resolve("roms/1234/mario.cci.partial")
        Files.createDirectories(partial.parent)
        Files.write(partial, "hello".toByteArray())
        Files.writeString(cache.resolve("roms/1234/mario.cci.partial.validator"), "\"v1\"")
        runClient(token = { "rmm_test" }) { client ->
            assertEquals("hello-rom", Files.readString(client.downloadRom(1234, "mario.cci", cache)))
        }
        assertEquals(listOf("bytes=5-", null), server.recorded.map { header(it, "Range") })
    }

    @Test
    fun aRomThatDoesNotMatchItsHashIsNotKept() {
        server.route("GET", "/api/roms/1234/content/mario.cci") { exchange, _ ->
            bytes(exchange, 200, "truncated".toByteArray())
        }
        val cache = dir.resolve("cache")
        runClient(token = { "rmm_test" }) { client ->
            val error = suspendCatching {
                client.downloadRom(1234, "mario.cci", cache, expectedMd5 = md5Hex("the-real-rom".toByteArray()))
            }.exceptionOrNull()
            assertTrue(error is RommResponseException)
        }
        assertFalse(Files.exists(cache.resolve("roms/1234/mario.cci")))
        assertFalse(Files.exists(cache.resolve("roms/1234/mario.cci.partial")))
    }

    @Test
    fun aCachedRomIsCheckedOnceAndReplacedWhenRommHasANewOne() {
        val cache = dir.resolve("cache")
        val target = RommClient.romCacheFile(cache, 1234, "mario.cci", emptyList())
        Files.createDirectories(target.parent)
        Files.writeString(target, "old-rom")
        server.route("GET", "/api/roms/1234/content/mario.cci") { exchange, _ ->
            bytes(exchange, 200, "new-rom".toByteArray())
        }
        runClient(token = { "rmm_test" }) { client ->
            // A file from before verification markers is hashed and kept when it still matches.
            client.downloadRom(1234, "mario.cci", cache, expectedMd5 = md5Hex("old-rom".toByteArray()))
            assertTrue(RommClient.isVerifiedRom(target, 7, md5Hex("old-rom".toByteArray())))
            assertTrue(server.recorded.isEmpty())
            client.downloadRom(1234, "mario.cci", cache, expectedMd5 = md5Hex("new-rom".toByteArray()))
        }
        assertEquals("new-rom", Files.readString(target))
        assertEquals(1, server.recorded.size)
        assertTrue(RommClient.isVerifiedRom(target, null, md5Hex("new-rom".toByteArray())))
        assertFalse(RommClient.isVerifiedRom(target, null, md5Hex("old-rom".toByteArray())))
    }

    @Test
    fun twoDownloadsOfOneRomTakeTurns() {
        val rom = ByteArray(256 * 1024) { (it % 251).toByte() }
        server.route("GET", "/api/roms/1234/content/big.bin") { exchange, _ ->
            Thread.sleep(50)
            bytes(exchange, 200, rom)
        }
        val cache = dir.resolve("cache")
        runClient(token = { "rmm_test" }) { client ->
            kotlinx.coroutines.coroutineScope {
                val first = async(Dispatchers.IO) { client.downloadRom(1234, "big.bin", cache, expectedMd5 = md5Hex(rom)) }
                val second = async(Dispatchers.IO) { client.downloadRom(1234, "big.bin", cache, expectedMd5 = md5Hex(rom)) }
                assertEquals(first.await(), second.await())
            }
        }
        assertEquals(1, server.recorded.size)
        assertTrue(rom.contentEquals(Files.readAllBytes(RommClient.romCacheFile(cache, 1234, "big.bin", emptyList()))))
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
        val created = Json.parseToJsonElement(server.recorded.last().body.toString(Charsets.UTF_8)).jsonObject
        assertEquals(true, created.getValue("allow_existing").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun aDeviceExistsConflictReusesTheReturnedId() {
        server.route("POST", "/api/devices") { exchange, _ ->
            json(
                exchange,
                409,
                """{"detail":{"error":"device_exists","message":"A device with this fingerprint already exists","device_id":"3f1c2b9e-8a4d-4c7e-9f21-6d0b5a7e1c34"}}""",
            )
        }
        runClient(token = { "rmm_test" }) { client ->
            val existing = client.registerDevice(null, "Thor", "0.2.0")
            assertEquals("3f1c2b9e-8a4d-4c7e-9f21-6d0b5a7e1c34", existing.deviceId)
            assertEquals("0.2.0", existing.clientVersion)
        }
        assertEquals(listOf("POST"), server.recorded.map { it.method })
    }

    @Test
    fun aConflictThatIsNotDeviceExistsStillFails() {
        server.route("POST", "/api/devices") { exchange, _ ->
            json(exchange, 409, """{"detail":"something else"}""")
        }
        runClient(token = { "rmm_test" }) { client ->
            val error = suspendCatching { client.registerDevice(null, "Thor", "0.2.0") }.exceptionOrNull()
            val http = error as RommHttpException
            assertEquals(409, http.status)
        }
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
    fun archiveNamesStayUniqueForTheSameSave() {
        assertEquals(
            "mario [2026-04-18_09-42-01-abcd1234].srm",
            archiveFileName("mario.srm", "2026-04-18_09-42-01-abcd1234"),
        )
        assertEquals("mario [stamp]", archiveFileName("mario", "stamp"))
        assertFalse(archiveStamp() == archiveStamp())
    }

    @Test
    fun deleteMatchesFileNameAndEmulatorAndMovesTheFileToTrash() {
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
                    "action": "delete",
                    "rom_id": 1234,
                    "save_id": null,
                    "file_name": "mario.srm",
                    "slot": "autosave",
                    "emulator": "azahar",
                    "reason": "deleted on server"
                  }],
                  "total_upload": 0,
                  "total_download": 0,
                  "total_conflict": 0,
                  "total_no_op": 0,
                  "total_delete": 1
                }
                """.trimIndent(),
            )
        }
        server.route("POST", "/api/sync/sessions/42/complete") { exchange, _ ->
            json(exchange, 200, fixture("sync-complete.json"))
        }
        val slot = dir.resolve("saves")
        Files.createDirectories(slot)
        val mario = slot.resolve("mario.srm")
        val otherName = slot.resolve("other.srm")
        val otherEmulator = slot.resolve("mario-melonds.srm")
        Files.writeString(mario, "drop")
        Files.writeString(otherName, "keep-name")
        Files.writeString(otherEmulator, "keep-emulator")
        val saves = listOf(
            localSave("drop").copy(file = otherEmulator, emulator = "melonds"),
            localSave("keep-name").copy(fileName = "other.srm", file = otherName),
            localSave("drop").copy(file = mario),
        )
        val ledger = SaveLedger(dir.resolve("ledger.json"))
        ledger.markSynced("autosave", md5Hex("drop".toByteArray()), "azahar")
        runClient(token = { "rmm_test" }) { client ->
            val report = client.syncSaves(
                "device-1",
                saves,
                destination = { slot.resolve("unused.srm") },
                ledger = ledger,
            )
            assertEquals(listOf(mario), report.deleted)
            assertTrue(report.completedSession)
        }
        assertFalse(Files.exists(mario))
        assertEquals("keep-name", Files.readString(otherName))
        assertEquals("keep-emulator", Files.readString(otherEmulator))
        val trashed = Files.list(slot.resolve(".trash")).use { stream -> stream.toList() }
        assertEquals(1, trashed.size)
        assertEquals("drop", Files.readString(trashed.single()))
        assertTrue(trashed.single().fileName.toString().endsWith("-mario.srm"))
        assertFalse(server.recorded.any { it.path == "/api/saves" })
        assertNull(ledger.slot("autosave"))
    }

    @Test
    fun aServerDeleteKeepsUnsyncedProgressOnTheServerFirst() {
        syncRoutes(operation("delete", saveId = null, serverHash = null))
        server.route("POST", "/api/saves") { exchange, _ ->
            json(exchange, 200, """{"id":60,"rom_id":1234,"file_name":"mario.srm"}""")
        }
        val save = localSave("progress")
        val ledger = SaveLedger(dir.resolve("ledger.json"))
        ledger.markSynced("autosave", md5Hex("older".toByteArray()), "azahar")
        runClient(token = { "rmm_test" }) { client ->
            val report = client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = ledger)
            assertEquals(listOf(save.file), report.deleted)
        }
        val upload = server.recorded.single { it.path == "/api/saves" }
        assertFalse(upload.query.orEmpty().contains("slot="))
        assertTrue(upload.body.toString(Charsets.ISO_8859_1).contains("progress"))
        assertEquals(listOf("progress"), trashed())
    }

    @Test
    fun aServerDeleteThatCannotKeepUnsyncedProgressLeavesTheFile() {
        syncRoutes(operation("delete", saveId = null, serverHash = null))
        server.route("POST", "/api/saves") { exchange, _ -> exchange.close() }
        val save = localSave("progress")
        runClient(token = { "rmm_test" }) { client ->
            val report = client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = SaveLedger(dir.resolve("ledger.json")))
            assertTrue(report.deleted.isEmpty())
            assertEquals(listOf("mario.srm"), report.queued)
        }
        assertEquals("progress", Files.readString(save.file))
    }

    @Test
    fun aRefusedNoOpUploadKeepsBothInsteadOfLooping() {
        syncRoutes(operation("no_op", saveId = 99, serverHash = md5Hex(SERVER_SAVE.toByteArray())))
        server.route("POST", "/api/saves") { exchange, _ ->
            if (exchange.requestURI.rawQuery.orEmpty().contains("slot=")) {
                json(exchange, 409, """{"detail":"Slot has a newer save since your last sync"}""")
            } else {
                json(exchange, 200, """{"id":61,"rom_id":1234,"file_name":"mario.srm"}""")
            }
        }
        server.route("GET", "/api/saves/99/content") { exchange, _ ->
            bytes(exchange, 200, SERVER_SAVE.toByteArray())
        }
        val save = localSave("progress")
        val ledger = SaveLedger(dir.resolve("ledger.json"))
        ledger.markSynced("autosave", md5Hex("older".toByteArray()), "azahar")
        runClient(token = { "rmm_test" }) { client ->
            val report = client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = ledger)
            assertEquals(61L, report.keptBoth.single().archivedSaveId)
        }
        assertEquals(SERVER_SAVE, Files.readString(save.file))
    }

    @Test
    fun anUploadOfBytesAlreadyAgreedTakesTheServerCopyInstead() {
        syncRoutes(operation("upload", saveId = 99, serverHash = md5Hex(SERVER_SAVE.toByteArray())))
        server.route("GET", "/api/saves/99/content") { exchange, _ ->
            bytes(exchange, 200, SERVER_SAVE.toByteArray())
        }
        val save = localSave("agreed")
        val ledger = SaveLedger(dir.resolve("ledger.json"))
        ledger.markSynced("autosave", md5Hex("agreed".toByteArray()), "azahar")
        runClient(token = { "rmm_test" }) { client ->
            client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = ledger)
        }
        assertFalse(server.recorded.any { it.path == "/api/saves" })
        assertEquals(SERVER_SAVE, Files.readString(save.file))
    }

    @Test
    fun anArchiveIsCheckedBySizeOnlyBecauseRommHashesItsMembers() {
        server.route("GET", "/api/roms/1234/content/game.zip") { exchange, _ ->
            bytes(exchange, 200, "zip-bytes".toByteArray())
        }
        runClient(token = { "rmm_test" }) { client ->
            val file = client.downloadRom(1234, "game.zip", dir.resolve("cache"), expectedSize = 9, expectedMd5 = "0".repeat(32))
            assertEquals("zip-bytes", Files.readString(file))
        }
    }

    @Test
    fun aCorruptLedgerIsSetAsideAndEverySlotCountsAsUnsynced() {
        val file = dir.resolve("ledger.json")
        Files.writeString(file, "{not json")
        val ledger = SaveLedger(file)
        assertTrue(ledger.unsynced("autosave", "abc"))
        ledger.markSynced("autosave", "abc", null)
        assertFalse(ledger.unsynced("autosave", "abc"))
        assertEquals(1L, Files.list(dir).use { s -> s.filter { it.fileName.toString().startsWith("ledger.json.unreadable-") }.count() })
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
            bytes(exchange, 200, SERVER_SAVE.toByteArray())
        }
        server.route("POST", "/api/saves/99/downloaded") { exchange, _ ->
            json(exchange, 200, """{"id":99}""")
        }
        server.route("POST", "/api/sync/sessions/42/complete") { exchange, _ ->
            json(exchange, 200, fixture("sync-complete.json"))
        }
        val save = localSave("local")
        runClient(token = { "rmm_test" }) { client ->
            repeat(2) {
                Files.writeString(save.file, "local")
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
                assertEquals(SERVER_SAVE, Files.readString(save.file))
            }
        }
        val uploads = server.recorded.filter { it.method == "POST" && it.path == "/api/saves" }
        assertEquals(2, uploads.size)
        val names = uploads.map { upload ->
            assertFalse(upload.query.orEmpty().contains("slot="))
            assertFalse(upload.query.orEmpty().contains("overwrite=true"))
            assertTrue(upload.query.orEmpty().contains("overwrite=false"))
            val uploaded = upload.body.toString(Charsets.ISO_8859_1)
            assertTrue(uploaded.contains("local"))
            val archivedName = Regex("""filename="([^"]+)"""").find(uploaded)!!.groupValues[1]
            assertTrue(archivedName.startsWith("mario ["))
            assertTrue(archivedName.endsWith("].srm"))
            assertFalse(archivedName == "mario.srm")
            archivedName
        }
        assertFalse(names[0] == names[1])
        val download = server.recorded.first { it.path == "/api/saves/99/content" }
        assertTrue(download.query.orEmpty().contains("optimistic=false"))
        val confirm = server.recorded.first { it.path == "/api/saves/99/downloaded" }
        assertTrue(confirm.body.toString(Charsets.UTF_8).contains(md5Hex(SERVER_SAVE.toByteArray())))
        val complete = server.recorded.first { it.path == "/api/sync/sessions/42/complete" }
        assertFalse(complete.body.toString(Charsets.UTF_8).contains("play_sessions"))
    }

    @Test
    fun aDownloadOverUnsyncedProgressKeepsBoth() {
        syncRoutes(fixture("negotiate-download.json"))
        server.route("POST", "/api/saves") { exchange, _ ->
            json(exchange, 200, """{"id":50,"rom_id":1234,"file_name":"mario.srm"}""")
        }
        server.route("GET", "/api/saves/99/content") { exchange, _ ->
            bytes(exchange, 200, SERVER_SAVE.toByteArray())
        }
        val save = localSave("local")
        val ledger = SaveLedger(dir.resolve("ledger.json"))
        runClient(token = { "rmm_test" }) { client ->
            val report = client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = ledger)
            assertEquals(50L, report.keptBoth.single().archivedSaveId)
        }
        assertEquals(SERVER_SAVE, Files.readString(save.file))
        val upload = server.recorded.single { it.path == "/api/saves" }
        assertFalse(upload.query.orEmpty().contains("slot="))
        assertTrue(upload.body.toString(Charsets.ISO_8859_1).contains("local"))
        assertEquals(listOf("local"), trashed())
        assertEquals(md5Hex(SERVER_SAVE.toByteArray()), ledger.slot("autosave")!!.syncedHash)
        assertEquals(md5Hex("local".toByteArray()), ledger.slot("autosave")!!.archivedHash)
    }

    @Test
    fun aDownloadOverASyncedSaveReplacesItAndKeepsTheOldCopyInTrash() {
        syncRoutes(fixture("negotiate-download.json"))
        server.route("GET", "/api/saves/99/content") { exchange, _ ->
            bytes(exchange, 200, SERVER_SAVE.toByteArray())
        }
        val save = localSave("local")
        val ledger = SaveLedger(dir.resolve("ledger.json"))
        ledger.markSynced("autosave", md5Hex("local".toByteArray()), "azahar")
        runClient(token = { "rmm_test" }) { client ->
            val report = client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = ledger)
            assertTrue(report.keptBoth.isEmpty())
            assertEquals(listOf(save.file), report.downloaded)
        }
        assertEquals(SERVER_SAVE, Files.readString(save.file))
        assertFalse(server.recorded.any { it.path == "/api/saves" })
        assertEquals(listOf("local"), trashed())
        val confirm = server.recorded.single { it.path == "/api/saves/99/downloaded" }
        assertTrue(confirm.body.toString(Charsets.UTF_8).contains(md5Hex(SERVER_SAVE.toByteArray())))
    }

    @Test
    fun aDownloadThatDoesNotMatchItsHashLeavesTheSaveAlone() {
        syncRoutes(fixture("negotiate-download.json"))
        server.route("GET", "/api/saves/99/content") { exchange, _ ->
            bytes(exchange, 200, "torn".toByteArray())
        }
        val save = localSave("local")
        val ledger = SaveLedger(dir.resolve("ledger.json"))
        ledger.markSynced("autosave", md5Hex("local".toByteArray()), "azahar")
        runClient(token = { "rmm_test" }) { client ->
            val report = client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = ledger)
            assertTrue(report.downloaded.isEmpty())
            assertEquals(1, report.failed.size)
        }
        assertEquals("local", Files.readString(save.file))
        assertFalse(Files.exists(dir.resolve("mario.srm.partial")))
        assertFalse(server.recorded.any { it.path == "/api/saves/99/downloaded" })
        assertEquals(md5Hex("local".toByteArray()), ledger.slot("autosave")!!.syncedHash)
    }

    @Test
    fun aNoOpThatWouldLeaveNewerLocalBytesBehindUploadsThem() {
        syncRoutes(operation("no_op", saveId = 99, serverHash = md5Hex("older".toByteArray())))
        server.route("POST", "/api/saves") { exchange, _ ->
            json(exchange, 200, """{"id":51,"rom_id":1234,"file_name":"mario.srm"}""")
        }
        val save = localSave("local")
        val ledger = SaveLedger(dir.resolve("ledger.json"))
        ledger.markSynced("autosave", md5Hex("older".toByteArray()), "azahar")
        runClient(token = { "rmm_test" }) { client ->
            val report = client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = ledger)
            assertEquals(listOf(51L), report.uploadedSaveIds)
        }
        assertTrue(server.recorded.single { it.path == "/api/saves" }.query.orEmpty().contains("slot=autosave"))
        assertEquals(md5Hex("local".toByteArray()), ledger.slot("autosave")!!.syncedHash)
    }

    @Test
    fun aMatchingNoOpRecordsTheSlotAsSynced() {
        syncRoutes(operation("no_op", saveId = 99, serverHash = md5Hex("local".toByteArray())))
        val save = localSave("local")
        val ledger = SaveLedger(dir.resolve("ledger.json"))
        runClient(token = { "rmm_test" }) { client ->
            client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = ledger)
        }
        assertFalse(server.recorded.any { it.path == "/api/saves" })
        assertEquals(md5Hex("local".toByteArray()), ledger.slot("autosave")!!.syncedHash)
    }

    @Test
    fun anUnreachableUploadStaysUnsyncedForTheNextNegotiate() {
        syncRoutes(operation("upload", saveId = null, serverHash = null))
        server.route("POST", "/api/saves") { exchange, _ ->
            exchange.close()
        }
        val save = localSave("local")
        val ledger = SaveLedger(dir.resolve("ledger.json"))
        runClient(token = { "rmm_test" }) { client ->
            val report = client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = ledger)
            assertEquals(listOf("mario.srm"), report.queued)
            assertTrue(report.uploadedSaveIds.isEmpty())
        }
        assertNull(ledger.slot("autosave"))
        assertTrue(ledger.unsynced("autosave", md5Hex("local".toByteArray())))
        assertEquals("local", Files.readString(save.file))
    }

    @Test
    fun aRetriedConflictDoesNotArchiveTheSameBytesTwice() {
        syncRoutes(fixture("negotiate-download.json").replace(""""action": "download"""", """"action": "conflict""""))
        server.route("POST", "/api/saves") { exchange, _ ->
            json(exchange, 200, """{"id":50,"rom_id":1234,"file_name":"mario.srm"}""")
        }
        var downloads = 0
        server.route("GET", "/api/saves/99/content") { exchange, _ ->
            downloads += 1
            if (downloads == 1) json(exchange, 503, """{"detail":"down"}""") else bytes(exchange, 200, SERVER_SAVE.toByteArray())
        }
        val save = localSave("local")
        val ledger = SaveLedger(dir.resolve("ledger.json"))
        runClient(token = { "rmm_test" }) { client ->
            val first = client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = ledger)
            assertEquals(1, first.failed.size)
            assertEquals("local", Files.readString(save.file))
            val second = client.syncSaves("device-1", listOf(save), destination = { save.file }, ledger = ledger)
            assertNull(second.keptBoth.single().archivedSaveId)
        }
        assertEquals(1, server.recorded.count { it.path == "/api/saves" })
        assertEquals(SERVER_SAVE, Files.readString(save.file))
    }

    @Test
    fun theTrashKeepsOnlyTheNewestCopies() {
        val file = dir.resolve("slot/sav.sav")
        Files.createDirectories(file.parent)
        repeat(SAVE_TRASH_KEEP + 3) { index ->
            Files.writeString(file, "v$index")
            copySaveToTrash(file)
            Thread.sleep(2)
        }
        val kept = Files.list(file.parent.resolve(TRASH_DIR)).use { it.toList() }
        assertEquals(SAVE_TRASH_KEEP, kept.size)
        assertTrue(kept.any { Files.readString(it) == "v${SAVE_TRASH_KEEP + 2}" })
        assertFalse(kept.any { Files.readString(it) == "v0" })
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
            val unauthorized = suspendCatching { client.heartbeat() }.exceptionOrNull()
            assertTrue(unauthorized is RommUnauthorized)
            assertEquals(401, (unauthorized as RommHttpException).status)
            val missing = suspendCatching { client.heartbeat() }.exceptionOrNull()
            assertTrue(missing is RommHttpException)
            assertFalse(missing is RommUnauthorized || missing is RommForbidden)
            assertEquals(404, (missing as RommHttpException).status)
            val down = suspendCatching { client.heartbeat() }.exceptionOrNull() as RommHttpException
            assertEquals(503, down.status)
        }
        server.route("GET", "/api/platforms") { exchange, _ ->
            json(exchange, 403, """{"detail":"forbidden"}""")
        }
        runClient(token = { "rmm_test" }) { client ->
            val forbidden = suspendCatching { client.platforms() }.exceptionOrNull()
            assertTrue(forbidden is RommForbidden)
            assertEquals(403, (forbidden as RommHttpException).status)
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
    fun cancelDuringUploadDoesNotMarkTheSaveSyncedOrCompleteTheSession() {
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
        val save = localSave("local")
        val ledger = SaveLedger(dir.resolve("ledger.json"))
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
                        ledger = ledger,
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
        assertNull(ledger.slot("autosave"))
        assertFalse(server.recorded.any { it.path.contains("/complete") })
        assertEquals("local", Files.readString(save.file))
    }

    @Test
    fun normalizeOriginFoldsSchemeAndHostCase() {
        assertEquals("https://romm.example", RommClient.normalizeOrigin("HTTPS://Romm.Example"))
        assertEquals("http://192.168.1.20:8080", RommClient.normalizeOrigin("HTTP://192.168.1.20:8080/api"))
        assertEquals("http://romm.local/Games", RommClient.normalizeOrigin("http://Romm.Local/Games/"))
        assertEquals("https://romm.example", RommClient.normalizeOrigin("HTTPS://Romm.Example/API"))
        assertEquals("http://romm.local/Games", RommClient.normalizeOrigin("http://romm.local/Games/Api"))
        assertEquals("http://[::1]", RommClient.normalizeOrigin("HTTP://[::1]/api"))
        val userinfo = runCatching { RommClient.normalizeOrigin("HTTP://User:s3cret-token@Romm.Example/API") }
        val userinfoError = userinfo.exceptionOrNull()
        assertTrue(userinfoError is IllegalArgumentException)
        assertTrue(userinfoError?.message?.contains("password") == true)
        assertFalse(userinfoError?.message?.contains("s3cret-token") == true)
        assertEquals(null, normalizeSetupOrigin("http://User:s3cret-token@romm.example"))
        assertEquals(null, normalizeSetupOrigin("http://User:s3cret-token@192.168.1.20"))
        val rejected = runCatching { RommClient.normalizeOrigin("FTP://romm.example") }
        assertTrue(rejected.exceptionOrNull() is IllegalArgumentException)
        assertEquals(
            "http://192.168.1.20",
            normalizeSetupOrigin("  HTTP://192.168.1.20/api/  "),
        )
        assertEquals(null, normalizeSetupOrigin("  HTTP://Romm.Example/api/  "))
    }

    @Test
    fun cleartextHttpIsOnlyALanAddress() {
        assertEquals("http://10.1.2.3", RommClient.normalizeOrigin("http://10.1.2.3"))
        assertEquals("http://127.0.0.1", RommClient.normalizeOrigin("http://127.0.0.1"))
        assertEquals("http://172.16.0.1", RommClient.normalizeOrigin("http://172.16.0.1"))
        assertEquals("http://172.31.255.255", RommClient.normalizeOrigin("http://172.31.255.255"))
        assertEquals("http://169.254.1.1", RommClient.normalizeOrigin("http://169.254.1.1"))
        assertEquals("http://nas.home.arpa", RommClient.normalizeOrigin("http://NAS.Home.Arpa"))
        assertEquals("http://printer.localhost", RommClient.normalizeOrigin("http://printer.localhost"))
        assertEquals("http://[fe80::1]", RommClient.normalizeOrigin("http://[fe80::1]"))
        assertEquals("http://[fd00::1]", RommClient.normalizeOrigin("http://[FD00::1]"))
        assertEquals("http://[::ffff:192.168.1.20]", RommClient.normalizeOrigin("http://[::ffff:192.168.1.20]"))
        assertEquals("http://100.64.0.1:8080", RommClient.normalizeOrigin("http://100.64.0.1:8080"))
        assertEquals("http://100.127.255.255", RommClient.normalizeOrigin("http://100.127.255.255"))
        assertEquals("http://nas.tail1234.ts.net", RommClient.normalizeOrigin("http://NAS.tail1234.ts.net"))
        assertEquals("http://[fd7a:115c:a1e0::1]", RommClient.normalizeOrigin("http://[fd7a:115c:a1e0::1]"))
        listOf(
            "http://romm.example",
            "http://8.8.8.8",
            "http://172.15.0.1",
            "http://172.32.0.1",
            "http://[2001:db8::1]",
            "http://evil.local.example",
            "http://100.63.255.255",
            "http://100.128.0.1",
            "http://ts.net",
            "http://nas.ts.net.example",
        ).forEach { raw ->
            val failed = runCatching { RommClient.normalizeOrigin(raw) }.exceptionOrNull()
            assertTrue(raw, failed is IllegalArgumentException)
            assertTrue(failed?.message?.contains("LAN") == true)
            assertEquals(null, normalizeSetupOrigin(raw))
            assertEquals(CLEARTEXT_LAN_ONLY, cleartextCredentialWarning(raw))
        }
        assertEquals(CLEARTEXT_CREDENTIAL_WARNING, cleartextCredentialWarning("http://User:s3cret-token@192.168.1.20"))
        assertFalse(
            cleartextCredentialWarning("http://User:s3cret-token@romm.example").orEmpty().contains("s3cret"),
        )
        assertFalse(httpCleartextAllowed("http://romm.example", "http://romm.example/api/heartbeat"))
        assertTrue(httpCleartextAllowed("http://nas.local", "http://nas.local/api/heartbeat"))
    }

    @Test
    fun cleartextGuardRejectsAnotherHttpHostAndStillHintsHttps() {
        assertTrue(httpCleartextAllowed("http://192.168.1.20:8080", "http://192.168.1.20:8080/api/heartbeat"))
        assertTrue(httpCleartextAllowed("http://192.168.1.20", "http://192.168.1.20:80/api/heartbeat"))
        assertFalse(httpCleartextAllowed("http://192.168.1.20:8080", "http://evil.example/api/heartbeat"))
        assertFalse(httpCleartextAllowed("http://192.168.1.20:8080", "http://192.168.1.20:9090/api/heartbeat"))
        assertTrue(httpCleartextAllowed("http://192.168.1.20:8080", "https://192.168.1.20/api/heartbeat"))
        assertFalse(sameSchemeRedirectLeavesOrigin("https://romm.example", "https://romm.example/api/roms"))
        assertFalse(sameSchemeRedirectLeavesOrigin("https://romm.example", "https://romm.example:443/api/roms"))
        assertTrue(sameSchemeRedirectLeavesOrigin("https://romm.example", "https://evil.example/api/roms"))
        assertTrue(sameSchemeRedirectLeavesOrigin("https://romm.example", "https://romm.example:8443/api/roms"))
        assertFalse(sameSchemeRedirectLeavesOrigin("http://romm.example", "https://evil.example/api/roms"))
        assertFalse(sameSchemeRedirectLeavesOrigin("https://romm.example", "http://evil.example/api/roms"))

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
    fun bearerStaysOnTheConfiguredOrigin() {
        val other = MockRomm()
        try {
            other.route("GET", "/api/platforms") { exchange, _ ->
                json(exchange, 200, fixture("platforms.json"))
            }
            server.route("GET", "/api/platforms") { exchange, _ ->
                exchange.responseHeaders.add("Location", "${other.origin}/api/platforms")
                exchange.sendResponseHeaders(302, -1)
                exchange.responseBody.close()
            }
            val rejected = runCatching {
                runClient(token = { "rmm_secret" }) { it.platforms() }
            }.exceptionOrNull()
            assertTrue(rejected is RommUnavailable)
            assertTrue(rejected?.message?.contains("Cleartext") == true)
            assertTrue(other.recorded.isEmpty())
            assertEquals("Bearer rmm_secret", header(server.recorded.single(), "Authorization"))
        } finally {
            other.close()
        }

        var hops = 0
        server.route("GET", "/api/roms") { exchange, _ ->
            hops += 1
            if (hops == 1) {
                exchange.responseHeaders.add("Location", "${server.origin}/api/roms?landed=1")
                exchange.sendResponseHeaders(302, -1)
                exchange.responseBody.close()
            } else {
                json(exchange, 200, fixture("roms.json"))
            }
        }
        runClient(token = { "rmm_secret" }) { client ->
            client.roms()
        }
        val followed = server.recorded.filter { it.path == "/api/roms" }
        assertTrue(followed.size >= 2)
        assertTrue(followed.all { header(it, "Authorization") == "Bearer rmm_secret" })
        assertTrue(followed.last().query.orEmpty().contains("landed=1"))
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

    @Test
    fun artworkLoadsFromTheOriginWithoutTheToken() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        server.route("GET", "/assets/romm/resources/roms/1/7/cover/big.png") { exchange, _ ->
            bytes(exchange, 200, png)
        }
        val loaded = runClient(token = { "rmm_should_not_be_sent" }) { client ->
            client.artwork("${server.origin}/assets/romm/resources/roms/1/7/cover/big.png")
        }
        assertTrue(png.contentEquals(loaded))
        val sent = server.recorded.single()
        assertNull(sent.headers.entries.firstOrNull { it.key.equals("Authorization", ignoreCase = true) })
    }

    @Test
    fun artworkOnAnotherOriginIsRefusedBeforeAnyRequest() {
        val refused = runClient { client ->
            runCatching { client.artwork("https://cdn.example/cover.jpg") }.exceptionOrNull()
        }
        assertTrue(refused is RommResponseException)
        assertTrue(server.recorded.isEmpty())
    }

    @Test
    fun artworkOverTheLimitIsRefused() {
        server.route("GET", "/assets/romm/resources/roms/1/7/cover/big.png") { exchange, _ ->
            bytes(exchange, 200, ByteArray(32))
        }
        val refused = runClient { client ->
            runCatching {
                client.artwork("${server.origin}/assets/romm/resources/roms/1/7/cover/big.png", maxBytes = 16)
            }.exceptionOrNull()
        }
        assertTrue(refused is RommResponseException)
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

    private fun syncRoutes(negotiate: String) {
        server.route("GET", "/openapi.json") { exchange, _ ->
            json(exchange, 200, fixture("openapi-5.4.0-alpha.2.json"))
        }
        server.route("POST", "/api/sync/negotiate") { exchange, _ ->
            json(exchange, 200, negotiate)
        }
        server.route("POST", "/api/saves/99/downloaded") { exchange, _ ->
            json(exchange, 200, """{"id":99}""")
        }
        server.route("POST", "/api/sync/sessions/42/complete") { exchange, _ ->
            json(exchange, 200, fixture("sync-complete.json"))
        }
    }

    private fun operation(action: String, saveId: Long?, serverHash: String?): String = """
        {
          "session_id": 42,
          "operations": [{
            "action": "$action",
            "rom_id": 1234,
            "save_id": ${saveId ?: "null"},
            "file_name": "mario.srm",
            "slot": "autosave",
            "emulator": "azahar",
            "reason": "test",
            "server_content_hash": ${serverHash?.let { "\"$it\"" } ?: "null"}
          }],
          "total_upload": 0,
          "total_download": 0,
          "total_conflict": 0,
          "total_no_op": 0,
          "total_delete": 0
        }
    """.trimIndent()

    private fun trashed(): List<String> {
        val trash = dir.resolve(TRASH_DIR)
        if (!Files.isDirectory(trash)) return emptyList()
        return Files.list(trash).use { stream -> stream.toList() }.map { Files.readString(it) }
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

/** Bytes whose MD5 is the hash `negotiate-download.json` names. */
private const val SERVER_SAVE = "The quick brown fox jumps over the lazy dog."

private fun kotlinx.serialization.json.JsonObject.req(name: String): String =
    getValue(name).jsonPrimitive.content
