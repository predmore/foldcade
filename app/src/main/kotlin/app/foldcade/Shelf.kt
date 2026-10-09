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
        ShelfGame(id = "stand-in.three", title = "Three", shortText = "Stand-in"),
        ShelfGame(id = "stand-in.four", title = "Four", shortText = "Stand-in"),
        ShelfGame(id = "stand-in.five", title = "Five", shortText = "Stand-in"),
        ShelfGame(id = "stand-in.six", title = "Six", shortText = "Stand-in"),
        ShelfGame(id = "stand-in.seven", title = "Seven", shortText = "Stand-in"),
        ShelfGame(id = "stand-in.eight", title = "Eight", shortText = "Stand-in"),
        ShelfGame(id = "stand-in.nine", title = "Nine", shortText = "Stand-in"),
        ShelfGame(id = "stand-in.ten", title = "Ten", shortText = "Stand-in"),
        ShelfGame(id = "stand-in.eleven", title = "Eleven", shortText = "Stand-in"),
        ShelfGame(id = "stand-in.twelve", title = "Twelve", shortText = "Stand-in"),
    )
}
