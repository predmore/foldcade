package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMusicSettingTest {
    @Test
    fun defaultIsOnAtAQuarterOfMediaVolume() {
        val music = HomeMusicSetting()
        assertTrue(music.enabled)
        assertEquals(0.25f, music.volume, 0.0001f)
        assertEquals(HomeMusicSetting.DEFAULT_TRACK_ID, music.trackId)
        assertEquals("Music  On", MusicCopy.musicLabel(true))
        assertEquals("Track  Lanternlight", MusicCopy.trackLabel("Lanternlight"))
        assertEquals("Volume  25%", MusicCopy.volumeLabel(0.25f))
    }

    @Test
    fun leftPanelKeepsMusicBesideTheOtherSettings() {
        assertEquals(
            listOf(
                Row.Library, Row.Theme, Row.Primary, Row.Arrange,
                Row.Music, Row.MusicTrack, Row.MusicVolume, Row.SetAsHome,
                Row.Background, Row.MotionSpeed,
            ),
            leftRows(homeRoleHeld = false),
        )
        assertEquals(
            listOf(
                Row.Library, Row.Theme, Row.Primary, Row.Arrange,
                Row.Music, Row.MusicTrack, Row.MusicVolume,
                Row.Background, Row.MotionSpeed,
            ),
            leftRows(homeRoleHeld = true),
        )
    }

    @Test
    fun activateTogglesMusicAndStepsVolume() {
        val opened = reduce(PickerModel(count = 1, rowsPerPage = 1, showLaunchTarget = false), Meaning.LeftPanel).first
        val music = opened.panel!!.copy(index = 4)
        val toggled = reduce(opened.copy(panel = music), Meaning.Activate).first
        assertFalse(toggled.music.enabled)
        assertEquals(4, toggled.panel?.index)
        val again = reduce(toggled, Meaning.Activate).first
        assertTrue(again.music.enabled)
        val named = opened.panel!!.copy(index = 5)
        val stayed = reduce(opened.copy(panel = named, trackTitle = "Lanternlight"), Meaning.Activate).first
        assertEquals(HomeMusicSetting.DEFAULT_TRACK_ID, stayed.music.trackId)
        assertEquals("Track  Lanternlight", MusicCopy.trackLabel(stayed.trackTitle))
        val volume = opened.panel!!.copy(index = 6)
        val stepped = reduce(opened.copy(panel = volume), Meaning.Activate).first
        assertEquals(0.30f, stepped.music.volume, 0.0001f)
        val wrapped = HomeMusicSetting(volume = 1f).stepped()
        assertEquals(0f, wrapped.volume, 0.0001f)
        assertEquals(0f, HomeMusicSetting().withVolume(-1f).volume, 0.0001f)
        assertEquals(1f, HomeMusicSetting().withVolume(2f).volume, 0.0001f)
    }

    @Test
    fun muteAndUiSoundsLowerTheLoop() {
        val setting = HomeMusicSetting()
        assertEquals(0.25f, playbackLevel(setting, uiSoundActive = false, mediaMuted = false), 0.0001f)
        assertEquals(0.10f, playbackLevel(setting, uiSoundActive = true, mediaMuted = false), 0.0001f)
        assertEquals(0f, playbackLevel(setting, uiSoundActive = false, mediaMuted = true), 0.0001f)
        assertEquals(0f, playbackLevel(setting.copy(enabled = false), uiSoundActive = true, mediaMuted = false), 0.0001f)
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
    fun musicStartsOnlyWhenHomeIsAloneAndQuiet() {
        assertTrue(homeMusicMayStart(homeInFront = true, gameInFront = false, otherAudioActive = false))
        assertFalse(homeMusicMayStart(homeInFront = false, gameInFront = false, otherAudioActive = false))
        assertFalse(homeMusicMayStart(homeInFront = true, gameInFront = true, otherAudioActive = false))
        assertFalse(homeMusicMayStart(homeInFront = true, gameInFront = false, otherAudioActive = true))
    }

    @Test
    fun suppressionClearsOnlyAfterTheGameLeaves() {
        assertTrue(homeMusicStaysSuppressed(suppressed = true, gameInFront = true))
        assertFalse(homeMusicStaysSuppressed(suppressed = true, gameInFront = false))
        assertFalse(homeMusicStaysSuppressed(suppressed = false, gameInFront = true))
        assertFalse(homeMusicStaysSuppressed(suppressed = false, gameInFront = false))
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
        assertEquals(
            "music/theme.ogg",
            packagedHomeMusicFile("""{"backgroundMusic":"music/theme.ogg"}""", "music/other.ogg", exists),
        )
        assertEquals(
            "music/lanternlight.ogg",
            packagedHomeMusicFile(null, "music/other.ogg", exists),
        )
        assertEquals(
            DEFAULT_BACKGROUND_MUSIC,
            packagedHomeMusicFile(null, "music/lanternlight.ogg", assetExists = { false }),
        )
    }
}
