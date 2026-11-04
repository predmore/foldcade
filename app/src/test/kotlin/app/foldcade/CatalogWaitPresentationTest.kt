package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.artwork.ArtFinder
import app.foldcade.artwork.ArtIndex
import app.foldcade.host.PluginHost
import app.foldcade.language.HeroSubject
import app.foldcade.language.HomeBoard
import app.foldcade.language.HostScreen
import app.foldcade.language.TileActivity
import app.foldcade.language.encodeHome
import app.foldcade.language.activityLine
import app.foldcade.language.cancellableTransfer
import app.foldcade.language.Copy
import app.foldcade.plugins.retroarch.Core
import app.foldcade.plugins.romm.RommEntry
import app.foldcade.plugins.romm.RommPlugins
import app.foldcade.plugins.romm.RommTokenSource
import app.foldcade.plugins.romm.RommWiring
import app.foldcade.romm.RomSummary
import app.foldcade.romm.RommClient
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CatalogWaitPresentationTest {
    private val id = "lib:romm:23"

    @Test fun bothScreensUseHistoryWithoutLookingItUpAgainWhileDrawing() {
        var reads = 0
        val shell = shell(remembered = { reads++; "Gain Ground" })
        shell.beginCatalogWait(id)
        repeat(10) {
            assertEquals("Gain Ground", (shell.focusedHero() as HeroSubject.Item).item.title)
            assertEquals("Gain Ground", shell.displayTitle(shell.findGame(id), shell.activityAt(index(shell))))
        }
        assertEquals(1, reads)
        assertNull("No cached metadata means no premature provider fetch", shell.artRecord(shell.findGame(id)))
        assertNull("Do not cache an artwork search before the catalog is known", shell.artQuery(shell.findGame(id)))
        assertNull(shell.focusedRecord())
        assertNull("An artwork identity must not become a launchable record", shell.launchRoute(id))
        shell.endCatalogWait(id)
        assertNull(shell.activityAt(index(shell)))
        assertNull(shell.artRecord(shell.findGame(id)))
    }

    @Test fun theArrivingCatalogNameReplacesHistoryImmediately() {
        val shell = shell(remembered = { "Old title" })
        shell.beginCatalogWait(id)
        val record = Game("romm", "23", "sega-genesis", Availability.RemoteOnly, "Gain Ground")
        shell.ingestLibrary("romm", listOf(GridEntry("23", record.label, "", record.platformId, null, false, record)), emptyList())
        assertEquals("Gain Ground", (shell.focusedHero() as HeroSubject.Item).item.title)
        assertEquals("Gain Ground", shell.displayTitle(shell.findGame(id), TileActivity.LoadingLibrary))
    }

    @Test fun anOfflineMetadataSnapshotNamesAndIllustratesAWaitBeforeHomeIngestsIt() = runBlocking {
        val previous = RommPlugins.wiring
        val root = Files.createTempDirectory("catalog-wait")
        val origin = "https://romm.example"
        val key = MessageDigest.getInstance("SHA-256").digest(RommClient.normalizeOrigin(origin).toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        val file = root.resolve("servers/$key/catalog.json").toFile().apply { parentFile!!.mkdirs() }
        val rom = RomSummary(
            23, "Cached Gain Ground", "Gain Ground.bin", "bin", 512_000, 1, "genesis", "genesis", "Sega Genesis",
            emptyList(), null, "/cover/23.png", null, true, false, false, emptyList(),
        )
        file.writeText("""{"version":1,"platforms":null,"roms":[${Json.encodeToString(rom)}],"listings":{}}""")
        try {
            RommPlugins.install(RommWiring(origin, RommTokenSource { error("No network or token read for cached identity") }, root.resolve("roms"), root))
            val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply { register(RommEntry()) }
            val shell = shell(host)
            shell.beginCatalogWait(id)
            assertNull("An unknown game must not fetch and poison the provider cache", shell.artRecord(shell.findGame(id)))
            assertNull("Never search for the anonymous fallback title", shell.artQuery(shell.findGame(id)))
            shell.endCatalogWait(id)
            // The ordinary background metadata restore fills memory; no catalog is published to Home.
            host.metadata(RommCredentials.METADATA_ID)!!.details(Game("romm", "23", "", Availability.RemoteOnly, id))
            shell.beginCatalogWait(id)
            val game = shell.findGame(id)
            assertEquals("Cached Gain Ground", (shell.focusedHero() as HeroSubject.Item).item.title)
            assertEquals("Cached Gain Ground", shell.artQuery(game)!!.title)
            val covers = CoverArt(host, ArtFinder({ emptyList() }, ArtIndex(root.resolve("art-index").toFile())))
            assertEquals("https://romm.example/cover/23.png", covers.cached(shell.artRecord(game), shell.artQuery(game))!!.cover)
            val played = shell(host, remembered = { "Played Gain Ground" })
            played.beginCatalogWait(id)
            assertEquals("Played Gain Ground", (played.focusedHero() as HeroSubject.Item).item.title)
            assertNull(shell.launchRoute(id))
        } finally {
            if (previous == null) RommPlugins.clearWiring() else RommPlugins.install(previous)
        }
    }

    @Test fun keptCoverFileChecksRunOffTheCallingThreadAndMissingArtDoesNotFailTheWait() = runBlocking {
        val caller = Thread.currentThread()
        val file = File("kept-cover.webp")
        assertSame(file, catalogWaitCover(id) {
            assertNotSame(caller, Thread.currentThread())
            assertEquals(id, it)
            file
        })
        assertNull(catalogWaitCover(id) { null })
        assertNull(catalogWaitCover(id) { throw IOException("Image removed") })
        assertNull(catalogWaitCover(id) { throw SecurityException("Image unavailable") })
    }

    @Test fun catalogCleanupPreservesCoreProgressAndTheSameLaunchCancelStopsIt() = runBlocking {
        val shell = shell(remembered = { "Gain Ground" })
        val work = LaunchWork()
        val finding = work.begin(id)
        val catalog = launch { CompletableDeferred<Unit>().await() }
        work.job = catalog
        shell.beginCatalogWait(id)

        // The resolved route starts the normal launch, cancelling its old catalog job.
        work.resolved(finding)
        val downloading = work.begin(id)
        val owner = Any()
        val core = Core("genesis_plus_gx", "Genesis Plus GX")
        val download = launch {
            try {
                shell.noteCoreDownload(id, owner, core.libraryName, .25f)
                CompletableDeferred<Unit>().await()
            } finally {
                shell.endCoreDownload(id, owner)
            }
        }
        work.job = download
        yield()
        shell.endCatalogWait(id) // The cancelled catalog's late finally must not erase core progress.
        val status = shell.activityAt(index(shell))!!
        assertEquals(TileActivity.Downloading(.25f, core = core.libraryName), status)
        assertEquals("${Copy.gettingCore(core.libraryName)} 25%", activityLine(status))
        assertTrue(status.cancellableTransfer)
        assertTrue(work.pending(id))
        work.retroArchCore = PreparedRetroArchCore("com.retroarch.aarch64", core, File("core.so"))

        // Both Home activities' B path cancels this coordinator, including the prepared sideload.
        work.cancel()
        download.join()
        catalog.join()
        assertFalse(work.current(downloading))
        assertFalse(work.pending(id))
        assertNull(work.retroArchCore)
        assertNull(shell.activityAt(index(shell)))
    }

    private fun shell(
        host: PluginHost = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()),
        remembered: (String) -> String? = { null },
    ): ShellController {
        val prefs = MemoryPrefs().apply {
            edit().putString("home_board", encodeHome(HomeBoard(slots = listOf(id))))
                .putString("romm_origin", "https://romm.example").putString("restore_holds", "{}").commit()
        }
        return ShellController(SessionStore(prefs), host, rememberedPlayName = remembered).also {
            it.touchCell(index(it), HostScreen.Bottom)
        }
    }

    private fun index(shell: ShellController): Int =
        (0 until shell.model.count).single { shell.homeFace(it)?.id == id }
}
