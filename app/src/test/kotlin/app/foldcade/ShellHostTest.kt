package app.foldcade

import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.Platform
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.canonicalPlatformId
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.LaunchableApp
import app.foldcade.language.SettingsCategory
import app.foldcade.language.settingsRows
import app.foldcade.language.settingsCategories
import app.foldcade.language.leftRows
import app.foldcade.language.Row
import app.foldcade.language.HostScreen
import app.foldcade.language.LibrarySort
import app.foldcade.language.Meaning
import app.foldcade.language.MusicTrack
import app.foldcade.language.defaultMusicTrack
import app.foldcade.language.displayOrder
import app.foldcade.localfolder.LocalFolderBackend
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.romm.RommEntry
import app.foldcade.plugins.romm.RommPlugins
import app.foldcade.romm.normalizeSetupOrigin
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
    fun originEditsSupersedeChallengesEvenWhenTheNormalizedAddressIsUnchanged() {
        val shell = shell(PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        val address = "https://romm.example"
        shell.openConnect(address)
        val visit = shell.beginDeviceCodeAttempt()
        val revision = shell.connectOriginRevision
        shell.showSetupHint(app.foldcade.language.Copy.rommCodeExpired, retry = true)
        shell.editOrigin("$address/")
        assertEquals(normalizeSetupOrigin(address), normalizeSetupOrigin(shell.model.connectOrigin))
        assertTrue(shell.deviceCodeVisitCurrent(visit))
        assertFalse(shell.deviceCodeFormCurrent(visit, revision))
        assertTrue(shell.deviceCodeFormCurrent(visit, shell.connectOriginRevision))
        assertNull(shell.model.connectHint)
        assertTrue(shell.model.connectRetry)
        shell.editOrigin(address)
        assertFalse(shell.deviceCodeFormCurrent(visit, revision))
        assertTrue(shell.deviceCodeFormCurrent(visit, shell.connectOriginRevision))
    }

    @Test
    fun tokenValidationRetiresDeviceCodePollingOnBothDisplaysUntilAnExplicitRetry() {
        val shell = shell(PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.openConnect("https://romm.example")
        val visit = shell.beginDeviceCodeAttempt()
        val revision = shell.connectOriginRevision
        assertTrue(shell.deviceCodeFormCurrent(visit, revision))
        val ticket = shell.beginSetupValidation()!!
        assertFalse(shell.deviceCodeVisitCurrent(visit))
        shell.showSetupHint("Token refused", retry = true)
        shell.finishSetupValidation(ticket)
        assertFalse(shell.deviceCodeFormCurrent(visit, revision))
        shell.editOrigin("https://another.example")
        assertTrue(shell.model.connectRetry)
        assertEquals(app.foldcade.language.Effect.RetryRommDeviceCode, shell.touchConnect(3, HostScreen.Bottom))
        val editedTicket = shell.beginSetupValidation()!!
        shell.editOrigin("https://edited.example")
        assertFalse(shell.setupValidationCurrent(editedTicket))
        assertTrue(shell.model.connectRetry)
        assertEquals(app.foldcade.language.Effect.RetryRommDeviceCode, shell.touchConnect(3, HostScreen.Top))
        val retry = shell.beginDeviceCodeAttempt()
        assertTrue(shell.deviceCodeFormCurrent(retry, shell.connectOriginRevision))
        shell.closeConnect()
        shell.openConnect("https://romm.example")
        assertFalse(shell.deviceCodeVisitCurrent(retry))
    }

    @Test
    fun artKeyValidationRejectsDuplicatesEditsAndResultsFromACancelledVisit() {
        val shell = shell(PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.openArtKey()
        shell.editToken("first-key")
        val first = shell.beginSetupValidation()!!
        assertTrue(shell.model.connectBusy)
        assertNull(shell.touchConnect(1, HostScreen.Top))
        assertNull(shell.beginSetupValidation())
        shell.editToken("second-key")
        assertFalse(shell.setupValidationCurrent(first))
        val second = shell.beginSetupValidation()!!
        shell.onMeaning(Meaning.Back, HostScreen.Top)
        shell.openArtKey()
        val third = shell.beginSetupValidation()!!
        shell.finishSetupValidation(second)
        assertFalse(shell.setupValidationCurrent(second))
        assertTrue(shell.model.connectBusy)
        shell.finishSetupValidation(third)
        assertFalse(shell.model.connectBusy)
    }

    @Test
    fun expiredRommCodesOfferControllerAndTouchRetryAndDoNotLeakIntoArtKeySetup() {
        val shell = shell(PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.openConnect("https://romm.example")
        shell.showSetupHint(app.foldcade.language.Copy.rommCodeExpired, retry = true)
        repeat(3) { shell.onMeaning(Meaning.MoveDown, HostScreen.Top) }
        assertEquals(app.foldcade.language.Effect.RetryRommDeviceCode, shell.onMeaning(Meaning.Activate, HostScreen.Top))
        assertEquals(app.foldcade.language.Effect.RetryRommDeviceCode, shell.touchConnect(3, HostScreen.Top))
        val previousAttempt = shell.connectVisit
        assertTrue(shell.beginDeviceCodeAttempt() > previousAttempt)
        assertFalse(shell.model.connectRetry)
        shell.showSetupHint(app.foldcade.language.Copy.rommCodeDenied, retry = true)
        shell.beginSetupValidation()
        assertNull(shell.touchConnect(3, HostScreen.Top))
        shell.editOrigin("https://another.example")
        assertTrue(shell.model.connectRetry)
        assertNull(shell.model.connectHint)
        val visit = shell.connectVisit
        shell.closeConnect()
        shell.openArtKey()
        assertTrue(shell.connectVisit > visit)
        assertFalse(shell.model.connectRetry)
    }

    @Test
    fun setupValidationRejectsDuplicateSubmitsAndStaleFormResults() {
        val shell = shell(PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.openConnect("https://romm.example")
        shell.editToken("first")
        val first = shell.beginSetupValidation()!!
        assertTrue(shell.model.connectBusy)
        assertNull(shell.beginSetupValidation())
        assertNull(shell.touchConnect(2, HostScreen.Top))
        shell.editToken("second")
        assertFalse(shell.setupValidationCurrent(first))
        assertFalse(shell.model.connectBusy)
        val second = shell.beginSetupValidation()!!
        shell.finishSetupValidation(first)
        assertTrue(shell.model.connectBusy)
        shell.onMeaning(Meaning.Back, HostScreen.Top)
        assertFalse(shell.setupValidationCurrent(second))
        shell.openConnect("https://romm.example")
        assertFalse(shell.model.connectBusy)
        assertFalse(shell.setupValidationCurrent(second))
        val third = shell.beginSetupValidation()!!
        shell.editOrigin("https://other.example")
        assertFalse(shell.setupValidationCurrent(third))
        val fourth = shell.beginSetupValidation()!!
        shell.finishSetupValidation(fourth)
        assertFalse(shell.model.connectBusy)
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
                "app.foldcade.plugins.aps3e.Aps3eEntry",
                "app.foldcade.plugins.armsx2.ArmsX2Entry",
                "app.foldcade.plugins.azahar.AzaharEntry",
                "app.foldcade.plugins.droiddeck.DroidDeckEntry",
                "app.foldcade.plugins.duckstation.DuckStationEntry",
                "app.foldcade.plugins.emulators.EmulatorsEntry",
                "app.foldcade.plugins.gamenative.GameNativeEntry",
                "app.foldcade.plugins.melonds.MelonDsEntry",
                "app.foldcade.plugins.moonlight.MoonlightEntry",
                "app.foldcade.plugins.retroarch.RetroArchEntry",
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
            dataRoot = cache.resolve("data"),
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
                "app.foldcade.plugins.aps3e.Aps3eEntry",
                "app.foldcade.plugins.armsx2.ArmsX2Entry",
                "app.foldcade.plugins.azahar.AzaharEntry",
                "app.foldcade.plugins.droiddeck.DroidDeckEntry",
                "app.foldcade.plugins.duckstation.DuckStationEntry",
                "app.foldcade.plugins.emulators.EmulatorsEntry",
                "app.foldcade.plugins.gamenative.GameNativeEntry",
                "app.foldcade.plugins.melonds.MelonDsEntry",
                "app.foldcade.plugins.moonlight.MoonlightEntry",
                "app.foldcade.plugins.retroarch.RetroArchEntry",
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
        assertEquals(listOf("nintendo-ds"), host.playersFor("nds").map { it.platformId }.distinct())
        assertEquals(setOf("melonds", "retroarch.nintendo-ds"), host.playersFor("nds").map { it.id }.toSet())
        assertTrue(host.playerIds().containsAll(listOf("azahar", "melonds", "gamenative", "moonlight")))
        assertEquals(
            listOf("my-boy.game-boy-advance", "pizza-boy-gba.game-boy-advance", "gba-emu.game-boy-advance"),
            host.playersFor("gba").map { it.id }.filterNot { it.startsWith("retroarch.") },
        )
        assertTrue("retroarch.game-boy-advance" in host.playersFor("gba").map { it.id })
        // RetroArch brings the systems no standalone emulator here runs.
        assertEquals(listOf("retroarch.sega-cd"), host.playersFor("segacd").map { it.id })
        assertEquals(listOf("retroarch.pc-engine"), host.playersFor("tg16").map { it.id })
        // Every RetroArch system is a platform the folder catalog declares.
        val retroArch = host.playerIds().filter { it.startsWith("retroarch.") }
        assertEquals(app.foldcade.plugins.retroarch.SYSTEMS.size, retroArch.size)
        assertEquals("nintendo-switch", host.playersFor("switch").single().platformId)
        assertEquals("dreamcast", host.playersFor("dc").first().platformId)
        assertEquals("GameNative", host.library("gamenative")?.displayName)
        assertNull(host.metadata("gamenative"))
        assertEquals("moonlight", host.player("moonlight")?.id)
        assertEquals("moonlight", host.playersFor("moonlight").single().platformId)
        assertFalse(host.player("moonlight") is app.foldcade.api.plugin.SaveFolderHolder)
        assertEquals("Moonlight", host.library("moonlight")?.displayName)
        assertEquals("droiddeck", host.playersFor("droiddeck").single().id)
        assertFalse(host.player("droiddeck") is app.foldcade.api.plugin.SaveFolderHolder)
        assertEquals("DroidDeck", host.library("droiddeck")?.displayName)
        assertNull(host.metadata("droiddeck"))
        // GameNative keeps PC to itself: a DroidDeck game never opens there, and the reverse.
        assertEquals(listOf("gamenative"), host.playersFor("pc").map { it.id })
        val stored = host.platformDefinitions()
        assertEquals(
            folder.platforms.map { it.id }.toSet() + setOf("pc", "moonlight", "droiddeck"),
            stored.map { it.id }.toSet(),
        )
        assertEquals(folder.platforms.size + 3, stored.size)
        val aliasesById = stored.associate { it.id to it.aliases }
        assertEquals(
            folder.platforms.associate { it.id to it.aliases },
            aliasesById - "pc" - "moonlight" - "droiddeck",
        )
        assertEquals(emptySet<String>(), aliasesById.getValue("pc"))
        assertEquals(emptySet<String>(), aliasesById.getValue("moonlight"))
        assertEquals(emptySet<String>(), aliasesById.getValue("droiddeck"))
        val cache = Files.createTempDirectory("romm-real-entries")
        try {
            publishRommWiring(
                "https://romm.example",
                MemoryCredentialStore(),
                cache,
                stored,
                dataRoot = cache.resolve("data"),
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
                defaultMusicTrack(),
                MusicTrack("dusk", "Dusk", "Foldcade project", "MIT", "music/dusk.ogg"),
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
        // Three folders, so there is an order to keep.
        shell.setInstalledApps(
            listOf(LaunchableApp("com.example.game", "Game", systemGame = true), LaunchableApp("com.example.notes", "Notes", systemGame = false)),
            emptySet(),
        )
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

    @Test
    fun saveConflictStartsOnKeepBothAndATouchOnDecideLaterIsB() {
        val shell = shell(PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()))
        shell.showSaveConflictPreview()
        assertEquals(app.foldcade.language.SaveConflictFocus.KeepBoth, shell.model.saveConflict?.focus)
        val later = shell.touchSaveConflictLater() as app.foldcade.language.Effect.ResolveSaveConflict
        assertEquals(app.foldcade.language.SaveConflictAction.DecideLater, later.action)
        assertTrue(later.preview)
        assertNull(shell.model.saveConflict)
        assertNull(shell.touchSaveConflictLater())

        shell.showSaveConflictPreview()
        val both = shell.touchSaveConflict(app.foldcade.language.SaveConflictFocus.KeepBoth)
            as app.foldcade.language.Effect.ResolveSaveConflict
        assertEquals(app.foldcade.language.SaveConflictAction.KeepBoth, both.action)
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

