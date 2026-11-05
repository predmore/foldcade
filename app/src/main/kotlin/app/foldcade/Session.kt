package app.foldcade

import app.foldcade.language.HostScreen
import app.foldcade.language.ScreenLayout

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

    /**
     * The screen [ScreenLayout.TopOnly] leaves black, at its lowest backlight. Nothing is
     * drawn there and its keys drive the grid. It stays dark beside a game too.
     */
    Dark,
}

data class ExternalApp(
    val id: String,
    val occupiesBothDisplays: Boolean = false,
    /** Themes and Licenses cover Home, but remain part of its Settings page. */
    val foldcadePage: Boolean = false,
)

/**
 * Dual-screen session. Idle is the hero and the picker where [layout] puts them: by
 * default the hero on top and the picker on the bottom.
 * A single-screen launch leaves the command center on the other panel, and
 * the picker there only while the user is [browsing] it. The dark screen of
 * [ScreenLayout.TopOnly] stays dark instead.
 * Android Home clears only the panel that has input. An app on both screens
 * runs on the top panel, so Home there clears both.
 *
 * Which screen a game launches on is the button that launched it: A for
 * [playOn], X for the other screen.
 */
data class Session(
    val topApp: ExternalApp? = null,
    val bottomApp: ExternalApp? = null,
    val defaultDisplayIsTop: Boolean = true,
    /** Settings › System › Layout. */
    val layout: ScreenLayout = ScreenLayout.Default,
    /** Settings › System › Play on: the screen A opens a one-screen game on. */
    val playOn: Panel = Panel.Top,
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
        if (externalOn(panel) != null) return null
        if (panel == darkPanel()) return Surface.Dark
        if (externalOn(panel.flip()) != null) return besideApp(panel)
        return if (panel == gridPanel()) Surface.Picker else Surface.Hero
    }

    /** The screen the grid has while no app is beside it. */
    fun gridPanel(): Panel = layout.gridScreen.panel()

    /** The screen [layout] keeps dark, if it keeps one. */
    fun darkPanel(): Panel? = layout.darkScreen?.panel()

    /** Where the L1 and R1 islands are, and where their menus and pages open. See [ScreenLayout.menuScreen]. */
    fun menuPanel(): Panel = layout.menuScreen.panel()

    /** The panel showing the hero now. None beside an app, and none with one screen. */
    fun heroPanel(): Panel? = Panel.entries.firstOrNull { surfaceOn(it) == Surface.Hero }

    /**
     * Where a page split over two screens puts its controls: the grid, or beside an app
     * the screen Foldcade has left. The hero's screen, when there is one, takes the
     * page's showcase half.
     */
    fun controlsPanel(): Panel? =
        Panel.entries.firstOrNull { surfaceOn(it) == Surface.Picker } ?: Panel.entries.firstOrNull { draws(it) }

    /**
     * True while keys on [panel] reach Foldcade. The dark screen's go to the grid, so only
     * while the grid's screen is Foldcade's; a game covering it keeps them from a grid nobody sees.
     */
    fun takesKeys(panel: Panel): Boolean = when (surfaceOn(panel)) {
        null -> false
        Surface.Dark -> draws(gridPanel())
        else -> true
    }

    /** True while Foldcade draws something on [panel]: not an app's, and not dark. */
    fun draws(panel: Panel): Boolean = surfaceOn(panel).let { it != null && it != Surface.Dark }

    /** A native page anchors its parent to its own screen, even when the other Home returns. */
    fun pageAvailable(panel: Panel): Boolean =
        if (externalOn(panel.flip())?.foldcadePage == true) false
        else draws(panel) || externalOn(panel)?.foldcadePage == true

    fun besideFoldcadePage(panel: Panel): Boolean = pageKeyTarget(panel) != null

    /** The owned activity beside this Home takes its page keys; the parent Settings stays inactive. */
    fun pageKeyTarget(panel: Panel): Panel? = panel.flip().takeIf { externalOn(it)?.foldcadePage == true }

    /** Android covers an owned page. Home has not resumed, so this panel is still taken. */
    fun settingsPageCovered(panel: Panel): Session =
        if (externalOn(panel)?.foldcadePage == true) place(panel, ExternalApp(OTHER_APP_ID)) else this

    private fun besideApp(panel: Panel): Surface =
        if (browsing == panel) Surface.Picker else Surface.CommandCenter

    /** The command center on [panel] gives way to the grid. Nothing changes on any other surface. The dark screen has none. */
    fun browse(panel: Panel): Session =
        if (surfaceOn(panel) == Surface.CommandCenter) copy(browsing = panel) else this

    /**
     * Where the collection icon editor shows: the screen with the hero, which shows the focused
     * collection large. Beside an app there is no hero, so it covers the grid it was opened from.
     */
    fun editorPanel(): Panel? =
        Panel.entries.firstOrNull { surfaceOn(it) == Surface.Hero } ?: Panel.entries.firstOrNull { surfaceOn(it) == Surface.Picker }

    fun stopBrowsing(): Session = if (browsing == null) this else copy(browsing = null)

    fun bothScreensFree(): Boolean = topApp == null && bottomApp == null

    /** True while the app or game [id] holds either panel. */
    fun runs(id: String): Boolean = topApp?.id == id || bottomApp?.id == id

    /** The island menus open on [menuPanel]. An app there would cover them, so L1 and R1 do nothing. */
    fun islandsOpen(): Boolean = externalOn(menuPanel()) == null

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

    /** A new layout. Nothing an app holds moves, and browsing beside one keeps its screen unless that screen went dark. */
    fun withLayout(next: ScreenLayout): Session =
        if (next == layout) this else copy(layout = next).let { if (it.browsing == it.darkPanel()) it.copy(browsing = null) else it }

    fun withPlayOn(panel: Panel): Session = copy(playOn = panel)

    fun launch(app: ExternalApp, requested: Panel = playOn): Session {
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

internal fun Panel.screen(): HostScreen = when (this) {
    Panel.Top -> HostScreen.Top
    Panel.Bottom -> HostScreen.Bottom
}

internal fun HostScreen.panel(): Panel = when (this) {
    HostScreen.Top -> Panel.Top
    HostScreen.Bottom -> Panel.Bottom
}
