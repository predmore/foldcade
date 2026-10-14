package app.foldcade.language

object SelectionCopy {
    const val select = "Select"
    const val check = "Check"
    const val actions = "Actions"
    const val done = "Done"
    fun count(count: Int): String = "$count selected"
}

/** Checks belong to ids in one view, never to cells that a catalog report can replace. */
data class HomeSelection(val view: String, val checked: Set<String> = emptySet(), val refusals: Int = 0, val refusedAt: Int = -1) {
    fun toggle(id: String?, at: Int): HomeSelection = if (id == null) copy(refusals = refusals + 1, refusedAt = at)
        else copy(checked = if (id in checked) checked - id else checked + id)
    fun ordered(slots: List<String?>): List<String> = slots.filterNotNull().filter { it in checked }.distinct()
    fun inView(next: String): HomeSelection? = takeIf { view == next }
}

enum class BulkAction { Collection, NewCollection, Pin, Favorite, Hide }

fun bulkCollectionChoices(board: HomeBoard, ids: List<String>): List<CollectionChoice> =
    if (ids.isEmpty()) emptyList() else collectionChoices(board, ids.first()).map { choice ->
        val count = ids.count { it in board.folders[choice.folderId]?.slots.orEmpty() }
        choice.copy(member = count == ids.size, some = count > 0 && count < ids.size)
    }

/** All means remove all; a partial or empty check means add the missing members. */
fun bulkCollection(board: HomeBoard, collectionId: String, ids: List<String>): HomeBoard {
    if (ids.isEmpty()) return board
    val remove = ids.all { it in board.folders[collectionId]?.slots.orEmpty() }
    return ids.distinct().fold(board) { next, id ->
        val member = id in next.folders[collectionId]?.slots.orEmpty()
        if (member == remove) toggleCollection(next, collectionId, id) else next
    }
}

fun bulkHide(board: HomeBoard, ids: List<String>): HomeBoard = ids.distinct().fold(board, ::hideGame)

/** Preserve a displaced pin while placing the collection at the first checked root slot. */
fun bulkNewCollection(board: HomeBoard, collectionId: String, name: String, ids: List<String>): HomeBoard {
    if (ids.isEmpty()) return board
    val members = ids.distinct().filter { it != HOME_ALL && it !in board.folders }
    if (members.isEmpty()) return board
    val rootAt = if (!board.allOpen && board.openFolderId == null) board.slots.indexOfFirst { it in members }.takeIf { it >= 0 } else null
    val freed = if (rootAt == null) board else board.copy(slots = board.slots.mapIndexed { at, id -> if (at == rootAt) null else id })
    var made = newCollection(freed, collectionId, name, at = rootAt)
    if (collectionId !in made.folders) return board
    for (id in members) made = addToCollection(made, collectionId, id)
    if (rootAt != null) made = pinToHome(made, board.slots[rootAt]!!)
    return made
}
