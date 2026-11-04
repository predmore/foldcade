package app.foldcade.language

/**
 * Copy for the ROM folders Foldcade finds by itself (`ROMs`, `Emulation/roms`, the same on
 * an SD card), and for asking to be Home when Moonlight or DroidDeck would use it.
 */
object FoundFolderCopy {
    /** Settings › Library's switch for all-files access, beside Emulators' Find saves. */
    const val findGames = "Find games"
    const val sdCard = "SD card"

    /** A found folder on a removable card: `SD card · roms`. */
    fun onCard(path: String) = "$sdCard · $path"

    const val offerTitle = "Found your ROMs"

    /**
     * The one-time offer, naming what Foldcade saw without any access: the folders and the
     * systems their folders are named for. "ROMs has Genesis and Nintendo DS games."
     */
    fun offerBody(places: List<String>, systems: List<String>): String {
        val shown = if (systems.size > MAX_SYSTEMS) systems.take(MAX_SYSTEMS - 1) + "more" else systems
        val verb = if (places.size == 1) "has" else "have"
        return "${and(places)} $verb ${and(shown)} games. Allow file access once and they’re on Home."
    }

    /** R1 after a scan: "Found 214 games in ROMs", or "Found 230 games in 2 folders". */
    fun foundTitle(games: Int, places: List<String>): String {
        val count = folderCountLine(games)
        return if (places.size == 1) "Found $count in ${places.single()}" else "Found $count in ${places.size} folders"
    }

    /** Under the title. Several folders are listed, since the title only counts them. */
    fun foundBody(places: List<String>): String =
        if (places.size == 1) "Sorted by system and ready to play." else places.joinToString(", ")

    /** The Home ask, named for the apps that need it: "Moonlight can add games here once Foldcade is Home." */
    fun homeBody(apps: List<String>): String =
        "${and(apps)} can add games here once Foldcade is Home."

    private fun and(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    private const val MAX_SYSTEMS = 3
}
