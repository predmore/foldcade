package app.foldcade

import java.util.Locale

data class ShelfGame(
    val id: String,
    val title: String,
    val shortText: String,
    val platformId: String? = null,
    val occupiesBothDisplays: Boolean = false,
    /** Key into the selected theme's original marks. Not a console logo. */
    val mark: String? = null,
)

object Shelf {
    private val marks = listOf("dual", "pocket", "desk", "beam")

    val games: List<ShelfGame> = listOf(
        "One", "Two", "Three", "Four", "Five", "Six",
        "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve",
    ).mapIndexed { index, title ->
        ShelfGame(
            id = "stand-in.${title.lowercase(Locale.ROOT)}",
            title = title,
            shortText = "Stand-in",
            mark = marks[index % marks.size],
        )
    }
}
