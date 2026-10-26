package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.Copy
import app.foldcade.language.HOME_MOONLIGHT
import app.foldcade.language.HOME_ROOT
import app.foldcade.language.HomeBoard
import app.foldcade.language.HeroSubject
import app.foldcade.language.HostScreen
import app.foldcade.language.StreamHostSetting
import app.foldcade.language.TileActivity
import app.foldcade.language.activityLine
import app.foldcade.language.GridStrip
import app.foldcade.language.Meaning
import app.foldcade.language.Row
import app.foldcade.language.decodeHome
import app.foldcade.language.encodeHome
import app.foldcade.language.onHome
import app.foldcade.language.panelRows
import app.foldcade.plugins.moonlight.moonlightApp
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every pinned Moonlight game is on Home, and Home waits for Android before dropping one. */
class MoonlightHomeTest {
    private val host = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
    private val hades = "$host|7"
    private val celeste = "$host|8"

    @After
    fun clearShelf() {
        Shelf.moonlightGames = emptyList()
    }

    @Test
    fun moonlightTilesWaitForTheFirstShortcutRead() {
        val store = SessionStore(MemoryPrefs())
        Shelf.moonlightGames = listOf(tile(hades, "Hades"), tile(celeste, "Celeste"))
        ShellController(store, plugins()).apply {
            settleMoonlight()
            noteShelfChanged()
        }
        val placed = board(store)
        assertTrue(hades in moonlightSlots(placed))
        store.saveHomeBoard(
            encodeHome(
                placed.copy(
                    hidden = placed.hidden + celeste,
                    folders = placed.folders.mapValues { (_, folder) ->
                        folder.copy(slots = folder.slots.map { if (it == celeste) null else it })
                    },
                ),
            ),
        )
        val slot = moonlightSlots(board(store)).indexOf(hades)

        // A cold start: the shelf is empty until Android answers.
        Shelf.moonlightGames = emptyList()
        val shell = ShellController(store, plugins())
        shell.noteShelfChanged()
        assertEquals(slot, moonlightSlots(board(store)).indexOf(hades))
        assertTrue(celeste in board(store).hidden)

        Shelf.moonlightGames = listOf(tile(hades, "Hades"), tile(celeste, "Celeste"))
        shell.settleMoonlight()
        shell.noteShelfChanged()
        assertEquals(slot, moonlightSlots(board(store)).indexOf(hades))
        assertFalse(onHome(board(store), celeste))

        // Android said both pins are gone.
        Shelf.moonlightGames = emptyList()
        shell.noteShelfChanged()
        assertFalse(hades in moonlightSlots(board(store)))
        assertFalse(celeste in board(store).hidden)
    }

    @Test
    fun anAcceptedPinGoesHomeEvenWithAddNewGamesOff() {
        val store = SessionStore(MemoryPrefs())
        store.saveHomeBoard(encodeHome(HomeBoard(addNewToHome = false)))
        Shelf.moonlightGames = listOf(tile(hades, "Hades"))
        val shell = ShellController(store, plugins())
        shell.settleMoonlight()
        shell.noteShelfChanged()
        assertFalse(onHome(board(store), hades))

        shell.placeMoonlightPin(hades)
        assertTrue(hades in moonlightSlots(board(store)))
    }

    @Test
    fun hidingAMoonlightGameKeepsItsShortcut() {
        val removed = mutableListOf<String>()
        Shelf.moonlightGames = listOf(tile(hades, "Hades"))
        val shell = ShellController(
            SessionStore(MemoryPrefs()),
            plugins(),
            onMoonlightRemoved = { removed += it },
        )
        shell.settleMoonlight()
        shell.noteShelfChanged()
        focus(shell, titles(shell).indexOf("Moonlight"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)

        focus(shell, titles(shell).indexOf("Hades"))
        choose(shell, Row.Hide)
        assertFalse("Hades" in titles(shell))
        assertEquals(emptyList<String>(), removed)
    }

    @Test
    fun unpinningAMoonlightGameKeepsItsShortcutSinceItStaysInItsFolder() {
        val removed = mutableListOf<String>()
        Shelf.moonlightGames = listOf(tile(hades, "Hades"))
        val shell = ShellController(
            SessionStore(MemoryPrefs()),
            plugins(),
            onMoonlightRemoved = { removed += it },
        )
        shell.settleMoonlight()
        shell.noteShelfChanged()
        focus(shell, titles(shell).indexOf("Moonlight"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Hades"))
        choose(shell, Row.AddToCollection)
        // Home is Add to's first choice, where the menu opens.
        assertEquals(Row.Collection(HOME_ROOT, Copy.home), panelRows(shell.model.panel!!, shell.model)[shell.model.panel!!.index])
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)

        focus(shell, titles(shell).indexOf("Hades"))
        choose(shell, Row.RemoveTile)
        assertFalse("Hades" in titles(shell))
        assertEquals(emptyList<String>(), removed)
        focus(shell, titles(shell).indexOf("Moonlight"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        assertEquals(listOf("Hades"), titles(shell))
    }

    @Test
    fun theLastReadIsKeptAndTheImportSheetsKeysAreGone() {
        val prefs = MemoryPrefs()
        prefs.edit()
            .putString("moonlight_source", "ImportedList")
            .putBoolean("moonlight_import_settled", true)
            .putStringSet("moonlight_placements", setOf(hades))
            .apply()
        val store = SessionStore(prefs)
        SessionStore.RETIRED_MOONLIGHT_KEYS.forEach { assertFalse(it, prefs.contains(it)) }

        store.setMoonlightPins(listOfNotNull(moonlightApp(host, "7", "Hades"), moonlightApp(host, "8", "Celeste")))
        assertEquals(listOf("Hades", "Celeste"), SessionStore(prefs).moonlightPins().map { it.label })
    }

    @Test
    fun aGameItsComputerIsStreamingReadsRunningThere() {
        Shelf.moonlightGames = listOf(tile(hades, "Hades"), tile(celeste, "Celeste"))
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins())
        shell.settleMoonlight()
        shell.noteShelfChanged()
        focus(shell, titles(shell).indexOf("Moonlight"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Hades"))
        // Before any host is known, the line is the one it always was.
        assertNull(availability(shell))
        // The top screen asks Moonlight's poster through the same record.
        assertEquals(hades, shell.focusedRecord()?.remoteKey)

        shell.setMoonlightHosts(mapOf(host to MoonlightHost("game-win", online = null)))
        assertEquals("On game-win", availability(shell))
        assertNull(shell.focusedActivity())

        shell.setMoonlightHosts(mapOf(host to MoonlightHost("game-win", online = true, runningAppId = "7")))
        assertEquals(TileActivity.OnHost("game-win"), shell.focusedActivity())
        assertEquals("Running on game-win", activityLine(shell.focusedActivity()!!))
        focus(shell, titles(shell).indexOf("Celeste"))
        assertNull(shell.focusedActivity())
        assertEquals("On game-win", availability(shell))

        // A computer that stopped answering streams nothing Foldcade can see.
        shell.setMoonlightHosts(mapOf(host to MoonlightHost("game-win", online = false, runningAppId = "7")))
        assertEquals("game-win is offline", availability(shell))
        focus(shell, titles(shell).indexOf("Hades"))
        assertNull(shell.focusedActivity())
    }

    @Test
    fun onlyAnAnsweringHostsAppIsStreaming() {
        val hosts = mapOf(
            host to MoonlightHost("game-win", online = true, runningAppId = "7"),
            "0b3e2a9c-1111-2222-3333-444455556666" to MoonlightHost("den", online = false, runningAppId = "9"),
            "0b3e2a9c-1111-2222-3333-777788889999" to MoonlightHost("loft", online = true),
        )
        assertEquals(mapOf(hades to "game-win"), streamingTiles(hosts))
    }

    @Test
    fun aMoonlightTileHasARecordSoMoonlightsPosterCanAnswer() {
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins())
        val record = shell.artRecord(tile(hades, "Hades"))!!
        assertEquals("moonlight", record.backendId)
        assertEquals(hades, record.remoteKey)
        assertEquals("Hades", record.label)
        assertNull(shell.artRecord(tile(hades, "Hades").copy(libraryId = "droiddeck")))
    }

    @Test
    fun hostNamesAndTheClientIdAreKept() {
        val prefs = MemoryPrefs()
        SessionStore(prefs).setMoonlightHostNames(mapOf("8FE9E201-578A-7C2C-B017-F2A9A9D86886" to "game-win"))
        assertEquals(mapOf("8FE9E201-578A-7C2C-B017-F2A9A9D86886" to "game-win"), SessionStore(prefs).moonlightHostNames())
        val id = SessionStore(prefs).gameStreamClientId()
        assertTrue(Regex("[0-9a-f]{16}").matches(id))
        assertEquals(id, SessionStore(prefs).gameStreamClientId())
    }

    @Test
    fun aListedGameHasNoShortcutToDelete() {
        Shelf.moonlightGames = listOf(tile(hades, "Hades"), tile(celeste, "Celeste").copy(shortcut = false))
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins())
        shell.settleMoonlight()
        shell.noteShelfChanged()
        focus(shell, titles(shell).indexOf("Moonlight"))
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Hades"))
        assertTrue(Row.DeleteShortcut in menuRows(shell))
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        focus(shell, titles(shell).indexOf("Celeste"))
        assertFalse(Row.DeleteShortcut in menuRows(shell))
    }

    @Test
    fun pairingsAreKeptOnlyForTheKeyTheyWereMadeWith() {
        val prefs = MemoryPrefs()
        val gameWin = PairedStreamHost("8FE9E201-578A-7C2C-B017-F2A9A9D86886", "game-win", 47984, HOST_CERT)
        SessionStore(prefs).setPairedStreamHosts("aaaa", listOf(gameWin))
        assertEquals(listOf(gameWin), SessionStore(prefs).pairedStreamHosts("aaaa"))
        assertEquals("8fe9e201-578a-7c2c-b017-f2a9a9d86886", SessionStore(prefs).pairedStreamHosts("aaaa").single().key)
        // Moved to another device, or Foldcade reinstalled: the key is new, so the pairing is gone.
        assertEquals(emptyList<PairedStreamHost>(), SessionStore(prefs).pairedStreamHosts("bbbb"))
        assertEquals(emptyList<PairedStreamHost>(), SessionStore(prefs).pairedStreamHosts(null))

        val desktop = moonlightApp(gameWin.uuid, "881448767", "Desktop")!!
        SessionStore(prefs).setListedStreamApps(listOf(desktop))
        assertEquals(listOf(desktop), SessionStore(prefs).listedStreamApps())
    }

    @Test
    fun settingsListsEachComputerWithWhetherItIsPaired() {
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins())
        shell.setMoonlightHosts(
            mapOf(
                "b0000000-0000-0000-0000-000000000000" to MoonlightHost("loft", online = true),
                host to MoonlightHost("game-win", online = null),
            ),
        )
        shell.setPairedHosts(setOf(host))
        assertEquals(
            listOf(StreamHostSetting(host, "game-win", paired = true), StreamHostSetting("b0000000-0000-0000-0000-000000000000", "loft", paired = false)),
            shell.model.streamHosts,
        )
    }

    private fun menuRows(shell: ShellController): List<Row> {
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        return panelRows(shell.model.panel ?: error("Y opened no menu"), shell.model)
    }

    private fun availability(shell: ShellController): String? =
        (shell.focusedHero() as HeroSubject.Item).item.availability

    private fun tile(key: String, title: String) = ShelfGame(
        id = key,
        title = title,
        shortText = "Moonlight",
        platformId = "moonlight",
        mark = "moonlight",
        libraryId = "moonlight",
        remoteKey = key,
    )

    private fun board(store: SessionStore): HomeBoard = decodeHome(store.homeBoardRaw())

    private fun moonlightSlots(board: HomeBoard): List<String?> = board.folders[HOME_MOONLIGHT]?.slots.orEmpty()

    private fun plugins() = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())

    private companion object {
        /** A test-only certificate, the simulated host's from :gamestream's pairing tests. */
        val HOST_CERT = app.foldcade.gamestream.certificate(
            """
-----BEGIN CERTIFICATE-----
MIIDBjCCAe6gAwIBAgIUVL0uE69U96DAyaHIo47jBS6xxQwwDQYJKoZIhvcNAQEL
BQAwIzEhMB8GA1UEAwwYU3Vuc2hpbmUgR2FtZXN0cmVhbSBIb3N0MB4XDTI2MTAy
MjAyNDEwMFoXDTQ2MTAxNzAyNDEwMFowIzEhMB8GA1UEAwwYU3Vuc2hpbmUgR2Ft
ZXN0cmVhbSBIb3N0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlrDX
S0zo5QvPaVtE0uS+VfriaVgD6cf7TZ3oAMAbbWrPYxlxwZIu0XdFiN/xr6tgUUHs
sZEe4W4LVqHzEkEP/cD0BvNUKJ/DShDihS5RDRD3VyKBcqhxb+A8ApLVpV6BHq+e
8/Vypg35TL+XwtBnGgseFFyD9SIBRab7vcJgPzGyE0NK9obJmh0IAyw8KgxhyF2Y
qWJYH1F/x0epBMNjmDoa4GhNl5eX2/HqPmzbwiv+PBUGanlnJ1ubl9BBRxccicLS
8DUAWf5B+hrExUkS9c6SbVLHeKBjEw/b+aXKJhPf3Grp0G1tvzlBg0UpGNbWMHx4
XFMFcQ0beXnFpxqkNQIDAQABozIwMDAdBgNVHQ4EFgQUt5oj50CWAPioMmulvP/z
bOcUewYwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAPr5K7HHf
u+tRSEo+nj71axQ+ETzo+QUXuzZS1TS/2E6QX28/khQJ+3E96tv4Rysnj7AfVnbg
FCO7s7UQ6VQ74fbhcwIzd7yC0h5+SFvaG2VLwVqLZlEtBMMZqGA0EdsGA5VdJCiy
9eSCfgHs+m5pmVcinxmCh+qVHa2G+B4g/rUoYHwmqh346sA4smzJHMfA6KgpHPh6
crNsCnh/eufMlY0i24EdMLjclgpzr1XgiFryQupYUY2xAhCy7dGFelw4jVCqAvEf
B5thN81tSee8IJqQkjzrX3+0GHGChiP7QBchc/hgXD/Z7f98uuWiICy4TYT97AgN
VQBRe5lqnnx88g==
-----END CERTIFICATE-----
            """.trimIndent().toByteArray(),
        )!!
    }

    /** Y, down the menu to [row], then A. */
    private fun choose(shell: ShellController, row: Row) {
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val panel = shell.model.panel ?: error("Y opened no menu")
        val rows = panelRows(panel, shell.model)
        assertTrue("$row in $rows", row in rows)
        walkPanelTo(shell, rows.indexOf(row), HostScreen.Bottom)
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
    }

    private fun focus(shell: ShellController, index: Int) {
        assertTrue("no such tile", index >= 0)
        val at = shell.model.focus.cellIndex
        val columns = GridStrip.column(index) - GridStrip.column(at)
        repeat(maxOf(columns, 0)) { shell.onMeaning(Meaning.MoveRight, HostScreen.Bottom) }
        repeat(maxOf(-columns, 0)) { shell.onMeaning(Meaning.MoveLeft, HostScreen.Bottom) }
        // Into a shorter last column the cursor lands on its last tile, so rows count from there.
        val rows = GridStrip.row(index) - GridStrip.row(shell.model.focus.cellIndex)
        repeat(maxOf(rows, 0)) { shell.onMeaning(Meaning.MoveDown, HostScreen.Bottom) }
        repeat(maxOf(-rows, 0)) { shell.onMeaning(Meaning.MoveUp, HostScreen.Bottom) }
        assertEquals(index, shell.model.focus.cellIndex)
    }

    private fun titles(shell: ShellController): List<String> =
        List(shell.model.count) { index -> shell.homeFace(index)?.title.orEmpty() }
}
