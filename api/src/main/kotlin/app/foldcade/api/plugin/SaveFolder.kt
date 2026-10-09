// SPDX-License-Identifier: Apache-2.0

package app.foldcade.api.plugin

/**
 * A player whose save folder is a tree the user has already picked.
 * [bindSaveFolder] stores a content URI. It does not search the device.
 * Players that do not implement this keep their own [Player.saveDeclarations].
 */
interface SaveFolderHolder {
    fun bindSaveFolder(contentUri: String)

    fun hasSaveFolder(): Boolean
}
