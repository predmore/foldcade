package app.foldcade.language

/**
 * Set up saves: one page that walks every emulator Settings › Emulators shows, one tile each,
 * and does each one's next step in place. The bottom screen is the strip of tiles; the top
 * shows the focused one large with that step. Nothing here is a stored wizard: every step
 * is read from the model as it is now, so a folder picked, a card switched in the emulator,
 * or access given on Android's page shows the moment Foldcade hears of it.
 */
data class SaveSetupPage(
    /** The focused tile's [SetupTile.key]. Null shows the end, All set or Done for now, once nothing needs you. */
    val focus: String? = null,
    /** Tiles skipped for this run alone. Never stored: the next run asks again, and R1 keeps saying so. */
    val skipped: Set<String> = emptySet(),
    /** The Find saves tile stays once offered, so its check can land after Android's page. */
    val offeredFind: Boolean = false,
    /** The focused tile that just started syncing. Its check lands, then the focus glides on. */
    val celebrating: String? = null,
    /** One line about the focused tile's last try, as a folder that was not its own. */
    val note: SetupNote? = null,
    /** Where pages this one opens go, such as an emulator's Settings. */
    val screen: HostScreen = HostScreen.Top,
    /** Grid focus to restore when the page closes. */
    val grid: GridFocus = GridFocus(),
    /** The Settings page that opened this one. Back returns there. */
    val settings: SettingsPage? = null,
    /**
     * The tile the page came back for after Android recreated Home, until it is on the strip, as
     * when the emulators are still loading. It takes the focus then, unless the user has moved.
     */
    val restoring: String? = null,
)

data class SetupNote(val key: String, val text: String)

/** The Find saves tile's key. Emulator tiles use their [EmulatorShelfEntry.key]. */
const val FIND_SAVES_TILE = "foldcade:find-saves"

/** How Android's folder picker meets a player's save folder. */
enum class FolderPick {
    /** No folder is known: the user finds it. */
    Browse,

    /** The picker opens on the folder itself: one found on the device, or the player's own data folder. */
    OpensThere,

    /** The folder is the root of the player's own files, which Android shows alone: only Allow is left. */
    OwnProvider,
}

/** What the user does inside an emulator, for the setup Foldcade cannot do from outside. */
enum class InAppStep {
    /** Point the emulator at the folder Foldcade hosts for its saves. */
    HostedFolder,

    /** Switch the emulator's memory card to one that can sync. */
    MemoryCard,

    /** One-time setup, such as firmware, before a game can start. */
    Setup,

    /** A user profile, which Eden keeps Switch saves under. */
    User,
}

/** One thing a tile needs, in the order the page asks for them. [done] is read live. */
sealed interface SetupStep {
    val done: Boolean

    /** The save folder, through Android's picker. */
    data class Folder(val playerId: String, val pick: FolderPick, val findable: Boolean, override val done: Boolean) : SetupStep

    /** Eden's own files, which hold its saves, keys and firmware. */
    data class EdenFolder(override val done: Boolean) : SetupStep

    /** RetroArch's cores folder, so Foldcade can tell which cores it has. */
    data class Cores(override val done: Boolean) : SetupStep

    /** Done in the emulator. [steps] is the emulator's own how-to. */
    data class InApp(val playerId: String, val kind: InAppStep, val steps: String?, override val done: Boolean) : SetupStep

    /** Android's all-files access, so Foldcade finds save folders itself. Optional. */
    data class AllFiles(override val done: Boolean) : SetupStep
}

/** One tile on the strip: an emulator, or Find saves. [packageName] is the installed app whose art it wears. */
data class SetupTile(
    val key: String,
    val label: String,
    val packageName: String?,
    val steps: List<SetupStep>,
    /** Where Foldcade found this emulator's folder on its own, shown once it syncs. */
    val foundIn: String? = null,
    /** On Find saves: the emulators whose folders Foldcade found on its own, A to Z. */
    val found: List<String> = emptyList(),
) {
    /** The step to do now, or null once everything is done. */
    val step: SetupStep? get() = steps.firstOrNull { !it.done }
    val done: Boolean get() = step == null
    val findSaves: Boolean get() = key == FIND_SAVES_TILE
}

enum class SetupStatus {
    Syncing,
    NeedsYou,
    Skipped,
}

fun setupStatus(tile: SetupTile, page: SaveSetupPage): SetupStatus = when {
    tile.done -> SetupStatus.Syncing
    tile.key in page.skipped -> SetupStatus.Skipped
    else -> SetupStatus.NeedsYou
}

private fun SetupTile.needsYou(page: SaveSetupPage): Boolean = setupStatus(this, page) == SetupStatus.NeedsYou

/**
 * One emulator's steps, from the rows its Settings page lists, each done exactly when that
 * row stops reading Not syncing ([rowNeedsYou]). A player whose saves live in the folder
 * Foldcade hosts has no folder of its own to pick.
 */
fun setupSteps(entry: EmulatorShelfEntry, model: PickerModel): List<SetupStep> = entry.rows.mapNotNull { row ->
    val done = !rowNeedsYou(row, model)
    val setting = { id: String -> model.playerSaves.firstOrNull { it.playerId == id } }
    when (row) {
        is Row.PlayerSave -> setting(row.playerId)?.takeIf { it.hostedTitle == null }?.let {
            SetupStep.Folder(row.playerId, it.pick, it.findable, done)
        }
        is Row.HostedSaves -> SetupStep.InApp(row.playerId, InAppStep.HostedFolder, setting(row.playerId)?.hostedSteps, done)
        is Row.SaveSync -> SetupStep.InApp(row.playerId, InAppStep.MemoryCard, setting(row.playerId)?.saveSyncSteps, done)
        is Row.PlayerSetup -> SetupStep.InApp(row.playerId, InAppStep.Setup, setting(row.playerId)?.setupSteps, done)
        Row.RetroArchCores -> SetupStep.Cores(done)
        Row.EdenFolder -> SetupStep.EdenFolder(done)
        Row.EdenUser -> SetupStep.InApp(EDEN_SHELF_KEY, InAppStep.User, model.eden?.setupSteps, done)
        else -> null
    }
}

/**
 * The strip: Find saves first while all-files access is off and could find a folder someone
 * still needs (and, once offered, for the rest of the run), then every emulator on Settings ›
 * Emulators, A to Z.
 */
fun saveSetupTiles(model: PickerModel, page: SaveSetupPage): List<SetupTile> {
    val emulators = emulatorShelf(model).map { entry ->
        SetupTile(entry.key, entry.label, entry.packageName, setupSteps(entry, model), model.playerSaves.firstOrNull { it.playerId == entry.key }?.foundIn)
    }
    val helps = emulators.any { tile -> tile.steps.any { it is SetupStep.Folder && it.findable && !it.done } }
    val find = (!model.allFilesGranted && helps) || (model.allFilesGranted && page.offeredFind)
    if (!find) return emulators
    // A folder found counts even while its emulator has another step, as RetroArch's cores.
    val found = emulators.filter { it.foundIn != null }.map { it.label }
    return listOf(SetupTile(FIND_SAVES_TILE, SettingsCopy.findSaves, null, listOf(SetupStep.AllFiles(model.allFilesGranted)), found = found)) + emulators
}

/**
 * True once there are tiles and none still needs you: the page ends. Skipped ones count as
 * answered for this run, so the end says All set only when [saveSetupSkipped] is 0.
 */
fun saveSetupFinished(tiles: List<SetupTile>, page: SaveSetupPage): Boolean = tiles.isNotEmpty() && tiles.none { it.needsYou(page) }

/** Emulators still waiting on you, for the head's count. Find saves is a shortcut, never a save that fails. */
fun saveSetupLeft(tiles: List<SetupTile>, page: SaveSetupPage): Int = tiles.count { !it.findSaves && it.needsYou(page) }

/** Emulators skipped this run, whose saves still do not sync. A skipped Find saves is not one. */
fun saveSetupSkipped(tiles: List<SetupTile>, page: SaveSetupPage): Int =
    tiles.count { !it.findSaves && setupStatus(it, page) == SetupStatus.Skipped }

/**
 * How long [tile]'s fresh check holds the focus. On Find saves it waits one stagger more for each
 * folder it found, so their checks are seen landing one after another before the focus moves.
 */
fun saveSetupPause(tile: SetupTile?, motion: MotionStyle): Int =
    motion.durationSetupPause + if (tile?.findSaves == true) motion.durationSetupStagger * tile.found.size else 0

/** The next tile after [after] that needs you, wrapping round to the ones before it, or null. */
private fun nextNeeding(tiles: List<SetupTile>, page: SaveSetupPage, after: String?): String? {
    val start = tiles.indexOfFirst { it.key == after }
    return (tiles.drop(start + 1) + tiles.take(start + 1)).firstOrNull { it.needsYou(page) }?.key
}

/**
 * Opens Set up saves on [target]'s tile, or on the first that needs you, or on All set.
 * [settings] is the page to return to. A target not on the strip yet is kept while there are
 * no tiles at all, as when the page comes back before the emulators have loaded.
 */
fun PickerModel.openSaveSetup(
    screen: HostScreen,
    target: String? = null,
    settings: SettingsPage? = null,
    grid: GridFocus = settings?.grid ?: focus,
): PickerModel {
    val fresh = SaveSetupPage(screen = screen, grid = grid, settings = settings)
    val tiles = saveSetupTiles(this, fresh)
    val shown = target?.takeIf { key -> tiles.isEmpty() || tiles.any { it.key == key } }
    val focus = shown ?: nextNeeding(tiles, fresh, null)
    return copy(
        panel = null,
        settings = null,
        playActivity = null,
        saveSetup = fresh.copy(focus = focus, offeredFind = tiles.any { it.findSaves }, restoring = target?.takeIf { key -> tiles.none { it.key == key } }),
    )
}

/** True when at least one emulator still needs a step, the moment a RomM sign-in is worth following with this page. */
fun saveSetupWanted(model: PickerModel): Boolean =
    saveSetupTiles(model, SaveSetupPage()).any { !it.findSaves && !it.done }

/**
 * Left and right move along the strip; up and down do nothing. Confirm does the focused
 * tile's step, or opens its Settings page once it syncs, or closes on All set. The bottom
 * button skips the tile for this run and glides to the next that needs you, as it dismisses
 * everywhere else. Back closes, to the Settings page that opened it if one did. A held key,
 * as the A that opened the page from R1, or one still down as Android's picker closes, acts
 * once: only the D-pad repeats.
 */
fun applySaveSetup(model: PickerModel, page: SaveSetupPage, meaning: Meaning, repeatCount: Int = 0): Pair<PickerModel, Effect?> {
    if (repeatCount > 0 && meaning != Meaning.MoveLeft && meaning != Meaning.MoveRight) return model to null
    val tiles = saveSetupTiles(model, page)
    val index = tiles.indexOfFirst { it.key == page.focus }
    val tile = tiles.getOrNull(index)
    val moved = { key: String? -> model.copy(saveSetup = page.copy(focus = key, celebrating = null, note = null, restoring = null)) to null }
    return when (meaning) {
        Meaning.MoveLeft -> when {
            tiles.isEmpty() -> model to null
            index < 0 -> moved(tiles.last().key)
            else -> moved(tiles.getOrNull(index - 1)?.key ?: page.focus)
        }
        Meaning.MoveRight -> when {
            tiles.isEmpty() -> model to null
            index < 0 -> moved(tiles.first().key)
            else -> moved(tiles.getOrNull(index + 1)?.key ?: page.focus)
        }
        Meaning.Back -> closeSaveSetup(model, page) to null
        Meaning.Activate -> when {
            tile == null -> if (saveSetupFinished(tiles, page)) closeSaveSetup(model, page) to null else model to null
            // A press while a fresh check lands goes on at once rather than into its Settings,
            // once Foldcade has read what the new folder still needs.
            page.celebrating == tile.key -> model.advanceSaveSetup(tile.key) to null
            else -> when (val step = tile.step) {
                null -> openTileSettings(model, page, tile) to null
                else -> model.copy(saveSetup = page.copy(skipped = page.skipped - tile.key, note = null)) to stepEffect(step)
            }
        }
        Meaning.ActivateBottom, Meaning.Dismiss -> if (tile != null && tile.needsYou(page)) {
            val skipped = page.copy(skipped = page.skipped + tile.key, celebrating = null, note = null)
            model.copy(saveSetup = skipped.copy(focus = nextNeeding(tiles, skipped, tile.key))) to null
        } else model to null
        else -> model to null
    }
}

private fun stepEffect(step: SetupStep): Effect = when (step) {
    is SetupStep.Folder -> Effect.ChoosePlayerSave(step.playerId)
    is SetupStep.EdenFolder -> Effect.ShowEdenFolder
    is SetupStep.Cores -> Effect.ShowRetroArchCores
    is SetupStep.InApp -> Effect.OpenPlayer(step.playerId)
    is SetupStep.AllFiles -> Effect.OpenAllFilesAccess
}

private fun closeSaveSetup(model: PickerModel, page: SaveSetupPage): PickerModel =
    model.copy(saveSetup = null, settings = page.settings, focus = page.grid)

/** A tile that syncs opens where it is changed by hand: its emulator's page, or Find saves' row. */
private fun openTileSettings(model: PickerModel, page: SaveSetupPage, tile: SetupTile): PickerModel {
    val opened = model.copy(saveSetup = null).openSettings(page.settings?.screen ?: page.screen, SettingsCategory.Players, page.grid)
    val settings = opened.settings ?: return opened
    val shelf = settingsRows(SettingsCategory.Players, opened)
    val shown = if (tile.findSaves) settings.copy(onRows = true, row = shelf.indexOf(Row.FindSaves).coerceAtLeast(0))
        else settings.copy(onRows = true, inner = SettingsInner.Emulator(tile.key), row = 0)
    return opened.copy(settings = shown)
}

/**
 * The focus glides on from [key], whose check has landed, to the next tile that needs you, or to
 * All set. A tile that has needed another step since, as a memory card read once its folder is
 * picked, keeps the focus.
 */
fun PickerModel.advanceSaveSetup(key: String): PickerModel {
    // A step read from the new folder, as aPS3e's firmware, may be on its way: the tile keeps the focus until it is read.
    if (checkingSaves) return this
    val page = saveSetup?.takeIf { it.celebrating == key } ?: return this
    val tiles = saveSetupTiles(this, page)
    val stays = page.focus != key || tiles.firstOrNull { it.key == key }?.done != true
    return copy(saveSetup = page.copy(celebrating = null, focus = if (stays) page.focus else nextNeeding(tiles, page, key)))
}

/** One line under [key]'s step about its last try. Null when the page is not open on that tile. */
fun PickerModel.noteSaveSetup(key: String, text: String): PickerModel? {
    val page = saveSetup?.takeIf { it.focus == key } ?: return null
    return copy(saveSetup = page.copy(note = SetupNote(key, text)))
}

/**
 * The page as the model under it changes. The focused tile that just started syncing celebrates
 * before the focus moves on ([advanceSaveSetup]). A tile that left, as an emulator uninstalled,
 * hands the focus to the next that needs you. The end gives way when something needs you again,
 * as after all-files access is taken back, so it never claims what is not so. A note leaves with
 * the step it was about.
 */
fun retainSaveSetup(previous: PickerModel, next: PickerModel): PickerModel {
    val page = next.saveSetup ?: return next
    val was = previous.saveSetup
    val before = if (was != null) saveSetupTiles(previous, was) else emptyList()
    val tiles = saveSetupTiles(next, page)
    var settled = page
    if (tiles.any { it.findSaves } && !settled.offeredFind) settled = settled.copy(offeredFind = true)
    // The tile the page came back for is on the strip now. A pick made behind the picker has landed
    // already, so a done tile's check lands here, and the focus glides on as after any pick.
    settled.restoring?.let { key ->
        val tile = tiles.firstOrNull { it.key == key } ?: return@let
        settled = settled.copy(focus = key, restoring = null, celebrating = key.takeIf { tile.done }, note = null)
    }
    val key = settled.focus
    val old = before.firstOrNull { it.key == key }
    val now = tiles.firstOrNull { it.key == key }
    if (key != null && was?.focus == key && old != null && now != null && !old.done && now.done) settled = settled.copy(celebrating = key)
    // A step that shows up only once the one before is done, as ARMSX2's card after its folder, calls the celebration off.
    if (settled.celebrating != null && tiles.firstOrNull { it.key == settled.celebrating }?.done != true) settled = settled.copy(celebrating = null)
    if (key != null && now == null && (old != null || tiles.isNotEmpty())) settled = settled.copy(focus = nextNeeding(tiles, settled, null), celebrating = null)
    if (settled.focus == null && tiles.isNotEmpty() && !saveSetupFinished(tiles, settled)) settled = settled.copy(focus = nextNeeding(tiles, settled, null))
    settled.note?.let { note ->
        val step = { list: List<SetupTile> -> list.firstOrNull { it.key == note.key }?.step }
        if (note.key != settled.focus || (was != null && step(before) != step(tiles))) settled = settled.copy(note = null)
    }
    return if (settled == page) next else next.copy(saveSetup = settled)
}

/** What the top screen's buttons say, with the user's own keys: the step's verb, then Skip while it needs you. */
fun saveSetupActions(model: PickerModel, page: SaveSetupPage): List<HintKey> {
    val confirm = promptKeyOf(model.faceMap.confirmKey) ?: PromptKey.FaceA
    val tiles = saveSetupTiles(model, page)
    val tile = tiles.firstOrNull { it.key == page.focus }
        ?: return if (saveSetupFinished(tiles, page)) listOf(HintKey(confirm, SaveSetupCopy.done)) else emptyList()
    return buildList {
        add(HintKey(confirm, SaveSetupCopy.action(tile)))
        if (tile.needsYou(page)) add(HintKey(actionFaces(model.faceMap).bottomGlyph, SaveSetupCopy.skip))
    }
}

/** One or two words a control, one short sentence a step. Emulators' own how-tos are theirs. */
object SaveSetupCopy {
    const val title = "Set up saves"
    const val syncing = "Syncing"
    const val needsYou = "Needs you"
    const val skipped = "Skipped"
    const val optional = "Optional"
    const val skip = "Skip"
    const val done = "Done"
    const val allSet = "All set"
    const val doneForNow = "Done for now"
    const val pick = "Pick"
    const val allow = "Allow"
    const val pickFolder = "Pick its folder"
    const val opensThere = "Opens right there. Tap Use this folder, then Allow."
    const val asksOnce = "Android asks once"
    const val useThisFolder = "Tap Use this folder, then Allow."
    const val cores = "Show Foldcade its cores"
    const val hostedFolder = "Point Memory Cards at Foldcade"
    const val memoryCard = "Switch to a folder card"
    const val finishSetup = "Finish its setup"
    const val edenUser = "Add a user in Eden"
    const val findOwn = "Foldcade finds the folders"
    const val findOwnBody = "Turn on Allow access, then come back."
    const val looking = "Looking for save folders…"
    const val nothingFound = "Nothing new found."

    /** A folder Foldcade found on its own says where, so the user can see it is the right one. */
    fun foundIn(path: String): String = "Found in $path."

    /** What Find saves found, once allowed: `Found melonDS and RetroArch.` */
    fun found(names: List<String>): String = "Found ${spoken(names, "and")}."

    fun browse(name: String) = "Find the folder $name keeps its saves in."
    fun open(name: String) = "Open $name"
    fun toGo(count: Int) = "$count to go"
    fun skippedCount(count: Int) = "$count skipped"

    /** The head's chip: what is left, then what was skipped, and All set only once every emulator syncs. */
    fun head(left: Int, skipped: Int): String = when {
        left > 0 -> toGo(left)
        skipped > 0 -> skippedCount(skipped)
        else -> allSet
    }

    /** The end's title. Skipped emulators still do not sync, so the page never calls that All set. */
    fun endTitle(skipped: Int): String = if (skipped == 0) allSet else doneForNow

    /** Under Done for now: how many emulators still do not sync. Null when all of them do. */
    fun endBody(skipped: Int): String? = if (skipped == 0) null else "$skipped still not syncing"

    /** A pick that was not [name]'s folder, in one line. */
    fun wrongFolder(name: String, markers: Collection<String>): String =
        if (markers.isEmpty()) "That isn’t $name’s folder. Try another."
        else "Not $name’s folder. Look for one with ${spoken(markers.sorted(), "or")}."

    /** `a`, `a and b`, `a, b and c`. */
    private fun spoken(items: List<String>, last: String): String =
        if (items.size < 2) items.joinToString() else "${items.dropLast(1).joinToString(", ")} $last ${items.last()}"

    fun status(tile: SetupTile, page: SaveSetupPage): String = when (setupStatus(tile, page)) {
        SetupStatus.Syncing -> if (tile.findSaves) SettingsCopy.accessAllowed else syncing
        SetupStatus.NeedsYou -> if (tile.findSaves) optional else needsYou
        SetupStatus.Skipped -> skipped
    }

    /** The step's title. None once the tile syncs: its status chip already says so. */
    fun stepTitle(step: SetupStep?): String? = when (step) {
        null -> null
        is SetupStep.Folder -> if (step.pick == FolderPick.OwnProvider) asksOnce else pickFolder
        is SetupStep.EdenFolder -> asksOnce
        is SetupStep.Cores -> cores
        is SetupStep.InApp -> when (step.kind) {
            InAppStep.HostedFolder -> hostedFolder
            InAppStep.MemoryCard -> memoryCard
            InAppStep.Setup -> finishSetup
            InAppStep.User -> edenUser
        }
        is SetupStep.AllFiles -> findOwn
    }

    /** The step's words. Find saves, once allowed, says what it found, after [checking] is over. */
    fun stepBody(tile: SetupTile, checking: Boolean = false): String? = when (val step = tile.step) {
        null -> when {
            tile.findSaves -> when {
                tile.found.isNotEmpty() -> found(tile.found)
                checking -> looking
                else -> nothingFound
            }
            tile.foundIn != null -> foundIn(tile.foundIn)
            else -> null
        }
        is SetupStep.Folder -> when (step.pick) {
            FolderPick.Browse -> browse(tile.label)
            FolderPick.OpensThere -> opensThere
            FolderPick.OwnProvider -> useThisFolder
        }
        is SetupStep.EdenFolder, is SetupStep.Cores -> useThisFolder
        is SetupStep.InApp -> step.steps
        is SetupStep.AllFiles -> findOwnBody
    }

    /** Confirm's word on the focused tile. */
    fun action(tile: SetupTile): String = when (val step = tile.step) {
        null -> SettingsCopy.title
        is SetupStep.Folder -> if (step.pick == FolderPick.OwnProvider) allow else pick
        is SetupStep.EdenFolder, is SetupStep.Cores, is SetupStep.AllFiles -> allow
        is SetupStep.InApp -> open(tile.label)
    }
}
