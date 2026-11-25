package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class SuspectRegressionTest {
    @Test fun aFirstSaveIsOrdinaryEvenInAFreshFolder() {
        assertNull(suspectRegression(null, SaveShape(800, 1), setOf(RegressionContext.FreshRoot)))
    }

    @Test fun aPreviouslyAgreedSaveAndRiskyContextCanRequireAChoiceAtTheSameSize() {
        val save = SaveShape(1024, 1)
        for (context in RegressionContext.entries) {
            assertEquals(SaveProtectionCopy.mayBeNewGame, suspectRegression(save, save, setOf(context)))
        }
    }

    @Test fun onlyALargeDropInASubstantialSingleFileRaisesSuspicion() {
        assertNull(suspectRegression(SaveShape(1024, 1), SaveShape(24, 1)))
        assertNull(suspectRegression(SaveShape(4096, 1), SaveShape(1025, 1)))
        assertNotNull(suspectRegression(SaveShape(4096, 1), SaveShape(1024, 1)))
    }

    @Test fun folderMemberCountIgnoresCompressionAndDistinguishesOneMemberFromRawFiles() {
        val previous = SaveShape(9999, 4)
        assertNull(suspectRegression(previous, SaveShape(200, 3)))
        assertNotNull(suspectRegression(previous, SaveShape(12_000, 2)))
        assertNotNull(suspectRegression(SaveShape(600, 2), SaveShape(400, 1, folder = true)))
        assertNull(suspectRegression(SaveShape(600, 2), SaveShape(100, 1)))
        assertNull(suspectRegression(previous, SaveShape(0, 0, folder = true)))
    }
}
