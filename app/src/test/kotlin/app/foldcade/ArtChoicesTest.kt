package app.foldcade

import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.artwork.*
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtChoicesTest {
    @get:Rule val temp = TemporaryFolder()
    private val plugins = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore())

    @Test fun overridesWinOverCachedAndFetchedArtOnlyForTheirKindAndResetRestoresAutomatic() = runBlocking {
        val query = ArtQuery("game", "Game")
        val automatic = ArtSet("automatic", "https://auto/cover", "https://auto/square")
        val scene = ArtScene("https://auto/hero", "https://auto/logo", "https://auto/screen")
        val source = object : ArtSource {
            override val id = "auto"
            override fun handles(query: ArtQuery) = true
            override suspend fun find(query: ArtQuery) = automatic
            override suspend fun scene(query: ArtQuery) = scene
        }
        var choices = ArtChoices().set(query.key, ArtKind.Tile, "https://choice/tile")
            .set(query.key, ArtKind.Hero, "https://choice/hero")
        val covers = CoverArt(plugins, ArtFinder({ listOf(source) }, ArtIndex(temp.newFile()))) { choices }
        assertEquals("https://choice/tile", covers.fetch(null, query)?.square)
        assertEquals("https://choice/tile", covers.cached(null, query)?.cover)
        assertEquals(scene.copy(background = "https://choice/hero"), covers.fetchScene(null, query))
        assertEquals(scene.copy(background = "https://choice/hero"), covers.cachedScene(null, query))
        choices = ArtChoices.decode(choices.encode()).set(query.key, ArtKind.Hero, null)
        assertEquals(scene, covers.cachedScene(null, query))
        assertEquals("https://choice/tile", covers.cached(null, query)?.square)
        choices = choices.set(query.key, ArtKind.Tile, null).set(query.key, ArtKind.Logo, "https://choice/logo")
        assertEquals(automatic, covers.cached(null, query))
        assertEquals(scene.copy(logo = "https://choice/logo"), covers.cachedScene(null, query))
        assertNull(covers.cached(null, query.copy(key = "another")))
    }

    @Test fun choicesCanSupplyArtWhenTheAutomaticChainHasNothing() {
        val query = ArtQuery("game", "Game")
        val choices = ArtChoices().set(query.key, ArtKind.Tile, "https://choice/tile").set(query.key, ArtKind.Logo, "https://choice/logo")
        val covers = CoverArt(plugins, ArtFinder({ emptyList() }, ArtIndex(temp.newFile()))) { choices }
        assertEquals("https://choice/tile", covers.cached(null, query)?.square)
        assertEquals(ArtScene(logo = "https://choice/logo"), covers.cachedScene(null, query))
    }

    @Test fun androidGamesCanSearchByTitleWhileAppsKeepTheirIcons() {
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins)
        shell.setInstalledApps(listOf(LaunchableApp("game", "Game", true), LaunchableApp("notes", "Notes", false)), emptySet())
        assertEquals(ArtQuery(androidHomeId("game"), "Game"), shell.artQuery(ShelfGame(androidHomeId("game"), "Game", "", androidPackage = "game")))
        assertNull(shell.artQuery(ShelfGame(androidHomeId("notes"), "Notes", "", androidPackage = "notes")))
    }

    @Test fun artInAllNeedsAKeyAndThePickerConsumesInputUntilBack() {
        val applied = mutableListOf<Triple<String, ArtKind, String?>>()
        val shell = ShellController(SessionStore(MemoryPrefs()), plugins,
            onArtApply = { id, kind, url -> applied += Triple(id, kind, url) })
        shell.ingestLibrary("local-folder", listOf(GridEntry(
            id = "game", title = "Game", shortText = "Nintendo 3DS", platformId = "nintendo-3ds",
            availabilityLabel = null, occupiesBothDisplays = false,
            game = app.foldcade.api.plugin.Game("local-folder", "game", "nintendo-3ds",
                app.foldcade.api.plugin.Availability.LocalOnly, "Game"),
        )), emptyList())
        shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        assertFalse(actionRows(shell.model.tileActions, shell.model.appActions).contains(Row.ChooseArt))
        if (shell.model.panel != null) shell.onMeaning(Meaning.Back, HostScreen.Top)
        shell.setArtKeySaved(true)
        shell.onMeaning(Meaning.Options, HostScreen.Bottom)
        val rows = panelRows(shell.model.panel!!, shell.model)
        val index = rows.indexOf(Row.ChooseArt)
        assertTrue(rows.toString(), index >= 0)
        shell.touchPanel(index, HostScreen.Top)
        shell.touchPanel(index, HostScreen.Top)
        val state = shell.artPicker!!
        assertEquals(homeGameId("local-folder", "game"), state.gameId)
        assertEquals(HomeKeys.ChoosingArt, shell.homeKeys())
        shell.artCandidates(state.copy(loading = false, candidates = listOf(ArtCandidate("1", "https://cdn/one", "https://cdn/thumb"))))
        assertNull(shell.onMeaning(Meaning.Activate, HostScreen.Bottom))
        assertEquals(Triple(state.gameId, ArtKind.Tile, "https://cdn/one"), applied.last())
        shell.onMeaning(Meaning.ActivateBottom, HostScreen.Bottom)
        assertEquals(Triple(state.gameId, ArtKind.Tile, null), applied.last())
        assertFalse(shell.artPicker!!.preview)
        shell.onMeaning(Meaning.RightPanel, HostScreen.Top)
        assertEquals(ArtKind.Hero, shell.artPicker!!.kind)
        assertTrue(shell.artPicker!!.preview)
        shell.onMeaning(Meaning.Back, HostScreen.Bottom)
        assertNull(shell.artPicker)
        assertTrue(shell.homeAllOpen())
        assertEquals(HomeKeys.AllLibrary, shell.homeKeys())
    }

    @Test fun changingTheMatchPersistsItRejectsOldCandidatesAndKeepsOtherOverrides() {
        val id = homeGameId("local-folder", "game")
        var saved = ArtChoices().set(id, ArtKind.Logo, "https://cdn/chosen-logo")
        fun shell(): ShellController {
            val shell = ShellController(SessionStore(MemoryPrefs()), plugins,
                savedArtMatch = { saved.matches[it] },
                onArtMatch = { gameId, match -> saved = saved.setMatch(gameId, match) })
            shell.ingestLibrary("local-folder", listOf(GridEntry(
                id = "game", title = "Game", shortText = "Nintendo 3DS", platformId = "nintendo-3ds",
                availabilityLabel = null, occupiesBothDisplays = false,
                game = app.foldcade.api.plugin.Game("local-folder", "game", "nintendo-3ds",
                    app.foldcade.api.plugin.Availability.LocalOnly, "Game"),
            )), emptyList())
            shell.onMeaning(Meaning.Activate, HostScreen.Bottom)
            shell.setArtKeySaved(true)
            shell.onMeaning(Meaning.Options, HostScreen.Bottom)
            val index = panelRows(shell.model.panel!!, shell.model).indexOf(Row.ChooseArt)
            shell.touchPanel(index, HostScreen.Top)
            shell.touchPanel(index, HostScreen.Top)
            return shell
        }
        val first = ArtMatch(1, "Wrong Collection")
        val second = ArtMatch(2, "QuackShot")
        val picker = shell()
        picker.artMatches(id, listOf(first, second))
        val previous = picker.artPicker!!.copy(loading = false, candidates = listOf(ArtCandidate("old", "https://cdn/old", "https://cdn/thumb")))
        picker.artCandidates(previous)
        picker.onMeaning(Meaning.LetterForward, HostScreen.Bottom)
        assertEquals(second, saved.matches[id])
        assertEquals(second, picker.artPicker!!.match)
        assertTrue(picker.artPicker!!.candidates.isEmpty())
        assertEquals(2, picker.artQuery(picker.focusedGame()!!)!!.steamGridDbGameId)
        picker.artCandidates(previous)
        assertTrue("Old match results replaced the new match", picker.artPicker!!.candidates.isEmpty())
        assertEquals("https://cdn/chosen-logo", saved.get(id, ArtKind.Logo))
        picker.onMeaning(Meaning.Back, HostScreen.Bottom)
        saved = ArtChoices.decode(saved.encode())
        val restarted = shell()
        assertEquals(second, restarted.artPicker!!.match)
        restarted.artMatches(id, listOf(first))
        assertEquals(listOf(second, first), restarted.artPicker!!.matches)
        assertEquals(2, restarted.artQuery(restarted.focusedGame()!!)!!.steamGridDbGameId)
    }

    @Test fun artIsOfferedOnlyWhileFoldcadeOwnsTheBottomScreen() {
        val cases = listOf(
            Session() to true,
            Session().launch(ExternalApp("bottom"), Panel.Bottom).browse(Panel.Top) to false,
            Session().launch(ExternalApp("top"), Panel.Top).browse(Panel.Bottom) to true,
            Session().launch(ExternalApp("both", occupiesBothDisplays = true)) to false,
        )
        for ((session, available) in cases) {
            val store = SessionStore(MemoryPrefs())
            val shell = ShellController(store, plugins)
            shell.setInstalledApps(listOf(LaunchableApp("game", "Game", true)), emptySet())
            shell.openHomeAll()
            shell.setArtKeySaved(true)
            store.update { session }
            shell.onMeaning(Meaning.Options, HostScreen.Top)
            val rows = actionRows(shell.model.tileActions, shell.model.appActions)
            assertEquals(session.toString(), available, Row.ChooseArt in rows)
            assertNull(shell.artPicker)
        }
    }

    @Test fun losingTheBottomScreenAfterOpeningTheMenuCannotOpenAnInvisibleArtPicker() {
        val store = SessionStore(MemoryPrefs())
        val shell = ShellController(store, plugins)
        shell.setInstalledApps(listOf(LaunchableApp("game", "Game", true)), emptySet())
        shell.openHomeAll()
        shell.setArtKeySaved(true)
        shell.onMeaning(Meaning.Options, HostScreen.Top)
        val index = panelRows(shell.model.panel!!, shell.model).indexOf(Row.ChooseArt)
        assertTrue(index >= 0)
        store.update { it.launch(ExternalApp("bottom"), Panel.Bottom).browse(Panel.Top) }
        shell.touchPanel(index, HostScreen.Top)
        shell.touchPanel(index, HostScreen.Top)
        assertNull(shell.artPicker)
        assertNull(store.session.surfaceOn(Panel.Bottom))
        assertEquals(Surface.Picker, store.session.surfaceOn(Panel.Top))
    }

}
