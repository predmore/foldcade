package app.foldcade.language

/**
 * One focused shelf item. [detail] is the short text. Artwork is the mark or icon.
 */
data class HeroItem(
    val key: String,
    val title: String,
    val detail: String,
    val mark: String? = null,
    val packageName: String? = null,
    val emptyShelfHint: Boolean = false,
    val availability: String? = null,
)

/**
 * A focused platform folder. [count] is the game count when the library reported one.
 * Null means the count is unknown. This is not the folder grid.
 */
data class HeroFolder(
    val key: String,
    val name: String,
    val count: Int?,
)

sealed interface HeroSubject {
    val key: String

    data class Item(val item: HeroItem) : HeroSubject {
        override val key: String get() = item.key
    }

    data class Folder(val folder: HeroFolder) : HeroSubject {
        override val key: String get() = folder.key
    }
}

data class HeroCopy(
    val title: String,
    val detail: String,
    val showsArt: Boolean,
)

/** Game count under a folder name. One game stays singular. */
fun folderCountLine(count: Int): String {
    val games = count.coerceAtLeast(0)
    return if (games == 1) "1 game" else "$games games"
}

fun heroCopy(subject: HeroSubject): HeroCopy = when (subject) {
    is HeroSubject.Item -> HeroCopy(
        title = subject.item.title,
        detail = subject.item.detail,
        showsArt = true,
    )
    is HeroSubject.Folder -> HeroCopy(
        title = subject.folder.name,
        detail = subject.folder.count?.let(::folderCountLine).orEmpty(),
        showsArt = false,
    )
}

/**
 * Two layers at most. A newer focus becomes [HeroBlend.front] and the item
 * that was arriving becomes [HeroBlend.back], at the alpha it had already
 * reached. The layer that was leaving is dropped, so rapid moves do not queue.
 */
data class HeroBlend<T>(
    val front: T?,
    val back: T?,
    val frontAlpha: Float,
    val backAlpha: Float,
)

fun <T> retargetHero(
    current: HeroBlend<T>,
    next: T?,
    same: (T?, T?) -> Boolean = { left, right -> left == right },
): HeroBlend<T> {
    if (same(next, current.front)) {
        return if (next == current.front) current else current.copy(front = next)
    }
    return HeroBlend(
        front = next,
        back = current.front,
        frontAlpha = 0f,
        backAlpha = current.frontAlpha.coerceIn(0f, 1f),
    )
}

/**
 * True only while artwork is arriving or leaving.
 * A settled front is not a layer: a graphics layer left in place keeps the
 * accessibility dump empty, so a platform title is not on screen.
 */
fun heroCrossfadeActive(hasFront: Boolean, hasBack: Boolean, frontAlpha: Float, backAlpha: Float): Boolean {
    if (hasBack || backAlpha > 0.001f) return true
    if (!hasFront) return false
    return frontAlpha < 0.999f
}
