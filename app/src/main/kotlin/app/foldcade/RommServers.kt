package app.foldcade

import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.host.PluginHost
import app.foldcade.language.SignedInBackend
import app.foldcade.plugins.romm.ROMM_ORIGIN_KEY
import app.foldcade.plugins.romm.forgetRommInstance
import app.foldcade.plugins.romm.isRommInstance
import app.foldcade.plugins.romm.rommCredentialKey
import app.foldcade.plugins.romm.rommOriginAlreadyConfigured
import java.net.URI

/**
 * What saving the connect form does with one more RomM server.
 * [SameServer] is [rommOriginAlreadyConfigured]: `https://host/` and `https://host/api` are one server.
 */
internal sealed interface RommServerAdd {
    data class Added(val instanceId: String) : RommServerAdd
    data object SameServer : RommServerAdd
}

/**
 * Opens another RomM server. [PluginHost.addInstance] mints `romm#2`.
 * [SessionStore.setRommInstance] stores the origin. The token is `access-token@romm#2`
 * under plugin id `romm`. A [pendingInstanceId] from device-code is reused instead of minting again.
 */
internal suspend fun addRommServer(
    host: PluginHost,
    session: SessionStore,
    credentials: CredentialStore,
    origin: String,
    token: String,
    defaultOrigin: String?,
    extras: List<Pair<String, String>>,
    pendingInstanceId: String?,
): RommServerAdd {
    val taken = listOfNotNull(defaultOrigin?.takeIf { it.isNotBlank() }) + extras.map { it.second }
    if (rommOriginAlreadyConfigured(taken, origin)) return RommServerAdd.SameServer
    val instanceId = pendingInstanceId ?: host.addInstance(RommCredentials.PLUGIN_ID).instanceId
    credentials.put(
        RommCredentials.PLUGIN_ID,
        rommCredentialKey(instanceId),
        Credential.ApiToken(token),
    )
    session.setRommInstance(instanceId, origin)
    host.configure(instanceId, mapOf(ROMM_ORIGIN_KEY to origin))
    return RommServerAdd.Added(instanceId)
}

/**
 * Drops one RomM server. [forgetRommInstance] calls [CredentialStore.forget] for that key.
 * It does not call [CredentialStore.forgetPlugin], which would drop every server's token.
 */
internal suspend fun removeRommServer(
    credentials: CredentialStore,
    session: SessionStore,
    instanceId: String,
) {
    forgetRommInstance(credentials, instanceId)
    if (instanceId == RommCredentials.PLUGIN_ID) session.clearRommOrigin()
    else session.removeRommInstance(instanceId)
}

/** The default server keeps the label the sign-out row already uses. A later server shows its host. */
internal fun rommServerLabel(instanceId: String, origin: String): String {
    if (instanceId == RommCredentials.PLUGIN_ID) return "RomM"
    val host = try {
        URI(origin.trim()).host
    } catch (_: Exception) {
        null
    }
    return host?.takeIf { it.isNotBlank() } ?: instanceId
}

/** Signed-in rows for the saved RomM servers, default first. */
internal fun rommSignedInBackends(
    defaultOrigin: String?,
    extras: List<Pair<String, String>>,
): List<SignedInBackend> {
    val rows = ArrayList<SignedInBackend>()
    if (!defaultOrigin.isNullOrBlank()) {
        rows.add(SignedInBackend(RommCredentials.PLUGIN_ID, rommServerLabel(RommCredentials.PLUGIN_ID, defaultOrigin)))
    }
    for ((id, origin) in extras) {
        rows.add(SignedInBackend(id, rommServerLabel(id, origin)))
    }
    return rows
}

/** Replaces RomM rows and leaves every other signed-in backend where it was. */
internal fun mergeSignedIn(current: List<SignedInBackend>, romm: List<SignedInBackend>): List<SignedInBackend> {
    val rest = current.filter { !isRommInstance(it.pluginId) }
    return romm + rest
}
