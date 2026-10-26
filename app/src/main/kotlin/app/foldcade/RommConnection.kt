package app.foldcade

import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.CredentialLookup
import app.foldcade.language.SignedInBackend

/** Replacing a token keeps the connection's identity, tiles, and save ownership. */
internal suspend fun saveRommConnection(
    session: SessionStore,
    credentials: CredentialStore,
    origin: String,
    token: String,
) {
    credentials.put(RommCredentials.PLUGIN_ID, RommCredentials.ACCESS_TOKEN, Credential.ApiToken(token))
    session.setRommOrigin(origin)
}

/** An unreadable token asks for sign-in without discarding the saved address. */
internal suspend fun readRommRecovery(
    credentials: CredentialStore,
    origin: String?,
): SignedInRecovery {
    if (origin.isNullOrBlank()) return SignedInRecovery(emptyList(), askToSignIn = false)
    return when (lookupOrUnreadable(credentials, RommCredentials.PLUGIN_ID, RommCredentials.ACCESS_TOKEN)) {
        is CredentialLookup.Present -> SignedInRecovery(listOf(SignedInBackend(RommCredentials.PLUGIN_ID, "RomM")), askToSignIn = false)
        CredentialLookup.Unreadable -> SignedInRecovery(emptyList(), askToSignIn = true)
        CredentialLookup.Absent -> SignedInRecovery(emptyList(), askToSignIn = false)
    }
}

internal suspend fun removeRommServer(credentials: CredentialStore, session: SessionStore) {
    credentials.forget(RommCredentials.PLUGIN_ID, RommCredentials.ACCESS_TOKEN)
    session.clearRommOrigin()
}
