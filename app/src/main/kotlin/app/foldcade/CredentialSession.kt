package app.foldcade

import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.language.SignedInBackend
import kotlin.coroutines.cancellation.CancellationException

internal data class SignedInRecovery(
    val backends: List<SignedInBackend>,
    val askToSignIn: Boolean,
)

/**
 * A keystore failure becomes [CredentialLookup.Unreadable].
 * Cancellation still propagates. Other exceptions do not escape.
 */
internal suspend fun lookupOrUnreadable(
    store: CredentialStore,
    pluginId: String,
    key: String,
): CredentialLookup = try {
    store.lookup(pluginId, key)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    CredentialLookup.Unreadable
}

/** An unreadable token signs the user out and asks them to sign in again. */
internal fun signedInRecovery(
    lookup: CredentialLookup,
    stored: List<SignedInBackend>,
): SignedInRecovery = when (lookup) {
    CredentialLookup.Unreadable -> SignedInRecovery(emptyList(), askToSignIn = true)
    CredentialLookup.Absent, is CredentialLookup.Present -> SignedInRecovery(stored, askToSignIn = false)
}

internal fun ShellController.applyRecovery(recovery: SignedInRecovery) {
    setSignedIn(recovery.backends)
    if (recovery.askToSignIn) askToSignInAgain()
}
