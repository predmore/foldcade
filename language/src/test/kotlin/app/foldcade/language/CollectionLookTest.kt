package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionLookTest {
    private val made = HomeFolder("home.user.1", "RPGs", userMade = true, mark = "sword", slots = listOf("lib:a"))

    @Test
    fun aCollectionsColourRoundTripsInARowOfItsOwn() {
        val tinted = setFolderIcon(HomeBoard(slots = listOf(HOME_ALL, made.id), folders = mapOf(made.id to made)), made.id, "ghost", CollectionTint.Violet)
        assertEquals(CollectionLook("ghost", CollectionTint.Violet), collectionLook(tinted.folders.getValue(made.id)))
        val raw = encodeHome(tinted)
        assertEquals(tinted, decodeHome(raw))
        // The folder row is the one earlier releases wrote; they skip the tint row and keep the rest.
        val older = raw.lines().filterNot { it.startsWith("tint\t") }.joinToString("\n")
        assertEquals(tinted.folders.getValue(made.id).copy(tint = null), decodeHome(older).folders.getValue(made.id))
    }

    @Test
    fun savesFromBeforeColoursAreGoldAndAPlainFolderShowsTheStackOfGames() {
        // A collection made before glyphs wore the folder mark, and before colours had none.
        val raw = "v1\nslots\thome.all\thome.user.1\thome.favorites\thome.user.2\n" +
            "folder\thome.user.1\tZelda\t\t\t1\t1\tfolder\tlib:a\n" +
            "folder\thome.favorites\tFavorites\t\t\t1\t0\tfolder\tlib:b\n" +
            "folder\thome.user.2\tPads\t\t\t1\t1\tconsole\tlib:c\n"
        val board = decodeHome(raw)
        assertEquals(CollectionLook(MARK_LIBRARY, CollectionTint.Gold), collectionLook(board.folders.getValue("home.user.1")))
        assertEquals(CollectionLook(MARK_STAR, CollectionTint.Gold), collectionLook(board.folders.getValue(HOME_FAVORITES)))
        assertEquals(CollectionLook(MARK_CONSOLE, CollectionTint.Gold), collectionLook(board.folders.getValue("home.user.2")))
        // A colour a later release adds reads as gold here.
        assertEquals(CollectionTint.Gold, collectionLook(made.copy(tint = "ultraviolet"))?.tint)
    }

    @Test
    fun recentlyPlayedIsATealClockAndFoldersKeepTheirMarks() {
        assertEquals(CollectionLook(MARK_CLOCK, CollectionTint.Teal), collectionLook(HomeFolder(HOME_RECENT, "Recently played", mark = MARK_RECENT)))
        assertNull(collectionLook(HomeFolder("home.platform.nds", "DS", platformId = "nintendo-ds", mark = "nintendo-ds")))
        assertNull(collectionLook(HomeFolder(HOME_MOONLIGHT, "Moonlight", bucket = HomeKind.Moonlight, mark = "moonlight")))
    }

    @Test
    fun onlyCollectionsTakeALookAndANullTintKeepsTheirColour() {
        val board = HomeBoard(slots = listOf(HOME_ALL, made.id), folders = mapOf(made.id to made.copy(tint = "teal")))
        val glyphOnly = setFolderIcon(board, made.id, "heart")
        assertEquals(CollectionLook("heart", CollectionTint.Teal), collectionLook(glyphOnly.folders.getValue(made.id)))
        assertEquals(board, setFolderIcon(board, made.id, MARK_FOLDER, CollectionTint.Rose))
        val system = mergeHome(HomeBoard(), emptyList(), emptyList())
        assertEquals(system, setFolderIcon(system, HOME_GAMENATIVE, "star", CollectionTint.Rose))
    }

    @Test
    fun aLookNamesItselfAsAMarkAndOnlyThatMarkParses() {
        val look = CollectionLook("two-players", CollectionTint.Coral)
        assertEquals(look, CollectionLook.parse(look.mark))
        assertNull(CollectionLook.parse("nintendo-3ds"))
        assertNull(CollectionLook.parse(null))
        assertNull(CollectionLook.parse("collection:ultraviolet:star"))
        assertNull(CollectionLook.parse("collection:gold:"))
        // Every choice has a name for under the grid, and every colour its own column.
        assertTrue(FOLDER_ICONS.all { glyphName(it).isNotEmpty() })
        assertEquals(FOLDER_ICONS.size, FOLDER_ICONS.toSet().size)
        assertEquals(10, IconPick.COLUMNS)
    }

    @Test
    fun theCursorPicksAsItMovesAndCrossesToTheChoiceInUse() {
        val start = IconPick.open(made.id, CollectionLook("sword", CollectionTint.Indigo))
        assertEquals(FOLDER_ICONS.indexOf("sword"), start.glyph)
        assertEquals(CollectionLook("sword", CollectionTint.Indigo), start.look)
        assertEquals("Sword", start.focusedName)
        // Sword is on the top row, so up goes to the colours, on indigo, the colour in use.
        val tints = start.step(Meaning.MoveUp)
        assertTrue(tints.onTints)
        assertEquals(start.look, tints.look)
        assertEquals("Indigo", tints.focusedName)
        val sky = tints.step(Meaning.MoveRight)
        assertEquals(CollectionTint.Sky, sky.look.tint)
        assertEquals(sky, sky.step(Meaning.MoveUp))
        // Down goes back to sword, wherever the colour cursor was.
        val back = sky.step(Meaning.MoveDown)
        assertFalse(back.onTints)
        assertEquals(CollectionLook("sword", CollectionTint.Sky), back.look)
        // The ends hold.
        val first = IconPick.open(made.id, CollectionLook(MARK_STAR, CollectionTint.Gold))
        assertEquals(first, first.step(Meaning.MoveLeft))
        val leftmost = first.step(Meaning.MoveUp)
        assertEquals(leftmost, leftmost.step(Meaning.MoveLeft))
        assertEquals(CollectionTint.Slate, (1..20).fold(leftmost) { pick, _ -> pick.step(Meaning.MoveRight) }.look.tint)
        val last = FOLDER_ICONS.lastIndex
        val end = first.copy(glyph = last)
        assertEquals(end, end.step(Meaning.MoveRight))
        assertEquals(end, end.step(Meaning.MoveDown))
        // Down onto a short last row stops at its end; up walks back a row.
        val above = first.copy(glyph = last - last % IconPick.COLUMNS - 1)
        assertEquals(last, above.step(Meaning.MoveDown).glyph)
        assertEquals(above.glyph - IconPick.COLUMNS, above.step(Meaning.MoveUp).glyph)
        // L1 and R1 step the colour from the grid, and the glyph stays.
        val deep = first.copy(glyph = 33)
        assertEquals(deep.copy(tint = 1), deep.step(Meaning.RightPanel))
        assertEquals(deep, deep.step(Meaning.LeftPanel))
        // A and B are the session's: the pick itself ignores them.
        assertEquals(start, start.step(Meaning.Activate))
        assertEquals(start, start.step(Meaning.CancelHold))
    }
}
