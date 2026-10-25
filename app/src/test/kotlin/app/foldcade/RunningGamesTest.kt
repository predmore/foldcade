package app.foldcade

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningGamesTest {
    @Test
    fun aPlayerHoldsOneGameSoANewStartThereReplacesTheLast() {
        val games = RunningGames()
        val seen = mutableListOf<Set<String>>()
        games.onChange = { seen += it }
        games.note(game("mario", "azahar"))
        games.note(game("zelda", "melonds"))
        games.note(game("toad", "azahar"))
        assertNull(games.forTile("mario"))
        assertEquals("toad", games.forPackage("azahar")?.tileId)
        assertEquals(setOf("zelda", "toad"), seen.last())
        games.forget("melonds")
        assertEquals(setOf("toad"), seen.last())
    }

    @Test
    fun aGameFoldedIntoASharedTileIsStillFoundThere() {
        val games = RunningGames()
        games.note(game("lib:folder:mario", "azahar"))
        val folded = mapOf("lib:folder:mario" to "same:mario", "lib:romm:12" to "same:mario")
        val tileOf = { id: String -> folded[id] ?: id }
        assertEquals("lib:folder:mario", games.forTile("same:mario", tileOf)?.tileId)
        assertEquals("lib:folder:mario", games.forTile("lib:romm:12", tileOf)?.tileId)
        assertNull(games.forTile("same:zelda", tileOf))
    }

    @Test
    fun aResumeFoundTheGameUnlessThePlayersMenuCameForward() {
        val game = "org.citra.citra_emu.activities.EmulationActivity"
        val menu = "org.citra.citra_emu.ui.main.MainActivity"
        assertEquals(true, resumeFound(listOf(game), menu, game))
        // The task was gone, so the player's front door opened its menu.
        assertEquals(false, resumeFound(listOf(menu), menu, game))
        // The menu, then the game it opened: the game is in front now.
        assertEquals(true, resumeFound(listOf(menu, game), menu, game))
        // A player whose start hands the game to another activity, as Dolphin's does.
        assertEquals(true, resumeFound(listOf("org.dolphinemu.dolphinemu.activities.EmulationActivity"),
            "org.dolphinemu.dolphinemu.ui.main.MainActivity", "org.dolphinemu.dolphinemu.ui.main.TvMainActivity"))
        // aPS3e: its front door hands on to its menu at once, and the game ran in EmulatorActivity.
        val quickStart = "aenu.aps3e.QuickStartActivity"
        assertEquals(false, resumeFound(listOf(quickStart, "aenu.aps3e.MainActivity"), quickStart, "aenu.aps3e.EmulatorActivity"))
        assertEquals(true, resumeFound(listOf("aenu.aps3e.EmulatorActivity"), quickStart, "aenu.aps3e.EmulatorActivity"))
        // A front door that is also where the game started can't tell the menu from the game.
        assertEquals(true, resumeFound(listOf(menu), menu, menu))
        // Nothing seen yet, or no usage access.
        assertNull(resumeFound(emptyList(), menu, game))
    }

    @Test
    fun aResumeBringsThePlayersTaskBackAndHandsItNothing() {
        // No second task, nothing cleared, and no single-top delivery of a game to load.
        assertEquals(0, RESUME_FLAGS and (Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        assertTrue(RESUME_FLAGS and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED != 0)
        assertTrue(RESUME_FLAGS and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun aReplacementClearsThePlayersTaskInsteadOfOpeningASecond() {
        val flags = replaceFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        assertEquals(0, flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        assertTrue(flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun aPlayerAnEarlierProcessLeftAGameInStillHasItReplaced() {
        val games = RunningGames()
        val kept = mutableListOf<Set<String>>()
        games.onPlayersChange = { kept += it }
        games.restore(setOf("azahar"))
        // Nothing is known about that game, so no tile reads Paused, but a start there replaces it.
        assertTrue(games.mayHold("azahar"))
        assertEquals(emptySet<String>(), games.tiles())
        games.note(game("zelda", "melonds"))
        assertEquals(setOf("azahar", "melonds"), kept.last())
        games.note(game("mario", "azahar"))
        assertEquals("mario", games.forPackage("azahar")?.tileId)
        games.forget("azahar")
        assertFalse(games.mayHold("azahar"))
        assertEquals(setOf("melonds"), kept.last())
        assertFalse(games.mayHold(null))
    }

    @Test
    fun aPlayerWhoseFrontDoorRunsItsGamesOnlyHoldsOne() {
        val games = RunningGames()
        games.note(game("mario", "ppsspp"))
        games.noteHolding("ppsspp")
        assertNull(games.forTile("mario"))
        assertTrue(games.mayHold("ppsspp"))
        // Its launcher activity would stack a second copy over the game.
        assertFalse(resumable("org.ppsspp.ppsspp.PpssppActivity", "org.ppsspp.ppsspp.PpssppActivity"))
        assertTrue(resumable("org.citra.citra_emu.ui.main.MainActivity", "org.citra.citra_emu.activities.EmulationActivity"))
        assertFalse(resumable(null, "org.citra.citra_emu.activities.EmulationActivity"))
    }

    private fun game(tile: String, pkg: String, startedAt: Long = 0L) = RunningGame(
        tileId = tile,
        title = tile,
        platformId = "nintendo-3ds",
        packageName = pkg,
        activity = "org.citra.citra_emu.activities.EmulationActivity",
        panel = Panel.Top,
        occupiesBoth = true,
        startedAtMillis = startedAt,
        saves = null,
    )
}
