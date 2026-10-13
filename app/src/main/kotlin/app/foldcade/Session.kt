package app.foldcade

/**
 * Which panel a surface belongs to. Not a display id.
 * Thor's default display is the top panel. A setting can swap that.
 */
enum class Panel {
    Top,
    Bottom,
}

/** Foldcade-owned surface. Null on a panel means an external app owns it. */
enum class Surface {
    Hero,
    Picker,

    /**
     * The quiet screen beside a single-screen app: the time, the battery, the
     * app's art, and a button that opens the grid here. See [Session.browsing].
     */
    CommandCenter,
}

data class ExternalApp(
    val id: String,
    val occupiesBothDisplays: Boolean = false,
)

/**
 * Dual-screen session. Idle is hero on top and picker on the bottom.
 * A single-screen launch leaves the command center on the other panel, and
 * the picker there only while the user is [browsing] it.
 * Android Home clears only the panel that has input. An app on both screens
 * runs on the top panel, so Home there clears both.
 *
 * Which screen a game launches on is the button that launched it: A for the
 * top screen, X for the bottom.
 */
data class Session(
    val topApp: ExternalApp? = null,
    val bottomApp: ExternalApp? = null,
    val defaultDisplayIsTop: Boolean = true,
    /**
     * The free panel showing the grid instead of the command center. Cleared
     * when an app takes that panel, when both panels are free again, and when
     * the user leaves it idle.
     */
    val browsing: Panel? = null,
) {
    fun externalOn(panel: Panel): ExternalApp? = when (panel) {
        Panel.Top -> topApp
        Panel.Bottom -> bottomApp
    }

    fun surfaceOn(panel: Panel): Surface? {
        val topFree = topApp == null
        val bottomFree = bottomApp == null
        return when (panel) {
            Panel.Top -> when {
                !topFree -> null
                bottomFree -> Surface.Hero
                else -> besideApp(panel)
            }
            Panel.Bottom -> when {
                !bottomFree -> null
                topFree -> Surface.Picker
                else -> besideApp(panel)
            }
        }
    }

    private fun besideApp(panel: Panel): Surface =
        if (browsing == panel) Surface.Picker else Surface.CommandCenter

    /** The command center on [panel] gives way to the grid. Nothing changes on any other surface. */
    fun browse(panel: Panel): Session =
        if (surfaceOn(panel) == Surface.CommandCenter) copy(browsing = panel) else this

    fun stopBrowsing(): Session = if (browsing == null) this else copy(browsing = null)

    fun bothScreensFree(): Boolean = topApp == null && bottomApp == null

    /** True while the app or game [id] holds either panel. */
    fun runs(id: String): Boolean = topApp?.id == id || bottomApp?.id == id

    /** The island menus open on the top screen. An app there would cover them, so L1 and R1 do nothing. */
    fun islandsOpen(): Boolean = topApp == null

    /** The picker handles controls only while it is the focused surface. */
    fun pickerHandlesKeys(focused: Panel): Boolean = surfaceOn(focused) == Surface.Picker

    /**
     * Screen a single-screen game opens on: [requested] while it is free. When it
     * is taken, the other screen if that one is free, so a running game is not
     * replaced. Null when both screens are already taken.
     */
    fun singleScreenTarget(requested: Panel): Panel? {
        val other = requested.flip()
        return when {
            externalOn(requested) == null -> requested
            externalOn(other) == null -> other
            else -> null
        }
    }

    fun withDefaultDisplayIsTop(top: Boolean): Session = copy(defaultDisplayIsTop = top)

    fun launch(app: ExternalApp, requested: Panel = Panel.Top): Session {
        if (app.occupiesBothDisplays) {
            return copy(topApp = app, bottomApp = app)
        }
        val target = singleScreenTarget(requested) ?: return this
        return place(target, app)
    }

    fun place(panel: Panel, app: ExternalApp): Session {
        if (app.occupiesBothDisplays) {
            return copy(topApp = app, bottomApp = app, browsing = null)
        }
        return when (panel) {
            Panel.Top -> copy(topApp = app)
            Panel.Bottom -> copy(bottomApp = app)
        }.settled()
    }

    fun clearIfCurrent(panel: Panel, appId: String): Session {
        if (externalOn(panel)?.id != appId) return this
        return home(panel)
    }

    /**
     * Android Home on the panel that currently has input.
     * Clears that panel only, except an app on both screens: it runs on the
     * top panel and only draws on the bottom, so Home on top clears both.
     */
    fun home(panelWithInput: Panel): Session {
        val app = externalOn(panelWithInput)
        if (app?.occupiesBothDisplays == true && panelWithInput == Panel.Top) {
            return copy(topApp = null, bottomApp = null, browsing = null)
        }
        return when (panelWithInput) {
            Panel.Top -> copy(topApp = null)
            Panel.Bottom -> copy(bottomApp = null)
        }.settled()
    }

    /** [browsing] only means something on a free panel beside an app. */
    private fun settled(): Session {
        val panel = browsing ?: return this
        return if (externalOn(panel) != null || externalOn(panel.flip()) == null) copy(browsing = null) else this
    }

    /**
     * Foldcade came to the front on [panel], however it got there.
     * Same as [home], except the bottom panel says nothing about an app on both
     * screens: that app draws over the bottom Foldcade without pausing it.
     */
    fun foldcadeResumed(panel: Panel): Session {
        val app = externalOn(panel) ?: return this
        if (app.occupiesBothDisplays && panel == Panel.Bottom) return this
        return home(panel)
    }
}

internal fun Panel.flip(): Panel = when (this) {
    Panel.Top -> Panel.Bottom
    Panel.Bottom -> Panel.Top
}
