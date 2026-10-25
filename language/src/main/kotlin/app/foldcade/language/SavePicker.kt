package app.foldcade.language

import java.time.Instant
import java.time.ZoneId

/** The menu's immutable subject. Effects never look up the currently focused game. */
data class SavePickerTarget(val gameId: String, val libraryId: String, val remoteKey: String, val playerId: String?, val sessionId: Long = 0)

data class SavePickerSave(
    val id: String, val slot: String?, val emulator: String?, val source: String?,
    val updatedAtMillis: Long, val sizeBytes: Long, val playing: Boolean, val screenshotUri: String?,
    val backup: Boolean = false,
)

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
    /** Header is 0; save rows start at 1. */
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
        Meaning.MoveUp -> state.copy(index = (state.index - 1).coerceAtLeast(0), message = null) to null
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
    const val choose = "Choose"
    const val cancel = "Cancel"
    const val playing = "Playing"
    const val empty = "No saves on RomM yet"
    const val error = "Can't reach RomM"
    const val retry = "Try again"
    const val loading = "Loading saves…"
    const val working = "Keeping your saves…"
    const val play = "Play this save"
    const val copy = "Copy to…"
    const val copyAction = "Copy save"
    const val copied = "Save copied"
    const val synced = "Saves synced"
    const val noDestinations = "No other slots yet"
    const val unknown = "Unknown"
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
    const val whenLabel = "When"
    const val fromLabel = "From"
    const val slotLabel = "Slot"
    const val sizeLabel = "Size"
    fun source(save: SavePickerSave): String = save.source?.takeIf { it.isNotBlank() } ?: save.emulator?.takeIf { it.isNotBlank() } ?: unknown
    fun slot(save: SavePickerSave): String = if (save.backup) backup else save.slot?.takeIf { it.isNotBlank() } ?: noSlot
    fun row(save: SavePickerSave): String = "${source(save)} · ${slot(save)}"
    fun destination(destination: SaveDestination): String = "${destination.source} · ${destination.slot}"
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
        is SavePickerChoice.Play -> "From ${source(choice.save)}, ${formatSaveWhen(choice.save.updatedAtMillis, style)}. Your current save stays on RomM." +
            (if (emulatorMismatch(choice.save.emulator, playerId)) " Made by ${choice.save.emulator}. It may not load in $player." else "") +
            (if (restartsOpenGame) " $restartOpen" else "")
        is SavePickerChoice.Copy -> "The save there now stays on RomM." +
            (if (restartsOpenGame && choice.destination.slot in ownSlots) " $restartOpen" else "")
        SavePickerChoice.Setup -> "Set up save syncing before playing a save from RomM."
    }
}

fun emulatorMismatch(emulator: String?, player: String?): Boolean = !emulator.isNullOrBlank() && !player.isNullOrBlank() &&
    emulator.substringBefore('.').lowercase() != player.substringBefore('.').lowercase()

fun relativeSaveWhen(millis: Long, now: Long, style: DateStyle, zone: ZoneId = ZoneId.systemDefault()): String {
    val seconds = ((now - millis).coerceAtLeast(0) / 1000)
    return when {
        seconds < 60 -> "Just now"
        seconds < 3600 -> "${seconds / 60} min ago"
        seconds < 86_400 -> "${seconds / 3600} h ago"
        seconds < 604_800 -> "${seconds / 86_400} d ago"
        else -> style.monthDay(Instant.ofEpochMilli(millis).atZone(zone))
    }
}
