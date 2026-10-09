package app.foldcade.credentials

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.secret
import java.util.Base64
import kotlinx.coroutines.flow.first
import java.net.URLDecoder
import java.net.URLEncoder

internal const val CREDENTIAL_STORE_NAME = "foldcade_credentials"

private val Context.credentialDataStore: DataStore<Preferences> by preferencesDataStore(
    name = CREDENTIAL_STORE_NAME,
)

internal data class StoredBlob(val kind: String, val bytes: ByteArray)

internal interface CredentialBlobs {
    suspend fun put(pluginId: String, key: String, blob: StoredBlob)

    suspend fun get(pluginId: String, key: String): StoredBlob?

    suspend fun remove(pluginId: String, key: String)

    suspend fun removePlugin(pluginId: String)

    suspend fun keys(pluginId: String): Set<String>

    suspend fun pluginIds(): Set<String>

    suspend fun clear()
}

internal class MemoryBlobs : CredentialBlobs {
    private val rows = mutableMapOf<String, MutableMap<String, StoredBlob>>()

    override suspend fun put(pluginId: String, key: String, blob: StoredBlob) {
        rows.getOrPut(pluginId) { mutableMapOf() }[key] = blob
    }

    override suspend fun get(pluginId: String, key: String): StoredBlob? = rows[pluginId]?.get(key)

    override suspend fun remove(pluginId: String, key: String) {
        val plugin = rows[pluginId] ?: return
        plugin.remove(key)
        if (plugin.isEmpty()) rows.remove(pluginId)
    }

    override suspend fun removePlugin(pluginId: String) {
        rows.remove(pluginId)
    }

    override suspend fun keys(pluginId: String): Set<String> = rows[pluginId]?.keys?.toSet() ?: emptySet()

    override suspend fun pluginIds(): Set<String> = rows.keys.toSet()

    override suspend fun clear() {
        rows.clear()
    }
}

internal data class IndexRow(val pluginId: String, val key: String, val kind: String)

internal fun encodeIndex(rows: List<IndexRow>): String =
    rows.joinToString("\n") { row ->
        listOf(row.pluginId, row.key, row.kind).joinToString("\u001f") { part ->
            URLEncoder.encode(part, Charsets.UTF_8)
        }
    }

internal fun decodeIndex(raw: String): List<IndexRow> =
    raw.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        val parts = line.split('\u001f')
        if (parts.size != 3) return@mapNotNull null
        IndexRow(
            pluginId = URLDecoder.decode(parts[0], Charsets.UTF_8),
            key = URLDecoder.decode(parts[1], Charsets.UTF_8),
            kind = URLDecoder.decode(parts[2], Charsets.UTF_8),
        )
    }.toList()

internal class DataStoreBlobs(
    private val store: DataStore<Preferences>,
) : CredentialBlobs {
    override suspend fun put(pluginId: String, key: String, blob: StoredBlob) {
        val pref = blobKey(pluginId, key)
        store.edit { prefs ->
            prefs[pref] = Base64.getEncoder().encodeToString(blob.bytes)
            val kept = decodeIndex(prefs[INDEX] ?: "").filterNot { it.pluginId == pluginId && it.key == key }
            prefs[INDEX] = encodeIndex(kept + IndexRow(pluginId, key, blob.kind))
        }
    }

    override suspend fun get(pluginId: String, key: String): StoredBlob? {
        val prefs = store.data.first()
        val row = decodeIndex(prefs[INDEX] ?: "").find { it.pluginId == pluginId && it.key == key } ?: return null
        val text = prefs[blobKey(pluginId, key)] ?: return null
        return StoredBlob(row.kind, Base64.getDecoder().decode(text))
    }

    override suspend fun remove(pluginId: String, key: String) {
        store.edit { prefs ->
            prefs.remove(blobKey(pluginId, key))
            prefs[INDEX] = encodeIndex(
                decodeIndex(prefs[INDEX] ?: "").filterNot { it.pluginId == pluginId && it.key == key },
            )
        }
    }

    override suspend fun removePlugin(pluginId: String) {
        store.edit { prefs ->
            val rows = decodeIndex(prefs[INDEX] ?: "")
            rows.filter { it.pluginId == pluginId }.forEach { prefs.remove(blobKey(it.pluginId, it.key)) }
            prefs[INDEX] = encodeIndex(rows.filterNot { it.pluginId == pluginId })
        }
    }

    override suspend fun keys(pluginId: String): Set<String> =
        decodeIndex(store.data.first()[INDEX] ?: "").filter { it.pluginId == pluginId }.map { it.key }.toSet()

    override suspend fun pluginIds(): Set<String> =
        decodeIndex(store.data.first()[INDEX] ?: "").map { it.pluginId }.toSet()

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun blobKey(pluginId: String, key: String) = stringPreferencesKey(
        "b." + URLEncoder.encode(pluginId, Charsets.UTF_8) + "." + URLEncoder.encode(key, Charsets.UTF_8),
    )

    private companion object {
        val INDEX = stringPreferencesKey("index")
    }
}

/**
 * Ciphertext in [blobs], key in [box]. A gone key clears every blob and
 * returns [CredentialLookup.Unreadable] so the shell can ask for sign-in.
 */
internal class SealedCredentialStore(
    private val blobs: CredentialBlobs,
    private val box: SecretBox,
) : CredentialStore {
    override suspend fun put(pluginId: String, key: String, credential: Credential) {
        require(pluginId.isNotBlank()) { "plugin id is empty" }
        require(key.isNotBlank()) { "credential key is empty" }
        val plain = credential.secret()
        require(plain.isNotBlank()) { "credential is empty" }
        val sealed = box.seal(plain.toByteArray(Charsets.UTF_8))
        blobs.put(pluginId, key, StoredBlob(kindOf(credential), sealed))
    }

    override suspend fun lookup(pluginId: String, key: String): CredentialLookup {
        val blob = blobs.get(pluginId, key) ?: return CredentialLookup.Absent
        return when (val opened = box.open(blob.bytes)) {
            is Opened.Plain -> {
                val credential = credentialOf(blob.kind, opened.bytes.toString(Charsets.UTF_8))
                if (credential == null) {
                    blobs.remove(pluginId, key)
                    CredentialLookup.Unreadable
                } else {
                    CredentialLookup.Present(credential)
                }
            }
            Opened.BadBlob -> {
                blobs.remove(pluginId, key)
                CredentialLookup.Unreadable
            }
            Opened.KeyGone -> {
                blobs.clear()
                CredentialLookup.Unreadable
            }
        }
    }

    override suspend fun forget(pluginId: String, key: String) {
        blobs.remove(pluginId, key)
    }

    override suspend fun forgetPlugin(pluginId: String) {
        blobs.removePlugin(pluginId)
    }

    override suspend fun keys(pluginId: String): Set<String> = blobs.keys(pluginId)

    override suspend fun pluginIds(): Set<String> = blobs.pluginIds()

    private fun kindOf(credential: Credential): String = when (credential) {
        is Credential.ApiToken -> KIND_TOKEN
        is Credential.Password -> KIND_PASSWORD
    }

    private fun credentialOf(kind: String, plain: String): Credential? = when (kind) {
        KIND_TOKEN -> Credential.ApiToken(plain)
        KIND_PASSWORD -> Credential.Password(plain)
        else -> null
    }

    private companion object {
        const val KIND_TOKEN = "api-token"
        const val KIND_PASSWORD = "password"
    }
}

class AndroidCredentialStore(context: Context) : CredentialStore by SealedCredentialStore(
    blobs = DataStoreBlobs(context.applicationContext.credentialDataStore),
    box = AndroidKeystoreBox(),
)
