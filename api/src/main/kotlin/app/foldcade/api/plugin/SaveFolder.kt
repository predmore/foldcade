// SPDX-License-Identifier: MIT

package app.foldcade.api.plugin

/**
 * A player whose save folder is a tree the user has already picked.
 * [bindSaveFolder] stores a content URI. It does not search the device.
 * Players that do not implement this keep their own [Player.saveDeclarations].
 */
interface SaveFolderHolder {
    fun bindSaveFolder(contentUri: String)

    fun hasSaveFolder(): Boolean

    /**
     * Players that return the same key share one folder: the shell stores it,
     * asks for it, and lists it in Settings once, and binds it to each of them.
     * Null keeps a folder for this player alone. Added in API minor 9.
     */
    val saveFolderKey: String?
        get() = null

    /**
     * A content URI the folder picker opens at, such as the player's default
     * save directory. A hint only: the user still picks. Added in API minor 9.
     */
    val suggestedSaveFolder: String?
        get() = null
}
