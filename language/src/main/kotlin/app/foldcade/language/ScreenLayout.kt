package app.foldcade.language

/**
 * How Foldcade spreads itself over the two screens while it has both. The showcase is the
 * hero: the focused game large, with the L1 and R1 islands over it. The grid is the picker
 * and its hint row. [TopOnly] keeps the grid, with the islands, on the top screen and leaves
 * the bottom one black at its lowest backlight.
 *
 * A game beside Foldcade still takes the screen it opens on, and the screen left beside it
 * is the command center, as in every layout, except the dark screen, which stays dark.
 * [id] is what Settings saves.
 */
enum class ScreenLayout(val id: String) {
    ShowcaseTop("showcase-top"),
    ShowcaseBottom("showcase-bottom"),
    TopOnly("top-only"),
    ;

    /** The screen the grid has while no game is beside it. */
    val gridScreen: HostScreen
        get() = when (this) {
            ShowcaseTop -> HostScreen.Bottom
            ShowcaseBottom, TopOnly -> HostScreen.Top
        }

    /** The screen the hero has while no game is beside it. None with one screen. */
    val showcaseScreen: HostScreen?
        get() = when (this) {
            ShowcaseTop -> HostScreen.Top
            ShowcaseBottom -> HostScreen.Bottom
            TopOnly -> null
        }

    /** The screen left black. */
    val darkScreen: HostScreen?
        get() = if (this == TopOnly) HostScreen.Bottom else null

    /**
     * Where the L1 and R1 islands sit, and where their menus open: over the hero, or over
     * the grid when there is no hero.
     */
    val menuScreen: HostScreen
        get() = showcaseScreen ?: gridScreen

    /**
     * Where Settings, Play activity, a game's About, and the connect form open: the top screen, the larger,
     * in every layout. Its rows were laid out for that width. A game there sends them to the
     * screen Foldcade still has ([onShownScreens]).
     */
    val pageScreen: HostScreen
        get() = HostScreen.Top

    companion object {
        val Default = ShowcaseTop

        /** A saved [id], or the default for one this build does not know. */
        fun fromId(id: String?): ScreenLayout = entries.firstOrNull { it.id == id } ?: Default
    }
}

fun HostScreen.other(): HostScreen = when (this) {
    HostScreen.Top -> HostScreen.Bottom
    HostScreen.Bottom -> HostScreen.Top
}

/** Settings › System's two screen rows. A word or two each, as the rest of the page. */
object ScreenCopy {
    const val layout = "Layout"
    // Each says what the top screen shows, as Play on says Top or Bottom.
    const val showcaseTop = "Art top"
    const val showcaseBottom = "Art bottom"
    const val topOnly = "Top only"
    const val playOn = "Play on"
}

fun layoutLabel(layout: ScreenLayout): String = when (layout) {
    ScreenLayout.ShowcaseTop -> ScreenCopy.showcaseTop
    ScreenLayout.ShowcaseBottom -> ScreenCopy.showcaseBottom
    ScreenLayout.TopOnly -> ScreenCopy.topOnly
}

fun screenLabel(screen: HostScreen): String = when (screen) {
    HostScreen.Top -> Copy.top
    HostScreen.Bottom -> Copy.bottom
}

/** A names the screen a game opens on, and [other], X unless calibration took it, the other screen. */
fun screenHints(confirm: PromptKey, other: PromptKey, playOn: HostScreen): List<HintKey> = listOf(
    HintKey(confirm, if (playOn == HostScreen.Top) Copy.hintTop else Copy.hintBottom),
    HintKey(other, if (playOn == HostScreen.Top) Copy.hintBottom else Copy.hintTop),
)

/**
 * Nothing Foldcade draws is aimed at a screen it does not show: the dark screen, or one a game
 * holds. A dialog, a menu, a page, or the connect form meant for one opens on a screen that is
 * [shown] instead. Pages return to the preferred page screen as soon as it is shown again.
 * Dialogs follow their page, or return to the screen that opened them. Islands keep their
 * explicit screen while shown. With none shown the model stays put.
 */
fun PickerModel.onShownScreens(shown: (HostScreen) -> Boolean): PickerModel {
    val lit = listOf(layout.menuScreen, layout.gridScreen, HostScreen.Top, HostScreen.Bottom).firstOrNull(shown) ?: return this
    val page = layout.pageScreen.takeIf(shown) ?: lit
    val pageOpen = settings != null || playActivity != null || gameAbout != null || connectOpen || saveSetup != null
    val next = copy(
        dialog = dialog?.let {
            val preferred = if (pageOpen) layout.pageScreen else it.preferredScreen
            it.copy(screen = preferred.takeIf(shown) ?: lit)
        },
        // Islands can also be submenus of Settings; keep their explicit screen while it is shown.
        panel = panel?.let { it.copy(screen = it.screen.takeIf(shown) ?: lit) },
        settings = settings?.copy(screen = page),
        playActivity = playActivity?.copy(screen = page),
        gameAbout = gameAbout?.copy(screen = page),
        connectScreen = connectScreen?.let { page },
        saveSetup = saveSetup?.copy(screen = page),
    )
    return if (next == this) this else next
}

/** [onShownScreens] with only the dark screen unshown, for a model that knows no apps. */
fun PickerModel.offTheDarkScreen(): PickerModel = onShownScreens { it != layout.darkScreen }
