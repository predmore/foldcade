package app.foldcade

/**
 * One start of the other screen's Home, so a second resume does not open another.
 * Cleared when that activity exists, when the start throws, or when the display
 * it was aimed at goes away before it arrives. A different display does not
 * cancel a start that is still in flight.
 */
internal class CompanionLaunch {
    var starting: Boolean = false
        private set

    var targetDisplayId: Int? = null
        private set

    /** False when a start is already on its way. */
    fun begin(displayId: Int): Boolean {
        if (starting) return false
        starting = true
        targetDisplayId = displayId
        return true
    }

    fun arrived() {
        starting = false
        targetDisplayId = null
    }

    fun failed() = arrived()

    fun displayRemoved(displayId: Int, companionAliveOnIt: Boolean) {
        if (targetDisplayId == displayId && !companionAliveOnIt) arrived()
    }
}
