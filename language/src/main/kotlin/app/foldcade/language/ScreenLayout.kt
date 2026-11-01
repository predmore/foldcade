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
     * Where the L1 and R1 islands sit, and where their menus, Settings, and Play activity
     * open: over the hero, or over the grid when there is no hero.
     */
    val menuScreen: HostScreen
        get() = showcaseScreen ?: gridScreen

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
    const val showcaseTop = "Classic"
    const val showcaseBottom = "Swapped"
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
 * Nothing Foldcade draws is aimed at the dark screen: a dialog, a menu, a page, or the
 * connect form meant for it opens over the grid instead. Keys from the dark screen are
 * already the grid's, so this only catches what was aimed there by default.
 */
fun PickerModel.offTheDarkScreen(): PickerModel {
    val dark = layout.darkScreen ?: return this
    val lit = layout.gridScreen
    fun HostScreen.lit(): HostScreen = if (this == dark) lit else this
    if (dialog?.screen != dark && panel?.screen != dark && settings?.screen != dark &&
        playActivity?.screen != dark && connectScreen != dark && saveSetup?.screen != dark
    ) return this
    return copy(
        dialog = dialog?.let { it.copy(screen = it.screen.lit()) },
        panel = panel?.let { it.copy(screen = it.screen.lit()) },
        settings = settings?.let { it.copy(screen = it.screen.lit()) },
        playActivity = playActivity?.let { it.copy(screen = it.screen.lit()) },
        connectScreen = connectScreen?.lit(),
        saveSetup = saveSetup?.let { it.copy(screen = it.screen.lit()) },
    )
}
