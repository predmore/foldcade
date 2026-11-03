package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The 2 November Thor QA's labels, hints and pages (UX-10, 18, 21, 23, 24, 29, 31). */
class QaPolishTest {
    @Test
    fun aSystemFolderTooWideForItsTileSaysItsShortName() {
        assertEquals("GBA", folderShortName(HomeFolder("p", "Game Boy Advance", platformId = "game-boy-advance")))
        assertEquals("Switch", folderShortName(HomeFolder("s", "Nintendo Switch", platformId = "nintendo-switch")))
        assertEquals(LibraryCopy.recent, folderShortName(HomeFolder(HOME_RECENT, Copy.recentlyPlayed, mark = MARK_RECENT)))
        // A name the player gave stays theirs, and a short name that is the same says nothing.
        assertNull(folderShortName(HomeFolder("p", "My handhelds", platformId = "game-boy-advance", namedByUser = true)))
        assertNull(folderShortName(HomeFolder("g", "Genesis", platformId = "genesis")))
        assertNull(folderShortName(HomeFolder("c", "Favourites", userMade = true)))
        assertEquals("Android", folderShortName(HomeFolder(HOME_ANDROID_GAMES, "Android Games", bucket = HomeKind.AndroidGame)))
        assertNull(folderShortName(HomeFolder(HOME_ANDROID_APPS, "Android Apps", bucket = HomeKind.AndroidApp)))
    }

    @Test
    fun androidsOwnSettingsIsNamedAndroidSettings() {
        assertEquals(Copy.androidSettings, appName(ANDROID_SETTINGS_PACKAGE, "Settings"))
        assertEquals("Files", appName("com.android.documentsui", "Files"))
        assertEquals("Settings", appName(null, "Settings"))
        assertEquals(SettingsCopy.title, appShortName(ANDROID_SETTINGS_PACKAGE))
        assertNull(appShortName("com.android.documentsui"))
    }

    @Test
    fun aMissingEmulatorEndsInWordsTheRowKeepsWhole() {
        val value = PlayerChoiceCopy.notInstalled("Missing DS player (QA)")
        assertEquals("Missing DS player (QA) · not installed", value)
        assertEquals("Missing DS player (QA)", value.removeSuffix(PlayerChoiceCopy.notInstalledEnd))
    }

    @Test
    fun aShippedPackSaysItComesWithFoldcadeOnceAndACleanOneReportsNothing() {
        val about = ThemeAbout(author = "Foldcade", description = "True black.")
        val afterglow = ThemeEntry("afterglow", "Afterglow", null, about, emptyList(), removable = false, inUse = true, onTrial = false)
        assertEquals(listOf(ThemesCopy.builtIn, ThemesCopy.inUse), themeFacts(afterglow))
        val installed = afterglow.copy(id = "dusk", name = "Dusk", release = "1.2.0", about = about.copy(author = "Ana"), removable = true, inUse = false)
        assertEquals(listOf("Ana", "1.2.0"), themeFacts(installed))
    }

    @Test
    fun aFolderPageSaysWhereItIsWhatItHoldsAndWhetherItBringsArt() {
        val both = LibraryFolder("tree", "foldcade-qa1102", path = "ROMs/foldcade-qa1102", games = 38, media = true)
        assertEquals("ROMs/foldcade-qa1102 · 38 games · ${SettingsCopy.artAndInfo}", libraryFolderDetail(both))
        // A media-only folder's title already says art and info; one with nothing found says only where it is.
        assertEquals("Download/ES-DE", libraryFolderDetail(LibraryFolder("t", "ES-DE", artAndInfo = true, path = "Download/ES-DE", media = true)))
        assertEquals("1 game", libraryFolderDetail(LibraryFolder("t", "x", games = 1)))
        // A folder at the top of its storage would say its name twice.
        assertEquals("9 games · ${SettingsCopy.artAndInfo}", libraryFolderDetail(LibraryFolder("t", "foldcade-qa1102D", path = "foldcade-qa1102D", games = 9, media = true)))
        assertNull(libraryFolderDetail(LibraryFolder("t", "x")))
    }

    @Test
    fun aFocusedFolderNamesAOpen() {
        val map = FaceMap.standard()
        assertEquals(listOf(Copy.hintOpen, Copy.hintOptions), gridHints(null, HomeKeys.Idle, map, options = true, play = PlayKey.Open).map { it.label })
        // Inside a folder, Back stays after it.
        assertEquals(listOf(Copy.hintOpen, Copy.back), gridHints(hintFor(HintPlace.InsidePlatform), HomeKeys.Idle, map, play = PlayKey.Open).map { it.label })
    }
}
