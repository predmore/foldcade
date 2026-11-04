package app.foldcade.language

object FirmwareCopy {
    fun ready(platform: String): String = "$platform BIOS ready"
    fun missing(files: List<String>, place: String): String =
        "Missing ${files.joinToString(", ")}. Put your BIOS in $place, then start the game again."
    fun folder(player: String, place: String, files: List<String> = emptyList()): String =
        (if (files.isEmpty()) "Your BIOS belongs in $place. " else "${files.joinToString(", ")} belongs in $place. ") +
            "Choose the $player folder once so Foldcade can check it, then start the game again."
    fun unreachable(player: String): String = "Could not check $player’s BIOS folder. Choose its folder again in Settings › Emulators, then retry."
    const val title = "BIOS needed"
    const val chooseTitle = "A home for your BIOS"
    const val melonSetup = "In melonDS Settings › System › Custom BIOS and firmware, choose RetroArch/system/nds for DS or RetroArch/system/dsi for DSi."
    const val readyBody = "Your BIOS is in place. Time to play."
}

fun firmwareFolderDialog(playerId: String, body: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.FirmwareFolder,
    title = FirmwareCopy.chooseTitle,
    body = body,
    buttons = listOf(DialogButton.ChooseSaveFolder, DialogButton.NotNow),
    index = 0,
    safeIndex = 1,
    subject = playerId,
    screen = screen,
)
