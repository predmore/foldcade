package app.foldcade

import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.RommCredentials
import app.foldcade.plugins.romm.RommPlugins
import app.foldcade.plugins.romm.RommTokenSource
import app.foldcade.plugins.romm.RommWiring
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking

/**
 * Reads the host [CredentialStore] through the RomM plugin's [RommTokenSource].
 * The plugin does not keep a second copy of the token.
 * A password entry is ignored: the merged RomM client has no password login.
 * [CredentialLookup.Unreadable] becomes null so the caller can ask for sign-in.
 */
fun rommTokenSource(store: CredentialStore): RommTokenSource = RommTokenSource {
    val found = try {
        runBlocking { store.lookup(RommCredentials.PLUGIN_ID, RommCredentials.ACCESS_TOKEN) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
    when (found) {
        is CredentialLookup.Present -> when (val credential = found.credential) {
            is Credential.ApiToken -> credential.value
            is Credential.Password -> null
        }
        CredentialLookup.Absent, CredentialLookup.Unreadable, null -> null
    }
}

/**
 * Points [RommPlugins] at [store]. A blank [origin] removes the wiring.
 * The token is not copied into the wiring.
 */
fun publishRommWiring(origin: String?, store: CredentialStore, cacheRoot: Path) {
    if (origin.isNullOrBlank()) {
        RommPlugins.clear()
        return
    }
    RommPlugins.install(
        RommWiring(
            origin = origin,
            tokenSource = rommTokenSource(store),
            cacheRoot = cacheRoot,
        ),
    )
}
