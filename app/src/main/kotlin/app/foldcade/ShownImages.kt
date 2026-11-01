package app.foldcade

import java.util.IdentityHashMap

/** Two panels can display the same decoded image. A leaving panel releases only its own use. */
internal class ShownImages<T : Any> {
    private val uses = IdentityHashMap<T, Int>()
    fun add(image: T) { uses[image] = (uses[image] ?: 0) + 1 }
    fun remove(image: T) {
        val count = uses[image] ?: return
        if (count == 1) uses.remove(image) else uses[image] = count - 1
    }
    fun contains(image: T): Boolean = uses.containsKey(image)
}

/** Trim once as a game takes a panel. Browsing beside it must keep the grid's cache. */
internal fun homeIconCacheMayTrim(before: Session, after: Session): Boolean =
    before.bothScreensFree() && !after.bothScreensFree()

/** Long enough for the page beside a game to fade in and the game's own screen to fade out. */
internal const val HOME_ICON_TRIM_DELAY_MS = 2_000L
