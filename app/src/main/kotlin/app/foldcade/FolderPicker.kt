package app.foldcade

/**
 * Why the document-tree picker is open. The library, a player's save folder,
 * and RetroArch's data folder share one result callback. The purpose has to
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

    /** RetroArch's data folder, so Foldcade can list its cores. */
    RetroArchCores,
}

/** The picker fields that are not Android's URI result. */
internal data class FolderPickerState(
    val purpose: FolderPurpose = FolderPurpose.Library,
    val pendingLaunch: Int? = null,
    val settingsPlayerId: String? = null,
) {
    fun encode(): String = buildString {
        append(purpose.name)
        append('\n')
        append(pendingLaunch?.toString().orEmpty())
        append('\n')
        append(settingsPlayerId.orEmpty())
    }
}

internal fun decodeFolderPickerState(raw: String?): FolderPickerState {
    if (raw.isNullOrBlank()) return FolderPickerState()
    val lines = raw.split('\n', limit = 3)
    val purpose = lines.getOrNull(0)?.let { name ->
        FolderPurpose.entries.firstOrNull { it.name == name }
    } ?: FolderPurpose.Library
    return FolderPickerState(
        purpose = purpose,
        pendingLaunch = lines.getOrNull(1)?.toIntOrNull(),
        settingsPlayerId = lines.getOrNull(2)?.takeIf { it.isNotEmpty() },
    )
}

/** What the shared picker result does once [state] is known. */
internal enum class FolderPickResult {
    UseAsLibrary,
    RememberSaveAndLaunch,
    RememberSaveForPlayer,
    ImportCores,
    SkipSaveAndLaunch,
    ClearSettingsPlayer,
    SettleCores,
    Ignore,
}

internal fun folderPickResult(state: FolderPickerState, choseFolder: Boolean): FolderPickResult = when (state.purpose) {
    FolderPurpose.Library -> if (choseFolder) FolderPickResult.UseAsLibrary else FolderPickResult.Ignore
    FolderPurpose.LaunchOffer, FolderPurpose.SaveOffer ->
        if (choseFolder) FolderPickResult.RememberSaveAndLaunch else FolderPickResult.SkipSaveAndLaunch
    FolderPurpose.Settings -> if (choseFolder) FolderPickResult.RememberSaveForPlayer else FolderPickResult.ClearSettingsPlayer
    FolderPurpose.RetroArchCores -> if (choseFolder) FolderPickResult.ImportCores else FolderPickResult.SettleCores
}
