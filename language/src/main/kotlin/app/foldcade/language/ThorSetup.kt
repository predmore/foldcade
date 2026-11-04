package app.foldcade.language

/** Settings reads a snapshot; configuration I/O stays in the host, away from drawing. */
enum class ThorSetupStatus { Ready, Applied, Kept, Restored, CloseEmulator, FileAccess, ChooseFolder, Manual, Failed }
enum class ThorSetupOption { MainScreen, OtherScreen, Resolution, AccurateShading }
data class ThorSetupSetting(
    val status: ThorSetupStatus,
    val changes: Set<ThorSetupOption> = emptySet(),
    val canRestore: Boolean = false,
)

object ThorSetupCopy {
    const val title = "Thor setup"
    const val apply = "Set up"
    const val restore = "Restore original"
    const val appInfo = "App info"
    const val fileAccess = "File access"
    const val chooseFolder = "Choose folder"
    const val noticePrefix = "foldcade:thor-setup:"
    const val needsNotice = "Azahar’s Thor setup needs a hand"
    const val appliedNotice = "Azahar’s Thor setup is ready"
    const val failed = "Couldn’t change the settings. Your config and any backups are kept. Check the user folder, or set these options in Azahar."
    const val intro = "Foldcade sets untouched options once. Your own settings stay yours, even if you change them later."
    const val driver = "Turnip is installed privately by Azahar. Pick your GPU driver in Azahar; Foldcade keeps that choice."
    const val restoreBody = "Restore the whole original config file? Today’s settings are backed up beside it and in Foldcade first. The Thor setup won’t be applied again."
    const val closeBody = "Finish playing and save your game first. Then open App info and choose Force stop. Return here to Set up, or launch your next game to apply the preset."
    const val accessBody = "This folder doesn’t allow a safe config replacement. For a local user folder, try Foldcade’s optional Find saves file access, then return here. You can also set the layout in Azahar."
    const val folderBody = "Choose the user folder Azahar uses, the one holding config, sdmc and nand."
    const val manualBody = "This preset is verified for Azahar 2126.2. Set up other versions in Azahar; Foldcade leaves them alone."
    const val keptBody = "Foldcade kept your current settings. This user folder won’t receive the preset again."
    const val restoredBody = "The original config has been restored. Foldcade won’t apply this preset again."
    fun option(option: ThorSetupOption): String = when (option) {
        ThorSetupOption.MainScreen -> "Screen layout · Single Screen"
        ThorSetupOption.OtherScreen -> "Secondary display layout · Opposite Screen"
        ThorSetupOption.Resolution -> "Resolution · 5×"
        ThorSetupOption.AccurateShading -> "Accurate multiplication · On"
    }
    fun status(status: ThorSetupStatus): String = when (status) {
        ThorSetupStatus.Ready -> "Not yet"
        ThorSetupStatus.Applied -> "Applied"
        ThorSetupStatus.Kept -> "Kept yours"
        ThorSetupStatus.Restored -> "Restored"
        ThorSetupStatus.CloseEmulator -> "Close Azahar"
        ThorSetupStatus.FileAccess -> "File access"
        ThorSetupStatus.ChooseFolder -> "Choose folder"
        ThorSetupStatus.Manual -> "In Azahar"
        ThorSetupStatus.Failed -> "Try again"
    }
}

fun thorSetupDialog(playerId: String, setup: ThorSetupSetting, screen: HostScreen): DialogState {
    val action = when {
        setup.status == ThorSetupStatus.FileAccess -> DialogButton.ThorFileAccess
        setup.status == ThorSetupStatus.ChooseFolder -> DialogButton.ChooseSaveFolder
        setup.status == ThorSetupStatus.CloseEmulator -> DialogButton.ThorAppInfo
        setup.canRestore -> DialogButton.RestoreThorSetup
        setup.status == ThorSetupStatus.Ready || setup.status == ThorSetupStatus.Failed -> DialogButton.ApplyThorSetup
        else -> null
    }
    val body = when (setup.status) {
        ThorSetupStatus.Applied -> listOf(ThorSetupCopy.intro, setup.changes.joinToString("\n", transform = ThorSetupCopy::option), ThorSetupCopy.driver).filter { it.isNotBlank() }.joinToString("\n\n")
        ThorSetupStatus.Kept -> ThorSetupCopy.keptBody
        ThorSetupStatus.Restored -> ThorSetupCopy.restoredBody
        ThorSetupStatus.CloseEmulator -> ThorSetupCopy.closeBody
        ThorSetupStatus.FileAccess -> ThorSetupCopy.accessBody
        ThorSetupStatus.ChooseFolder -> ThorSetupCopy.folderBody
        ThorSetupStatus.Manual -> ThorSetupCopy.manualBody
        ThorSetupStatus.Failed -> ThorSetupCopy.failed
        ThorSetupStatus.Ready -> ThorSetupCopy.intro + "\n\n" + ThorSetupCopy.driver
    }
    return DialogState(
        kind = DialogKind.ThorSetup,
        title = ThorSetupCopy.title,
        body = body,
        buttons = if (action == null) listOf(DialogButton.Ok) else listOf(action, DialogButton.NotNow),
        index = if (action == DialogButton.RestoreThorSetup) 1 else 0,
        safeIndex = if (action == null) 0 else 1,
        screen = screen,
        subject = playerId,
    )
}

fun restoreThorSetupDialog(playerId: String, screen: HostScreen): DialogState = DialogState(
    kind = DialogKind.RestoreThorSetup,
    title = ThorSetupCopy.restore,
    body = ThorSetupCopy.restoreBody,
    buttons = listOf(DialogButton.RestoreThorSetup, DialogButton.NotNow),
    index = 1,
    safeIndex = 1,
    screen = screen,
    subject = playerId,
)
