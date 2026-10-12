package app.foldcade

import app.foldcade.language.Copy

data class ShelfGame(
    val id: String,
    val title: String,
    val shortText: String,
    val platformId: String? = null,
    val occupiesBothDisplays: Boolean = false,
    /** Key into the original line glyphs. Not a console logo. */
    val mark: String? = null,
    /** Content URI the player can open. Null until a library hands one over. */
    val contentUri: String? = null,
    val androidPackage: String? = null,
    val favorite: Boolean = false,
    /** Library that owns this game. Null for a shelf tile that is not from a backend. */
    val libraryId: String? = null,
    /** Opaque key inside [libraryId]. The shell does not parse it. */
    val remoteKey: String? = null,
    /**
     * GameNative is installed and this tile is the empty shelf.
     * It tells the player to add a shortcut. It does not launch a game.
     */
    val emptyShelfHint: Boolean = false,
    val availabilityLabel: String? = null,
)

object Shelf {
    /**
     * Games confirmed from GameNative shortcuts. Empty until that catalog is read.
     * Built-in tiles stay in front of these.
     */
    @Volatile
    var catalog: List<ShelfGame> = emptyList()

    /**
     * Enabled pinned Moonlight games. Empty until the last read or a saved one is loaded.
     * These follow the GameNative catalog.
     */
    @Volatile
    var moonlightGames: List<ShelfGame> = emptyList()

    /**
     * True when `app.gamenative` or `app.gamenative.gold` is installed.
     * With an empty [catalog], the PC tile is an empty-shelf hint.
     */
    @Volatile
    var gameNativeInstalled: Boolean = false

    /**
     * Games DroidDeck pinned to Home or synced into a library folder.
     * Empty until the last read or a saved one is loaded. These follow the Moonlight games.
     */
    @Volatile
    var droidDeckGames: List<ShelfGame> = emptyList()

    /**
     * True when `com.droiddeck.launcher` is installed. With no [droidDeckGames],
     * the DroidDeck tile says how to add one. It still opens DroidDeck.
     */
    @Volatile
    var droidDeckInstalled: Boolean = false

    private val builtIn: List<ShelfGame> = listOf(
        ShelfGame("shelf.clamshell", "Clamshell", "Dual screen", mark = "clamshell"),
        ShelfGame("shelf.slim", "Slim Dual", "Side by side", mark = "slim"),
        ShelfGame("shelf.handheld", "Handheld", "Landscape", mark = "handheld"),
        ShelfGame("shelf.cartridge", "Cartridge", "Solid media", mark = "cartridge"),
        ShelfGame("shelf.disc", "Disc", "Optical", mark = "disc"),
        ShelfGame("shelf.cloud", "Cloud", "Stream", mark = "cloud"),
        ShelfGame(
            id = "pc.gamenative",
            title = "PC",
            shortText = Copy.progressInGameNative,
            platformId = "pc",
            occupiesBothDisplays = false,
        ),
        ShelfGame(
            id = MOONLIGHT_TILE,
            title = "Moonlight",
            shortText = "Stream",
            platformId = "moonlight",
            occupiesBothDisplays = false,
        ),
        // Opens DroidDeck itself. On Home, the installed app in Emulators does that instead.
        ShelfGame(
            id = DROIDDECK_TILE,
            title = "DroidDeck",
            shortText = Copy.steamInDroidDeck,
            platformId = "droiddeck",
            occupiesBothDisplays = false,
            mark = "droiddeck",
        ),
    )

    val games: List<ShelfGame>
        get() = builtIn.map { tile ->
            if (tile.id == PC_TILE && gameNativeInstalled && catalog.isEmpty()) {
                tile.copy(
                    shortText = Copy.addShortcutInGameNative,
                    emptyShelfHint = true,
                )
            } else if (tile.id == DROIDDECK_TILE && droidDeckInstalled && droidDeckGames.isEmpty()) {
                tile.copy(shortText = Copy.addToHomeInDroidDeck)
            } else {
                tile
            }
        } + catalog + moonlightGames + droidDeckGames

    const val PC_TILE: String = "pc.gamenative"
    const val MOONLIGHT_TILE: String = "moonlight"
    const val DROIDDECK_TILE: String = "droiddeck"

    /**
     * Built-in tiles that open their app, not a game. They stay off Home: the
     * installed app is there instead, in Emulators, so library folders hold only games.
     */
    val APP_TILES: Set<String> = setOf(PC_TILE, MOONLIGHT_TILE, DROIDDECK_TILE)

    /** Shelf ids of DroidDeck games start with this, then the game id. */
    const val DROIDDECK_PREFIX: String = "droiddeck."
}
