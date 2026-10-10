package app.foldcade

import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.canonicalPlatformId
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.SettingsCategory
import app.foldcade.language.settingsRows
import app.foldcade.language.settingsCategories
import app.foldcade.language.leftRows
import app.foldcade.language.Row
import app.foldcade.language.HostScreen
import app.foldcade.language.LibrarySort
import app.foldcade.language.Meaning
import app.foldcade.language.MusicTrack
import app.foldcade.language.defaultLanternlightTrack
import app.foldcade.language.displayOrder
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.romm.RommEntry
import app.foldcade.plugins.romm.RommPlugins
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
    fun bundledEntriesComeFromServiceFiles() {
        val loaded = ServiceLoader.load(
            PluginEntry::class.java,
            ShellHostTest::class.java.classLoader,
        ).map { it.javaClass.name }.sorted()
        assertEquals(
            listOf(
                "app.foldcade.localfolder.LocalFolderEntry",
                "app.foldcade.plugins.azahar.AzaharEntry",
                "app.foldcade.plugins.emulators.EmulatorsEntry",
                "app.foldcade.plugins.gamenative.GameNativeEntry",
                "app.foldcade.plugins.melonds.MelonDsEntry",
                "app.foldcade.plugins.moonlight.MoonlightEntry",
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
        val cache = Files.createTempDirectory("romm-setup")
        publishRommWiring(
            "https://romm.example",
            MemoryCredentialStore(),
            cache,
            host.platformDefinitions(),
        )
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
                "app.foldcade.plugins.azahar.AzaharEntry",
                "app.foldcade.plugins.emulators.EmulatorsEntry",
                "app.foldcade.plugins.gamenative.GameNativeEntry",
                "app.foldcade.plugins.melonds.MelonDsEntry",
                "app.foldcade.plugins.moonlight.MoonlightEntry",
                "app.foldcade.plugins.romm.RommEntry",
            ),
            loaded.map { it.javaClass.name }.toSet(),
        )
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        host.load(ShellHostTest::class.java.classLoader)
        assertTrue(host.rejected.joinToString { "${it.plugin}: ${it.reason}" }, host.rejected.isEmpty())
        assertTrue(host.library("local-folder") is LocalFolderBackend)
        assertEquals("RomM", host.library("romm")?.displayName)
        assertEquals("azahar", host.player("azahar")?.id)
        assertEquals("nintendo-3ds", host.playersFor("3ds").single().platformId)
        assertEquals("melonds", host.player("melonds")?.id)
        assertEquals("nintendo-ds", host.playersFor("nds").single().platformId)
        assertTrue(host.playerIds().containsAll(listOf("azahar", "melonds", "gamenative", "moonlight")))
        assertEquals(
            listOf("my-boy.game-boy-advance", "pizza-boy-gba.game-boy-advance", "gba-emu.game-boy-advance"),
            host.playersFor("gba").map { it.id },
        )
        assertEquals("nintendo-switch", host.playersFor("switch").single().platformId)
        assertEquals("dreamcast", host.playersFor("dc").first().platformId)
        assertEquals("GameNative", host.library("gamenative")?.displayName)
        assertNull(host.metadata("gamenative"))
        assertEquals("moonlight", host.player("moonlight")?.id)
        assertEquals("moonlight", host.playersFor("moonlight").single().platformId)
        assertFalse(host.player("moonlight") is app.foldcade.api.plugin.SaveFolderHolder)
        assertEquals("Moonlight", host.library("moonlight")?.displayName)
        val stored = host.platformDefinitions()
        assertEquals(
            folder.platforms.map { it.id }.toSet() + setOf("pc", "moonlight"),
            stored.map { it.id }.toSet(),
        )
        assertEquals(folder.platforms.size + 2, stored.size)
        val aliasesById = stored.associate { it.id to it.aliases }
        assertEquals(
            folder.platforms.associate { it.id to it.aliases },
            aliasesById - "pc" - "moonlight",
        )
        assertEquals(emptySet<String>(), aliasesById.getValue("pc"))
        assertEquals(emptySet<String>(), aliasesById.getValue("moonlight"))
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
            assertEquals("moonlight", canonicalPlatformId(definitions, "moonlight"))
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
    fun volumeRowStepsFivePercentAndTicks() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val cues = mutableListOf<String>()
        var bare = 0
        val levels = mutableListOf<Float>()
        val shell = ShellController(
            SessionStore(MemoryPrefs()),
            host,
            onMusic = { levels += it.volume },
            cue = { _, slot ->
                cues += slot
                true
            },
            bareTick = { bare += 1 },
        )
        shell.openSetting(Row.MusicVolume)
        assertEquals(Row.MusicVolume, shell.focusedSetting())
        cues.clear()
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        assertEquals(0.60f, shell.model.music.volume, 0.0001f)
        assertEquals(listOf(0.55f, 0.60f), levels)
        assertEquals(listOf("move", "move"), cues)
        assertEquals(0, bare)
        assertEquals(Row.MusicVolume, shell.focusedSetting())

        var fallback = 0
        val quiet = ShellController(
            SessionStore(MemoryPrefs()),
            host,
            cue = { _, _ -> false },
            bareTick = { fallback += 1 },
        )
        quiet.openSetting(Row.MusicVolume)
        fallback = 0
        quiet.onMeaning(Meaning.MoveLeft, HostScreen.Bottom)
        assertEquals(0.45f, quiet.model.music.volume, 0.0001f)
        assertEquals(1, fallback)
        repeat(9) { quiet.onMeaning(Meaning.MoveLeft, HostScreen.Bottom) }
        assertEquals(0f, quiet.model.music.volume, 0.0001f)
        val ticksAtSilence = fallback
        quiet.onMeaning(Meaning.MoveLeft, HostScreen.Bottom)
        assertEquals(0f, quiet.model.music.volume, 0.0001f)
        assertEquals(ticksAtSilence, fallback)
    }

    @Test
    fun trackRowStoresTheNextHomeTrack() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val store = SessionStore(MemoryPrefs())
        val ids = mutableListOf<String>()
        val shell = ShellController(
            store,
            host,
            onMusic = { ids += it.trackId },
            homeTracks = listOf(
                defaultLanternlightTrack(),
                MusicTrack("dusk", "Dusk", "Foldcade project", "GPLv3", "music/dusk.ogg"),
            ),
        )
        shell.openSetting(Row.MusicTrack)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals("dusk", shell.model.music.trackId)
        assertEquals("Dusk", shell.model.trackTitle)
        assertEquals(listOf("dusk"), ids)
        assertEquals("dusk", store.musicTrackId())
        assertEquals(Row.MusicTrack, shell.focusedSetting())
    }

    @Test
    fun recentlyPlayedOrderFollowsLastPlayed() {
        val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())
        val store = SessionStore(MemoryPrefs())
        val last = mapOf("shelf.handheld" to 30L, "shelf.clamshell" to 10L)
        val shell = ShellController(store, host, lastPlayedMillis = { last[it] })
        shell.openSetting(Row.Order)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(LibrarySort.RecentlyPlayed, shell.model.sort)
        assertEquals(LibrarySort.RecentlyPlayed, store.librarySort())
        // The curated grid keeps the user's placement. Recently played sorts All, not these slots.
        assertEquals(listOf(0, 1, 2), displayOrder(shell.model).take(3))
    }

    @Test
    fun settingsRowActsOverTheHomeGrid() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        repeat(leftRows().indexOf(Row.Settings)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertTrue(shell.model.settings != null)
        val library = settingsCategories(shell.model).indexOf(SettingsCategory.Library)
        repeat(library) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        val rows = settingsRows(SettingsCategory.Library, shell.model)
        repeat(rows.indexOf(Row.Order)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        // The home board must not take A while the page is open.
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(LibrarySort.RecentlyPlayed, store.librarySort())
        assertTrue(shell.model.settings != null)
    }

    private fun shell(host: PluginHost) = ShellController(SessionStore(MemoryPrefs()), host)

    /** L1, down to Settings, then down to [row]'s category and right into its rows, key by key. */
    private fun ShellController.openSetting(row: Row) {
        onMeaning(Meaning.LeftPanel, HostScreen.Bottom)
        repeat(leftRows().indexOf(Row.Settings)) { onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        onMeaning(Meaning.Activate, HostScreen.Bottom)
        val categories = settingsCategories(model)
        val category = categories.indexOfFirst { row in settingsRows(it, model) }
        repeat(category) { onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        onMeaning(Meaning.MoveRight, HostScreen.Bottom)
        repeat(settingsRows(categories[category], model).indexOf(row)) { onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
    }

    private fun ShellController.focusedSetting(): Row? {
        val page = model.settings ?: return null
        if (!page.onRows) return null
        return settingsRows(settingsCategories(model)[page.category], model).getOrNull(page.row)
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

