package app.foldcade.language

object FirmwareCopy {
    fun ready(platform: String): String = "$platform BIOS ready"
    fun missing(system: String, files: List<String>, place: String, rommConnected: Boolean = false): String {
        val detail = files.joinToString(", ").removePrefix("$system BIOS ").trim('(', ')')
        val romm = if (rommConnected) " or RomM’s firmware" else ""
        return "Missing the $system BIOS ($detail). Put it in $place$romm. Then start the game again."
    }
    fun folder(player: String, place: String, files: List<String> = emptyList()): String =
        (if (files.isEmpty()) "Your BIOS belongs in $place. " else "${files.joinToString(", ")} belongs in $place. ") +
            "Choose the $player folder once to let Foldcade check and place your BIOS."
    fun unreachable(player: String): String = "Could not check $player’s BIOS folder. Choose its folder again in Settings › Emulators. Then start the game again."
    const val title = "BIOS needed"
    fun folderNoticeTitle(player: String): String = "$player BIOS folder"
    const val wrongFolder = "Choose RetroArch or its system folder. That one pick covers your BIOS."
    const val chooseTitle = "A home for your BIOS"
    const val readyBody = "Your BIOS is in place. Time to play."
}

/** A required RetroArch BIOS may already live in a custom System/BIOS directory. */
fun missingFirmwareDialog(body: String, screen: HostScreen, firmwarePlayerId: String? = null): DialogState =
    if (firmwarePlayerId != null) firmwareFolderDialog(firmwarePlayerId, body, screen)
    else playerNotice(FirmwareCopy.title, body, screen)

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
