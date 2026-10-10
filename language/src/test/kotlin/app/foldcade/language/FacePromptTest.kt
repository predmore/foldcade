package app.foldcade.language

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacePromptTest {
    @Test
    fun defaultMapKeepsConfirmOnAAndTheThorDiamond() {
        val map = FaceMap.standard()
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, map.confirmKey)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, map.backKey)
        assertEquals(FaceSet.N, map.set)
        assertEquals(DiamondPoint.Top, map.positions[FaceLetter.X])
        assertEquals(DiamondPoint.Left, map.positions[FaceLetter.Y])
        assertEquals(DiamondPoint.Right, map.positions[FaceLetter.A])
        assertEquals(DiamondPoint.Bottom, map.positions[FaceLetter.B])
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_A, faces = map))
        assertEquals(Meaning.Back, meaningOf(KeyEvent.KEYCODE_BUTTON_B, faces = map))
        assertEquals(
            FaceArt("ic_btn_face_diamond_n_a", null),
            faceArt(FaceLetter.A, map, filled = false),
        )
        assertEquals(
            FaceArt("ic_btn_face_diamond_n_a_filled", null),
            faceArt(FaceLetter.A, map, filled = true),
        )
    }

    @Test
    fun xboxStyleUsesTheAlternateDiamondWithoutFlippingKeycodes() {
        val map = resolveFaceMap(ThorStyle.Xbox, confirm = null)
        assertEquals(FaceSet.X, map.set)
        assertEquals(DiamondPoint.Top, map.positions[FaceLetter.Y])
        assertEquals(DiamondPoint.Left, map.positions[FaceLetter.X])
        assertEquals(DiamondPoint.Right, map.positions[FaceLetter.B])
        assertEquals(DiamondPoint.Bottom, map.positions[FaceLetter.A])
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, map.confirmKey)
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_A, faces = map))
        assertEquals(Meaning.Back, meaningOf(KeyEvent.KEYCODE_BUTTON_B, faces = map))
        assertEquals("ic_btn_face_diamond_x_a", faceArt(FaceLetter.A, map, filled = false).diamond)
        assertNull(faceArt(FaceLetter.A, map, filled = false).letter)
    }

    @Test
    fun swappedConfirmUsesThePhysicalLabel() {
        val map = resolveFaceMap(ThorStyle.Standard, confirm = PromptKey.FaceB)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, map.confirmKey)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, map.backKey)
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_B, faces = map))
        assertEquals(Meaning.Back, meaningOf(KeyEvent.KEYCODE_BUTTON_A, faces = map))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_X, faces = map))
        assertEquals(FaceSet.N, map.set)
        assertEquals("ic_btn_face_diamond_n_b", faceArt(FaceLetter.B, map, filled = false).diamond)
        assertEquals(FaceLetter.B, promptedLetter(confirm = true, back = true, map))
    }

    @Test
    fun remappedConfirmUsesThatLetterOnTheDetectedSet() {
        val map = faceMapWithConfirm(resolveFaceMap(ThorStyle.Xbox, null), PromptKey.FaceX)
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, map.confirmKey)
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, map.backKey)
        assertEquals(Meaning.Activate, meaningOf(KeyEvent.KEYCODE_BUTTON_X, faces = map))
        assertNull(meaningOf(KeyEvent.KEYCODE_BUTTON_A, faces = map))
        assertEquals(Meaning.Back, meaningOf(KeyEvent.KEYCODE_BUTTON_Y, faces = map))
        assertEquals(FaceSet.X, map.set)
        assertEquals("ic_btn_face_diamond_x_x", faceArt(FaceLetter.X, map, filled = false).diamond)
        assertEquals("ic_btn_face_diamond_x_x_filled", faceArt(FaceLetter.X, map, filled = true).diamond)
    }

    @Test
    fun unknownLayoutUsesTheBlankDiamondPlusTheLetter() {
        val scrambled = FaceMap(
            confirmKey = KeyEvent.KEYCODE_BUTTON_A,
            backKey = KeyEvent.KEYCODE_BUTTON_B,
            positions = mapOf(
                FaceLetter.A to DiamondPoint.Top,
                FaceLetter.B to DiamondPoint.Left,
                FaceLetter.X to DiamondPoint.Right,
                FaceLetter.Y to DiamondPoint.Bottom,
            ),
        )
        assertEquals(FaceSet.Neither, scrambled.set)
        assertEquals(
            FaceArt("ic_btn_face_diamond_blank_top", "ic_btn_a"),
            faceArt(FaceLetter.A, scrambled, filled = false),
        )
        assertEquals(
            FaceArt("ic_btn_face_diamond_blank_top_filled", "ic_btn_a_filled"),
            faceArt(FaceLetter.A, scrambled, filled = true),
        )
    }

    @Test
    fun hintClusterPutsSquareLeftAndTheReturnArrowBottomRight() {
        val map = FaceMap.standard()
        val roles = listOf(ClusterRole.Select, ClusterRole.Start, ClusterRole.Home, ClusterRole.Back)
        assertEquals(
            listOf("ic_btn_square", "ic_btn_triangle", "ic_btn_home", "ic_btn_back"),
            roles.map { clusterGlyph(it, thor = true, filled = false) },
        )
        assertEquals(
            listOf("ic_btn_select", "ic_btn_start", "ic_btn_home", "ic_btn_back"),
            roles.map { clusterGlyph(it, thor = false, filled = false) },
        )
        val held = setOf(PromptKey.Select, PromptKey.FaceB)
        assertTrue(clusterFilled(ClusterRole.Select, held, map))
        assertEquals("ic_btn_square_filled", clusterGlyph(ClusterRole.Select, thor = true, filled = true))
        assertTrue(clusterFilled(ClusterRole.Back, held, map))
        assertFalse(clusterFilled(ClusterRole.Start, held, map))
        assertFalse(clusterFilled(ClusterRole.Home, held, map))
    }

    @Test
    fun calibrationStaysOffUntilALaterLaunchAndFollowsTheDevice() {
        assertFalse(offerButtonCalibration(firstSession = true, styleKnown = false, dismissed = false, calibrated = false))
        assertFalse(offerButtonCalibration(firstSession = false, styleKnown = true, dismissed = false, calibrated = false))
        assertFalse(offerButtonCalibration(firstSession = false, styleKnown = false, dismissed = true, calibrated = false))
        assertFalse(offerButtonCalibration(firstSession = false, styleKnown = false, dismissed = false, calibrated = true))
        assertTrue(offerButtonCalibration(firstSession = false, styleKnown = false, dismissed = false, calibrated = false))
        val saved = mapOf("pad-a" to PromptKey.FaceX)
        assertEquals(PromptKey.FaceX, saved["pad-a"])
        assertNull(saved["pad-b"])
        assertEquals(ThorStyle.Standard, interpretThorStyle(" Thor "))
        assertEquals(ThorStyle.Xbox, interpretThorStyle("xbox"))
        assertNull(interpretThorStyle("ban on use"))
        assertNull(interpretThorStyle(null))
    }

    @Test
    fun islandChipsNameTheFilledTwinsWithoutOpeningTheMenu() {
        assertEquals(IslandChip.LEFT, islandGlyph(left = true, held = emptySet()))
        assertEquals(IslandChip.RIGHT, islandGlyph(left = false, held = emptySet()))
        assertEquals(IslandChip.LEFT_FILLED, islandGlyph(left = true, held = setOf(PromptKey.L1)))
        assertEquals(IslandChip.RIGHT_FILLED, islandGlyph(left = false, held = setOf(PromptKey.R1)))
        assertEquals("ic_btn_l1_filled", IslandChip.LEFT_FILLED)
        assertEquals("ic_btn_r1_filled", IslandChip.RIGHT_FILLED)
    }

    @Test
    fun buttonLabelRowOffersPressConfirmAndCanBeDismissed() {
        val root = PickerModel(count = 1, rowsPerPage = 1, showLaunchTarget = false, offerButtonLabels = true)
        val rows = settingsRows(SettingsCategory.Personalization, root)
        assertTrue(rows.indexOf(Row.ButtonLabels) > rows.indexOf(Row.MotionSpeed))
        val capturing = reduce(root.onSetting(Row.ButtonLabels), Meaning.Activate).first
        assertTrue(capturing.capturingConfirm)
        assertEquals(RowText(Copy.buttonLabels, Copy.pressConfirm), rowText(Row.ButtonLabels, capturing))
        val (dismissed, effect) = reduce(capturing, Meaning.Activate)
        assertEquals(Effect.DismissButtonLabels, effect)
        assertFalse(dismissed.offerButtonLabels)
        assertFalse(dismissed.capturingConfirm)
    }
}
