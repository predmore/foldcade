package app.foldcade

import app.foldcade.api.plugin.*
import app.foldcade.host.PluginHost
import app.foldcade.language.*
import app.foldcade.localfolder.LocalFolderEntry
import app.foldcade.plugins.melonds.MelonDsEntry
import app.foldcade.plugins.retroarch.RetroArchEntry
import kotlinx.coroutines.Dispatchers
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in JVM workload: FOLDCADE_MOVE_BENCHMARK=1 ./gradlew :app:testDebugUnitTest --tests '*MoveTimingTest'. */
class MoveTimingTest {
    @Test fun syntheticLibraryMoves() {
        assumeTrue(System.getenv("FOLDCADE_MOVE_BENCHMARK") == "1")
        for (size in listOf(500, 2000)) {
            val host = PluginHost(Dispatchers.Unconfined, MemoryCredentialStore()).apply {
                register(LocalFolderEntry()); register(MelonDsEntry()); register(RetroArchEntry())
            }
            val entries = List(size) { index ->
                val game = Game("local-folder", "$index", "nintendo-ds", Availability.LocalOnly, "Game %04d".format(index))
                GridEntry(homeGameId(game.backendId, game.remoteKey), game.label, "", game.platformId, null, false, game)
            }
            val store = SessionStore(MemoryPrefs())
            store.saveHomeBoard(encodeHome(HomeBoard(slots = listOf(HOME_ALL) + entries.map { it.id }, recentAfter = Long.MAX_VALUE)))
            val shell = ShellController(store, host)
            shell.setInstalledApps(emptyList(), host.playerPackageNames(), host.playerPackageNames())
            shell.ingestLibrary("local-folder", entries, host.platformDefinitions())
            for (view in listOf("Home", "All")) {
                if (view == "All") shell.openHomeAll() else shell.goHome()
                fun move(i: Int) {
                    // Bounce through real tiles, staying off the ends. Include the grid's repeated face/title reads.
                    val direction = if (i % 20 < 10) Meaning.MoveRight else Meaning.MoveLeft
                    shell.onMeaning(direction, HostScreen.Bottom)
                    val first = shell.model.focus.cellIndex / Metrics.rows * Metrics.rows
                    repeat(12) { offset ->
                        val at = (first + offset).coerceAtMost(shell.model.count - 1)
                        repeat(3) { shell.homeFace(at) }
                        shell.tileFromOrder(at)?.title
                    }
                }
                repeat(1000, ::move)
                val samples = List(7) {
                    val started = System.nanoTime()
                    repeat(1000, ::move)
                    (System.nanoTime() - started) / 1_000_000.0 / 1000
                }
                println("MOVE_TIMING size=$size view=$view medianMs=${samples.sorted()[3]} samplesMs=$samples")
            }
        }
    }
}
