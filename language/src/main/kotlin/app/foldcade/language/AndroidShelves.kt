package app.foldcade.language

/**
 * Which installed-app shelf a launchable package is on.
 * Games are the system game category or the legacy game flag.
 * Everything else starts on Apps. A user move overrides that.
 */
enum class AndroidShelf {
    Games,
    Apps,
}

/** The grid the picker is showing. Stand-ins stay the library grid. */
enum class HomeGrid {
    StandIns,
    AndroidGames,
    Apps,
    HiddenApps,
}

fun homeGridLabel(grid: HomeGrid): String? = when (grid) {
    HomeGrid.StandIns -> null
    HomeGrid.AndroidGames -> Copy.androidGames
    HomeGrid.Apps -> Copy.apps
    HomeGrid.HiddenApps -> Copy.hiddenApps
}

/**
 * One launchable package.
 * [systemGame] is true when the system set CATEGORY_GAME or FLAG_IS_GAME.
 * The scanner owns those constants. This type does not.
 */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val systemGame: Boolean,
)

/**
 * What the user has done to one package.
 * A null [shelf] keeps the system shelf.
 * [showDespitePlayer] puts an emulator back on Apps after the default hide.
 */
data class AppShelfRecord(
    val shelf: AndroidShelf? = null,
    val hidden: Boolean = false,
    val favorite: Boolean = false,
    val showDespitePlayer: Boolean = false,
)

data class AppShelfState(
    val records: Map<String, AppShelfRecord> = emptyMap(),
) {
    fun record(packageName: String): AppShelfRecord = records[packageName] ?: AppShelfRecord()

    fun put(packageName: String, record: AppShelfRecord): AppShelfState =
        copy(records = records + (packageName to record))

    fun pin(packageName: String, favorite: Boolean): AppShelfState =
        put(packageName, record(packageName).copy(favorite = favorite))

    fun hide(packageName: String): AppShelfState =
        put(packageName, record(packageName).copy(hidden = true))

    fun show(packageName: String, coveredByPlayer: Boolean): AppShelfState {
        val current = record(packageName)
        return put(
            packageName,
            current.copy(
                hidden = false,
                showDespitePlayer = current.showDespitePlayer || coveredByPlayer,
            ),
        )
    }

    fun move(packageName: String, shelf: AndroidShelf): AppShelfState {
        val current = record(packageName)
        return put(
            packageName,
            current.copy(
                shelf = shelf,
                hidden = false,
                showDespitePlayer = current.showDespitePlayer || shelf == AndroidShelf.Apps,
            ),
        )
    }
}

/** Right-panel actions for the focused installed app. */
data class AppActions(
    val favorite: Boolean,
    val onGamesShelf: Boolean,
    val hiddenShelf: Boolean,
)

fun systemShelf(app: LaunchableApp): AndroidShelf =
    if (app.systemGame) AndroidShelf.Games else AndroidShelf.Apps

fun assignedShelf(app: LaunchableApp, record: AppShelfRecord): AndroidShelf =
    record.shelf ?: systemShelf(app)

/**
 * Emulator packages a player plugin already covers stay off Apps until the user
 * shows them or moves them onto Apps. They are not removed from Android Games.
 */
fun hiddenFromAppsByDefault(
    app: LaunchableApp,
    state: AppShelfState,
    playerPackages: Set<String>,
): Boolean {
    if (app.packageName !in playerPackages) return false
    val record = state.record(app.packageName)
    if (record.hidden || record.showDespitePlayer) return false
    if (record.shelf == AndroidShelf.Apps) return false
    return assignedShelf(app, record) == AndroidShelf.Apps
}

fun appsOn(
    shelf: AndroidShelf,
    apps: List<LaunchableApp>,
    state: AppShelfState,
    playerPackages: Set<String>,
): List<LaunchableApp> {
    return apps.asSequence()
        .filter { app ->
            val record = state.record(app.packageName)
            !record.hidden &&
                assignedShelf(app, record) == shelf &&
                !(shelf == AndroidShelf.Apps && hiddenFromAppsByDefault(app, state, playerPackages))
        }
        .distinctBy { it.packageName }
        .sortedWith(shelfOrder(state))
        .toList()
}

/** User-hidden apps, plus emulators Apps is hiding until the user shows them. */
fun hiddenApps(
    apps: List<LaunchableApp>,
    state: AppShelfState,
    playerPackages: Set<String>,
): List<LaunchableApp> {
    return apps.asSequence()
        .filter { app ->
            state.record(app.packageName).hidden || hiddenFromAppsByDefault(app, state, playerPackages)
        }
        .distinctBy { it.packageName }
        .sortedWith(shelfOrder(state))
        .toList()
}

private fun shelfOrder(state: AppShelfState): Comparator<LaunchableApp> =
    compareByDescending<LaunchableApp> { state.record(it.packageName).favorite }
        .thenBy { it.label.lowercase() }
        .thenBy { it.packageName }
