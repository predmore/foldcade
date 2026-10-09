package app.foldcade

data class ShelfGame(
    val id: String,
    val title: String,
    val shortText: String,
    val platformId: String? = null,
    val occupiesBothDisplays: Boolean = false,
    /** Key into the original line glyphs. Not a console logo. */
    val mark: String? = null,
)

object Shelf {
    val games: List<ShelfGame> = listOf(
        ShelfGame("shelf.clamshell", "Clamshell", "Dual screen", mark = "clamshell"),
        ShelfGame("shelf.slim", "Slim Dual", "Side by side", mark = "slim"),
        ShelfGame("shelf.handheld", "Handheld", "Landscape", mark = "handheld"),
        ShelfGame("shelf.cartridge", "Cartridge", "Solid media", mark = "cartridge"),
        ShelfGame("shelf.disc", "Disc", "Optical", mark = "disc"),
        ShelfGame("shelf.cloud", "Cloud", "Stream", mark = "cloud"),
    )
}
