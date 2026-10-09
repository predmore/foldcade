package app.foldcade

data class ShelfGame(
    val id: String,
    val title: String,
    val shortText: String,
    val platformId: String? = null,
    val occupiesBothDisplays: Boolean = false,
)

object Shelf {
    val games: List<ShelfGame> = listOf(
        ShelfGame(id = "stand-in.one", title = "One", shortText = "Stand-in"),
        ShelfGame(id = "stand-in.two", title = "Two", shortText = "Stand-in"),
    )
}
