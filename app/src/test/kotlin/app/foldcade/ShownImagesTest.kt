package app.foldcade

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShownImagesTest {
    @Test fun theOtherPanelsImageStaysPinned() {
        val images = ShownImages<Any>()
        val art = Any()
        images.add(art)
        images.add(art)
        images.remove(art)
        assertTrue(images.contains(art))
        images.remove(art)
        assertFalse(images.contains(art))
    }

    @Test fun equalBytesAreStillDifferentDecodedImages() {
        val images = ShownImages<String>()
        val a = String(charArrayOf('a'))
        val b = String(charArrayOf('a'))
        images.add(a)
        assertFalse(images.contains(b))
        images.remove(b)
        assertTrue(images.contains(a))
    }

    @Test fun onlyTheFirstPanelTakenTrims() {
        val home = Session()
        val single = home.launch(ExternalApp("game"))
        val both = home.launch(ExternalApp("game", occupiesBothDisplays = true))
        assertTrue(homeIconCacheMayTrim(home, single))
        assertTrue(homeIconCacheMayTrim(home, both))
        assertFalse(homeIconCacheMayTrim(single, single.browse(Panel.Bottom)))
        assertFalse(homeIconCacheMayTrim(single, both))
        assertFalse(homeIconCacheMayTrim(single, home))
        assertFalse(homeIconCacheMayTrim(home, home))
    }
}
