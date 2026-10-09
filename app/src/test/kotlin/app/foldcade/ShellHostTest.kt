package app.foldcade

import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Placement
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.canonicalPlatformId
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.HostScreen
import app.foldcade.language.Meaning
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.romm.RommPlugins
import app.foldcade.plugins.romm.RommTokenSource
import java.nio.file.Files
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellHostTest {
    @Test
    fun libraryNamesComeFromTheGuardedAccessor() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(entry(LabelLibrary("sample.library", "Sample library")))
        val shell = shell(host)
        openLibrary(shell)
        assertEquals(listOf("Sample library"), shell.model.backends)
        assertFalse(shell.model.unavailable)
    }

    @Test
    fun pluginCallExceptionBecomesUnavailable() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(entry(object : LabelLibrary("boom.library", "Boom") {
            override val displayName: String
                get() = throw IllegalStateException("label")
        }))
        val shell = shell(host)
        openLibrary(shell)
        assertTrue(shell.model.unavailable)
    }

    @Test
    fun typedPluginExceptionIsNotTheUnavailableState() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(entry(object : LabelLibrary("typed.library", "Typed") {
            override val displayName: String
                get() = throw PluginException.Unavailable("offline")
        }))
        val shell = shell(host)
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        val failure = runCatching { shell.onMeaning(Meaning.Activate, HostScreen.Bottom) }
        assertTrue(failure.exceptionOrNull() is PluginException.Unavailable)
        assertFalse(shell.model.unavailable)
    }

    @Test
    fun bundledEntriesComeFromServiceFiles() {
        val loaded = ServiceLoader.load(
            PluginEntry::class.java,
            ShellHostTest::class.java.classLoader,
        ).map { it.javaClass.name }.sorted()
        assertEquals(
            listOf(
                "app.foldcade.localfolder.LocalFolderEntry",
                "app.foldcade.plugins.romm.RommEntry",
            ),
            loaded,
        )
    }

    @Test
    fun rommSetupMapsSlugsThroughHostPlatformAliases() {
        val reads = AtomicInteger()
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val platforms = listOf(
                countedPlatform(reads, "nintendo-3ds", "Nintendo 3DS", setOf("cci"), setOf("3ds", "n3ds")),
                countedPlatform(reads, "nintendo-ds", "Nintendo DS", setOf("nds"), setOf("nds")),
            )
        })
        val afterRegister = reads.get()
        val shell = shell(host)
        val cache = Files.createTempDirectory("romm-setup")
        shell.installRomm("https://romm.example", RommTokenSource { null }, cache)
        val definitions = RommPlugins.wiring?.platforms
        assertEquals(host.platformDefinitions(), definitions)
        assertEquals("nintendo-3ds", canonicalPlatformId(definitions!!, "3ds"))
        assertEquals("nintendo-3ds", canonicalPlatformId(definitions, "n3ds"))
        assertEquals("nintendo-ds", canonicalPlatformId(definitions, "nds"))
        assertNull(canonicalPlatformId(definitions, "snes"))
        assertEquals(afterRegister, reads.get())
    }

    @Test
    fun loadedEntriesMapRommSlugsToCanonicalIds() = runBlocking {
        val loaded = ServiceLoader.load(
            PluginEntry::class.java,
            ShellHostTest::class.java.classLoader,
        ).toList()
        val folder = loaded.filterIsInstance<LocalFolderEntry>().single()
        assertEquals(
            setOf(
                "app.foldcade.localfolder.LocalFolderEntry",
                "app.foldcade.plugins.romm.RommEntry",
            ),
            loaded.map { it.javaClass.name }.toSet(),
        )
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.load(ShellHostTest::class.java.classLoader)
        assertTrue(host.rejected.isEmpty())
        assertTrue(host.library("local-folder") is LocalFolderBackend)
        assertEquals("RomM", host.library("romm")?.displayName)
        val stored = host.platformDefinitions()
        assertEquals(folder.platforms.map { it.id }, stored.map { it.id })
        assertEquals(folder.platforms.map { it.aliases }, stored.map { it.aliases })
        val cache = Files.createTempDirectory("romm-real-entries")
        try {
            publishRommWiring(
                "https://romm.example",
                MemoryCredentialStore(),
                cache,
                stored,
            )
            val definitions = RommPlugins.wiring?.platforms
            assertEquals(stored, definitions)
            assertEquals("nintendo-3ds", canonicalPlatformId(definitions!!, "3ds"))
            assertEquals("nintendo-3ds", canonicalPlatformId(definitions, "n3ds"))
            assertEquals("nintendo-3ds", canonicalPlatformId(definitions, "new-nintendo-3ds"))
            assertEquals("nintendo-ds", canonicalPlatformId(definitions, "nds"))
            assertEquals("snes", canonicalPlatformId(definitions, "snes"))
            assertEquals("snes", canonicalPlatformId(definitions, "sfam"))
            assertEquals("nes", canonicalPlatformId(definitions, "nes"))
            assertEquals("nes", canonicalPlatformId(definitions, "famicom"))
            assertEquals("psp", canonicalPlatformId(definitions, "psp"))
            assertEquals("genesis", canonicalPlatformId(definitions, "genesis"))
            assertEquals("game-boy-advance", canonicalPlatformId(definitions, "gba"))
            assertEquals("nintendo-64", canonicalPlatformId(definitions, "n64"))
            assertEquals("playstation", canonicalPlatformId(definitions, "psx"))
            assertEquals("gamecube", canonicalPlatformId(definitions, "ngc"))
            assertEquals("game-gear", canonicalPlatformId(definitions, "gamegear"))
            assertEquals("nintendo-3ds", host.platform("3DS")?.id)
            assertEquals("nintendo-ds", host.platform("NDS")?.id)
        } finally {
            cache.toFile().deleteRecursively()
        }
    }

    @After
    fun clearRommWiring() {
        RommPlugins.clear()
    }

    @Test
    fun noRegisteredLibraryStaysAvailable() {
        val shell = shell(PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        openLibrary(shell)
        assertFalse(shell.model.unavailable)
        assertTrue(shell.model.backends.isEmpty())
    }

    private fun shell(host: PluginHost) = ShellController(SessionStore(MemoryPrefs()), host)

    private fun openLibrary(shell: ShellController) {
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    private fun entry(library: LibraryBackend) = object : PluginEntry {
        override val apiVersion = PLUGIN_API_VERSION
        override val libraries = listOf(library)
    }
}

private fun countedPlatform(
    reads: AtomicInteger,
    id: String,
    name: String,
    extensions: Set<String>,
    aliases: Set<String>,
) = object : Platform {
    override val id: String
        get() {
            reads.incrementAndGet()
            return id
        }
    override val displayName: String
        get() {
            reads.incrementAndGet()
            return name
        }
    override val extensions: Set<String>
        get() {
            reads.incrementAndGet()
            return extensions
        }
    override val aliases: Set<String>
        get() {
            reads.incrementAndGet()
            return aliases
        }
}

private open class LabelLibrary(
    override val id: String,
    private val label: String,
) : LibraryBackend {
    override val displayName: String
        get() = label

    override suspend fun connect() = Unit

    override suspend fun disconnect() = Unit

    override suspend fun listPlatforms(): List<ListedPlatform> = emptyList()

    override suspend fun listGames(platformId: String, query: GameQuery): GamePage =
        GamePage(emptyList(), null)

    override suspend fun ensureLocal(game: Game): LaunchTarget =
        LaunchTarget.ContentUri(uri = game.remoteKey)

    override suspend fun saves(game: Game): SaveSet = SaveSet(id, emptyList())

    override suspend fun prepareLaunch(game: Game, player: Player): Placement =
        Placement(target = LaunchTarget.ContentUri(uri = game.remoteKey))

    override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves) =
        error("unused")
}
