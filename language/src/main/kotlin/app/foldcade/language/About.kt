package app.foldcade.language

/**
 * Y › About: one game's page, on the screen its menu was on. [game] is the tile's id,
 * and [grid] is the focus that comes back when the page closes.
 */
data class GameAboutPage(
    val screen: HostScreen,
    val game: String,
    val grid: GridFocus = GridFocus(),
)

object GameAboutCopy {
    /** The Y menu row, and the page beside it. */
    const val about = "About"
    const val released = "Released"
    const val genre = "Genre"
    const val genres = "Genres"
    const val developer = "Developer"
    const val publisher = "Publisher"
    const val players = "Players"
    const val size = "Size"
    const val screens = "Screens"

    /** The page's one line when nothing about the game is known yet. */
    const val noInfo = "No info for this game yet. It comes from RomM, or an ES-DE, Batocera or Skraper media folder."
}

/**
 * Y offers About on a game a library lists, from RomM or a folder. An Android game, a stream,
 * or a shortcut has nothing for the page to say, so its menu leaves the row out.
 */
fun hasAboutPage(item: HomeItem): Boolean = !item.placeholder && item.kind == HomeKind.Rom

/** One line of a game's page: what it is, and what the library says. */
data class AboutFact(val label: String, val value: String)

/**
 * The facts a game's page lists, in order, leaving out what no source gave. A developer
 * that also published the game is said once.
 */
fun aboutFacts(
    year: Int?,
    genres: List<String>,
    developer: String?,
    publisher: String?,
    players: String?,
    sizeBytes: Long? = null,
    bothScreens: Boolean = false,
): List<AboutFact> = buildList {
    year?.let { add(AboutFact(GameAboutCopy.released, it.toString())) }
    val named = genres.mapNotNull { it.trim().ifEmpty { null } }
    if (named.isNotEmpty()) add(AboutFact(if (named.size == 1) GameAboutCopy.genre else GameAboutCopy.genres, named.joinToString(", ")))
    val madeBy = developer?.trim()?.ifEmpty { null }
    val sentBy = publisher?.trim()?.ifEmpty { null }
    madeBy?.let { add(AboutFact(GameAboutCopy.developer, it)) }
    sentBy?.takeUnless { madeBy != null && it.equals(madeBy, ignoreCase = true) }?.let { add(AboutFact(GameAboutCopy.publisher, it)) }
    players?.trim()?.ifEmpty { null }?.let { add(AboutFact(GameAboutCopy.players, it)) }
    sizeBytes?.takeIf { it > 0 }?.let { add(AboutFact(GameAboutCopy.size, formatDownloadSize(it))) }
    if (bothScreens) add(AboutFact(GameAboutCopy.screens, Copy.usesBothScreens))
}

/**
 * The page is only read. B closes it and puts the grid focus back, and so do L1 and R1,
 * and Select, as Y then Select opened it. A key still held from the press that opened
 * it does nothing here, and nor does any other key.
 */
internal fun applyGameAbout(model: PickerModel, page: GameAboutPage, meaning: Meaning, repeatCount: Int): Pair<PickerModel, Effect?> {
    if (repeatCount > 0) return model to null
    return when (meaning) {
        Meaning.Back, Meaning.LeftPanel, Meaning.RightPanel, Meaning.View -> model.copy(gameAbout = null, focus = page.grid) to null
        else -> model to null
    }
}

/** The hint row beside the page: only back. */
fun gameAboutHints(model: PickerModel): List<HintKey> =
    listOf(HintKey(promptKeyOf(model.faceMap.backKey) ?: PromptKey.FaceB, Copy.back))
