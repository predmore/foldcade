package app.foldcade

import android.content.Intent
import org.junit.Assert.assertEquals
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
    fun aResumeFoundTheGameOnlyWhenItsOwnActivityCameForward() {
        val game = "org.citra.citra_emu.activities.EmulationActivity"
        val menu = "org.citra.citra_emu.ui.main.MainActivity"
        assertEquals(true, resumeFound(listOf(game), game))
        // The task was gone, so the player's front door opened its menu.
        assertEquals(false, resumeFound(listOf(menu), game))
        // The menu, then the game it opened: the game is in front now.
        assertEquals(true, resumeFound(listOf(menu, game), game))
        // Nothing seen yet, or no usage access.
        assertNull(resumeFound(emptyList(), game))
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
