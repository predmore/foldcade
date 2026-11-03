package app.foldcade.language

/** A saved emulator, including a libretro core when one was chosen. Labels survive uninstall. */
data class PlayerChoice(val playerId: String, val core: String? = null, val label: String)

fun PlayerChoice.samePlayer(other: PlayerChoice): Boolean = playerId == other.playerId && core == other.core

/** The shell supplies installed options and the result of following the parent setting. */
data class PlayerChoiceRow(
    val options: List<PlayerChoice>,
    val selected: PlayerChoice? = null,
    val inherited: String? = null,
    val system: Boolean = false,
    val selectedUsable: Boolean = selected == null || options.any { it.samePlayer(selected) },
) {
    val visible: Boolean get() = options.size >= 2 || selected != null
    val missing: Boolean get() = selected != null && !selectedUsable
    val values: List<PlayerChoice?> get() = listOf(null) + options + listOfNotNull(selected?.takeIf { choice -> options.none { it.samePlayer(choice) } })

    fun value(): String = selected?.let { choice ->
        options.firstOrNull { it.samePlayer(choice) }?.label ?: if (missing) PlayerChoiceCopy.notInstalled(choice.label) else choice.label
    } ?: PlayerChoiceCopy.inherited(system, inherited)

    /**
     * The emulator alone, for a row too narrow for "Default · X" or "Auto · X": which emulator
     * plays matters more than that it was inherited. Null when [value] is already just a name.
     */
    fun shortValue(): String? = if (selected == null) inherited else null

    fun step(direction: Int): PlayerChoice? {
        val values = values
        val index = values.indexOfFirst { value ->
            if (selected == null) value == null else value?.samePlayer(selected) == true
        }.coerceAtLeast(0)
        return values[Math.floorMod(index + direction, values.size)]
    }
}

/** The id a system's fallback notice carries, before the system's platform id. */
const val PLAYER_CHOICE_SYSTEM_NOTICE = "foldcade:player-choice:system:"

data class SystemPlayerChoice(val platformId: String, val label: String, val choice: PlayerChoiceRow)

object PlayerChoiceCopy {
    const val systems = "Systems"
    const val openWith = "Open with"
    const val automatic = "Automatic"
    const val default = "Default"
    fun inherited(system: Boolean, label: String?): String = when {
        label == null -> if (system) automatic else default
        system -> "Auto · $label"
        else -> "Default · $label"
    }
    /** What a choice that is gone ends with. A row too narrow for both shortens the name, never this. */
    const val notInstalledEnd = " · not installed"
    fun notInstalled(label: String): String = "$label$notInstalledEnd"
    fun core(label: String): String = "$label (RetroArch)"
    fun opened(label: String): String = "Opened with $label"
    fun unavailable(choice: PlayerChoice): String = if (choice.core == null) {
        "${choice.label} isn't installed."
    } else {
        "RetroArch's ${choice.label.removeSuffix(" (RetroArch)")} core isn't installed."
    }
}
