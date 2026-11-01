package app.foldcade.localfolder

import app.foldcade.api.plugin.ArtworkRole
import org.junit.Assert.*
import org.junit.Test

class MediaMatchingTest {
    private fun game(name: String = "The Game (USA).m3u", path: String = name) =
        FolderGame("content://rom", name, titleFromFileName(name), "playstation", relativePath = path)
    private fun entry(path: String, platform: String = "playstation", title: String? = null, romPath: String? = null) =
        MediaEntry(platform, path, romPath, title)

    @Test fun exactRelativeStemWinsForSubfoldersAndPlaylists() {
        val expected = entry("rpg/the game (usa)")
        assertEquals(expected, matchMedia(game(path = "RPG/The Game (USA).m3u"), listOf(
            entry("The Game (USA)"), expected, entry("RPG/The Game (USA)", "snes"))))
        assertEquals(entry("The Game (USA)"), matchMedia(game(), listOf(entry("The Game (USA)"))))
    }

    @Test fun gamelistPathsAndNamesAreFallbacksWithinOnePlatform() {
        val gamelist = entry("unrelated-media", romPath = "./RPG/The Game (USA).m3u")
        assertEquals(gamelist, matchMedia(game(path = "RPG/The Game (USA).m3u"), listOf(gamelist)))
        assertEquals(gamelist, matchMedia(game(), listOf(gamelist)))
        assertNull(matchMedia(game(), listOf(gamelist.copy(platformId = "snes"))))
    }

    @Test fun normalizedTitlesNeedOneMatchAndMayUseTheSameRegion() {
        assertEquals(normalizeMediaTitle("The Rock & Roll (USA) [!]") , normalizeMediaTitle("Rock-and-Roll, The"))
        val europe = entry("Game (Europe)")
        val usa = entry("Game (USA) [Rev 1]")
        assertEquals(usa, matchMedia(game(), listOf(europe, usa)))
        assertNull(matchMedia(game("The Game.m3u"), listOf(europe, usa)))
        assertNull(matchMedia(game(), listOf(usa, entry("Game (USA) [Rev 2]"))))
        assertNull(matchMedia(game(), listOf(entry("Game 2"))))
    }

    @Test fun duplicateBasenamesRequireTheRelativeDirectory() {
        val a = entry("one/The Game (USA)")
        val b = entry("two/The Game (USA)")
        assertEquals(b, matchMedia(game(path = "two/The Game (USA).m3u"), listOf(a, b)))
        assertNull(matchMedia(game(), listOf(a, b)))
    }

    @Test fun everyRoleAliasIsCaseInsensitiveAndUnsupportedMediaIsIgnored() {
        for ((folders, role) in listOf(
            listOf("covers", "box2dfront", "boxart", "boxfront", "box", "images", "imgs") to ArtworkRole.Cover,
            listOf("screenshots", "snaps", "snap", "titlescreens", "titleshots") to ArtworkRole.Screenshot,
            listOf("marquees", "wheel", "wheels", "logos") to ArtworkRole.Logo,
            listOf("fanart", "backgrounds") to ArtworkRole.Background,
        )) for (folder in folders) assertEquals(folder, role, mediaRole(folder.uppercase())?.role)
        assertTrue(mediaRole("titlescreens")!!.rank > mediaRole("screenshots")!!.rank)
        for (folder in listOf("3dboxes", "backcovers", "miximages", "physicalmedia", "videos", "manuals", "other")) {
            assertNull(mediaRole(folder))
        }
        assertEquals("genesis", platformFromFolderName("megadrive")?.id)
        assertEquals("playstation", platformFromFolderName("psx")?.id)
        assertEquals("nintendo-3ds", platformFromFolderName("n3ds")?.id)
    }

    @Test fun relativePathsStayInsideTheirSystem() {
        assertEquals("images/x.png", mediaPath("./media/../images/x.png"))
        assertEquals("Game.zip", gamelistRomPath("/home/pi/roms/nes/Game.zip"))
        assertEquals("Game.zip", gamelistRomPath("C:\\ROMs\\nes\\Game.zip"))
        for (path in listOf("../outside.png", "/outside.png", "https://cdn/x.png", "content://tree/x", "C:\\x.png")) {
            assertNull(path, mediaPath(path))
        }
    }
}
