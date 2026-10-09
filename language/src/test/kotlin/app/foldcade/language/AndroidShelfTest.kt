package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidShelfTest {
    private val game = LaunchableApp("com.example.game", "Example", systemGame = true)
    private val settings = LaunchableApp("com.android.settings", "Settings", systemGame = false)
    private val azahar = LaunchableApp("org.azahar_emu.azahar", "Azahar", systemGame = false)
    private val players = setOf(azahar.packageName)

    @Test
    fun systemGamesStartOnGamesAndEverythingElseOnApps() {
        val apps = listOf(game, settings, azahar)
        assertEquals(listOf(game), appsOn(AndroidShelf.Games, apps, AppShelfState(), players))
        assertEquals(listOf(settings), appsOn(AndroidShelf.Apps, apps, AppShelfState(), players))
        assertEquals(listOf(azahar), hiddenApps(apps, AppShelfState(), players))
    }

    @Test
    fun aGameFlagThatIsAlsoAPlayerStaysOnGames() {
        val flagged = azahar.copy(systemGame = true)
        val apps = listOf(flagged)
        assertEquals(listOf(flagged), appsOn(AndroidShelf.Games, apps, AppShelfState(), players))
        assertTrue(appsOn(AndroidShelf.Apps, apps, AppShelfState(), players).isEmpty())
        assertTrue(hiddenApps(apps, AppShelfState(), players).isEmpty())
    }

    @Test
    fun userCanMoveHideAndPin() {
        val apps = listOf(game, settings)
        val moved = AppShelfState().move(settings.packageName, AndroidShelf.Games)
        assertEquals(
            listOf(game, settings),
            appsOn(AndroidShelf.Games, apps, moved, emptySet()),
        )
        assertTrue(appsOn(AndroidShelf.Apps, apps, moved, emptySet()).isEmpty())

        val hidden = moved.hide(game.packageName)
        assertEquals(listOf(settings), appsOn(AndroidShelf.Games, apps, hidden, emptySet()))
        assertEquals(listOf(game), hiddenApps(apps, hidden, emptySet()))

        val pinned = hidden.pin(settings.packageName, favorite = true)
        val later = settings.copy(label = "Zebra")
        val earlier = LaunchableApp("com.example.aaa", "Alpha", systemGame = true)
        val ordered = appsOn(
            AndroidShelf.Games,
            listOf(earlier, later),
            pinned.move(later.packageName, AndroidShelf.Games).pin(later.packageName, true),
            emptySet(),
        )
        assertEquals(later.packageName, ordered.first().packageName)
        assertEquals(earlier.packageName, ordered.last().packageName)
    }

    @Test
    fun showingAPlayerPackagePutsItBackOnApps() {
        val apps = listOf(azahar, settings)
        val shown = AppShelfState().show(azahar.packageName, coveredByPlayer = true)
        assertFalse(hiddenFromAppsByDefault(azahar, shown, players))
        assertEquals(
            listOf(azahar, settings),
            appsOn(AndroidShelf.Apps, apps, shown, players),
        )
        assertTrue(hiddenApps(apps, shown, players).isEmpty())
    }

    @Test
    fun movingAPlayerPackageOntoAppsShowsIt() {
        val moved = AppShelfState().move(azahar.packageName, AndroidShelf.Apps)
        assertEquals(
            listOf(azahar),
            appsOn(AndroidShelf.Apps, listOf(azahar), moved, players),
        )
    }

    @Test
    fun movingBackToGamesLeavesApps() {
        val moved = AppShelfState()
            .move(settings.packageName, AndroidShelf.Games)
            .move(settings.packageName, AndroidShelf.Apps)
        assertEquals(AndroidShelf.Apps, moved.record(settings.packageName).shelf)
        assertEquals(
            listOf(settings),
            appsOn(AndroidShelf.Apps, listOf(settings), moved, emptySet()),
        )
    }
}
