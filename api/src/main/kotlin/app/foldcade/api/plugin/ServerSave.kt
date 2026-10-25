// SPDX-License-Identifier: MIT
package app.foldcade.api.plugin

/**
 * One version kept by a server. Frozen since API minor 16.
 * Null [slot] is an unslotted archive; null [source] means the origin is unknown.
 * [source] names the origin device, falling back to the emulator. Times are epoch milliseconds.
 * [screenshotUri] is credential-free; the library's image loader supplies authentication.
 * [playing] identifies the version held for the supplied player's next start.
 */
data class ServerSave(
    val id: String,
    val slot: String?,
    val emulator: String?,
    val source: String?,
    val updatedAtMillis: Long,
    val sizeBytes: Long,
    val playing: Boolean,
    val screenshotUri: String?,
)
