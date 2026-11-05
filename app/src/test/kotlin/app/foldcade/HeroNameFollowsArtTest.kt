package app.foldcade

import app.foldcade.language.HeroFolder
import app.foldcade.language.HeroSubject
import app.foldcade.ui.bloomCropBias
import app.foldcade.ui.namedSubject
import app.foldcade.ui.titleCollapse
import app.foldcade.ui.richestShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The name card names the art on screen (review-B S5′), and a tile's square keeps the busy band (60-F2). */
class HeroNameFollowsArtTest {
    private fun folder(key: String, count: Int = 1) = HeroSubject.Folder(HeroFolder(key = key, name = key, count = count))

    @Test fun theNameWaitsForTheArtThenGoesAheadOfSlowArt() {
        val old = folder("old")
        val new = folder("new")
        assertEquals(old, namedSubject(focus = new, shown = old, waited = false))
        assertEquals(new, namedSubject(focus = new, shown = old, waited = true))
        assertEquals(new, namedSubject(focus = new, shown = null, waited = false))
        assertNull(namedSubject(focus = null, shown = old, waited = false))
        // The same subject shows the focus's fresher copy at once.
        val fresher = folder("old", count = 2)
        assertEquals(fresher, namedSubject(focus = fresher, shown = old, waited = false))
    }

    @Test fun theSquareGoesWhereTheBoxIsBusiest() {
        // A title band at the bottom of a tall box, a plain sky at its top.
        val bottomTitle = IntArray(72) { if (it >= 60) 900 else 100 }
        assertEquals(1f, richestShare(bottomTitle, window = 48), 0f)
        val topTitle = IntArray(72) { if (it < 10) 900 else 100 }
        assertEquals(0f, richestShare(topTitle, window = 48), 0f)
        // Even detail keeps the middle, and a square has nowhere to go.
        assertEquals(0.5f, richestShare(IntArray(72) { 100 }, window = 48), 0f)
        assertEquals(0.5f, richestShare(IntArray(48) { it }, window = 48), 0f)
    }

    @Test fun aCollapsingTitleIsGoneBeforeItsLineClosesOnIt() {
        for (step in 0..100) {
            val (room, alpha) = titleCollapse(step / 100f)
            if (room < 1f) assertEquals(0f, alpha, 0f)
        }
        assertEquals(1f to 1f, titleCollapse(1f))
        assertEquals(0f to 0f, titleCollapse(0f))
    }

    @Test fun aBloomFromATileKeepsTheBandItsTileKept() {
        // The tile kept the bottom of its box, its title, so every frame does, square stand-in or sharp cover.
        assertEquals(1f, bloomCropBias(wide = false, fromTile = true, tileShare = 1f, grow = 0f), 0f)
        assertEquals(1f, bloomCropBias(wide = false, fromTile = true, tileShare = 1f, grow = 1f), 0f)
        // No tile cut, or a hero's whole cover: centred first, then up toward the title most boxes carry.
        assertEquals(0.5f, bloomCropBias(wide = false, fromTile = true, tileShare = null, grow = 0f), 0f)
        assertEquals(0.5f, bloomCropBias(wide = false, fromTile = false, tileShare = 1f, grow = 0f), 0f)
        assertEquals(true, bloomCropBias(wide = false, fromTile = false, tileShare = 1f, grow = 1f) < 0.5f)
        assertEquals(0.5f, bloomCropBias(wide = true, fromTile = true, tileShare = 0f, grow = 1f), 0f)
    }
}
