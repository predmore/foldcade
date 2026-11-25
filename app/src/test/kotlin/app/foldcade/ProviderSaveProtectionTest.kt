package app.foldcade

import android.content.ContextWrapper
import app.foldcade.api.plugin.*
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.plugins.aps3e.Aps3ePlayer
import app.foldcade.plugins.dolphin.DolphinCard
import app.foldcade.plugins.dolphin.DolphinPlayer
import app.foldcade.plugins.dolphin.gameCubeDisc
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProviderSaveProtectionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val dolphin = DolphinPlayer()
    private val pkg = dolphin.packageNames.single()
    private val root = FolderRef("${DolphinPlayer.PROVIDER}:root", "Dolphin’s files")
    private val legacy = FolderRef("primary:dolphin-emu", "dolphin-emu")
    private val bound = RootReading(root, true, emptyList(), "old", true, true, null)
    private val game = Game("server", "1", dolphin.platformId, Availability.Cached, "Test disc")
    private val reader = object : GameFileReader {
        override val fileName = "Test.iso"
        override fun read(offset: Long, length: Int): ByteArray? = ByteArray(0x460).also { data ->
            "GFZE8P".toByteArray().copyInto(data)
            byteArrayOf(0xc2.toByte(), 0x33, 0x9f.toByte(), 0x3d).copyInto(data, 0x1c)
            data[0x45b] = 1
        }.takeIf { offset == 0L && length == 0x460 }
    }

    @Test fun proofRequiresTheExactProviderRootForTheInstalledFlavor() {
        val ps3 = Aps3ePlayer()
        for (name in ps3.packageNames) {
            val id = "/storage/emulated/0/Android/data/$name/files/aps3e"
            val uri = treeUri("$name.provider", id)
            assertEquals(name, providerSaveRootPackage(ps3, uri))
            assertNull(providerSaveRootPackage(ps3, folderDocumentUri(uri, "$id/config")))
            assertNull(providerSaveRootPackage(ps3, treeUri(SharedPaths.EXTERNAL_STORAGE_AUTHORITY, "primary:${id.substringAfter("0/")}")))
            assertNull(providerSaveRootPackage(ps3, treeUri("$name.provider.evil", id)))
        }
        assertNull(providerSaveRootPackage(ps3, treeUri("aenu.aps3e.provider", "/storage/emulated/0/Android/data/aenu.aps3e.premium/files/aps3e")))
        assertEquals(pkg, providerSaveRootPackage(dolphin, treeUri(DolphinPlayer.PROVIDER, "root/")))
        assertNull(providerSaveRootPackage(dolphin, treeUri(DolphinPlayer.PROVIDER, "root/GC")))
        assertNull(providerSaveRootPackage(dolphin, treeUri("org.dolphinemu.mmjr.user", "root")))
    }

    @Test fun reinstallInvalidatesPriorProofEvenIfTheUriStillListsAndRegrantCanRestoreIt() {
        val directory = temporary.newFolder().toPath()
        val store = SaveProtectionStore(directory)
        val first = snapshot()
        val checked = providerSaveRootVerdict(dolphin, first, pkg, pkg, installChanged = false) as RootVerdict.Uses
        assertEquals(EvidenceKind.OwnProvider, checked.evidence.kind)
        store.verified("dolphin", root, checked.evidence, checked.atMillis, pkg, 10, true)
        val restored = SaveProtectionStore(directory)
        assertTrue(restored.location("dolphin", root, pkg, 10) is LocationFact.Verified)
        val reinstalled = first.copy(installFirstMillis = 11)
        assertTrue(restored.location("dolphin", root, pkg, 11) is LocationFact.Bound)
        assertEquals(IncidentKind.LostAccess, (providerSaveRootVerdict(dolphin, reinstalled, pkg, pkg,
            restored.reinstalled("dolphin", root, pkg, 11)) as RootVerdict.Lost).kind)
        val granted = providerSaveRootVerdict(dolphin, reinstalled, pkg, pkg, installChanged = false) as RootVerdict.Uses
        restored.verified("dolphin", root, granted.evidence, granted.atMillis, pkg, 11, true)
        assertFalse(restored.reinstalled("dolphin", root, pkg, 11))
        assertTrue(restored.location("dolphin", root, pkg, 11) is LocationFact.Verified)
    }

    @Test fun missingOrWrongOwnerCannotProduceOwnProviderProof() {
        assertEquals(RootVerdict.NoNews, providerSaveRootVerdict(dolphin, snapshot(), null, null, false))
        assertEquals(RootVerdict.NoNews, providerSaveRootVerdict(dolphin, snapshot(), pkg, "wrong.package", false))
        assertEquals(RootVerdict.NoNews, providerSaveRootVerdict(dolphin, snapshot().copy(installFirstMillis = null), pkg, pkg, false))
        val lost = snapshot(bound.copy(reachable = false))
        assertEquals(RootVerdict.NoNews, providerSaveRootVerdict(dolphin, lost, pkg, pkg, false))
        assertEquals(IncidentKind.LostAccess, (judgeRoots(lost, LocationFact.Bound(root)) as RootVerdict.Lost).kind)
        val ps3 = Aps3ePlayer()
        val wrongFlavor = snapshot().copy(packageName = ps3.packageNames.first())
        assertEquals(IncidentKind.LostAccess, (providerSaveRootVerdict(ps3, wrongFlavor, ps3.packageNames.last(),
            ps3.packageNames.last(), false) as RootVerdict.Lost).kind)
    }

    @Test fun aCopiedOrUnknownConfigHasNoVerdictAndAProvenCustomCardGatesOnlyTheMatchingGame() {
        val disc = gameCubeDisc(reader)!!
        for (card in listOf(DolphinCard.Unknown, DolphinCard.Default, DolphinCard.OtherDevice,
            DolphinCard.Custom(null), DolphinCard.Custom("dolphin-emu/${disc.defaultCardPath}"),
            DolphinCard.Custom("Android/data/$pkg/files/${disc.defaultCardPath}"))) {
            assertNull(dolphinAlternate(card, disc, ownProvider = true))
        }
        val card = DolphinCard.Custom("QA/Custom Card")
        assertNull(dolphinAlternate(card, disc, ownProvider = false))
        assertEquals(RootVerdict.NoNews, dolphinConfigVerdict(snapshot().copy(configUnavailable = true)))
        val now = snapshot().copy(configSavePath = dolphinAlternate(card, disc, ownProvider = true))
        val store = SaveProtectionStore(temporary.newFolder().toPath())
        val saves = shelf()
        val probes = SaveProbes(ContextWrapper(null), saves, store, { null }, SaveIncidentCopies(saves, host(LocalFolderBackend()), store))
        val verdict = dolphinConfigVerdict(now)
        assertTrue(verdict is RootVerdict.UsesOther)
        probes.apply(dolphin, now, verdict, game)
        val incident = store.incidents().single()
        assertEquals("primary:QA/Custom Card", incident.alternateSavePath)
        assertTrue(incident.dolphinCustomCard && incident.blocksLaunch && incident.appliesTo(game))
        assertFalse(incident.appliesTo(game.copy(remoteKey = "2")))
        assertFalse(incident.appliesTo(game.copy(backendId = LocalFolderBackend.ID)))
        assertTrue(store.pendingBackup("dolphin", game))
        assertFalse(store.pendingBackup("dolphin", game.copy(remoteKey = "2")))
        assertNull(saveFixTarget(incident))
        assertTrue(SaveProtectionCopy.body(incident).startsWith(SaveProtectionCopy.dolphinCustomCardDetail))
    }

    @Test fun legacyWritesNeedAStableReadableBaselineAndBoundWritesWin() {
        val candidate = bound.copy(root = legacy, bound = false, spotHash = null)
        val before = snapshot(bound, candidate)
        val after = snapshot(bound, candidate.copy(spotHash = "legacy write"))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, before, 90_000))
        assertEquals(SessionVerdict.WroteElsewhere(legacy), judgeSession(before, after, 10))
        assertEquals(SessionVerdict.Wrote(root), judgeSession(before, after.copy(roots = listOf(bound.copy(spotHash = "bound write"), after.roots.last())), 10))
        assertEquals(SessionVerdict.NoNews, judgeSession(snapshot(), after, 10))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, after.copy(installFirstMillis = 11), 10))
        assertEquals(SessionVerdict.NoNews, judgeSession(before, after.copy(roots = listOf(bound.copy(spotReadable = false), after.roots.last())), 10))
    }

    @Test fun unreadableConfigNeverShowsProtectedEvenWithAStoredProviderCheckAndReceipt() {
        val store = SaveProtectionStore(temporary.newFolder().toPath())
        store.verified("dolphin", root, Evidence(EvidenceKind.OwnProvider, "Dolphin’s own folder"), 100, pkg, 10, true)
        val saves = shelf()
        val probes = SaveProbes(ContextWrapper(null), saves, store, { null }, SaveIncidentCopies(saves, host(LocalFolderBackend()), store))
        val snapshot = snapshot().copy(configUnavailable = true)
        val location = probes.location(dolphin, snapshot)
        assertEquals(LocationFact.Bound(root, configUnavailable = true), location)
        val facts = SaveFacts(SaveKeeper.Emulator(dolphin.displayName), location, SpotFact.Resolved, OffDeviceFact.OnRomm(50), null, emptyList(), true)
        assertEquals(SaveStatus.Unconfirmed(UnconfirmedWhy.FolderNotChecked, null), deriveSaveStatus(facts))
        assertEquals(RootVerdict.NoNews, judgeRoots(snapshot, location))
        assertTrue(probes.location(dolphin, snapshot.copy(configUnavailable = false)) is LocationFact.Verified)
    }

    @Test fun customCardCopiesAreFilteredDeduplicatedAndKeptThroughTheBackupApiOnly() = runBlocking {
        val saves = shelf()
        val store = SaveProtectionStore(temporary.newFolder().toPath())
        val incident = incident(game)
        store.put(incident)
        var attempts = 0
        val backend = object : LibraryBackend by LocalFolderBackend() {
            override val id = game.backendId
            override val instanceId = id
            override suspend fun keepAsBackups(game: Game, player: Player, observed: ObservedSaves): List<String> {
                attempts++
                assertEquals(this@ProviderSaveProtectionTest.game, game.copy(availability = Availability.Cached))
                val slot = observed.slots.single()
                assertEquals("gci", slot.slot)
                val bytes = Files.readAllBytes(Path.of(URI(slot.contentUri!!)))
                assertEquals(md5Hex(bytes), slot.contentHash)
                val archive = saveEntries(bytes, setOf(""))!!
                assertEquals(setOf("ours.gci"), archive.keys)
                assertArrayEquals("GFZE8P".gci(), archive["ours.gci"])
                return if (attempts == 1) emptyList() else listOf("verified-unslotted-backup")
            }
            override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult = error("Unexpected slot handoff")
        }
        val copies = SaveIncidentCopies(saves, host(backend), store)
        repeat(2) { stageDolphinCard(saves, copies, dolphin, game, reader, "content://card", incident) }
        copies.keep(game, dolphin)
        assertEquals(1, saves.queuedCopies().size)
        assertTrue(store.incident(incident.id)?.kept.orEmpty().isEmpty())
        copies.keep(game, dolphin)
        assertTrue(saves.queuedCopies().isEmpty())
        assertEquals(listOf("verified-unslotted-backup"), store.incident(incident.id)?.kept)
        stageDolphinCard(saves, copies, dolphin, game, reader, "content://card", incident)
        copies.keep(game, dolphin)
        assertEquals(2, attempts)
    }

    @Test fun localOnlyCopiesRemainOnTheDevice() = runBlocking {
        val local = game.copy(backendId = LocalFolderBackend.ID, availability = Availability.LocalOnly)
        val saves = shelf()
        val store = SaveProtectionStore(temporary.newFolder().toPath())
        val incident = incident(local)
        store.put(incident)
        val copies = SaveIncidentCopies(saves, host(LocalFolderBackend()), store)
        stageDolphinCard(saves, copies, dolphin, local, reader, "content://card", incident)
        copies.keepAll("server")
        copies.keep(local, dolphin)
        val staged = saves.queuedCopies().single()
        assertNull(staged.origin)
        assertEquals(SaveFixCopyDestination.Device, copies.destinations(listOf(staged.slot.contentUri!!)).values.single())
        assertTrue(Files.isRegularFile(Path.of(URI(staged.slot.contentUri!!))))
        assertTrue(store.incident(incident.id)?.kept.orEmpty().isEmpty())
    }

    private fun snapshot(vararg roots: RootReading) = ProbeSnapshot(200, 10, pkg, roots.toList().ifEmpty { listOf(bound) })

    private fun incident(game: Game) = SaveIncident("WrongFolder:dolphin:${homeGameId(game.backendId, game.remoteKey)}", IncidentKind.WrongFolder,
        "dolphin", "Dolphin", listOf(homeGameId(game.backendId, game.remoteKey)), root, null,
        Evidence(EvidenceKind.Config, SaveProtectionCopy.dolphinCustomCardDetail), 200, alternateSavePath = "primary:Custom")

    private fun host(library: LibraryBackend) = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val libraries = listOf(library)
        })
    }

    private fun shelf(): PlayerSaves {
        val dir = temporary.newFolder().toPath()
        val docs = object : SaveDocuments {
            private val files = mapOf("ours.gci" to "GFZE8P".gci(), "other.gci" to "GZLE01".gci(), "card.raw" to "GFZE8P".gci())
            override fun children(folderUri: String, path: String) = files.map { (name, bytes) -> SaveChild(name, name, false, bytes.size.toLong()) }
            override fun list(folderUri: String, name: String) = files.keys.map { SaveEntry(it, it, false) }
            override fun read(uri: String) = files[uri]
            override fun head(uri: String, size: Int) = read(uri)?.take(size)?.toByteArray()
            override fun displayName(uri: String) = "Test.iso"
            override fun find(folderUri: String, name: String): String? = null
            override fun create(folderUri: String, name: String): String? = error("Read only")
            override fun write(uri: String, bytes: ByteArray) = error("Read only")
            override fun delete(uri: String): Boolean = error("Read only")
        }
        val marks = object : SaveMarks {
            override fun mark(key: String): String? = null
            override fun setMark(key: String, hash: String) = error("No working slot was uploaded")
            override fun clearMark(key: String) = error("No working slot was uploaded")
        }
        return PlayerSaves(docs, marks, dir.resolve("backups"), dir.resolve("handover"))
    }

    private fun String.gci() = ByteArray(64).also { toByteArray().copyInto(it) }
}
