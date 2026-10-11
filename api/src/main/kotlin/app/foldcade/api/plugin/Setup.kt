// SPDX-License-Identifier: Apache-2.0

package app.foldcade.api.plugin

/**
 * One step the shell shows before [LibraryBackend.connect].
 * Open. A plugin that branches on this must use an `else` branch.
 * Adding a leaf bumps [PLUGIN_API_MINOR].
 */
sealed interface Setup {
    /**
     * Plain text. The host stores it and passes it back through [LibraryBackend.configure].
     * Not a secret.
     */
    data class Text(val key: String, val prompt: String) : Setup

    /**
     * The step for a plugin that only takes a pasted token.
     * The host stores [Credential.ApiToken] under [key].
     */
    data class Secret(val key: String, val prompt: String) : Setup

    /**
     * Device-code step. [credentialKey] is the [CredentialStore] key, not the plugin id.
     * The shell always offers paste into that same key. Paste is not a second step.
     */
    data class DeviceCode(val credentialKey: String) : Setup
}

/**
 * What [LibraryBackend.beginDeviceCode] shows.
 * Frozen. A new constructor property bumps [PLUGIN_API_VERSION].
 */
data class DeviceCodeChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
)

/**
 * One answer from [LibraryBackend.pollDeviceCode].
 * Open. A plugin that branches on this must use an `else` branch.
 * [Approved] carries only the token. The plugin keeps any device id itself.
 */
sealed interface DeviceCodePoll {
    data object Pending : DeviceCodePoll
    data object SlowDown : DeviceCodePoll
    data object Denied : DeviceCodePoll
    data object Expired : DeviceCodePoll
    data class Approved(val token: String) : DeviceCodePoll
}
