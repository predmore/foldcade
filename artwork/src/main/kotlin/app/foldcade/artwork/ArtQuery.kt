package app.foldcade.artwork

/**
 * What the shell knows about one tile when it asks for art.
 *
 * [key] is the tile's home id. Results are remembered under it.
 * [platformId] is a canonical platform id, such as `nintendo-3ds`.
 * [fileName] is a ROM's whole file name, tags and extension included.
 * [steamAppId] is set for a Steam game.
 */
data class ArtQuery(
    val key: String,
    val title: String,
    val platformId: String? = null,
    val fileName: String? = null,
    val steamAppId: Int? = null,
)

/**
 * Image addresses one source found for a game. [cover] is box art or a
 * portrait grid. [square] is square art for a grid tile, when the source has it.
 * [background] is wide art for behind the hero.
 */
data class ArtSet(
    val source: String,
    val cover: String,
    val square: String? = null,
    val background: String? = null,
)

/**
 * What the top screen draws behind and around a focused game.
 *
 * [background] is wide art that can fill the screen, such as a Steam library
 * hero. [logo] is the game's logo on a clear background. [screen] is a real
 * title screen or snapshot, often small: it is drawn crisp, not filled.
 */
data class ArtScene(
    val background: String? = null,
    val logo: String? = null,
    val screen: String? = null,
) {
    val isEmpty: Boolean get() = background == null && logo == null && screen == null

    /** Wide art and a logo are the whole scene. A title screen is only drawn without them. */
    val complete: Boolean get() = background != null && logo != null

    /** Each part from this scene, or from [other] where this one has none. */
    fun or(other: ArtScene?): ArtScene = if (other == null) {
        this
    } else {
        ArtScene(background ?: other.background, logo ?: other.logo, screen ?: other.screen)
    }
}

/** One place art comes from. Sources run in order, and the first with a cover wins. */
interface ArtSource {
    val id: String

    /** True when this source can look [query] up at all. Nothing is sent. */
    fun handles(query: ArtQuery): Boolean

    /**
     * The art for [query], or null when this source has none.
     * A source that cannot be reached throws, so the miss is not remembered.
     */
    suspend fun find(query: ArtQuery): ArtSet?

    /**
     * The scene for [query]: wide art, a logo, or a title screen. Only the
     * focused game is asked, so a grid of tiles costs nothing here. Null when
     * this source has none. Throws when it cannot be reached.
     */
    suspend fun scene(query: ArtQuery): ArtScene? = null
}
