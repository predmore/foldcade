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
}
