// SPDX-License-Identifier: Apache-2.0

package app.foldcade.api

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
 * Which screen a game launches on is stored per game, then per platform.
 * It is not a face button. The chrome control is what changes it.
 */
data class Session(
    val topApp: ExternalApp? = null,
    val bottomApp: ExternalApp? = null,
    val defaultDisplayIsTop: Boolean = true,
    val gameScreens: Map<String, Panel> = emptyMap(),
    val platformScreens: Map<String, Panel> = emptyMap(),
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
     * The launch-target control is on screen only while both panels belong to
     * Foldcade and the focused entry can take one screen.
     */
    fun launchTargetControlVisible(occupiesBothDisplays: Boolean): Boolean =
        bothScreensFree() && !occupiesBothDisplays

    /**
     * Screen a single-screen game opens on.
     * The only free screen wins. Otherwise the stored game, then its platform, then top.
     * Null when both screens are already taken.
     */
    fun singleScreenTarget(gameId: String, platformId: String?): Panel? {
        val topFree = topApp == null
        val bottomFree = bottomApp == null
        return when {
            topFree && bottomFree ->
                gameScreens[gameId] ?: platformId?.let { platformScreens[it] } ?: Panel.Top
            topFree -> Panel.Top
            bottomFree -> Panel.Bottom
            else -> null
        }
    }

    fun cycleStoredScreen(gameId: String, platformId: String?, onPlatform: Boolean): Session {
        if (onPlatform && platformId != null) {
            val current = platformScreens[platformId] ?: Panel.Top
            return copy(platformScreens = platformScreens + (platformId to current.flip()))
        }
        val current = gameScreens[gameId] ?: platformId?.let { platformScreens[it] } ?: Panel.Top
        return copy(gameScreens = gameScreens + (gameId to current.flip()))
    }

    fun withDefaultDisplayIsTop(top: Boolean): Session = copy(defaultDisplayIsTop = top)

    fun launch(app: ExternalApp, platformId: String? = null): Session {
        if (app.occupiesBothDisplays) {
            return copy(topApp = app, bottomApp = app)
        }
        val target = singleScreenTarget(app.id, platformId) ?: return this
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
