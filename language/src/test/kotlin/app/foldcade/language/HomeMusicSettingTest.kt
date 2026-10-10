package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMusicSettingTest {
    @Test
    fun defaultIsOnAtHalfTheSlider() {
        val music = HomeMusicSetting()
        assertTrue(music.enabled)
        assertEquals(0.5f, music.volume, 0.0001f)
        assertEquals(HomeMusicSetting.DEFAULT_TRACK_ID, music.trackId)
        assertEquals("Music  On", MusicCopy.musicLabel(true))
        assertEquals("Track  Lanternlight", MusicCopy.trackLabel("Lanternlight"))
        assertEquals("Volume  50%", MusicCopy.volumeLabel(0.5f))
        assertEquals("Volume  25%", MusicCopy.volumeLabel(0.25f))
    }

    @Test
    fun leftPanelKeepsMusicBesideTheOtherSettings() {
        assertEquals(
            listOf(
                Row.Library, Row.Theme, Row.Primary, Row.Arrange, Row.Order,
                Row.Music, Row.MusicTrack, Row.MusicVolume, Row.SetAsHome,
                Row.Background, Row.MotionSpeed, Row.UsageAccess, Row.MoonlightSource,
                Row.AndroidGames, Row.Apps, Row.HiddenApps,
                Row.AndroidSettings, Row.DefaultHomeApp,
            ),
            leftRows(homeRoleHeld = false),
        )
        assertEquals(
            listOf(
                Row.Library, Row.Theme, Row.Primary, Row.Arrange, Row.Order,
                Row.Music, Row.MusicTrack, Row.MusicVolume,
                Row.Background, Row.MotionSpeed, Row.UsageAccess, Row.MoonlightSource,
                Row.AndroidGames, Row.Apps, Row.HiddenApps,
                Row.AndroidSettings, Row.DefaultHomeApp,
            ),
            leftRows(homeRoleHeld = true),
        )
    }

    @Test
    fun activateTogglesMusicAndStepsVolume() {
        val opened = reduce(PickerModel(count = 1, rowsPerPage = 1, showLaunchTarget = false), Meaning.LeftPanel).first
        val music = opened.panel!!.copy(index = 5)
        val toggled = reduce(opened.copy(panel = music), Meaning.Activate).first
        assertFalse(toggled.music.enabled)
        assertEquals(5, toggled.panel?.index)
        val again = reduce(toggled, Meaning.Activate).first
        assertTrue(again.music.enabled)
        val named = opened.panel!!.copy(index = 6)
        val stayed = reduce(opened.copy(panel = named, trackTitle = "Lanternlight"), Meaning.Activate).first
        assertEquals(HomeMusicSetting.DEFAULT_TRACK_ID, stayed.music.trackId)
        assertEquals("Track  Lanternlight", MusicCopy.trackLabel(stayed.trackTitle))
        val volume = opened.panel!!.copy(index = 7)
        val atQuarter = opened.copy(panel = volume, music = HomeMusicSetting(volume = 0.25f))
        val stepped = reduce(atQuarter, Meaning.Activate).first
        assertEquals(0.30f, stepped.music.volume, 0.0001f)
        val wrapped = HomeMusicSetting(volume = 1f).stepped()
        assertEquals(0f, wrapped.volume, 0.0001f)
        assertEquals(0f, HomeMusicSetting().withVolume(-1f).volume, 0.0001f)
        assertEquals(1f, HomeMusicSetting().withVolume(2f).volume, 0.0001f)
    }

    @Test
    fun dpadStepsVolumeByFiveAndHoldsTheRow() {
        val opened = reduce(PickerModel(count = 1, rowsPerPage = 1, showLaunchTarget = false), Meaning.LeftPanel).first
        val volume = opened.panel!!.copy(index = 7)
        val atHalf = opened.copy(panel = volume, music = HomeMusicSetting(volume = 0.5f))
        val raised = reduce(atHalf, Meaning.MoveRight).first
        assertEquals(0.55f, raised.music.volume, 0.0001f)
        assertEquals(7, raised.panel?.index)
        val lowered = reduce(atHalf, Meaning.MoveLeft).first
        assertEquals(0.45f, lowered.music.volume, 0.0001f)
        assertEquals(7, lowered.panel?.index)
        val held = reduce(reduce(atHalf, Meaning.MoveRight).first, Meaning.MoveRight).first
        assertEquals(0.60f, held.music.volume, 0.0001f)
        val full = reduce(opened.copy(panel = volume, music = HomeMusicSetting(volume = 1f)), Meaning.MoveRight).first
        assertEquals(1f, full.music.volume, 0.0001f)
        val silent = reduce(opened.copy(panel = volume, music = HomeMusicSetting(volume = 0f)), Meaning.MoveLeft).first
        assertEquals(0f, silent.music.volume, 0.0001f)
        val theme = opened.panel!!.copy(index = 1)
        val ignored = reduce(opened.copy(panel = theme, music = HomeMusicSetting(volume = 0.5f)), Meaning.MoveRight).first
        assertEquals(0.5f, ignored.music.volume, 0.0001f)
        assertEquals(1, ignored.panel?.index)
    }

    @Test
    fun muteAndUiSoundsLowerTheLoop() {
        val setting = HomeMusicSetting(volume = 0.25f)
        assertEquals(0.5f, playbackLevel(setting, uiSoundActive = false, mediaMuted = false), 0.0001f)
        assertEquals(0.2f, playbackLevel(setting, uiSoundActive = true, mediaMuted = false), 0.0001f)
        assertEquals(0f, playbackLevel(setting, uiSoundActive = false, mediaMuted = true), 0.0001f)
        assertEquals(0f, playbackLevel(setting.copy(enabled = false), uiSoundActive = true, mediaMuted = false), 0.0001f)
    }

    @Test
    fun fullScaleIsUnityAndAQuarterIsNotSilent() {
        val full = HomeMusicSetting(volume = 1f)
        assertEquals(1f, playbackLevel(full, uiSoundActive = false, mediaMuted = false), 0.0001f)
        assertEquals(0.4f, playbackLevel(full, uiSoundActive = true, mediaMuted = false), 0.0001f)
        val quarter = playbackLevel(HomeMusicSetting(volume = 0.25f), uiSoundActive = false, mediaMuted = false)
        assertEquals(0.5f, quarter, 0.0001f)
        assertTrue(quarter >= 0.4f)
        val half = playbackLevel(HomeMusicSetting(), uiSoundActive = false, mediaMuted = false)
        assertEquals(0.7071f, half, 0.001f)
    }

    @Test
    fun aLateDuckCannotTurnTheLevelBackDown() {
        val latch = DuckLatch()
        val first = latch.begin()
        assertTrue(latch.active)
        val second = latch.begin()
        assertFalse(latch.end(first))
        assertTrue(latch.active)
        assertTrue(latch.end(second))
        assertFalse(latch.active)
        val stuck = latch.begin()
        latch.clear()
        assertFalse(latch.active)
        assertFalse(latch.end(stuck))
        val full = HomeMusicSetting(volume = 1f)
        assertEquals(1f, playbackLevel(full, uiSoundActive = latch.active, mediaMuted = false), 0.0001f)
    }

    @Test
    fun themeBackgroundMusicIsOptional() {
        assertEquals(DEFAULT_BACKGROUND_MUSIC, backgroundMusicFromThemeJson(null))
        assertEquals(DEFAULT_BACKGROUND_MUSIC, backgroundMusicFromThemeJson("""{"name":"Example"}"""))
        assertEquals(DEFAULT_BACKGROUND_MUSIC, backgroundMusicFromThemeJson("""{"backgroundMusic":""}"""))
        assertEquals(DEFAULT_BACKGROUND_MUSIC, backgroundMusicFromThemeJson("""{"backgroundMusic":"../secret.ogg"}"""))
        assertEquals("music/loop.ogg", backgroundMusicFromThemeJson("""{"backgroundMusic":"music/loop.ogg"}"""))
    }

    @Test
    fun manifestSelectsLanternlightById() {
        val json = """
            {"tracks":[{"id":"lanternlight","title":"Lanternlight","composer":"Foldcade project","license":"GPLv3","file":"music/lanternlight.ogg"}]}
        """.trimIndent()
        val tracks = musicTracksFromManifest(json)
        assertEquals(1, tracks.size)
        assertEquals("Lanternlight", musicTrack(tracks, "lanternlight")?.title)
        assertEquals("music/lanternlight.ogg", musicTrack(tracks, "missing")?.file)
        assertEquals("Foldcade project", tracks.single().composer)
        assertTrue(musicTracksFromManifest("""{"tracks":[{"id":"x","title":"X","composer":"C","license":"GPLv3","file":"../no.ogg"}]}""").isEmpty())
    }

    @Test
    fun musicStartsOnlyWhenHomeHoldsEveryScreen() {
        assertTrue(homeMusicMayStart(homeOnEveryScreen = true, gameInFront = false))
        assertFalse(homeMusicMayStart(homeOnEveryScreen = false, gameInFront = false))
        assertFalse(homeMusicMayStart(homeOnEveryScreen = true, gameInFront = true))
    }

    @Test
    fun homeMustBeResumedOnEveryLitScreen() {
        val both = setOf("top", "bottom")
        assertTrue(homeOnEveryScreen(resumed = both, lit = both))
        // An app open on one screen pauses the home there.
        assertFalse(homeOnEveryScreen(resumed = setOf("bottom"), lit = both))
        assertFalse(homeOnEveryScreen(resumed = setOf("top"), lit = both))
        // A screen that is off does not count against the other.
        assertTrue(homeOnEveryScreen(resumed = setOf("top"), lit = setOf("top")))
        // Asleep: nothing is lit and nothing is resumed.
        assertFalse(homeOnEveryScreen(resumed = emptySet<String>(), lit = emptySet()))
    }

    @Test
    fun focusReturnFadesInFromSilenceUnlessAGameIsInFront() {
        assertTrue(
            fadeInOnFocusReturn(
                playbackResumed = true,
                fromAudioFocus = true,
                homeInFront = true,
                gameInFront = false,
            ),
        )
        assertFalse(
            fadeInOnFocusReturn(
                playbackResumed = false,
                fromAudioFocus = true,
                homeInFront = true,
                gameInFront = false,
            ),
        )
        assertFalse(
            fadeInOnFocusReturn(
                playbackResumed = true,
                fromAudioFocus = false,
                homeInFront = true,
                gameInFront = false,
            ),
        )
        assertFalse(
            fadeInOnFocusReturn(
                playbackResumed = true,
                fromAudioFocus = true,
                homeInFront = true,
                gameInFront = true,
            ),
        )
        assertFalse(
            fadeInOnFocusReturn(
                playbackResumed = true,
                fromAudioFocus = true,
                homeInFront = false,
                gameInFront = false,
            ),
        )
    }

    @Test
    fun missingSelectedAssetFallsBackToLanternlight() {
        val present = setOf("music/lanternlight.ogg", "music/theme.ogg")
        val exists: (String) -> Boolean = { it in present }
        assertEquals("music/theme.ogg", packagedHomeMusicFile("music/theme.ogg", exists))
        assertEquals(DEFAULT_BACKGROUND_MUSIC, packagedHomeMusicFile("music/other.ogg", exists))
        assertEquals(
            DEFAULT_BACKGROUND_MUSIC,
            packagedHomeMusicFile("music/lanternlight.ogg", assetExists = { false }),
        )
    }

    @Test
    fun trackPickerCyclesHomeTracksAndDropsAThemeTrackWhenThatThemeLeaves() {
        val lantern = defaultLanternlightTrack()
        val dusk = MusicTrack("dusk", "Dusk", "Foldcade project", "GPLv3", "music/dusk.ogg")
        val theme = MusicTrack(
            THEME_TRACK_ID,
            "Afterglow",
            "Afterglow",
            "theme",
            "music/afterglow.ogg",
            fromTheme = true,
        )
        val opened = reduce(PickerModel(count = 1, rowsPerPage = 1, showLaunchTarget = false), Meaning.LeftPanel).first
        val model = opened.copy(
            homeTracks = listOf(lantern, dusk),
            themeTracks = listOf(null, theme),
            themes = listOf("Built-in", "Afterglow"),
            themeIndex = 1,
            trackTitle = lantern.title,
        )
        val onTrack = model.copy(panel = model.panel!!.copy(index = 6))
        val duskPick = reduce(onTrack, Meaning.Activate).first
        assertEquals("dusk", duskPick.music.trackId)
        assertEquals("Dusk", duskPick.trackTitle)
        assertEquals("Track  Dusk", MusicCopy.trackLabel(duskPick.trackTitle))
        assertEquals(6, duskPick.panel?.index)
        val themePick = reduce(duskPick, Meaning.Activate).first
        assertEquals(THEME_TRACK_ID, themePick.music.trackId)
        assertEquals("Afterglow", themePick.trackTitle)
        val wrapped = reduce(themePick, Meaning.Activate).first
        assertEquals(HomeMusicSetting.DEFAULT_TRACK_ID, wrapped.music.trackId)
        val back = reduce(wrapped, Meaning.MoveLeft).first
        assertEquals(THEME_TRACK_ID, back.music.trackId)
        assertEquals(6, back.panel?.index)
        val forward = reduce(back, Meaning.MoveRight).first
        assertEquals(HomeMusicSetting.DEFAULT_TRACK_ID, forward.music.trackId)

        val onThemeRow = back.copy(panel = back.panel!!.copy(index = 1))
        val builtIn = reduce(onThemeRow, Meaning.Activate).first
        assertEquals(0, builtIn.themeIndex)
        assertEquals(HomeMusicSetting.DEFAULT_TRACK_ID, builtIn.music.trackId)
        assertEquals("Lanternlight", builtIn.trackTitle)
        assertTrue(offeredMusicTracks(builtIn.homeTracks, builtIn.themeTracks.getOrNull(0)).none { it.fromTheme })

        val stayed = reduce(duskPick.copy(panel = duskPick.panel!!.copy(index = 1)), Meaning.Activate).first
        assertEquals(0, stayed.themeIndex)
        assertEquals("dusk", stayed.music.trackId)
    }

    @Test
    fun themeTrackRequiresAFileInsideTheZip() {
        val named = """{"name":"Moss","backgroundMusic":"music/moss.ogg"}"""
        assertEquals(null, themeMusicTrack("Moss", named, emptySet()))
        val offered = themeMusicTrack("Moss", named, setOf("music/moss.ogg"))
        assertEquals(THEME_TRACK_ID, offered?.id)
        assertEquals("Moss", offered?.title)
        assertEquals(true, offered?.fromTheme)
        assertEquals("music/moss.ogg", offered?.file)
        assertEquals(null, themeMusicTrack("Moss", """{"name":"Moss"}""", setOf("music/moss.ogg")))
        assertEquals(null, themeMusicTrack("Moss", """{"name":"Moss","backgroundMusic":"../x.ogg"}""", setOf("../x.ogg")))
        val tracks = offeredMusicTracks(listOf(defaultLanternlightTrack()), offered)
        assertEquals(listOf(HomeMusicSetting.DEFAULT_TRACK_ID, THEME_TRACK_ID), tracks.map { it.id })
        assertEquals(HomeMusicSetting.DEFAULT_TRACK_ID, cycledMusicTrack(listOf(defaultLanternlightTrack()), "lanternlight", 1).id)
    }
}
