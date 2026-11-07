package app.foldcade

import app.foldcade.language.HeroFolder
import app.foldcade.language.HeroSubject
import app.foldcade.ui.TileCut
import app.foldcade.ui.bloomCropBias
import app.foldcade.ui.namedSubject
import app.foldcade.ui.titleCollapse
import app.foldcade.ui.richestShare
import app.foldcade.ui.titleSafeStart
import app.foldcade.ui.heroFactAlpha
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

    @Test fun theNamedGamesFactsFollowItsArtAndNeverAnotherGamesWarning() {
        assertEquals(1f, heroFactAlpha("old", "old", null, 1f, 0f), 0f)
        // The name timed out before its slow art: no previous game's save warning under it.
        assertEquals(0f, heroFactAlpha("new", "old", null, 1f, 0f), 0f)
        // As the art arrives, the single named line shares exactly its foreground opacity.
        assertEquals(0f, heroFactAlpha("new", "new", "old", 0f, 1f), 0f)
        assertEquals(.35f, heroFactAlpha("new", "new", "old", .35f, .65f), 0f)
        assertEquals(.65f, heroFactAlpha("old", "new", "old", .35f, .65f), 0f)
        // A fast third focus cannot inherit either retained game's facts.
        assertEquals(0f, heroFactAlpha("third", "new", "old", .35f, .65f), 0f)
        // Same-game art upgrades do not pulse the facts; an artless fallback can name itself.
        assertEquals(1f, heroFactAlpha("new", "new", "new", .35f, .65f), 0f)
        assertEquals(1f, heroFactAlpha("new", null, null, 0f, 0f), 0f)
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

    @Test fun aTitleBandAtTheSquareEdgeFinishesBeforeTheCrop() {
        // Thor's 256×384 tile decode, read 48 px wide. Samus pulls the richest
        // square up to 11–59, inside METROID. The title and subtitle finish at 68.
        val metroid = intArrayOf(
            861,1649,1347,1337,1842,1509,1085,1545,1642,1615,3156,3572,3444,4096,4618,3984,4151,4328,
            4013,3863,4135,4035,4004,3615,3773,3425,2024,2080,2551,2039,1885,1924,1806,2419,2554,2969,
            2760,2679,2244,2483,2197,2278,1592,1364,1095,1110,786,563,411,370,291,220,266,236,178,131,
            7926,4131,5406,3483,3321,3931,3262,3010,2553,2092,171,2512,936,132,72,66,
        )
        val start = titleSafeStart(metroid, 48)
        assertEquals(23, start)
        assertTrue((56..68).all { it in start until start + 48 })
        // The same band printed at the top is completed toward the top.
        assertEquals(1, titleSafeStart(metroid.reversedArray(), 48))
    }

    @Test fun finishingATitleDoesNotChaseAContinuousIllustrationOrLeaveTheBox() {
        val continuous = IntArray(72) { if (it in 16..65) 900 else 100 }
        // No quiet division inside the square: shifting just chops the other edge of art.
        assertEquals(app.foldcade.ui.richestStart(continuous, 48), titleSafeStart(continuous, 48))
        assertEquals(12, titleSafeStart(IntArray(72) { 100 }, 48))
        assertEquals(0, titleSafeStart(IntArray(48) { 100 }, 48))
        val edgeTitle = IntArray(72) { if (it >= 60) 900 else 100 }
        assertEquals(24, titleSafeStart(edgeTitle, 48))
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

    @Test fun artWithNoTileCutGrowsWithOneBiasForTheStandInAndTheSharpCover() {
        // A landscape cover's tile is its centre square and records no cut: its stand-in is the whole
        // picture, as the sharp cover is, so both take the same bias at every frame.
        for (step in 0..10) {
            val grow = step / 10f
            val aspect = 1f - grow * (1f - 9f / 16f)
            assertEquals(
                bloomCropBias(false, true, null, standIn = true, aspect = aspect, grow = grow),
                bloomCropBias(false, true, null, standIn = false, aspect = aspect, grow = grow),
                0f,
            )
        }
        // The first frame is the tile's centre square.
        assertEquals(0.5f, bloomCropBias(false, true, null, standIn = true, aspect = 1f, grow = 0f), 0f)
    }
}
