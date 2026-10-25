package app.foldcade.language

import androidx.compose.ui.graphics.Color

/**
 * How a collection looks: a folder in one of ten colours, with a glyph pressed into its face.
 * Foldcade's own folders keep their platform's mark on the dark tile, so the two never read alike.
 * The art is drawn by tools/art/marks.py; docs/iconography.md explains the parts.
 */
enum class CollectionTint(val id: String, val color: Color, val label: String) {
    Gold("gold", Color(0xFFFFC15A), "Gold"),
    Coral("coral", Color(0xFFFF7F5C), "Coral"),
    Rose("rose", Color(0xFFFF5C8A), "Rose"),
    Violet("violet", Color(0xFFB48CFF), "Violet"),
    Indigo("indigo", Color(0xFF7C8CFF), "Indigo"),
    Sky("sky", Color(0xFF4CC9FF), "Sky"),
    Teal("teal", Color(0xFF3EE6C0), "Teal"),
    Mint("mint", Color(0xFF8CF2A6), "Mint"),
    Lime("lime", Color(0xFFC6EE5A), "Lime"),
    Slate("slate", Color(0xFFA3B1C6), "Slate"),
    ;

    companion object {
        /** A saved id. One this release does not know is null, so the collection wears the default. */
        fun of(id: String?): CollectionTint? = entries.firstOrNull { it.id == id }
    }
}

const val MARK_STAR: String = "star"
const val MARK_CLOCK: String = "clock"

/** The theme glyphs, in the picker's order. Clock is Recently played's. */
val COLLECTION_GLYPHS: List<String> = listOf(
    MARK_STAR, "heart", "trophy", "crown", "gem", "sword", "shield", "skull", "flame", "rocket",
    "racing-flag", "puzzle", "ghost", "mushroom", "music", "two-players", "hourglass", "play-badge", "check-badge", MARK_CLOCK,
    "leaf", "paw",
)

/** A collection's glyph and colour. [mark] names both, so the grid and the hero draw it like any mark. */
data class CollectionLook(val glyph: String, val tint: CollectionTint) {
    val mark: String get() = "$PREFIX${tint.id}:$glyph"

    companion object {
        private const val PREFIX = "collection:"

        /** The look a [mark] names, or null for a plain mark. */
        fun parse(mark: String?): CollectionLook? {
            if (mark == null || !mark.startsWith(PREFIX)) return null
            val tint = CollectionTint.of(mark.removePrefix(PREFIX).substringBefore(':')) ?: return null
            val glyph = mark.substringAfter(PREFIX).substringAfter(':').takeIf { it.isNotEmpty() } ?: return null
            return CollectionLook(glyph, tint)
        }
    }
}

/**
 * The look a group wears, or null for one of Foldcade's folders. A collection that never chose a
 * glyph, saved by an earlier release, shows the stack of games. Every
 * collection is gold until the user picks a colour, as folders were. Recently played is a teal clock.
 */
fun collectionLook(folder: HomeFolder): CollectionLook? = when {
    folder.recent -> CollectionLook(MARK_CLOCK, CollectionTint.Teal)
    folder.collection -> CollectionLook(
        glyph = folder.mark?.takeIf { it in FOLDER_ICONS }
            ?: MARK_LIBRARY,
        tint = CollectionTint.of(folder.tint) ?: CollectionTint.Gold,
    )
    else -> null
}

/**
 * The icon editor for one collection. A row of colours sits over the grid of [FOLDER_ICONS], one
 * colour per column. The cursor picks as it moves, so the tile and the preview show the choice
 * at once; [was] is what B puts back. Up from the grid's top row goes to the colour in use, and
 * down from the colours to the glyph in use, so crossing between them never changes either.
 * L1 and R1 step the colour from anywhere, so a glyph deep in the grid keeps its place.
 */
data class IconPick(
    val folderId: String,
    val was: CollectionLook,
    val glyph: Int,
    val tint: Int,
    val onTints: Boolean = false,
) {
    val look: CollectionLook get() = CollectionLook(FOLDER_ICONS[glyph], CollectionTint.entries[tint])

    /** The name under the grid: the focused colour's, or the focused glyph's. */
    val focusedName: String get() = if (onTints) CollectionTint.entries[tint].label else glyphName(FOLDER_ICONS[glyph])

    fun step(meaning: Meaning): IconPick {
        val last = FOLDER_ICONS.lastIndex
        when (meaning) {
            Meaning.LeftPanel -> return copy(tint = (tint - 1).coerceAtLeast(0))
            Meaning.RightPanel -> return copy(tint = (tint + 1).coerceAtMost(CollectionTint.entries.lastIndex))
            else -> Unit
        }
        if (onTints) {
            return when (meaning) {
                Meaning.MoveLeft -> copy(tint = (tint - 1).coerceAtLeast(0))
                Meaning.MoveRight -> copy(tint = (tint + 1).coerceAtMost(CollectionTint.entries.lastIndex))
                Meaning.MoveDown -> copy(onTints = false)
                else -> this
            }
        }
        return when (meaning) {
            Meaning.MoveLeft -> copy(glyph = (glyph - 1).coerceAtLeast(0))
            Meaning.MoveRight -> copy(glyph = (glyph + 1).coerceAtMost(last))
            Meaning.MoveUp -> if (glyph < COLUMNS) copy(onTints = true) else copy(glyph = glyph - COLUMNS)
            // A short last row takes the cursor to its end; the last row itself goes nowhere.
            Meaning.MoveDown -> when {
                glyph + COLUMNS <= last -> copy(glyph = glyph + COLUMNS)
                glyph / COLUMNS < last / COLUMNS -> copy(glyph = last)
                else -> this
            }
            else -> this
        }
    }

    companion object {
        /** One column per colour. */
        val COLUMNS: Int = CollectionTint.entries.size

        fun open(folderId: String, look: CollectionLook): IconPick = IconPick(
            folderId = folderId,
            was = look,
            glyph = FOLDER_ICONS.indexOf(look.glyph).coerceAtLeast(0),
            tint = look.tint.ordinal,
        )
    }
}

/**
 * The slot a collection's glyph comes from. It is the glyph's flat symbol, pressed into the folder,
 * unless a pack drew the glyph only as a mark and no pack ahead of Afterglow drew its symbol. That pack
 * predates symbols, so its mark keeps showing on the folder as it is. [candidates] lists a slot's icons,
 * the composition's first choice first.
 */
fun collectionGlyphSlot(glyph: String, candidates: (String) -> List<ThemeIconAsset>): String {
    val symbol = "symbol.$glyph"
    val mark = candidates("mark.$glyph").firstOrNull() ?: return symbol
    val symbols = candidates(symbol)
    // Only the fallback pack draws every symbol, so a lone symbol from another owner is Afterglow's.
    return if (symbols.size <= 1 && symbols.none { it.owner == mark.owner }) "mark.$glyph" else symbol
}

/**
 * The image a collection screens onto its folder, for a glyph from a pack's legacy black-backed
 * `marks/<name>.png`, as a tile does with that mark. Null draws the glyph as it is.
 */
fun <T> screenedInset(glyph: ResolvedThemeIcon<T>?): T? = glyph?.takeIf { it.asset.blend == ThemeIconBlend.Screen }?.image

/** One or two words under the icon grid. */
fun glyphName(mark: String): String = when (mark) {
    MARK_STAR -> "Star"
    "heart" -> "Heart"
    "trophy" -> "Trophy"
    "crown" -> "Crown"
    "gem" -> "Gem"
    "sword" -> "Sword"
    "shield" -> "Shield"
    "skull" -> "Skull"
    "flame" -> "Flame"
    "rocket" -> "Rocket"
    "racing-flag" -> "Flag"
    "puzzle" -> "Puzzle"
    "ghost" -> "Ghost"
    "mushroom" -> "Mushroom"
    "music" -> "Music"
    "two-players" -> "Co-op"
    "hourglass" -> "Backlog"
    "play-badge" -> "Playing"
    "check-badge" -> "Finished"
    MARK_CLOCK -> "Clock"
    "leaf" -> "Cozy"
    "paw" -> "Paw"
    MARK_LIBRARY -> "Library"
    "android-games" -> "Android"
    "android-apps" -> "Apps"
    "pc" -> "PC"
    "moonlight" -> "Moonlight"
    "droiddeck" -> "DroidDeck"
    "emulators" -> "Emulators"
    MARK_CONSOLE -> "Gamepad"
    "nintendo-3ds" -> "3DS"
    "nintendo-ds" -> "DS"
    "game-boy" -> "Game Boy"
    "game-boy-color" -> "GBC"
    "game-boy-advance" -> "GBA"
    "nes" -> "NES"
    "snes" -> "SNES"
    "nintendo-64" -> "N64"
    "gamecube" -> "GameCube"
    "wii" -> "Wii"
    "nintendo-switch" -> "Switch"
    "playstation" -> "PlayStation"
    "playstation-2" -> "PS2"
    "playstation-3" -> "PS3"
    "psp" -> "PSP"
    "genesis" -> "Genesis"
    "sega-cd" -> "Sega CD"
    "master-system" -> "Master System"
    "game-gear" -> "Game Gear"
    "saturn" -> "Saturn"
    "dreamcast" -> "Dreamcast"
    else -> ""
}
