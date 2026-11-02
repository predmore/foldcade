package app.foldcade

import app.foldcade.localfolder.FolderRescan
import app.foldcade.localfolder.LocalFolderBackend

internal data class FolderRefresh(val changed: Boolean, val library: LoadedLibrary?)

/** An unchanged completed walk reports connectivity without invalidating the media snapshot. */
internal suspend fun readFolderRefresh(
    backend: LocalFolderBackend,
    complete: () -> Boolean,
    load: suspend () -> LoadedLibrary,
): FolderRefresh? {
    val rescan = backend.rescan()
    if (rescan == FolderRescan.NotScanned || !complete()) return null
    val changed = rescan == FolderRescan.Changed
    return FolderRefresh(changed, if (changed) load() else null)
}
