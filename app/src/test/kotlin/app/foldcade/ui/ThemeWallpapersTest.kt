package app.foldcade.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import app.foldcade.language.builtInTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ThemeWallpapersTest {
    @Test fun firstPaintUsesTheChosenThemeAndCompletingTheSameCacheInvalidatesIt() {
        val wallpapers = ThemeWallpapers { null }
        val requested = FoldPaint(builtInTheme().copy(artScale = .77f), wallpapers)
        val shown = preparedPaintState(requested)
        assertSame(requested, shown.value)
        assertFalse(shown.value.wallpapers.prepared)
        var changed = false
        val observer = SnapshotStateObserver { it() }
        observer.start()
        try {
            observer.observeReads(Any(), { changed = true }) { shown.value }
            wallpapers.prepare()
            shown.value = requested
            Snapshot.sendApplyNotifications()
            assertTrue("Publishing the same paint must reveal the completed wallpaper cache", changed)
            assertSame(requested, shown.value)
            assertTrue(shown.value.wallpapers.prepared)
        } finally {
            observer.stop()
        }
    }

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
