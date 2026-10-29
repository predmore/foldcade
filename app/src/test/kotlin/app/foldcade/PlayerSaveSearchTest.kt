package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.GamePage
import app.foldcade.api.plugin.GameQuery
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.LibraryBackend
import app.foldcade.api.plugin.ListedPlatform
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.api.plugin.ObservedSaves
import app.foldcade.api.plugin.ObservedSlot
import app.foldcade.api.plugin.PLUGIN_API_MINOR
import app.foldcade.api.plugin.PLUGIN_API_VERSION
import app.foldcade.api.plugin.PlacedSave
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerIntent
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderSearch
import app.foldcade.api.plugin.SaveSet
import app.foldcade.api.plugin.SaveSlot
import app.foldcade.api.plugin.ServerSave
import app.foldcade.api.plugin.StartDisplay
import app.foldcade.api.plugin.SyncOutcome
import app.foldcade.api.plugin.SyncResult
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.language.DialogButton
import app.foldcade.language.DialogKind
import app.foldcade.language.HostScreen
import app.foldcade.language.saveNotPlacedDialog
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A save found by folder name follows the same checks as one whose folders were named up front. */
class PlayerSaveSearchTest {
    private lateinit var backups: java.nio.file.Path
    private val docs = Tree()
    private val marks = MemoryMarks()
    private lateinit var saves: PlayerSaves

    @Before
    fun setUp() {
        backups = Files.createTempDirectory("search-backups")
        saves = PlayerSaves(docs, marks, backups, backups.resolve("handover"))
        docs.names[ROM] = "Need for Speed Underground 2 (USA).iso"
        docs.dir("memcards/card")
        docs.file("memcards/card/BASLUS-210651/data", "played")
        docs.file("memcards/card/BESLES-99999/data", "someone else")
    }

    @After
    fun tearDown() {
        backups.toFile().deleteRecursively()
    }

    @Test
    fun aSearchReadsOnlyTheMatchingFolders() {
        val spot = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = false).single()
        assertEquals(SaveFolderSearch("memcards/card", "SLUS-21065"), spot.search)
        val read = saves.changedSince("romm", GAME, PLAYER, listOf(spot)).slots.single()
        val bytes = Files.readAllBytes(java.nio.file.Path.of(java.net.URI.create(read.contentUri)))
        val entries = saveEntries(bytes, setOf("BASLUS-210651", "BESLES-99999"))!!
        assertEquals("played", entries.getValue("BASLUS-210651/data")!!.toString(Charsets.UTF_8))
        assertTrue(entries.keys.all { it.startsWith("BASLUS-210651") })
    }

    @Test
    fun placingUpdatesTheMatchAndLeavesTheOtherGame() {
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.library[SERVER] = zipOf("BASLUS-210651/data" to "server")
        val changed = mutableListOf<String>()
        assertTrue(saves.place("romm", GAME, PLAYER, spots, listOf(placed()), changed))
        assertEquals(listOf("card1"), changed)
        assertEquals("server", docs.text("memcards/card/BASLUS-210651/data"))
        assertEquals("someone else", docs.text("memcards/card/BESLES-99999/data"))
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
    }

    @Test
    fun aZipThatNamesAnotherFolderIsRefused() {
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.library[SERVER] = zipOf("BASLUS-210651/data" to "server", "OTHER/data" to "no")
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed())))
        assertEquals("played", docs.text("memcards/card/BASLUS-210651/data"))
    }

    @Test
    fun noMatchingFolderIsNotUploaded() {
        docs.files.entries.removeIf { it.key.startsWith("memcards/card/BASLUS") }
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = false)
        assertTrue(saves.changedSince("romm", GAME, PLAYER, spots).slots.isEmpty())
    }

    @Test
    fun theReturnPathDoesNotWriteAndALaunchThatSetsUpCountsAsAChange() {
        val changed = mutableListOf<String>()
        assertTrue(saves.spots(Setup(), GAME, LaunchTarget.ContentUri(ROM), forLaunch = false, changed = changed).isEmpty())
        assertTrue(changed.isEmpty())
        assertFalse(docs.files.containsKey("memcards/new/note"))
        val spots = saves.spots(Setup(), GAME, LaunchTarget.ContentUri(ROM), forLaunch = true, changed = changed)
        assertEquals(listOf("card1"), changed)
        assertEquals(1, docs.files.getValue("memcards/new/note").single().toInt())
        assertEquals("memcards/new", spots.single().search?.parent)
    }

    @Test
    fun movingACardToBackupsKeepsEveryByteBeforeDeletingIt() {
        val bytes = ByteArray(32) { it.toByte() }
        docs.file("memcards/old.ps2", bytes)
        val changed = mutableListOf<String>()
        saves.spots(Mover(), GAME, LaunchTarget.ContentUri(ROM), forLaunch = true, changed = changed)
        assertEquals(listOf("card1"), changed)
        assertFalse(docs.files.containsKey("memcards/old.ps2"))
        val kept = Files.list(backups.resolve("mover")).use { stream ->
            stream.map { Files.readAllBytes(it) }.toList()
        }
        assertEquals(1, kept.size)
        assertTrue(bytes.contentEquals(kept.single()))
    }

    @Test
    fun aNameMatchesWithDashesAndUnderscoresRemoved() {
        assertTrue(saveFolderNameMatches("BASLUS-210651", "SLUS-21065"))
        assertTrue(saveFolderNameMatches("ba_slus-21065", "SLUS-21065"))
        assertFalse(saveFolderNameMatches("BESLES-99999", "SLUS-21065"))
        assertFalse(saveFolderNameMatches("_pcsx2_deleted_BASLUS-210651", "SLUS-21065"))
    }

    @Test
    fun aDeletedSaveIsNotPartOfTheGame() {
        docs.dir("memcards/card/_pcsx2_deleted_BASLUS-210651")
        docs.file("memcards/card/_pcsx2_deleted_BASLUS-210651/data", "gone")
        val spot = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = false).single()
        val read = saves.changedSince("romm", GAME, PLAYER, listOf(spot)).slots.single()
        val bytes = Files.readAllBytes(java.nio.file.Path.of(java.net.URI.create(read.contentUri)))
        val entries = saveEntries(bytes, setOf("BASLUS-210651"))
        assertEquals("played", entries!!.getValue("BASLUS-210651/data")!!.toString(Charsets.UTF_8))
        assertTrue(entries.keys.none { it.startsWith("_") })
    }

    @Test
    fun aZipThatNamesADeletedSaveIsRefused() {
        val spots = saves.spots(PLAYER, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        saves.noteHandedOver("romm", GAME, PLAYER, spots, saves.changedSince("romm", GAME, PLAYER, spots))
        docs.library[SERVER] = zipOf(
            "BASLUS-210651/data" to "server",
            "_pcsx2_deleted_BASLUS-210651/data" to "gone",
        )
        assertFalse(saves.place("romm", GAME, PLAYER, spots, listOf(placed())))
        assertEquals("played", docs.text("memcards/card/BASLUS-210651/data"))
    }

    @Test
    fun aWriteOverDifferentBytesKeepsTheOldOnes() {
        val old = "previous".toByteArray()
        docs.file("memcards/card/note", old)
        val player = object : Player by Idle {
            override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", TREE))
            override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean): SaveFolderSearch? {
                check(folder.write("memcards/card/note", "next".toByteArray()))
                check(folder.write("memcards/card/note", "next".toByteArray()))
                return SaveFolderSearch("memcards/card", "SLUS-21065")
            }
        }
        saves.spots(player, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        assertEquals("next", docs.text("memcards/card/note"))
        val kept = Files.list(backups.resolve("armsx2")).use { stream ->
            stream.map { Files.readAllBytes(it) }.toList()
        }
        assertEquals(1, kept.size)
        assertTrue(old.contentEquals(kept.single()))
    }

    @Test
    fun aThrowOnTheWayBackIsNoSpotAndAThrowOnLaunchIsNotPlaced() {
        val throwing = object : Player by Idle {
            override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", TREE))
            override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean): SaveFolderSearch? =
                error("card setup failed")
        }
        assertTrue(saves.spots(throwing, GAME, LaunchTarget.ContentUri(ROM), forLaunch = false).isEmpty())
        assertThrows(SaveNotPlaced::class.java) {
            saves.spots(throwing, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        }
    }

    @Test
    fun edenZipMovesAcrossUsersAndCreatesTheMissingUserAndTitleFolders() {
        val player = eden(1)
        val firstPath = edenPath(1)
        docs.file("$firstPath/systemdata", "first device")
        val spots = saves.spots(player, GAME, LaunchTarget.ContentUri(ROM), forLaunch = false)
        val observed = saves.changedSince("romm", GAME, player, spots)
        val zip = Files.readAllBytes(java.nio.file.Path.of(java.net.URI.create(observed.slots.single().contentUri)))
        assertEquals(setOf(TITLE), saveZipRoots(zip))
        assertEquals("first device", saveEntries(zip, setOf(TITLE))!!.getValue("$TITLE/systemdata")!!.toString(Charsets.UTF_8))
        saves.noteHandedOver("romm", GAME, player, spots, observed)
        docs.library[SERVER] = zip

        eden(2)
        val secondPath = edenPath(2)
        assertFalse(secondPath in docs.folders)
        val destination = saves.spots(player, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        assertEquals(secondPath.substringBeforeLast('/'), destination.single().search!!.parent)
        val placed = PlacedSave("savedata", SERVER, md5Hex(zip))
        assertTrue(saves.place("romm", GAME, player, destination, listOf(placed)))
        assertEquals("first device", docs.text("$secondPath/systemdata"))
        assertEquals("first device", docs.text("$firstPath/systemdata"))
        assertTrue(secondPath in docs.folders)
        assertTrue(saves.changedSince("romm", GAME, player, destination).slots.isEmpty())
    }

    @Test
    fun edenSetsAsideTheWholeOldZipBeforeWritingAndRefusesAChangedAgainSave() {
        val player = eden(1)
        val path = edenPath(1)
        docs.file("$path/systemdata", "old progress")
        docs.file("$path/extra", "old extra")
        val spots = saves.spots(player, GAME, LaunchTarget.ContentUri(ROM), forLaunch = true)
        saves.noteHandedOver("romm", GAME, player, spots, saves.changedSince("romm", GAME, player, spots))
        docs.library[SERVER] = zipOf("$TITLE/systemdata" to "server")
        val placed = PlacedSave("savedata", SERVER, md5Hex(docs.library.getValue(SERVER)))
        docs.beforeWrite = {
            val kept = Files.list(backups.resolve("eden")).use { it.toList() }
            val entries = saveEntries(Files.readAllBytes(kept.single()), setOf(TITLE))!!
            assertEquals("old progress", entries.getValue("$TITLE/systemdata")!!.toString(Charsets.UTF_8))
            assertEquals("old extra", entries.getValue("$TITLE/extra")!!.toString(Charsets.UTF_8))
        }
        assertTrue(saves.place("romm", GAME, player, spots, listOf(placed)))
        docs.beforeWrite = {}
        assertEquals("server", docs.text("$path/systemdata"))
        docs.file("$path/systemdata", "changed again")
        docs.library[SERVER] = zipOf("$TITLE/systemdata" to "another device")
        assertFalse(saves.place("romm", GAME, player, spots, listOf(placed.copy(contentHash = md5Hex(docs.library.getValue(SERVER))))))
        assertEquals("changed again", docs.text("$path/systemdata"))
    }

    @Test
    fun anIncomingEdenSaveWithoutAUserStopsBeforeNegotiationOrBaseline() = runBlocking {
        val player = app.foldcade.plugins.eden.EdenPlayer().apply { bindSaveFolder(TREE) }
        docs.names[ROM] = "Fantasy Life [$TITLE].nsp"
        val library = SaveLibrary().apply { remoteSlot = "savedata" }
        val awaiting = mutableListOf<String>()
        val before = docs.files.toMap()
        val failure = runCatching {
            LaunchSaves(saves, plugins(library)) { awaiting += it }.prepare(edenRoute(player), player, LaunchTarget.ContentUri(ROM))
        }.exceptionOrNull()
        assertTrue(failure is EdenSaveNotPlaced)
        assertTrue((failure as EdenSaveNotPlaced).needsUser)
        assertEquals(1, library.listings)
        assertEquals(0, library.negotiations)
        assertTrue(marks.stored.isEmpty())
        assertTrue(awaiting.isEmpty())
        assertEquals(before, docs.files)
    }

    @Test
    fun aHeldEdenSaveWithoutAResolvedTitleStopsBeforeNegotiationOrBaseline() = runBlocking {
        val player = eden(1)
        docs.names[ROM] = "unresolved.nsp"
        val library = SaveLibrary().apply { heldSlot = "savedata" }
        val failure = runCatching {
            LaunchSaves(saves, plugins(library)) { error("no save was placed") }
                .prepare(edenRoute(player), player, LaunchTarget.ContentUri(ROM))
        }.exceptionOrNull()
        assertTrue(failure is EdenSaveNotPlaced)
        assertFalse((failure as EdenSaveNotPlaced).needsUser)
        assertEquals(0, library.listings)
        assertEquals(0, library.negotiations)
        assertTrue(marks.stored.isEmpty())
    }

    @Test
    fun anIncomingSaveOnAnotherFoldedCopyAlsoStopsUnresolvedEden() = runBlocking {
        val player = app.foldcade.plugins.eden.EdenPlayer().apply { bindSaveFolder(TREE) }
        docs.names[ROM] = "Fantasy Life [$TITLE].nsp"
        val first = SaveLibrary()
        val second = SaveLibrary("other").apply { remoteSlot = "savedata" }
        val route = edenRoute(player)
        val other = route.saves.copy(backendId = second.id)
        val failure = runCatching {
            LaunchSaves(saves, plugins(first, second)) { error("no save was placed") }
                .prepare(route.copy(servers = listOf(route.saves, other)), player, LaunchTarget.ContentUri(ROM))
        }.exceptionOrNull()
        assertTrue(failure is EdenSaveNotPlaced)
        assertEquals(1, first.listings)
        assertEquals(1, second.listings)
        assertEquals(0, first.negotiations + second.negotiations)
        assertTrue(marks.stored.isEmpty())
    }

    @Test
    fun anUnavailableListingCannotAcknowledgeAnUnplaceableEdenSave() = runBlocking {
        val player = app.foldcade.plugins.eden.EdenPlayer().apply { bindSaveFolder(TREE) }
        docs.names[ROM] = "Fantasy Life [$TITLE].nsp"
        val library = SaveLibrary().apply { listingFails = true }
        val failure = runCatching {
            LaunchSaves(saves, plugins(library)) { error("no save was placed") }
                .prepare(edenRoute(player), player, LaunchTarget.ContentUri(ROM))
        }.exceptionOrNull()
        assertTrue(failure is EdenSaveNotPlaced)
        assertFalse((failure as EdenSaveNotPlaced).needsUser)
        val route = edenRoute(player)
        assertTrue(edenSaveHold(failure) { PendingBackendLaunch("Fantasy Life", player.platformId, route, player.id, Panel.Bottom) } != null)
        assertEquals(0, library.negotiations)
        assertTrue(marks.stored.isEmpty())
    }

    @Test
    fun aResolvedEdenSaveWithAHashMismatchKeepsThePlainOkPrompt() = runBlocking {
        resolvedEdenPlacementFailure(tornWrite = false)
    }

    @Test
    fun aResolvedEdenSaveWithATornWriteKeepsThePlainOkPrompt() = runBlocking {
        resolvedEdenPlacementFailure(tornWrite = true)
    }

    private suspend fun resolvedEdenPlacementFailure(tornWrite: Boolean) {
        val player = eden(1)
        val path = edenPath(1)
        docs.file("$path/systemdata", "old progress")
        val route = edenRoute(player)
        assertEquals(1, saves.spots(player, route.saves, LaunchTarget.ContentUri(ROM), forLaunch = true).size)
        docs.library[SERVER] = zipOf("$TITLE/systemdata" to "server")
        val hash = if (tornWrite) md5Hex(docs.library.getValue(SERVER)) else "0".repeat(32)
        val library = SaveLibrary().apply {
            placement = app.foldcade.api.plugin.Placement(LaunchTarget.ContentUri(ROM), listOf(PlacedSave("savedata", SERVER, hash)))
        }
        if (tornWrite) docs.beforeWrite = {
            docs.file("$path/systemdata", "partial write")
            error("write interrupted")
        }
        val failure = runCatching {
            LaunchSaves(saves, plugins(library)) { error("no save was placed") }
                .prepare(route, player, LaunchTarget.ContentUri(ROM))
        }.exceptionOrNull()
        assertEquals(SaveNotPlaced::class.java, failure?.javaClass)
        assertEquals(1, library.negotiations)
        assertEquals(if (tornWrite) "partial write" else "old progress", docs.text("$path/systemdata"))
        val pending = edenSaveHold(failure as SaveNotPlaced) { error("a failed placement must not offer a bypass") }
        assertNull(pending)
        val dialog = saveNotPlacedDialog("Fantasy Life", HostScreen.Bottom, playAnyway = pending != null)
        assertEquals(DialogKind.Ok, dialog.kind)
        assertEquals(Copy.saveNotPlacedBody, dialog.body)
        assertEquals(listOf(DialogButton.Ok), dialog.buttons)
        assertEquals(0, dialog.safeIndex)
    }

    @Test
    fun playingEdenWithoutSyncingBypassesHeldAndIncomingSavesForOnlyThisLaunch() = runBlocking {
        val player = app.foldcade.plugins.eden.EdenPlayer().apply { bindSaveFolder(TREE) }
        docs.names[ROM] = "Fantasy Life [$TITLE].nsp"
        val library = SaveLibrary().apply { remoteSlot = "savedata" }
        val launch = LaunchSaves(saves, plugins(library)) { error("no save was placed") }
        val route = edenRoute(player)
        val prepared = launch.prepare(route, player, LaunchTarget.ContentUri(ROM), syncing = saveSyncForLaunch(player, player.id))
        assertTrue(prepared.spots.isEmpty())
        assertEquals(LaunchTarget.ContentUri(ROM), prepared.target)
        assertEquals(0, library.listings)
        assertEquals(0, library.negotiations)
        assertTrue(marks.stored.isEmpty())
        assertTrue(runCatching { launch.prepare(route, player, LaunchTarget.ContentUri(ROM)) }.exceptionOrNull() is EdenSaveNotPlaced)
        assertTrue(marks.stored.isEmpty())
        // The opt-out also bypasses sync when both the profile and title resolve.
        eden(1)
        launch.prepare(route, player, LaunchTarget.ContentUri(ROM), syncing = saveSyncForLaunch(player, player.id))
        assertEquals(0, library.negotiations)
        assertTrue(marks.stored.isEmpty())
    }

    @Test
    fun freshEdenWithNoIncomingSaveStartsUnplacedAndHandsOverItsNewSaveOnReturn() = runBlocking {
        val player = app.foldcade.plugins.eden.EdenPlayer().apply { bindSaveFolder(TREE) }
        docs.names[ROM] = "Fantasy Life [$TITLE].nsp"
        val library = SaveLibrary().apply { remoteSlot = "autosave" }
        val plugins = plugins(library)
        val route = edenRoute(player)
        val prepared = LaunchSaves(saves, plugins) { error("no save was placed") }.prepare(route, player, LaunchTarget.ContentUri(ROM))
        assertTrue(prepared.spots.isEmpty())
        assertEquals(LaunchTarget.ContentUri(ROM), prepared.target)
        assertEquals(0, library.negotiations)
        assertTrue(marks.stored.isEmpty())
        eden(1)
        docs.file("${edenPath(1)}/systemdata", "first boot progress")
        val returning = returnSpots(saves, LibraryReturn("server", route.saves, player, prepared.spots, ROM))
        assertEquals("savedata", returning.single().slot)
        val observed = saves.changedSince("server", route.saves, player, returning)
        assertEquals(1, observed.slots.size)
        plugins.reconcile("server", route.saves, playerForSaveSpots(player, route.saves, returning), observed)
        val zip = Files.readAllBytes(java.nio.file.Path.of(java.net.URI.create(library.captured.single().contentUri)))
        assertEquals("first boot progress", saveEntries(zip, setOf(TITLE))!!.getValue("$TITLE/systemdata")!!.toString(Charsets.UTF_8))
        saves.noteHandedOver("server", route.saves, player, returning, observed)
        assertTrue(saves.changedSince("server", route.saves, player, returning).slots.isEmpty())
    }

    private fun edenRoute(player: Player): GameRoute {
        val game = GAME.copy(backendId = "server", platformId = player.platformId)
        return GameRoute("eden", game, game)
    }

    private fun plugins(vararg libraries: LibraryBackend) = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
        register(object : PluginEntry {
            override val apiVersion = PLUGIN_API_VERSION
            override val apiMinor = PLUGIN_API_MINOR
            override val libraries = libraries.toList()
        })
    }

    private class SaveLibrary(override val id: String = "server") : LibraryBackend {
        override val displayName = id
        var heldSlot: String? = null
        var remoteSlot: String? = null
        var listingFails = false
        var listings = 0
        var negotiations = 0
        var placement: app.foldcade.api.plugin.Placement? = null
        val captured = mutableListOf<ObservedSlot>()
        override suspend fun connect() = Unit
        override suspend fun disconnect() = Unit
        override suspend fun listPlatforms() = emptyList<ListedPlatform>()
        override suspend fun listGames(platformId: String, query: GameQuery) = GamePage(emptyList(), null)
        override suspend fun ensureLocal(game: Game): LaunchTarget = error("already downloaded")
        override suspend fun saves(game: Game) = SaveSet(id, heldSlot?.let { listOf(SaveSlot(it, "0".repeat(32), "eden")) }.orEmpty())
        override suspend fun serverSaves(game: Game, player: Player?): List<ServerSave> {
            listings++
            check(!listingFails) { "offline" }
            return remoteSlot?.let { listOf(ServerSave("1", it, "eden", null, 0, 1, false, null)) }.orEmpty()
        }
        override suspend fun prepareLaunch(game: Game, player: Player): app.foldcade.api.plugin.Placement {
            negotiations++
            placement?.let { return it }
            error("an unresolved or declined Eden save must never enter mutating negotiation")
        }
        override suspend fun reconcile(game: Game, player: Player, observed: ObservedSaves): SyncResult {
            captured += observed.slots
            return SyncResult(SyncOutcome.Queued)
        }
    }

    private fun eden(user: Int): app.foldcade.plugins.eden.EdenPlayer {
        docs.names[ROM] = "Fantasy Life [$TITLE].nsp"
        docs.file(app.foldcade.plugins.eden.EdenPlayer.PROFILES, ByteArray(0x650).apply { this[0x10] = user.toByte() })
        return app.foldcade.plugins.eden.EdenPlayer().apply { bindSaveFolder(TREE) }
    }

    private fun edenPath(user: Int) = "${app.foldcade.plugins.eden.EdenPlayer.SAVES}/${"0".repeat(30)}0$user/$TITLE"

    private fun placed() = PlacedSave("card1", SERVER, md5Hex(docs.library.getValue(SERVER)))

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray =
        saveZip(entries.associate { (name, text) -> name to text.toByteArray() })

    /** Names the card's folders from the serial, which the test already knows. */
    private class Searcher : Player by Idle {
        override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", TREE))
        override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean) =
            SaveFolderSearch("memcards/card", "SLUS-21065")
    }

    /** Writes a marker. The shell must refuse that write on the way back. */
    private class Setup : Player by Idle {
        override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", TREE))
        override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean): SaveFolderSearch? {
            if (!folder.write("memcards/new/note", byteArrayOf(1))) return null
            return SaveFolderSearch("memcards/new", "SLUS-21065")
        }
    }

    /** Moves one file into Foldcade's backups. */
    private class Mover : Player by Idle {
        override val id = "mover"
        override fun saveDeclarations(game: Game) = listOf(SaveDeclaration("card1", TREE))
        override fun findSaveFolders(slot: String, game: GameFileReader, folder: SaveFolderAccess, forLaunch: Boolean): SaveFolderSearch? {
            if (!folder.moveToBackup("memcards/old.ps2")) return null
            return SaveFolderSearch("memcards/new", "SLUS-21065")
        }
    }

    private class Tree : SaveDocuments {
        val names = mutableMapOf<String, String>()
        val library = mutableMapOf<String, ByteArray>()
        val files = sortedMapOf<String, ByteArray>()
        val folders = sortedSetOf<String>()
        var beforeWrite: () -> Unit = {}

        fun dir(path: String) {
            var folder = path
            while (folder.isNotEmpty()) {
                folders += folder
                folder = folder.substringBeforeLast('/', "")
            }
        }

        fun file(path: String, text: String) = file(path, text.toByteArray())

        fun file(path: String, bytes: ByteArray) {
            dir(path.substringBeforeLast('/', ""))
            files[path] = bytes
        }

        fun text(path: String): String? = files[path]?.toString(Charsets.UTF_8)

        override fun displayName(uri: String) = names[uri]
        override fun find(folderUri: String, name: String) = "$TREE/$name".takeIf { name in files }
        override fun create(folderUri: String, name: String): String {
            dir(name.substringBeforeLast('/', ""))
            files[name] = ByteArray(0)
            return "$TREE/$name"
        }
        override fun read(uri: String) = library[uri] ?: files[uri.removePrefix("$TREE/")]
        override fun write(uri: String, bytes: ByteArray) {
            beforeWrite()
            files[uri.removePrefix("$TREE/")] = bytes
        }
        override fun list(folderUri: String, name: String): List<SaveEntry>? {
            if (name !in folders) return null
            val below = "$name/"
            return folders.filter { it.startsWith(below) }.map { SaveEntry(it.removePrefix(below), "$TREE/$it", true) } +
                files.keys.filter { it.startsWith(below) }.map { SaveEntry(it.removePrefix(below), "$TREE/$it", false) }
        }

        override fun delete(uri: String): Boolean {
            val path = uri.removePrefix("$TREE/")
            val gone = files.remove(path) != null
            files.keys.removeIf { it.startsWith("$path/") }
            return gone || !files.containsKey(path)
        }
        override fun createFolder(folderUri: String, name: String): Boolean {
            dir(name)
            return true
        }
        override fun children(folderUri: String, path: String): List<SaveChild>? {
            if (path.isNotEmpty() && path !in folders) return null
            val prefix = if (path.isEmpty()) "" else "$path/"
            val found = linkedMapOf<String, SaveChild>()
            for (folder in folders) {
                val rest = folder.removePrefix(prefix)
                if (!folder.startsWith(prefix) || rest.isEmpty() || '/' in rest) continue
                found[rest] = SaveChild(rest, "$TREE/$folder", true, 0)
            }
            for ((file, bytes) in files) {
                val rest = file.removePrefix(prefix)
                if (!file.startsWith(prefix) || rest.isEmpty() || '/' in rest) continue
                found[rest] = SaveChild(rest, "$TREE/$file", false, bytes.size.toLong())
            }
            return found.values.toList()
        }
    }

    private class MemoryMarks : SaveMarks {
        val stored = mutableMapOf<String, String>()
        override fun mark(key: String) = stored[key]
        override fun setMark(key: String, hash: String) {
            stored[key] = hash
        }
        override fun clearMark(key: String) {
            stored.remove(key)
        }
    }

    private companion object {
        const val TITLE = "0100755017EE0000"
        const val TREE = "content://tree/arms"
        const val ROM = "content://roms/nfsu2.iso"
        const val SERVER = "lib://card1"
        val GAME = Game("romm", "37", "playstation-2", Availability.Cached, "Need for Speed Underground 2")
        val Idle = object : Player {
            override val id = "armsx2"
            override val displayName = "ARMSX2"
            override val platformId = "playstation-2"
            override val packageNames = emptyList<String>()
            override val needsLocalFile = true
            override val startDisplay = StartDisplay.PickerChoice
            override val occupiesBothDisplays = false
            override val requiresImportedGame = false
            override fun saveDeclarations(game: Game) = emptyList<SaveDeclaration>()
            override fun launchIntent(request: LaunchRequest): PlayerIntent = error("unused")
        }
        val PLAYER = Searcher()
    }
}
