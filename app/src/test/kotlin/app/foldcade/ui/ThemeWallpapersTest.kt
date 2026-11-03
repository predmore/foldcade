package app.foldcade.ui

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ThemeWallpapersTest {
    @Test fun ReadingPaintNeverDecodesAndPreparationRemembersMissingImages() {
        val decoded = mutableListOf<Boolean>()
        val wallpapers = ThemeWallpapers { decoded += it; null }
        assertNull(wallpapers.top)
        assertNull(wallpapers.bottom)
        assertFalse(wallpapers.prepared)
        assertTrue(decoded.isEmpty())
        wallpapers.prepare()
        wallpapers.prepare()
        assertEquals(listOf(true, false), decoded)
        assertTrue(wallpapers.prepared)
    }

    @Test fun BothPanelsSharePreparationAndPublishOnlyAfterBothDecodes() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val decoded = mutableListOf<Boolean>()
        val wallpapers = ThemeWallpapers {
            decoded += it
            if (it) {
                entered.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
            }
            null
        }
        val first = Thread { wallpapers.prepare() }.apply { start() }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        val second = Thread { wallpapers.prepare() }.apply { start() }
        assertFalse(wallpapers.prepared)
        finish.countDown()
        first.join(5_000)
        second.join(5_000)
        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
        assertEquals(listOf(true, false), decoded)
        assertTrue(wallpapers.prepared)
    }
}
