package app.foldcade.language

/**
 * The grid the picker is showing: the home board, or the one Hidden place. Installed apps
 * have no shelves of their own: they are filed on the board, in Android Games, Android Apps
 * and Emulators, as every other game is in its folder.
 */
enum class HomeGrid {
    StandIns,
    Hidden,
}

fun homeGridLabel(grid: HomeGrid): String? = when (grid) {
    HomeGrid.StandIns -> null
    HomeGrid.Hidden -> Copy.hiddenApps
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

/** Android's own Settings app. Its label, "Settings", would read as Foldcade's. */
const val ANDROID_SETTINGS_PACKAGE = "com.android.settings"

/**
 * An app's name as Foldcade says it: its own [label], except Android's Settings, which is
 * "Android settings" on its tile, beside a game, and in a close warning, as R1 names it.
 */
fun appName(packageName: String?, label: String): String =
    if (packageName == ANDROID_SETTINGS_PACKAGE) Copy.androidSettings else label
