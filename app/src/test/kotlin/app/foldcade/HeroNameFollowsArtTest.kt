package app.foldcade

import app.foldcade.language.HeroFolder
import app.foldcade.language.HeroSubject
import app.foldcade.ui.TileCut
import app.foldcade.ui.bloomCropBias
import app.foldcade.ui.namedSubject
import app.foldcade.ui.titleCollapse
import app.foldcade.ui.richestShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlin.math.roundToInt
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

    @Test fun aBloomFromATileKeepsTheTitleItsTileShowedAsItWidens() {
        // A 2:3 box read 48 wide is 72 rows; a 16:9 frame shows 27 of them.
        val wide = TileCut(72, 0).rowsFor(9f / 16f)
        assertEquals(27f, wide, 0f)
        fun shown(cut: TileCut): IntRange {
            val top = (cut.band(wide).first * (72 - wide)).roundToInt()
            return top until top + wide.roundToInt()
        }
        // Mario Kart DS: the tile is centred (rows 12-60) and the logo is at its top, rows 12-20.
        val kart = TileCut(72, 12)
        assertTrue((12..20).all { it in shown(kart) })
        // Bravely Second: the tile leans low (rows 17-65) for a title at rows 55-65.
        val bravely = TileCut(72, 17)
        assertTrue((55..64).all { it in shown(bravely) })
        // The tile's own square shows the same rows of itself.
        val (box, own) = bravely.band(wide)
        assertEquals(box * (72 - wide) - 17, own * (48 - wide), 0.001f)
        // The kept edge stays put as the frame widens, so the title never judders.
        for (step in 0..40) {
            val rows = 48f - step * 0.5f
            assertEquals(12f, kart.band(rows).first * (72 - rows), 0.001f)
            assertEquals(65f, bravely.band(rows).first * (72 - rows) + rows, 0.001f)
        }
        // At the tile's own shape the crop is the tile exactly.
        assertEquals(12f / 24f, bloomCropBias(false, true, kart, standIn = false, aspect = 1f, grow = 0f), 0.0001f)
        // No tile cut, or a hero's whole cover: centred first, then up toward the title most boxes carry.
        assertEquals(0.5f, bloomCropBias(false, true, null, standIn = false, aspect = 1f, grow = 0f), 0f)
        assertEquals(0.5f, bloomCropBias(false, false, kart, standIn = false, aspect = 1f, grow = 0f), 0f)
        assertTrue(bloomCropBias(false, false, kart, standIn = false, aspect = 0.5f, grow = 1f) < 0.5f)
        assertEquals(0.5f, bloomCropBias(true, true, kart, standIn = false, aspect = 0.5f, grow = 1f), 0f)
    }
}
