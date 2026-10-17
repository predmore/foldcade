package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SameGamesTest {
    private val platforms = listOf(HomePlatform("nintendo-3ds", "Nintendo 3DS", setOf("3ds")))
    private val local = "lib:local-folder:content://rf4"
    private val server = "lib:romm:31"
    private val key = sameKey("nintendo-3ds", "Rune Factory 4 (USA).cci")!!
    private val tile = sameGameId(key)

    @Test
    fun oneFileOnOnePlatformIsOneGameWhateverItsCase() {
        assertEquals(key, sameKey("Nintendo-3DS", "rune factory 4 (usa).CCI"))
        // A name spelled with a combining accent is the same name as its composed form.
        assertEquals(sameKey("snes", "Pokémon.sfc"), sameKey("snes", "Pokémon.sfc"))
        assertNull(sameKey("nintendo-3ds", " "))
        assertNull(sameKey(null, "a.cci"))
        assertEquals("rune factory 4 (usa).cci", sameGameFileName(tile))
        assertNull(sameGameFileName(server))
    }

    @Test
    fun aLocalFileAndItsServerCopyAreOneTile() {
        val fold = fold(
            listOf(rom(local, "Rune Factory 4", played = 9), rom(server, "Rune Factory 4 (USA).cci", played = 4), rom("lib:romm:7", "Mario")),
            mapOf(
                local to SameSource(key, rank = 2, named = true),
                server to SameSource(key, rank = 1, named = false),
                "lib:romm:7" to SameSource(sameKey("nintendo-3ds", "mario.cci"), 1, true),
            ),
        )
        assertEquals(listOf(tile, "lib:romm:7"), fold.items.map { it.id })
        val merged = fold.items.first()
        // The server's name is only its file name here, so the folder's title wins.
        assertEquals("Rune Factory 4", merged.title)
        assertEquals(9L, merged.lastPlayedMillis)
        assertEquals(listOf(server, local), fold.members.getValue(tile))
        assertEquals(mapOf(local to tile, server to tile), fold.aliases)
    }

    @Test
    fun aServersOwnNameTitlesTheTile() {
        val fold = fold(
            listOf(rom(local, "Rune Factory 4"), rom(server, "Rune Factory 4: Special")),
            mapOf(local to SameSource(key, 2, true), server to SameSource(key, 1, true)),
        )
        assertEquals("Rune Factory 4: Special", fold.items.single().title)
    }

    @Test
    fun differentNamesOrPlatformsStayApart() {
        val items = listOf(rom(local, "A"), rom(server, "A"), rom("lib:romm:9", "A"))
        val fold = fold(
            items,
            mapOf(
                local to SameSource(key, 2, true),
                server to SameSource(sameKey("nintendo-3ds", "Rune Factory 4 (Europe).cci"), 1, true),
                "lib:romm:9" to SameSource(sameKey("nintendo-ds", "Rune Factory 4 (USA).cci"), 1, true),
            ),
        )
        assertEquals(items, fold.items)
        assertTrue(fold.aliases.isEmpty())
    }

    @Test
    fun threeCopiesAreOneTile() {
        val second = "lib:romm#2:5"
        val fold = fold(
            listOf(rom(second, "B"), rom(local, "B"), rom(server, "B")),
            mapOf(
                local to SameSource(key, 3, true),
                server to SameSource(key, 1, true),
                second to SameSource(key, 2, true),
            ),
        )
        assertEquals(listOf(tile), fold.items.map { it.id })
        assertEquals(listOf(server, second, local), fold.members.getValue(tile))
    }

    @Test
    fun aTileOutlivesACopyThatIsNotListed() {
        val aliases = mapOf(local to tile, server to tile)
        val alone = fold(listOf(rom(local, "RF4")), mapOf(local to SameSource(key, 2, true)), aliases)
        assertEquals(listOf(tile), alone.items.map { it.id })
        assertEquals(listOf(local), alone.members.getValue(tile))
        assertEquals(aliases, alone.aliases)
        // Never paired, it keeps its own id.
        val fresh = fold(listOf(rom(local, "RF4")), mapOf(local to SameSource(key, 2, true)))
        assertEquals(listOf(local), fresh.items.map { it.id })
        // A copy the library can no longer name, such as one that became several files, stays.
        val unnamed = fold(listOf(rom(server, "RF4")), mapOf(server to SameSource(null, 1, true)), aliases)
        assertEquals(listOf(tile), unnamed.items.map { it.id })
    }

    @Test
    fun aCopyListedUnderAnotherNameIsItsOwnGameAgain() {
        val aliases = mapOf(local to tile, server to tile)
        val renamed = SameSource(sameKey("nintendo-3ds", "RF4.cci"), 1, true)
        val fold = fold(listOf(rom(server, "RF4")), mapOf(server to renamed), aliases)
        assertEquals(listOf(server), fold.items.map { it.id })
        assertEquals(mapOf(local to tile), fold.aliases)
    }

    @Test
    fun aliasesRoundTripAndRefuseAnythingElse() {
        val aliases = mapOf(local to tile, server to tile)
        assertEquals(aliases, decodeSameGames(encodeSameGames(aliases)))
        assertEquals(emptyMap<String, String>(), decodeSameGames(null))
        assertEquals(emptyMap<String, String>(), decodeSameGames("a\u001fb"))
        assertEquals(setOf(tile, local, server), sameGameIds(tile, aliases))
        assertEquals(setOf(local), sameGameIds(local, emptyMap()))
    }

    @Test
    fun bothPlacesBecomeTheTileAndASecondPlaceAGap() {
        val folder = platformFolderId("nintendo-3ds")
        val board = HomeBoard(
            slots = listOf(HOME_ALL, local, server, folder),
            folders = mapOf(
                folder to HomeFolder(folder, "Nintendo 3DS", platformId = "nintendo-3ds", slots = listOf(local, server)),
                HOME_FAVORITES to HomeFolder(HOME_FAVORITES, "Favorites", slots = listOf(server)),
                HOME_RECENT to HomeFolder(HOME_RECENT, "Recently played", slots = listOf(server, local)),
            ),
        )
        val adopted = adoptSameGames(board, mapOf(local to tile, server to tile))
        assertEquals(listOf(HOME_ALL, tile, null, folder), adopted.slots)
        assertEquals(listOf(tile, null), adopted.folders.getValue(folder).slots)
        assertEquals(listOf(tile), adopted.folders.getValue(HOME_FAVORITES).slots)
        assertEquals(listOf(tile, null), adopted.folders.getValue(HOME_RECENT).slots)
        assertEquals(adopted, adoptSameGames(adopted, mapOf(local to tile, server to tile)))
    }

    @Test
    fun aCopyHiddenAsTheDuplicateDoesNotHideTheGame() {
        val folder = platformFolderId("nintendo-3ds")
        val placed = HomeBoard(
            slots = listOf(HOME_ALL, folder),
            folders = mapOf(folder to HomeFolder(folder, "Nintendo 3DS", platformId = "nintendo-3ds", slots = listOf(local))),
        )
        val hiddenCopy = hideGame(placed.copy(folders = placed.folders + (folder to placed.folders.getValue(folder).copy(slots = listOf(local, server)))), server)
        val adopted = adoptSameGames(hiddenCopy, mapOf(local to tile, server to tile))
        assertFalse(tile in adopted.hidden)
        assertFalse(server in adopted.hiddenSlots)
        assertFalse(tile in adopted.hiddenSlots)
        assertEquals(listOf(tile, null), adopted.folders.getValue(folder).slots)
    }

    @Test
    fun aGameHiddenEverywhereStaysHiddenWithItsPlaces() {
        val folder = platformFolderId("nintendo-3ds")
        val board = HomeBoard(
            slots = listOf(HOME_ALL, local, folder),
            folders = mapOf(folder to HomeFolder(folder, "Nintendo 3DS", platformId = "nintendo-3ds", slots = listOf(local))),
        )
        val hidden = hideGame(board, local)
        val adopted = adoptSameGames(hidden, mapOf(local to tile, server to tile))
        assertEquals(setOf(tile), adopted.hidden)
        assertEquals(hidden.hiddenSlots.getValue(local), adopted.hiddenSlots.getValue(tile))
        assertTrue(local !in adopted.hiddenSlots)
        // A new copy of a hidden game does not bring it back.
        val merged = mergeHome(adopted, listOf(rom(tile, "RF4")), platforms)
        assertTrue(tile in merged.hidden)
        assertFalse(tile in merged.folders.getValue(folder).slots)
        val shown = showHidden(merged, tile, listOf(rom(tile, "RF4")), platforms)
        assertEquals(tile, shown.slots[1])
    }

    @Test
    fun aHiddenGameRemembersEveryCopysPlaces() {
        val quests = "home.user.1"
        val board = HomeBoard(
            slots = listOf(HOME_ALL, quests),
            folders = mapOf(
                quests to HomeFolder(quests, "Quests", userMade = true, slots = listOf(local)),
                HOME_FAVORITES to HomeFolder(HOME_FAVORITES, "Favorites", slots = listOf(server)),
            ),
        )
        val hidden = hideGame(hideGame(board, local), server)
        val adopted = adoptSameGames(hidden, mapOf(local to tile, server to tile))
        assertEquals(setOf(quests, HOME_FAVORITES), adopted.hiddenSlots.getValue(tile).map { it.group }.toSet())
        val shown = showHidden(mergeHome(adopted, listOf(rom(tile, "RF4")), platforms), tile, listOf(rom(tile, "RF4")), platforms)
        assertTrue(tile in shown.folders.getValue(quests).slots)
        assertTrue(tile in shown.folders.getValue(HOME_FAVORITES).slots)
    }

    @Test
    fun aGameTakenOffHomeStaysOff() {
        val adopted = adoptSameGames(HomeBoard(offHome = setOf(server)), mapOf(server to tile, local to tile))
        assertEquals(setOf(tile), adopted.offHome)
        val placedToo = adoptSameGames(HomeBoard(slots = listOf(HOME_ALL, local), offHome = setOf(server)), mapOf(server to tile, local to tile))
        assertTrue(placedToo.offHome.isEmpty())
    }

    @Test
    fun aMergeWhileTheServerIsLoadingKeepsTheTilePlaced() {
        val folder = platformFolderId("nintendo-3ds")
        val board = mergeHome(HomeBoard(), listOf(rom(local, "RF4"), rom(server, "RF4")), platforms)
        assertEquals(listOf(local, server), board.folders.getValue(folder).slots)
        val adopted = adoptSameGames(board, mapOf(local to tile, server to tile))
        val loading = mergeHome(adopted, emptyList(), platforms, unsettled = { it == tile })
        assertEquals(listOf(tile, null), loading.folders.getValue(folder).slots)
        val settled = mergeHome(loading, listOf(rom(tile, "RF4")), platforms)
        assertEquals(listOf(tile, null), settled.folders.getValue(folder).slots)
    }

    @Test
    fun theTileTakesEachCopysArtOnce() {
        val choices = ArtChoices()
            .set(local, ArtKind.Tile, "https://a/tile.png")
            .set(server, ArtKind.Tile, "https://b/tile.png")
            .set(server, ArtKind.Hero, "https://b/hero.png")
            .setMatch(server, ArtMatch(7, "RF4"))
        val adopted = choices.adopt(mapOf(tile to listOf(local, server)))
        assertEquals("https://a/tile.png", adopted.get(tile, ArtKind.Tile))
        assertEquals("https://b/hero.png", adopted.get(tile, ArtKind.Hero))
        assertEquals(ArtMatch(7, "RF4"), adopted.matches[tile])
        assertNull(adopted.get(local, ArtKind.Tile))
        assertNull(adopted.matches[server])
        assertEquals(adopted, adopted.adopt(mapOf(tile to listOf(local, server))))
        // The tile's own choice wins, and one cleared later stays cleared.
        val own = choices.set(tile, ArtKind.Tile, "https://t/tile.png").adopt(mapOf(tile to listOf(local, server)))
        assertEquals("https://t/tile.png", own.get(tile, ArtKind.Tile))
        val cleared = adopted.set(tile, ArtKind.Tile, null)
        assertNull(cleared.adopt(mapOf(tile to listOf(local, server))).get(tile, ArtKind.Tile))
    }

    private fun fold(items: List<HomeItem>, sources: Map<String, SameSource>, aliases: Map<String, String> = emptyMap()) =
        foldSameGames(items, sources, aliases)

    private fun rom(id: String, title: String, played: Long = 0L) =
        HomeItem(id, title, HomeKind.Rom, platformId = "nintendo-3ds", mark = "nintendo-3ds", lastPlayedMillis = played)
}
