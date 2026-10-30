// SPDX-License-Identifier: MIT

package app.foldcade.api.plugin

/**
 * A player whose save folder is a granted tree or a document inside one.
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

    /**
     * Usual save folders below primary shared storage, most likely first, such as
     * `RetroArch/saves`. The shell can reuse a writable grant when exactly one
     * folder fits. Empty names no places. Added in API minor 18.
     */
    val saveFolderPlaces: List<String>
        get() = emptyList()

    /**
     * True when the player gets this folder through its own Android picker.
     * A place counts only while the installed player holds read and write access
     * to it. False for folders the player opens by path or in its own app storage.
     * Added in API minor 18.
     */
    val saveFolderPickedInPlayer: Boolean
        get() = false

    /**
     * Names the player's save folder holds, one of which a folder the user picks
     * should have, such as DuckStation's `memcards` and `bios`. The shell does not
     * keep a picked folder that has none of them, and asks again. Empty checks
     * nothing. Added in API minor 12.
     */
    val saveFolderMarkers: Set<String>
        get() = emptySet()
}

/**
 * A player that keeps saves in a folder Foldcade hosts, for saves it cannot keep
 * anywhere Foldcade can read them: DuckStation writes its memory cards private to
 * itself in its own folder. Android's folder picker lists that folder as
 * Foldcade, with [hostedFolderTitle] beside it, and the user picks it once in
 * the player's own settings, as [hostedFolderSteps] says. The player then writes
 * those saves through Foldcade, which reads them like any save folder.
 *
 * The player writes a save whole into another file and renames it over the save,
 * as DuckStation does. Foldcade holds a rename while it checks and writes the same
 * save before a launch, so neither is lost. Bytes written into the save itself
 * through a file the player holds open are not held, and may be.
 *
 * [bindHostedFolder] hands the player the folder's content URI, and whether the
 * player has been given it, which the shell sees from the player's grant on it.
 * Only then are the saves there. Added in API minor 12.
 */
interface HostedSaveFolder {
    /** What the folder holds, shown beside Foldcade in the picker, such as `DuckStation memory cards`. */
    val hostedFolderTitle: String

    /** How the user points the player at the folder, once, in the player's own settings. */
    val hostedFolderSteps: String

    fun bindHostedFolder(contentUri: String, inUse: Boolean)

    /** True when the player has been given the folder, so the saves it hosts are the player's. */
    fun hostedFolderInUse(): Boolean
}
