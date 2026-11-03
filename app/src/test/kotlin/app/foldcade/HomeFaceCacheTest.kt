package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.language.*
import org.junit.Assert.*
import org.junit.Test

class HomeFaceCacheTest {
    private val group = "home.user.1"
    private val items = listOf(
        HomeItem("a", "Alpha", HomeKind.Rom, "nds", mark = "a"),
        HomeItem("b", "Beta", HomeKind.Rom, "nds"),
    )
    private val platforms = listOf(HomePlatform("nintendo-ds", "Nintendo DS", setOf("nds")))
    private val initial = HomeBoard(
        slots = listOf(HOME_ALL, group, "a", null, "b", "a", null),
        folders = mapOf(group to HomeFolder(group, "Games", userMade = true, slots = listOf("b", null, "a"))),
        recentAfter = Long.MAX_VALUE,
    )
    private fun session() = HomeSession(encodeHome(initial)).apply {
        remerge(emptyList(), emptyList(), items, platforms)
        restore(initial)
    }

    /** faceOf deliberately reads the live board/catalog, independently of the visible-face cache. */
    private fun assertCurrent(home: HomeSession) {
        val slots = visibleSlots(home.board)
        val expected = slots.map { id -> if (id == null) HomeFace(null, "", "", null, true, false, false, false, null, null, false) else home.faceOf(id) }
        assertEquals(expected, home.faces())
        assertEquals(expected.size, home.visibleCount())
        assertEquals(slots.indexOfLast { it != null }, home.lastOccupiedIndex())
        for (index in -1..expected.size) assertEquals(expected.getOrNull(index), home.face(index))
        assertSame(home.faces(), home.faces())
    }

    @Test fun facesFollowEveryKindOfBoardEdit() {
        val home = session()
        fun edit(change: (HomeBoard) -> HomeBoard) { home.restore(change(home.board)); assertCurrent(home) }
        assertCurrent(home)
        edit { renameFolder(it, group, "Renamed") }
        edit { setFolderIcon(it, group, "star", CollectionTint.Sky) }
        edit { openFolder(it, group) }
        edit { setFolderSort(it, group, FolderSort.Alphabetical, items) }
        edit { setFolderSort(it, group, FolderSort.Custom, items) }
        edit { closeFolder(it) }
        edit { hideGame(it, "a") }
        edit { showHidden(it, "a", items, platforms) }
        edit { enterEdit(it) }
        home.startMove(1); assertCurrent(home)
        home.handle(Meaning.MoveRight, 1); assertCurrent(home)
        home.handle(Meaning.CancelHold, home.board.hold!!.index); assertCurrent(home)
        edit { tidy(it) }
        edit { setFolderOnHome(it, group, false, items, platforms) }
        edit { setFolderOnHome(it, group, true, items, platforms) }
        edit { removeFromHome(it, visibleSlots(it).indexOf("a")) }
    }

    @Test fun catalogPlatformCopyAndRestoredGapChangesInvalidateFaces() {
        val home = session()
        val before = home.faces()
        val remote = Game("romm", "1", "nds", Availability.RemoteOnly, "Alpha")
        home.remerge(emptyList(), emptyList(), items.map { it.copy(title = it.title + " updated", mark = "new") },
            listOf(platforms.single().copy(name = "DS renamed")), remembered = mapOf("a" to remote), copies = mapOf("a" to listOf(remote)))
        assertCurrent(home)
        assertNotEquals(before, home.faces())
        assertEquals("DS renamed", home.faceOf("a").shortText)
        assertTrue(home.faceOf("a").onServer)
        home.remerge(emptyList(), emptyList(), items, platforms,
            copies = mapOf("a" to listOf(remote.copy(availability = Availability.Cached))))
        assertCurrent(home)
        assertFalse(home.faceOf("a").onServer)
        home.restore(home.board.copy(slots = home.board.slots + "missing"))
        assertCurrent(home)
        assertFalse(home.faces().last().empty)
        home.restoredGaps = setOf("missing")
        assertCurrent(home)
        assertTrue(home.faces().last().empty)
    }

    @Test fun iconPreviewAndFolderContentsInvalidateTheRootFace() {
        val home = session()
        val before = home.faces()
        home.pickIcon(1)
        home.handle(Meaning.MoveRight, 1)
        assertCurrent(home)
        assertNotEquals(before[1].mark, home.face(1)!!.mark)
        home.handle(Meaning.Back, 1)
        assertCurrent(home)
        assertEquals(before, home.faces())
        home.restore(home.board.copy(folders = home.board.folders + (group to home.board.folders.getValue(group).copy(slots = listOf("a")))))
        assertCurrent(home)
        assertNotEquals(before[1].shortText, home.face(1)!!.shortText)
    }

    @Test fun foldersViewReusesFacesAndRebuildsAfterItsNameOrCountChanges() {
        val home = session()
        val system = platformFolderId("nintendo-ds")
        home.restore(setFolderOnHome(home.board, system, true, items, platforms))
        home.showFolders()
        val before = home.faces()
        assertSame(before, home.faces())
        home.restore(renameFolder(home.board, system, "New name"))
        val after = home.faces()
        assertNotEquals(before, after)
        assertEquals(home.folderRows().map { it.folder.name }, after.map { it.title })
        assertEquals(home.folderRows().map { folderCountLine(it.count, apps = it.folder.holdsApps) }, after.map { it.shortText })
        assertEquals(after.size, home.visibleCount())
    }
}
