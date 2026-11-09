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
    @Test fun weekIncludesUsageFromOtherLaunchersAndMatchesTheHeroWithoutDoubleCounting() {
        var now = 1_000_000L
        val plays = PlaySessions.open(File(Files.createTempDirectory("play-week-usage").toFile(), "events.log"), PlayClock { now })
        val game = "android:com.example.game"
        plays.packageOf = { if (it == game || it == "stand-in") "com.example.game" else null }
        plays.usageGames = { listOf(game, "stand-in") }
        val session = plays.start(game)
        now += 60_000L
        plays.signal(session, PlaySignal.End)
        now += 60_000L
        plays.applyUsage(true, listOf(
            UsageSample("com.example.game", UsageMove.Resumed, 1_000_000L),
            UsageSample("com.example.game", UsageMove.Paused, 1_120_000L),
            UsageSample("com.unknown.system", UsageMove.Resumed, 1_000_000L),
            UsageSample("com.unknown.system", UsageMove.Paused, 1_120_000L),
        ), now)
        val spans = plays.spans()
        assertEquals(setOf(game), spans.map { it.gameId }.toSet())
        assertEquals(120_000L, spans.sumOf { it.endMillis - it.startMillis })
        assertEquals(plays.shown(game).activeMillis, spans.sumOf { it.endMillis - it.startMillis })
        assertEquals(60_000L, spans.filter { it.sessionId == session }.sumOf { it.endMillis - it.startMillis })
        plays.remove(session)
        assertEquals(60_000L, plays.spans().sumOf { it.endMillis - it.startMillis })
        assertEquals(plays.shown(game).activeMillis, plays.spans().sumOf { it.endMillis - it.startMillis })
        plays.applyUsage(false, emptyList(), now)
        assertTrue(plays.spans().isEmpty())
        assertEquals(0L, plays.shown(game).activeMillis)
    }

    @Test fun usageOnlyPlayAppearsInTheWeekWithoutInventingARemovableLaunch() {
        val now = 3_000_000L
        val plays = PlaySessions.open(File(Files.createTempDirectory("play-external-usage").toFile(), "events.log"), PlayClock { now })
        val game = "android:com.example.game"
        plays.packageOf = { if (it == game) "com.example.game" else null }
        plays.usageGames = { listOf(game) }
        plays.applyUsage(true, listOf(
            UsageSample("com.example.game", UsageMove.Resumed, now - 120_000L),
            UsageSample("com.example.game", UsageMove.Paused, now),
        ), now)
        assertEquals(120_000L, plays.spans().sumOf { it.endMillis - it.startMillis })
        assertEquals(plays.shown(game).activeMillis, plays.spans().sumOf { it.endMillis - it.startMillis })
        assertTrue(plays.spans().all { it.sessionId.isEmpty() })
        val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val sessions = app.foldcade.language.PlayHistory(plays.spans(), java.time.ZoneOffset.UTC).daySessions(today)
        assertTrue("Outside-only play has no removable day rows", sessions.isEmpty())
        val model = app.foldcade.language.PickerModel(count = 1, rowsPerPage = 1).copy(playActivity =
            app.foldcade.language.PlayActivityPage(app.foldcade.language.HostScreen.Top))
        val hints = app.foldcade.language.playActivityHints(model, hasSessions = sessions.isNotEmpty())
        assertFalse(hints.any { it.label == app.foldcade.language.PlayCopy.sessions })
        assertFalse(plays.hasTrackedSession())
        assertTrue("Y › Play time must be available for usage-only games", plays.hasPlay())
    }

    @Test fun aRomKeepsItsOwnLaunchTimeBesideTheWholeEmulatorsUsage() {
        var now = 2_000_000L
        val plays = PlaySessions.open(File(Files.createTempDirectory("play-rom-week").toFile(), "events.log"), PlayClock { now })
        plays.packageOf = { playTimePackage(it) { null } }
        plays.usageGames = { listOf("android:me.magnum.melonds") }
        val session = plays.start("lib:local-folder:tetris.nds")
        now += 30_000L
        plays.signal(session, PlaySignal.End)
        plays.applyUsage(true, listOf(UsageSample("me.magnum.melonds", UsageMove.Resumed, 1_000_000L)), now)
        assertEquals(30_000L, plays.spans().filter { it.gameId.startsWith("lib:") }.sumOf { it.endMillis - it.startMillis })
        assertEquals(1_030_000L, plays.spans().filter { it.gameId.startsWith("android:") }.sumOf { it.endMillis - it.startMillis })
        plays.applyUsage(false, emptyList(), now)
        assertEquals(30_000L, plays.spans().sumOf { it.endMillis - it.startMillis })
    }

    @Test fun usageReplacesOnlyItsWindowAndKeepsOlderLaunchHistory() {
        var now = 1_000_000L
        val plays = PlaySessions.open(File(Files.createTempDirectory("play-older-usage").toFile(), "events.log"), PlayClock { now })
        val game = "android:com.example.game"
        plays.packageOf = { if (it == game) "com.example.game" else null }
        plays.usageGames = { listOf(game) }
        val session = plays.start(game)
        now += 120_000L
        plays.signal(session, PlaySignal.End)
        plays.applyUsage(true, listOf(
            UsageSample("com.example.game", UsageMove.Resumed, now - 60_000L),
            UsageSample("com.example.game", UsageMove.Paused, now),
        ), now, fromMillis = now - 60_000L)
        assertEquals(120_000L, plays.shown(game).activeMillis)
        assertEquals(120_000L, plays.spans().sumOf { it.endMillis - it.startMillis })
        assertEquals(60_000L, plays.spans().filter { it.endMillis <= now - 60_000L }.sumOf { it.endMillis - it.startMillis })
    }

    @Test fun backgroundLaunchersAndEmulatorsDoNotBecomePlayFromUsageAlone() {
        val shell = ShellController(SessionStore(MemoryPrefs()), app.foldcade.host.PluginHost(
            kotlinx.coroutines.Dispatchers.Unconfined, app.foldcade.api.plugin.MemoryCredentialStore()))
        shell.setInstalledApps(listOf(
            app.foldcade.language.LaunchableApp("com.example.game", "Game", true),
            app.foldcade.language.LaunchableApp("com.example.launcher", "Launcher", false),
            app.foldcade.language.LaunchableApp("com.example.emulator", "Emulator", true),
        ), setOf("com.example.emulator"))
        assertEquals(listOf("android:com.example.game"), shell.usageGameIds())
    }

    @Test fun gamePageTotalAndWeekIncludeUsageWhileNonGamePagesKeepLaunchTime() {
        var now = 10_000_000L
        val plays = PlaySessions.open(File(Files.createTempDirectory("play-page-total").toFile(), "events.log"), PlayClock { now })
        val game = "android:com.example.game"
        val utility = "android:com.example.app"
        plays.packageOf = { playTimePackage(it) { null } }
        plays.usageGames = { listOf(game) }
        val session = plays.start(utility)
        now += 30_000L
        plays.signal(session, PlaySignal.End)
        plays.applyUsage(true, listOf(
            UsageSample("com.example.game", UsageMove.Resumed, now - 120_000L),
            UsageSample("com.example.game", UsageMove.Paused, now),
            UsageSample("com.example.app", UsageMove.Resumed, now - 600_000L),
            UsageSample("com.example.app", UsageMove.Paused, now),
        ), now)
        val history = app.foldcade.language.PlayHistory(plays.spans(), java.time.ZoneOffset.UTC)
        val day = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val week = history.week(day.minusDays(app.foldcade.language.PLAY_WEEK_DAYS - 1L), app.foldcade.language.DateStyle())
        assertEquals(120_000L, plays.shown(game).activeMillis)
        assertEquals(plays.shown(game).activeMillis, week.top.single { it.first == game }.second)
        assertEquals(30_000L, plays.shown(utility).activeMillis)
        assertEquals(plays.shown(utility).activeMillis, week.top.single { it.first == utility }.second)
    }

}
