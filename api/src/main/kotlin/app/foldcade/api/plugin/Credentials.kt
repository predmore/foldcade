package app.foldcade.api.plugin

/**
 * A secret the host stores for one plugin.
 *
 * [toString] never includes the secret. Do not log a [Credential].
 * Open. A plugin that branches on this must use an `else` branch.
 * Adding a subclass bumps [PLUGIN_API_MINOR].
 */
sealed class Credential {
    /**
     * Client API token or device-code access token.
     * RomM on the merged client uses this. The password is not kept.
     */
    data class ApiToken(val value: String) : Credential() {
        override fun toString(): String = "ApiToken(***)"
    }

    /**
     * A password, only for a backend whose client has no token login.
     * The merged RomM client does not use this.
     */
    data class Password(val value: String) : Credential() {
        override fun toString(): String = "Password(***)"
    }
}

/** What [CredentialStore.lookup] found. [Unreadable] means sign in again. */
sealed interface CredentialLookup {
    data class Present(val credential: Credential) : CredentialLookup
    data object Absent : CredentialLookup

    /** The ciphertext is still there, or was, and the key cannot open it. */
    data object Unreadable : CredentialLookup
}

/**
 * The keystore key cannot encrypt or decrypt. The shell asks for sign-in.
 * The message does not include a secret.
 */
class CredentialUnreadable(message: String, cause: Throwable? = null) : Exception(message, cause)

/** [pluginId] is not an id this plugin registered. */
class CredentialDenied(val pluginId: String) : Exception("Plugin cannot open credentials for $pluginId")

/**
 * Host-owned secrets. Plugins do not implement this and do not touch the
 * file or the keystore. Every entry is stored under a plugin id.
 * One plugin's id does not reveal another plugin's entries.
 */
interface CredentialStore {
    suspend fun put(pluginId: String, key: String, credential: Credential)

    suspend fun lookup(pluginId: String, key: String): CredentialLookup

    suspend fun forget(pluginId: String, key: String)

    suspend fun forgetPlugin(pluginId: String)

    suspend fun keys(pluginId: String): Set<String>

    suspend fun pluginIds(): Set<String>
}

/**
 * Credentials for one plugin id. The host builds this.
 * A plugin cannot change the id.
 */
interface PluginCredentials {
    val pluginId: String

    suspend fun put(key: String, credential: Credential)

    suspend fun lookup(key: String): CredentialLookup

    suspend fun forget(key: String)

    suspend fun forgetAll()

    suspend fun keys(): Set<String>
}

/**
 * Opens [PluginCredentials] for ids this plugin registered.
 * [open] throws [CredentialDenied] for any other id.
 */
interface CredentialAccess {
    fun open(pluginId: String): PluginCredentials
}

/**
 * In-memory store for JVM tests and for a host that is not on Android.
 * It does not encrypt. The Android app uses the keystore store instead.
 */
class MemoryCredentialStore : CredentialStore {
    private val lock = Any()
    private val rows = mutableMapOf<String, MutableMap<String, Credential>>()

    override suspend fun put(pluginId: String, key: String, credential: Credential) {
        require(pluginId.isNotBlank()) { "plugin id is empty" }
        require(key.isNotBlank()) { "credential key is empty" }
        require(credential.secret().isNotBlank()) { "credential is empty" }
        synchronized(lock) {
            rows.getOrPut(pluginId) { mutableMapOf() }[key] = credential
        }
    }

    override suspend fun lookup(pluginId: String, key: String): CredentialLookup {
        val found = synchronized(lock) { rows[pluginId]?.get(key) }
        return if (found == null) CredentialLookup.Absent else CredentialLookup.Present(found)
    }

    override suspend fun forget(pluginId: String, key: String) {
        synchronized(lock) {
            val plugin = rows[pluginId] ?: return
            plugin.remove(key)
            if (plugin.isEmpty()) rows.remove(pluginId)
        }
    }

    override suspend fun forgetPlugin(pluginId: String) {
        synchronized(lock) { rows.remove(pluginId) }
    }

    override suspend fun keys(pluginId: String): Set<String> =
        synchronized(lock) { rows[pluginId]?.keys?.toSet() ?: emptySet() }

    override suspend fun pluginIds(): Set<String> =
        synchronized(lock) { rows.keys.toSet() }
}

/**
 * [access] the host passes to [PluginEntry.bind].
 * [allowed] is the set of slot ids that entry contributed.
 */
class BoundCredentialAccess(
    private val allowed: Set<String>,
    private val store: CredentialStore,
) : CredentialAccess {
    override fun open(pluginId: String): PluginCredentials {
        if (pluginId !in allowed) throw CredentialDenied(pluginId)
        return StoreView(pluginId, store)
    }
}

private class StoreView(
    override val pluginId: String,
    private val store: CredentialStore,
) : PluginCredentials {
    override suspend fun put(key: String, credential: Credential) {
        store.put(pluginId, key, credential)
    }

    override suspend fun lookup(key: String): CredentialLookup = store.lookup(pluginId, key)

    override suspend fun forget(key: String) {
        store.forget(pluginId, key)
    }

    override suspend fun forgetAll() {
        store.forgetPlugin(pluginId)
    }

    override suspend fun keys(): Set<String> = store.keys(pluginId)
}

/** The secret bytes as text. Do not log the result. */
fun Credential.secret(): String = when (this) {
    is Credential.ApiToken -> value
    is Credential.Password -> value
}

/**
 * Ids the RomM connect screen and a RomM plugin share.
 * The merged client stores a client API token or a device-code access token.
 * It has no password login, so [Credential.Password] is not written for RomM.
 */
object RommCredentials {
    const val PLUGIN_ID = "romm"
    const val ACCESS_TOKEN = "access-token"
}
