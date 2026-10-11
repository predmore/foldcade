package app.foldcade.plugins.romm

import app.foldcade.api.plugin.CredentialStore

/** The [app.foldcade.api.plugin.Setup.Text] key for a RomM origin. */
const val ROMM_ORIGIN_KEY = "origin"

/**
 * Credential key for one RomM instance. The plugin id stays [ROMM_LIBRARY_ID].
 * The default instance keeps `access-token`. A later instance adds `access-token@romm#2`.
 */
fun rommCredentialKey(instanceId: String): String =
    if (instanceId == ROMM_LIBRARY_ID) "access-token" else "access-token@$instanceId"

/** True when [backendId] is the RomM type or a host-minted copy of it. */
fun isRommInstance(backendId: String): Boolean =
    backendId == ROMM_LIBRARY_ID || backendId.startsWith("$ROMM_LIBRARY_ID#")

/**
 * True when [candidate] is a server already in [stored].
 * The comparison is [RommData.canonicalOrigin], the form [RommData.serverKey] hashes,
 * so `https://host/` and `https://host/api` are one server.
 */
fun rommOriginAlreadyConfigured(stored: Iterable<String>, candidate: String): Boolean {
    val key = RommData.canonicalOrigin(candidate)
    return stored.any { RommData.canonicalOrigin(it) == key }
}

/**
 * Drops one server's token. Never [CredentialStore.forgetPlugin], which would drop every server.
 */
suspend fun forgetRommInstance(store: CredentialStore, instanceId: String) {
    store.forget(ROMM_LIBRARY_ID, rommCredentialKey(instanceId))
}
