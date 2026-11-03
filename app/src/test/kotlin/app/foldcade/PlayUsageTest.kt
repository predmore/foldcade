package app.foldcade

import app.foldcade.host.play.*
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class PlayUsageTest {
    @Test fun aPausedRomKeepsItsOwnMinuteWhileTheEmulatorHasSixHours() {
        var now = 30_000_000L
        val plays = PlaySessions.open(File(Files.createTempDirectory("play-usage").toFile(), "events.log"), PlayClock { now })
        plays.packageOf = { game -> playTimePackage(game) { "me.magnum.melonds" } }
        val rom = "lib:local-folder:tetris.nds"
        val session = plays.start(rom)
        now += 50_000L
        plays.signal(session, PlaySignal.Background)
        plays.applyUsage(true, listOf(UsageSample("me.magnum.melonds", UsageMove.Resumed, 1_000_000L),
            UsageSample("me.magnum.melonds", UsageMove.Paused, 23_140_000L)), now)
        assertEquals(50_000L, plays.shown(rom).activeMillis)
        plays.signal(session, PlaySignal.End)
        assertEquals(50_000L, plays.shown(rom).activeMillis)
        assertNull(playTimePackage("lib:romm:13") { "me.magnum.melonds" })
        assertNull(playTimePackage("moonlight.game") { "com.limelight" })
    }

    @Test fun anAndroidGameAndAnAppStandInKeepTheirPackageUsage() {
        assertEquals("com.example.game", playTimePackage("android:com.example.game") { "wrong" })
        assertNull(playTimePackage("android:") { "wrong" })
        for (id in Shelf.APP_TILES) assertEquals("app", playTimePackage(id) { "app" })
    }
}
