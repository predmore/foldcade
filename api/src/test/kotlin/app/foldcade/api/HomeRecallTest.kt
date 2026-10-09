package app.foldcade.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRecallTest {
    @Test
    fun homeCategoryIsARecall() {
        assertTrue(
            isAndroidHomeRecall(
                IntentFacts.ACTION_MAIN,
                setOf(IntentFacts.CATEGORY_HOME),
            ),
        )
    }

    @Test
    fun secondaryHomeCategoryIsARecall() {
        assertTrue(
            isAndroidHomeRecall(
                IntentFacts.ACTION_MAIN,
                setOf(IntentFacts.CATEGORY_SECONDARY_HOME),
            ),
        )
    }

    @Test
    fun launcherIconIsNotARecall() {
        assertFalse(
            isAndroidHomeRecall(
                IntentFacts.ACTION_MAIN,
                setOf(IntentFacts.CATEGORY_LAUNCHER),
            ),
        )
    }

    @Test
    fun otherActionsAreNotARecall() {
        assertFalse(
            isAndroidHomeRecall(
                "android.intent.action.VIEW",
                setOf(IntentFacts.CATEGORY_HOME),
            ),
        )
    }
}
