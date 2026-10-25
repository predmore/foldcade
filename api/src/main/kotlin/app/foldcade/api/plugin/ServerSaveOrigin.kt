// SPDX-License-Identifier: MIT
package app.foldcade.api.plugin

/** Origin detail for a server save. Frozen since API minor 16. Null fields are unknown. */
data class ServerSaveOrigin(
    val client: String?,
    val deviceName: String?,
    val thisDevice: Boolean,
)
