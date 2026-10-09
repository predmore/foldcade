// SPDX-License-Identifier: Apache-2.0

package app.foldcade.api.plugin

/**
 * What the backend hands the player.
 * The backend builds this. The player reads it.
 * The shell does not parse [Game.remoteKey] and does not branch on the backend.
 * Open. A plugin that branches on this must use an `else` branch.
 */
sealed interface LaunchTarget {
    /**
     * A content URI the player can open.
     * [firmware] is present only when this backend has bytes that player requires.
     */
    data class ContentUri(
        val uri: String,
        val firmware: List<FirmwareBytes> = emptyList(),
    ) : LaunchTarget

    /**
     * Values the player already knows how to read. Not a file.
     *
     * GameNative fits as `app_id` (decimal string) and `game_source`.
     * Moonlight fits as `host_uuid` and `app_id` (the app id stays a string).
     * The shell does not read [values].
     */
    data class AppRef(
        val values: Map<String, String>,
    ) : LaunchTarget
}
