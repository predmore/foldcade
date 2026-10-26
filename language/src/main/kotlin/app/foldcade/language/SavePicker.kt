package app.foldcade.language

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The menu's immutable subject. Effects never look up the currently focused game. */
data class SavePickerTarget(val gameId: String, val libraryId: String, val remoteKey: String, val playerId: String?, val sessionId: Long = 0)

data class SavePickerSave(
    val id: String, val slot: String?, val emulator: String?, val source: String?,
    val updatedAtMillis: Long, val sizeBytes: Long, val playing: Boolean, val screenshotUri: String?,
    val backup: Boolean = false,
    val origin: SavePickerOrigin? = null,
    /** The emulator's own name, "melonDS" for RomM's "melonds". Only shown; [emulator] is what RomM keeps. */
    val emulatorLabel: String? = null,
)

data class SavePickerOrigin(val client: String?, val deviceName: String?, val thisDevice: Boolean)

data class SaveDestination(val slot: String, val emulator: String?, val source: String)

sealed interface SavePickerChoice {
    data class Play(val save: SavePickerSave) : SavePickerChoice
    data class Copy(val save: SavePickerSave, val destination: SaveDestination) : SavePickerChoice
    data object Setup : SavePickerChoice
}

data class SavePickerState(
    val target: SavePickerTarget,
    val gameLabel: String,
    val playerName: String,
    val health: SaveHealth,
    val ownSlots: List<String> = emptyList(),
    val saves: List<SavePickerSave> = emptyList(),
    /** The status chip is 0; save rows start at 1. See [firstIndex]. */
    val index: Int = 0,
    val loading: Boolean = true,
    val error: Boolean = false,
    val busy: Boolean = false,
    val copying: SavePickerSave? = null,
    val destinationIndex: Int = 0,
    val confirmation: SavePickerChoice? = null,
    val confirmIndex: Int = 1,
    val message: String? = null,
    val canPlay: Boolean = true,
    val restartsOpenGame: Boolean = false,
) {
    val focused: SavePickerSave? get() = copying ?: saves.getOrNull(index - 1)

    /**
     * The status chip takes focus only while A does something there: setting up, or
     * syncing held progress. Otherwise the newest save is where the page starts, and
     * the chip is only read.
     */
    val statusActs: Boolean get() = health.needsAttention
    val firstIndex: Int get() = if (statusActs || saves.isEmpty()) 0 else 1

    /** [index] moved onto a row that can hold it, after the saves or the health change. */
    fun settled(): SavePickerState = copy(index = index.coerceIn(firstIndex, saves.size.coerceAtLeast(firstIndex)))
    fun destinations(): List<SaveDestination> {
        val source = copying ?: return emptyList()
        val latest = saves.sortedByDescending { it.updatedAtMillis }.filter { !it.slot.isNullOrBlank() }
            .distinctBy { it.slot }.filter { it.slot != source.slot }
            .map { SaveDestination(it.slot!!, it.emulator, SavePickerCopy.source(it)) }
        return latest + ownSlots.filter { slot -> slot != source.slot && latest.none { it.slot == slot } }
            .map { SaveDestination(it, target.playerId, playerName) }
    }
}

fun applySavePicker(state: SavePickerState, meaning: Meaning): Pair<SavePickerState?, Effect?> {
    if (state.busy) return state to null
    val confirm = state.confirmation
    if (confirm != null) {
        return when (meaning) {
            Meaning.Back -> state.copy(confirmation = null) to null
            Meaning.MoveUp, Meaning.MoveLeft, Meaning.MoveDown, Meaning.MoveRight -> state.copy(confirmIndex = 1 - state.confirmIndex) to null
            Meaning.Activate -> if (state.confirmIndex == 1) state.copy(confirmation = null) to null else when (confirm) {
                SavePickerChoice.Setup -> state.copy(confirmation = null) to Effect.FixSavePicker(state.target)
                is SavePickerChoice.Play -> if (!state.canPlay) state.copy(confirmation = null, message = SavePickerCopy.installFirst) to null
                    else state.copy(confirmation = null, busy = true, message = null) to Effect.PlayPickedSave(state.target, confirm.save.id)
                is SavePickerChoice.Copy -> state.copy(confirmation = null, busy = true, message = null) to Effect.CopyPickedSave(state.target, confirm.save.id, confirm.destination)
            }
            else -> state to null
        }
    }
    val copying = state.copying
    if (copying != null) {
        val targets = state.destinations()
        return when (meaning) {
            Meaning.Back -> state.copy(copying = null) to null
            Meaning.MoveUp -> state.copy(destinationIndex = (state.destinationIndex - 1).coerceAtLeast(0)) to null
            Meaning.MoveDown -> state.copy(destinationIndex = (state.destinationIndex + 1).coerceAtMost((targets.size - 1).coerceAtLeast(0))) to null
            Meaning.Activate -> state.copy(confirmation = targets.getOrNull(state.destinationIndex)?.let { SavePickerChoice.Copy(copying, it) }, confirmIndex = 1) to null
            else -> state to null
        }
    }
    if (meaning == Meaning.Back) return null to null
    if (state.loading) return state to null
    if (state.error) return if (meaning == Meaning.Activate) state.copy(loading = true, error = false, message = null) to Effect.LoadSavePicker(state.target) else state to null
    return when (meaning) {
        Meaning.MoveUp -> state.copy(index = (state.index - 1).coerceAtLeast(state.firstIndex), message = null) to null
        Meaning.MoveDown -> state.copy(index = (state.index + 1).coerceAtMost(state.saves.size), message = null) to null
        Meaning.Activate -> if (state.index == 0) when (state.health) {
            is SaveHealth.NotSyncing -> state to Effect.FixSavePicker(state.target)
            SaveHealth.Waiting -> state.copy(busy = true, message = null) to Effect.SyncSavePicker(state.target)
            else -> state to null
        } else if (!state.canPlay) state.copy(message = SavePickerCopy.installFirst) to null else when (state.health) {
            is SaveHealth.NotSyncing -> state.copy(confirmation = SavePickerChoice.Setup, confirmIndex = 1) to null
            SaveHealth.DeviceOnly -> state.copy(message = SavePickerCopy.unsupported) to null
            else -> state.copy(confirmation = state.focused?.let(SavePickerChoice::Play), confirmIndex = 1) to null
        }
        Meaning.Options -> state.copy(copying = state.focused, destinationIndex = 0) to null
        else -> state to null
    }
}

/** All screen, confirmation and failure copy stays with the shared UI language. */
object SavePickerCopy {
    const val title = "Saves"
    const val cancel = "Cancel"
    const val playing = "Playing"
    const val empty = "No saves on RomM yet"
    const val error = "Can't reach RomM"
    const val retry = "Try again"
    const val loading = "Loading saves…"
    const val working = "Keeping your saves…"
    /** A's word on a save and on the confirmation's button. */
    const val play = "Play"
    /** Y's word on a save, and the page's title while a slot is chosen. */
    const val copy = "Copy"
    const val copyTo = "Copy to"
    const val syncNow = "Sync now"
    const val copied = "Save copied"
    const val synced = "Saves synced"
    const val noDestinations = "No other slots yet"
    const val unknown = "Unknown"
    const val thisDevice = "This device"
    const val noSlot = "No slot"
    const val backup = "Backup"
    const val downloadFirst = "Download the game first."
    const val restartOpen = "The open game restarts. Anything not saved is lost."
    const val unavailable = "Connect to RomM first. Your save on this device stays as it is."
    const val failed = "The choice could not finish. Both saves are kept. Try again."
    const val unreadable = "Your save on this device couldn't be read. Check the save folder and try again."
    const val returnHome = "Return to Home before choosing a save."
    const val installFirst = "Install an emulator to play this save."
    const val unsupported = "This emulator can't load saves through Foldcade."
    private fun fallbackSource(save: SavePickerSave): String =
        named(save.source, save) ?: named(save.emulator, save) ?: unknown

    /** [raw] as players read it: an emulator id wears the emulator's name. */
    private fun named(raw: String?, save: SavePickerSave): String? {
        val text = raw?.takeIf { it.isNotBlank() } ?: return null
        val id = (save.emulator ?: save.source)?.substringBefore('.')
        return save.emulatorLabel?.takeIf { id != null && text.substringBefore('.').equals(id, ignoreCase = true) } ?: text
    }

    /** RomM can hold a device as "AYN AYN Thor", maker and model both naming the maker. */
    fun deviceName(name: String?): String? {
        val words = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: return null
        return words.filterIndexed { i, word -> i == 0 || !word.equals(words[i - 1], ignoreCase = true) }
            .joinToString(" ").takeIf { it.isNotEmpty() }
    }
    fun source(save: SavePickerSave): String {
        val origin = save.origin ?: return fallbackSource(save)
        return when {
            origin.thisDevice -> thisDevice
            origin.client?.equals("foldcade", ignoreCase = true) == true -> deviceName(origin.deviceName) ?: fallbackSource(save)
            else -> clientName(origin.client) ?: fallbackSource(save)
        }
    }
    /** The detail page and confirmation keep the client, device and emulator together. */
    fun sourceDetail(save: SavePickerSave): String {
        val client = clientName(save.origin?.client)
        val device = deviceName(save.origin?.deviceName)
        val from = if (client != null && device != null) "$client on $device" else client ?: device ?: fallbackSource(save)
        return listOfNotNull(from, named(save.emulator, save)?.takeIf { !it.equals(from, ignoreCase = true) }).joinToString(" · ")
    }
    private fun clientName(client: String?): String? = when (val name = client?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }) {
        null -> null
        "argosy", "argosy-launcher" -> "Argosy"
        "grout" -> "Grout"
        "web" -> "RomM web"
        "foldcade" -> "Foldcade"
        "retroarch" -> "RetroArch"
        else -> name.split(Regex("[\\s_-]+")).joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
    }
    fun slot(save: SavePickerSave): String = if (save.backup) backup else save.slot?.takeIf { it.isNotBlank() } ?: noSlot
    fun row(save: SavePickerSave): String = "${source(save)} · ${slot(save)}"
    fun destination(destination: SaveDestination): String = "${destination.source} · ${destination.slot}"
    /** Under the game's name on the top screen, once the saves are known. */
    fun count(saves: Int): String = when (saves) {
        0 -> empty
        1 -> "1 save on RomM"
        else -> "$saves saves on RomM"
    }
    fun header(health: SaveHealth): String = when (health) {
        SaveHealth.Fine -> "Syncing with RomM"
        is SaveHealth.NotSyncing -> "Not syncing · Set up"
        SaveHealth.Waiting -> "Waiting to upload"
        SaveHealth.DeviceOnly -> Copy.savesDeviceOnly
    }
    fun explanation(health: SaveHealth): String = when (health) {
        SaveHealth.Fine -> "Foldcade keeps this game's saves on RomM."
        is SaveHealth.NotSyncing -> "Set up the emulator to keep this game's saves on RomM."
        SaveHealth.Waiting -> "Progress on this device is waiting to reach RomM."
        SaveHealth.DeviceOnly -> "This emulator keeps its saves on this device."
    }
    fun confirmationTitle(choice: SavePickerChoice, player: String): String = when (choice) {
        is SavePickerChoice.Play -> "Play this save?"
        is SavePickerChoice.Copy -> "Copy to ${choice.destination.slot}?"
        SavePickerChoice.Setup -> "Set up $player first"
    }
    fun confirmationBody(choice: SavePickerChoice, playerId: String?, player: String, style: DateStyle, restartsOpenGame: Boolean = false, ownSlots: List<String> = emptyList()): String = when (choice) {
        is SavePickerChoice.Play -> "From ${sourceDetail(choice.save)}, ${formatSaveWhen(choice.save.updatedAtMillis, style)}. Your current save stays on RomM." +
            (if (emulatorMismatch(choice.save.emulator, playerId)) " Made by ${named(choice.save.emulator, choice.save)}. It may not load in $player." else "") +
            (if (restartsOpenGame) " $restartOpen" else "")
        is SavePickerChoice.Copy -> "The save there now stays on RomM." +
            (if (restartsOpenGame && choice.destination.slot in ownSlots) " $restartOpen" else "")
        SavePickerChoice.Setup -> "Set up save syncing before playing a save from RomM."
    }
}

fun emulatorMismatch(emulator: String?, player: String?): Boolean = !emulator.isNullOrBlank() && !player.isNullOrBlank() &&
    emulator.substringBefore('.').lowercase() != player.substringBefore('.').lowercase()

/**
 * A save row's time, short enough to sit beside its slot: "Just now", "5m ago" and
 * "2h ago" today, then "Yesterday", then the date, with the year once it is not this
 * one. The top screen shows the focused save's full date and time.
 */
fun relativeSaveWhen(millis: Long, now: Long, style: DateStyle, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = at.toLocalDate()
    if (day == today || day.isAfter(today)) {
        val seconds = ((now - millis).coerceAtLeast(0) / 1000)
        return when {
            seconds < 60 -> "Just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3600}h ago"
        }
    }
    return when {
        day == today.minusDays(1) -> "Yesterday"
        day.year == today.year -> style.monthDay(at)
        else -> style.monthDayYear(at)
    }
}

/**
 * The keys under the Saves page, for what has focus now. B is the header's own glyph,
 * as on Settings and Play activity, so it is not named again here. Nothing while the
 * page loads or works, and nothing under a confirmation, which shows its own buttons.
 */
fun savePickerHints(state: SavePickerState, faces: FaceMap): List<HintKey> {
    if (state.loading || state.busy || state.confirmation != null) return emptyList()
    val confirm = promptKeyOf(faces.confirmKey) ?: PromptKey.FaceA
    if (state.error) return listOf(HintKey(confirm, SavePickerCopy.retry))
    if (state.copying != null) return if (state.destinations().isEmpty()) emptyList() else listOf(HintKey(confirm, SavePickerCopy.copy))
    if (state.focused != null) return listOf(HintKey(confirm, SavePickerCopy.play), HintKey(actionFaces(faces).optionsGlyph, SavePickerCopy.copy))
    return when (state.health) {
        is SaveHealth.NotSyncing -> listOf(HintKey(confirm, Copy.setUp))
        SaveHealth.Waiting -> listOf(HintKey(confirm, SavePickerCopy.syncNow))
        else -> emptyList()
    }
}
