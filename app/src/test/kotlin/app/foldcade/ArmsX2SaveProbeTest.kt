package app.foldcade

import android.content.ContextWrapper
import app.foldcade.api.plugin.*
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.plugins.armsx2.ArmsX2Player
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.net.URI
import kotlin.io.path.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArmsX2SaveProbeTest {
    @get:Rule val temporary = TemporaryFolder()
    private val player = ArmsX2Player()
    private val bound = FolderRef("primary:ARMSX2", "ARMSX2")
    private val outside = FolderRef("primary:Outside Cards", "Outside Cards")
    private val outsideUri = treeUri(SharedPaths.EXTERNAL_STORAGE_AUTHORITY, outside.identity)
    private val config = ConfigSavePath(null, "Disposable.ps2", outside, proves = false)
    private val game = Game("server", "first", player.platformId, Availability.Cached, "Test disc")
    private val boundRead = RootReading(bound, true, emptyList(), null, true, true, null, spotFile = "memcards/Disposable.ps2")

    @Test fun configAndAnUnchangedMirrorGiveNoPreLaunchOrSessionVerdict() {
        val signals = listOf(SignalReading("PCSX2-Android.ini", outside.identity, 199L, false, false),
            SignalReading("armsx2-settings.json", null, 3L, false, false))
        val now = snapshot(boundRead.copy(signals = signals), null)
        assertEquals(RootVerdict.NoNews, judgeRoots(now, LocationFact.Bound(bound)))
        assertEquals(RootVerdict.NoNews, judgeRoots(now.copy(configUnavailable = true), LocationFact.Bound(bound)))
        assertEquals(SessionVerdict.NoNews, judgeSession(now.copy(atMillis = 150L), now, 60_000L))
    }

    @Test fun insideWritesVerifyAndAChangedCardNameCannotFabricateAWrite() {
        val before = snapshot(boundRead, null).copy(configSavePath = null)
        val after = before.copy(roots = listOf(boundRead.copy(spotHash = "new bytes")))
        assertEquals(SessionVerdict.Wrote(bound), judgeSession(before, after, 75L))
        assertEquals(SessionVerdict.NoNews, judgeSession(before,
            after.copy(roots = listOf(boundRead.copy(spotHash = "new bytes", spotFile = "memcards/Other.ps2"))), 75L))
    }

    @Test fun onlyTheTestGamesChangedFolderCanConfirmAnOutsideCard() {
        val docs = CardDocuments()
        val probe = probe(docs)
        val before = snapshot(boundRead, probe.outsideReading(config, outsideUri))
        docs.otherGame = true
        val otherGameWrote = snapshot(boundRead, probe.outsideReading(config, outsideUri))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, otherGameWrote, 100L))
        docs.progress = byteArrayOf(5)
        val after = snapshot(boundRead, probe.outsideReading(config, outsideUri))
        assertEquals(SessionVerdict.WroteElsewhere(outside), judgeSession(before, after, 100L))
        assertEquals(SessionVerdict.NoNews, judgeSession(snapshot(boundRead, null), after, 100L))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, after.copy(installFirstMillis = 9L), 100L))
        assertEquals(SessionVerdict.NoNews, judgeSession(before,
            after.copy(roots = listOf(boundRead.copy(spotFile = "memcards/Another.ps2"), after.roots.last())), 100L))
    }

    @Test fun rawAndUnreadableCardsCannotProveAnOutsideWrite() {
        val docs = CardDocuments()
        val probe = probe(docs)
        val before = snapshot(boundRead, probe.outsideReading(config, outsideUri))
        docs.raw = true
        assertFalse(probe.outsideReading(config, outsideUri)!!.spotReadable)
        assertEquals(SessionVerdict.NoNews, judgeSession(before, snapshot(boundRead, probe.outsideReading(config, outsideUri)), 100L))
    }

    @Test fun aConfirmedWrongDataRootGatesEveryArmsX2Game() {
        val store = SaveProtectionStore(temporary.newFolder().toPath())
        val probes = saveProbes(shelf(CardDocuments()), store)
        val live = FolderRef("primary:Games/ARMSX2", "Games/ARMSX2")
        val now = snapshot(boundRead, boundRead.copy(root = live, bound = false, spotHash = "other root"))
        probes.apply(player, now, RootVerdict.UsesOther(live, Evidence(EvidenceKind.SessionWrite, "Game saved there"), now.atMillis), game)
        val incident = store.incidents().single()
        assertEquals("WrongFolder:armsx2", incident.id)
        assertEquals(live, saveFixTarget(incident))
        assertNull(incident.alternateSavePath)
        assertTrue(incident.blocksLaunch && incident.appliesTo(game))
        assertTrue(incident.appliesTo(game.copy(remoteKey = "second")))
        assertTrue(incident.appliesTo(game.copy(backendId = LocalFolderBackend.ID)))
    }

    @Test fun noteSessionDoesNotVerifyAWatchedWriteWhileTheConfiguredCardIsOutside() {
        val store = SaveProtectionStore(temporary.newFolder().toPath())
        val probes = saveProbes(shelf(CardDocuments()), store)
        val beforeOutside = snapshot(boundRead, null)
        val afterOutside = snapshot(boundRead.copy(spotHash = "written"), null).copy(atMillis = 300)
        val beforeInside = beforeOutside.copy(configSavePath = null)
        val afterInside = afterOutside.copy(configSavePath = null)
        for ((before, after) in listOf(beforeInside to afterOutside, beforeOutside to afterInside)) {
            assertEquals(SessionVerdict.Wrote(bound), judgeSession(before, after, 100))
            probes.noteSession(player, game, before, after)
            assertTrue(store.location(player.id, bound, after.packageName, after.installFirstMillis) is LocationFact.Bound)
        }
        probes.noteSession(player, game, beforeInside, afterInside)
        assertTrue(probes.location(player, afterInside) is LocationFact.Verified)
        assertTrue((probes.location(player, afterOutside) as LocationFact.Bound).cardOutside)
    }

    @Test fun aConfirmedOutsideCardGatesOnlyItsGameAndUploadsOnlyAnArchive() = runBlocking {
        val docs = CardDocuments()
        val saves = shelf(docs)
        val probe = ArmsX2SaveProbe(player, saves, disc())
        val store = SaveProtectionStore(temporary.newFolder().toPath())
        val before = snapshot(boundRead, probe.outsideReading(config, outsideUri))
        docs.progress = byteArrayOf(17)
        docs.otherGame = true
        val after = snapshot(boundRead, probe.outsideReading(config, outsideUri))
        saveProbes(saves, store).apply(player, after,
            RootVerdict.UsesOther(outside, Evidence(EvidenceKind.SessionWrite, "Game saved there"), after.atMillis), game)
        val incident = store.incidents().single()
        assertEquals("WrongFolder:armsx2:${homeGameId(game.backendId, game.remoteKey)}", incident.id)
        assertEquals(config.effective, incident.alternateSavePath)
        assertTrue(incident.blocksLaunch && incident.appliesTo(game))
        assertFalse(incident.appliesTo(game.copy(remoteKey = "second")))
        assertFalse(incident.appliesTo(game.copy(backendId = LocalFolderBackend.ID)))
        assertNull(saveFixTarget(incident))
        var archives = 0
        val server = object : LibraryBackend by LocalFolderBackend() {
            override val id = game.backendId
            override val instanceId = id
            override suspend fun keepAsBackups(game: Game, player: Player, observed: ObservedSaves): List<String> {
                assertEquals(this@ArmsX2SaveProbeTest.game.remoteKey, game.remoteKey)
                val card = observed.slots.single()
                assertEquals("card1", card.slot)
                val path = Path.of(URI(card.contentUri!!))
                val bytes = path.readBytes()
                assertEquals(md5Hex(bytes), card.contentHash)
                val saves = saveEntries(bytes, setOf("BASLUS-12345"))!!.filterValues { it != null }
                assertEquals(setOf("BASLUS-12345/data.bin"), saves.keys)
                assertArrayEquals(byteArrayOf(17), saves.values.single())
                archives++
                return listOf("verified-backup")
            }
        }
        val copies = SaveIncidentCopies(saves, host(server), store)
        val pending = LibraryReturn(game.backendId, game, player, baseline = before)
        assertTrue(probe.preserveReturn(pending, emptyList(), after, incident, store, copies, "app.foldcade") { outsideUri })
        copies.keep(game, player)
        assertEquals(listOf("verified-backup"), store.incident(incident.id)?.kept)
        assertTrue(saves.queuedCopies().isEmpty())
        assertTrue(probe.preserveReturn(pending, emptyList(), after, incident, store, copies, "app.foldcade") { outsideUri })
        copies.keep(game, player)
        assertEquals(1, archives)
    }

    @Test fun playingAnywayKeepsTheLocalCopyEvenIfTheConfigWasChangedBack() = runBlocking {
        val docs = CardDocuments().apply { progress = byteArrayOf(22) }
        val saves = shelf(docs)
        val probe = ArmsX2SaveProbe(player, saves, disc())
        val local = game.copy(backendId = LocalFolderBackend.ID, availability = Availability.LocalOnly)
        val store = SaveProtectionStore(temporary.newFolder().toPath())
        val copies = SaveIncidentCopies(saves, host(LocalFolderBackend()), store)
        val before = snapshot(boundRead, null)
        val pending = LibraryReturn(local.backendId, local, player, baseline = before, unprotectedSession = true)
        val after = snapshot(boundRead, null).copy(configSavePath = null)
        assertTrue(probe.preserveReturn(pending, emptyList(), after, null, store, copies, "app.foldcade") { outsideUri })
        val staged = saves.queuedCopies().single()
        assertEquals(local.backendId, staged.game.backendId)
        assertTrue(Files.isRegularFile(Path.of(URI(staged.slot.contentUri!!))))
    }

    @Test fun changingTheSelectedCardWhilePlayingFencesTheNextPlacement() {
        val docs = CardDocuments().apply { progress = byteArrayOf(5) }
        val saves = shelf(docs)
        val probe = ArmsX2SaveProbe(player, saves, disc())
        val store = SaveProtectionStore(temporary.newFolder().toPath())
        val copies = SaveIncidentCopies(saves, host(LocalFolderBackend()), store)
        val previous = probe.outsideSpot(outsideUri, config.effective)!!
        val selected = probe.outsideSpot(outsideUri, "Other.ps2")!!
        val pending = LibraryReturn(game.backendId, game, player, spots = listOf(previous))
        assertTrue(probe.preserveReturn(pending, listOf(selected), null, null, store, copies, "app.foldcade") { null })
        assertTrue(store.pendingBackup(player.id, game))
        assertEquals(1, saves.queuedCopies().size)
        assertTrue(store.incidents().isEmpty())
    }

    @Test fun anUnreadableIniOnReturnDoesNotArchiveTheUnchangedCardSelection() {
        val docs = CardDocuments().apply {
            progress = byteArrayOf(5)
            iniUnreadable = true
        }
        val saves = shelf(docs)
        val probe = ArmsX2SaveProbe(player, saves, disc())
        val store = SaveProtectionStore(temporary.newFolder().toPath())
        val copies = SaveIncidentCopies(saves, host(LocalFolderBackend()), store)
        val spot = probe.outsideSpot(outsideUri, config.effective)!!
        val unreadable = probe.config(saves.folderAccess(player, outsideUri, allowWrite = false))
        assertFalse(unreadable.nativeReadable)
        assertNull(unreadable.outsideCard())
        val before = snapshot(boundRead, null).copy(configSavePath = null)
        val after = snapshot(boundRead.copy(spotFile = null, spotReadable = false), null)
            .copy(configSavePath = unreadable.outsideCard(), configUnavailable = true)
        val pending = LibraryReturn(game.backendId, game, player, spots = listOf(spot), baseline = before)
        assertFalse(probe.preserveReturn(pending, listOf(spot), after, null, store, copies, "app.foldcade") { null })
        assertFalse(store.pendingBackup(player.id, game))
        assertTrue(saves.queuedCopies().isEmpty())
    }

    private fun snapshot(boundRead: RootReading, outsideRead: RootReading?) =
        ProbeSnapshot(200, 1, "com.armsx2", listOfNotNull(boundRead, outsideRead), config)

    private fun saveProbes(saves: PlayerSaves, store: SaveProtectionStore) = SaveProbes(ContextWrapper(null), saves, store,
        { error("Tests supply root readings") }, SaveIncidentCopies(saves, host(LocalFolderBackend()), store))

    private fun probe(docs: SaveDocuments) = ArmsX2SaveProbe(player, shelf(docs), disc())

    private fun shelf(docs: SaveDocuments): PlayerSaves {
        val root = temporary.newFolder().toPath()
        return PlayerSaves(docs, object : SaveMarks {
            override fun mark(key: String): String? = null
            override fun setMark(key: String, hash: String) = Unit
            override fun clearMark(key: String) = Unit
        }, root.resolve("backups"), root.resolve("handover"))
    }

    private fun host(library: LibraryBackend) = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val libraries = listOf(library)
        })
    }

    private class CardDocuments : SaveDocuments {
        var progress: ByteArray? = null
        var otherGame = false
        var raw = false
        var iniUnreadable = false
        override fun children(folderUri: String, path: String): List<SaveChild>? = when (path) {
            "" -> listOfNotNull(SaveChild("Disposable.ps2", "card", !raw, 0L),
                SaveChild("PCSX2-Android.ini", "ini", false, 10L).takeIf { iniUnreadable })
            "Disposable.ps2" -> listOfNotNull(
                progress?.let { SaveChild("BASLUS-12345", "first", true, 0L) },
                SaveChild("BASLUS-98765", "second", true, 0L).takeIf { otherGame },
            )
            "Disposable.ps2/BASLUS-12345" -> listOf(SaveChild("data.bin", "progress", false, 1L))
            else -> null
        }
        override fun list(folderUri: String, name: String): List<SaveEntry>? = when (name) {
            "Disposable.ps2/BASLUS-12345" -> listOf(SaveEntry("data.bin", "progress", false))
            else -> error("another game's card files were read")
        }
        override fun read(uri: String) = progress.takeIf { uri == "progress" }
        override fun displayName(uri: String) = "Test.iso"
        override fun find(folderUri: String, name: String): String? = null
        override fun create(folderUri: String, name: String): String? = error("read only")
        override fun write(uri: String, bytes: ByteArray) = error("read only")
        override fun delete(uri: String): Boolean = error("read only")
    }

    private fun disc(): GameFileReader {
        val image = ByteArray(22 * 2048)
        val bytes = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN)
        fun record(at: Int, lba: Int, size: Int, flags: Byte, name: ByteArray) {
            image[at] = (33 + name.size).toByte()
            bytes.putInt(at + 2, lba)
            bytes.putInt(at + 10, size)
            image[at + 25] = flags
            image[at + 32] = name.size.toByte()
            name.copyInto(image, at + 33)
        }
        val cnf = "BOOT2 = cdrom0:\\SLUS_123.45;1\n".toByteArray()
        image[16 * 2048] = 1
        "CD001".toByteArray().copyInto(image, 16 * 2048 + 1)
        record(16 * 2048 + 156, 20, 2048, 2, byteArrayOf(0))
        record(20 * 2048, 21, cnf.size, 0, "SYSTEM.CNF;1".toByteArray())
        cnf.copyInto(image, 21 * 2048)
        return object : GameFileReader {
            override val fileName = "Test.iso"
            override fun read(offset: Long, length: Int): ByteArray? =
                if (offset >= image.size) null else image.copyOfRange(offset.toInt(), minOf(image.size, offset.toInt() + length))
        }
    }
}
