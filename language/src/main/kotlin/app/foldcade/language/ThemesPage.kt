package app.foldcade.language

/** Settings › Look › Themes: every pack, what it says about itself, and what Foldcade found in it. */
object ThemesCopy {
    const val row = "Themes"
    const val title = "Themes"
    const val add = "Add a theme"
    const val restart = "Restart to see changes"
    const val builtIn = "Comes with Foldcade"
    const val inUse = "In use"
    const val onTrial = "On trial"
    const val builds = "Builds on"
    const val license = "License"
    const val report = "Nothing to report"
    const val removeArmed = "Press X again to remove"
    const val remove = "Remove"
    const val adding = "Adding"
    const val added = "Added. Restart to use it."
    const val removed = "Removed. Restart to finish."
    const val notRemoved = "That theme couldn't be removed."
    const val restarting = "Restarting"

    /** What a refused install says. One short line each. */
    fun refused(reason: ThemeInstallRefusal): String = when (reason) {
        ThemeInstallRefusal.NotATheme -> "That file isn't a Foldcade theme."
        ThemeInstallRefusal.NoId -> "That theme is too old to install. It needs an id and version 2."
        ThemeInstallRefusal.Bundled -> "That theme comes with Foldcade already."
        ThemeInstallRefusal.NoRelease -> "That theme has no release number."
        ThemeInstallRefusal.NewerHost -> "That theme needs a newer Foldcade."
        ThemeInstallRefusal.OtherSource -> "A theme with that id came from somewhere else."
        ThemeInstallRefusal.NotNewer -> "That release is already installed, or a newer one is."
        ThemeInstallRefusal.WentBack -> "That release stopped Foldcade before, so it stays out."
        ThemeInstallRefusal.Broken -> "That theme is missing files, or a shader didn't compile."
        ThemeInstallRefusal.TooLarge -> "That theme is too large."
    }
}

/** One pack as the Themes page lists it. [removable] packs are installed, not shipped. */
data class ThemeEntry(
    val id: String,
    val name: String,
    val release: String?,
    val about: ThemeAbout,
    val diagnostics: List<ThemeDiagnostic>,
    val removable: Boolean,
    val inUse: Boolean,
    val onTrial: Boolean,
    val parents: List<String> = emptyList(),
)

/** A row on the Themes page. */
sealed interface ThemesRow {
    data class Pack(val entry: ThemeEntry) : ThemesRow
    data object Add : ThemesRow
    data object Restart : ThemesRow
}

/** What a key on the Themes page asks the host to do. */
sealed interface ThemesAction {
    data object Close : ThemesAction
    data object Add : ThemesAction
    data object Restart : ThemesAction
    data class Remove(val entry: ThemeEntry) : ThemesAction
}

/**
 * The Themes page. Up and down move through the packs, then Add a theme, then Restart once
 * something changed. A on Add opens the file picker; X twice on an installed pack removes it.
 * [note] is the one line the page says back, such as why an install was refused.
 */
data class ThemesPage(
    val packs: List<ThemeEntry>,
    val restartable: Boolean = false,
    val focus: Int = 0,
    val armed: Boolean = false,
    val note: String? = null,
    val busy: Boolean = false,
) {
    val rows: List<ThemesRow> get() = packs.map(ThemesRow::Pack) + ThemesRow.Add + listOfNotNull(ThemesRow.Restart.takeIf { restartable })

    val focused: ThemesRow get() = rows[focus.coerceIn(0, rows.lastIndex)]

    fun on(meaning: Meaning): Pair<ThemesPage, ThemesAction?> {
        // An install in flight finishes before the page takes another key.
        if (busy) return this to null
        return when (meaning) {
            Meaning.MoveUp -> moved(-1) to null
            Meaning.MoveDown -> moved(1) to null
            Meaning.Back -> this to ThemesAction.Close
            Meaning.Activate -> when (focused) {
                ThemesRow.Add -> copy(armed = false, note = null) to ThemesAction.Add
                ThemesRow.Restart -> copy(armed = false, note = ThemesCopy.restarting, busy = true) to ThemesAction.Restart
                is ThemesRow.Pack -> this to null
            }
            Meaning.ActivateBottom -> {
                val pack = (focused as? ThemesRow.Pack)?.entry?.takeIf { it.removable } ?: return this to null
                if (armed) copy(armed = false, note = null) to ThemesAction.Remove(pack)
                else copy(armed = true, note = ThemesCopy.removeArmed) to null
            }
            else -> this to null
        }
    }

    private fun moved(step: Int): ThemesPage {
        val next = (focus + step).coerceIn(0, rows.lastIndex)
        return if (next == focus) this else copy(focus = next, armed = false, note = null)
    }

    /** An install began: nothing else is pressed until it ends. */
    fun adding(): ThemesPage = copy(busy = true, note = ThemesCopy.adding)

    /** An install or a removal ended, with what to say, and the page's packs as they now stand. */
    fun settled(note: String, changed: Boolean, packs: List<ThemeEntry> = this.packs): ThemesPage =
        copy(packs = packs, restartable = restartable || changed, busy = false, armed = false, note = note,
            focus = focus.coerceIn(0, packs.size + if (restartable || changed) 1 else 0))
}
