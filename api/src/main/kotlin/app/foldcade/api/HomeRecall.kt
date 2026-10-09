// SPDX-License-Identifier: Apache-2.0

package app.foldcade.api

object IntentFacts {
    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val CATEGORY_HOME = "android.intent.category.HOME"
    const val CATEGORY_SECONDARY_HOME = "android.intent.category.SECONDARY_HOME"
    const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
}

/**
 * True when Android is bringing Foldcade forward as Home on a display.
 * A launcher-icon start is not a recall. Recall does not name the other display.
 */
fun isAndroidHomeRecall(action: String?, categories: Set<String>): Boolean {
    if (action != IntentFacts.ACTION_MAIN) return false
    if (IntentFacts.CATEGORY_LAUNCHER in categories &&
        IntentFacts.CATEGORY_HOME !in categories &&
        IntentFacts.CATEGORY_SECONDARY_HOME !in categories
    ) {
        return false
    }
    return IntentFacts.CATEGORY_HOME in categories ||
        IntentFacts.CATEGORY_SECONDARY_HOME in categories
}
