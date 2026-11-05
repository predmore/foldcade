package app.foldcade

/**
 * Why the document-tree picker is open. The library, a player's save folder,
 * RetroArch's data folder, and Eden's data folder share one result callback. The purpose has to
 * survive the activity being recreated while the picker is in front, or the
 * new instance treats every choice as a game library.
 */
internal enum class FolderPurpose {
    Library,
    LaunchOffer,

    /**
     * A player's save folder, asked for before a library game whose saves sync.
     * The player's id rides in [FolderPickerState.settingsPlayerId], so a new
     * instance still knows whose folder it is.
     */
    SaveOffer,
    Settings,
    Firmware,

    /** RetroArch's data folder, so Foldcade can list its cores. */
    RetroArchCores,

    /** Eden's data folder, so Foldcade can add keys and firmware. */
    EdenFolder,

    /** Eden’s validated picker, answering this launch’s save-folder offer. */
    EdenSaveOffer,
}

/** A restored SAF address may guide the picker; other archive strings use its default location. */
internal fun libraryFolderStart(raw: String): String? = raw.takeIf {
    try { java.net.URI(it).scheme == "content" } catch (_: IllegalArgumentException) { false }
    catch (_: java.net.URISyntaxException) { false }
}

/** The picker fields that are not Android's URI result. */
internal data class FolderPickerState(
    val purpose: FolderPurpose = FolderPurpose.Library,
    val pendingLaunch: Int? = null,
    val settingsPlayerId: String? = null,
    val guide: app.foldcade.language.FolderGuide? = null,
) {
    fun encode(): String = buildString {
        append(purpose.name)
        append('\n')
        append(pendingLaunch?.toString().orEmpty())
        append('\n')
        append(settingsPlayerId.orEmpty())
        append('\n')
        append(guide?.screen?.name.orEmpty())
        append('\n')
        append(guide?.playerId.orEmpty())
        append('\n')
        append(guide?.opensThere?.toString().orEmpty())
    }
}

internal fun decodeFolderPickerState(raw: String?): FolderPickerState {
    if (raw.isNullOrBlank()) return FolderPickerState()
    val lines = raw.split('\n')
    val purpose = lines.getOrNull(0)?.let { name ->
        FolderPurpose.entries.firstOrNull { it.name == name }
    } ?: FolderPurpose.Library
    return FolderPickerState(
        purpose = purpose,
        pendingLaunch = lines.getOrNull(1)?.toIntOrNull(),
        settingsPlayerId = lines.getOrNull(2)?.takeIf { it.isNotEmpty() },
        guide = lines.getOrNull(3)?.let { name -> app.foldcade.language.HostScreen.entries.firstOrNull { it.name == name } }?.let { screen ->
            app.foldcade.language.FolderGuide(screen, lines.getOrNull(4)?.takeIf { it.isNotEmpty() }, lines.getOrNull(5) != "false")
        },
    )
}

/** What the shared picker result does once [state] is known. */
internal enum class FolderPickResult {
    UseAsLibrary,
    RememberSaveAndLaunch,
    RememberSaveForPlayer,
    ImportCores,
    UseFirmwareFolder,
    UseEdenFolder,
    SkipSaveAndLaunch,
    ClearSettingsPlayer,
    SettleCores,
    SettleEden,
    Ignore,
}

internal fun folderPickResult(state: FolderPickerState, choseFolder: Boolean): FolderPickResult = when (state.purpose) {
    FolderPurpose.Library -> if (choseFolder) FolderPickResult.UseAsLibrary else FolderPickResult.Ignore
    FolderPurpose.LaunchOffer, FolderPurpose.SaveOffer ->
        if (choseFolder) FolderPickResult.RememberSaveAndLaunch else FolderPickResult.SkipSaveAndLaunch
    FolderPurpose.Firmware -> if (choseFolder) FolderPickResult.UseFirmwareFolder else FolderPickResult.Ignore
    FolderPurpose.Settings -> if (choseFolder) FolderPickResult.RememberSaveForPlayer else FolderPickResult.ClearSettingsPlayer
    FolderPurpose.RetroArchCores -> if (choseFolder) FolderPickResult.ImportCores else FolderPickResult.SettleCores
    FolderPurpose.EdenSaveOffer -> if (choseFolder) FolderPickResult.UseEdenFolder else FolderPickResult.SkipSaveAndLaunch
    FolderPurpose.EdenFolder -> if (choseFolder) FolderPickResult.UseEdenFolder else FolderPickResult.SettleEden
}
