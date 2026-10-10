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
}

data class ExternalApp(
    val id: String,
    val occupiesBothDisplays: Boolean = false,
)

/**
 * Dual-screen session. Idle is hero on top and picker on the bottom.
 * A single-screen launch leaves the picker on the other panel.
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
                else -> Surface.Picker
            }
            Panel.Bottom -> when {
                !bottomFree -> null
                else -> Surface.Picker
            }
        }
    }

    fun bothScreensFree(): Boolean = topApp == null && bottomApp == null

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
            return copy(topApp = app, bottomApp = app)
        }
        return when (panel) {
            Panel.Top -> copy(topApp = app)
            Panel.Bottom -> copy(bottomApp = app)
        }
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
            return copy(topApp = null, bottomApp = null)
        }
        return when (panelWithInput) {
            Panel.Top -> copy(topApp = null)
            Panel.Bottom -> copy(bottomApp = null)
        }
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

private fun Panel.flip(): Panel = when (this) {
    Panel.Top -> Panel.Bottom
    Panel.Bottom -> Panel.Top
}
