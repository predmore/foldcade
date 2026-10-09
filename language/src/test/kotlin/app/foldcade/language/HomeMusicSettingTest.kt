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
        assertEquals("Music  On", MusicCopy.musicLabel(true))
        assertEquals("Volume  25%", MusicCopy.volumeLabel(0.25f))
    }

    @Test
    fun leftPanelKeepsMusicBesideTheOtherSettings() {
        assertEquals(
            listOf(Row.Library, Row.Theme, Row.Primary, Row.Arrange, Row.Music, Row.MusicVolume, Row.SetAsHome),
            leftRows(homeRoleHeld = false),
        )
        assertEquals(
            listOf(Row.Library, Row.Theme, Row.Primary, Row.Arrange, Row.Music, Row.MusicVolume),
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
        val volume = opened.panel!!.copy(index = 5)
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
}
