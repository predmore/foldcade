package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException

/**
 * Where the RomM client token comes from.
 *
 * The host owns storage. When `CredentialStore` is on `:api`, the host passes
 * a source that reads that store. This plugin does not write a token file,
 * and it does not open Android Keystore or DataStore.
 * [accessToken] is the client's existing bearer. Null means nobody has signed in.
 */
fun interface RommTokenSource {
    fun accessToken(): String?
}

/**
 * Client-token sign-in for one call.
 * A password is wiped and is not sent or stored. RomM's client token is the
 * auth this plugin uses. Device-code sign-in returns that token to the caller.
 */
object RommSignIn {
    fun clientToken(accessToken: String, password: CharArray? = null): String =
        try {
            val token = accessToken.trim().removePrefix("Bearer ").trim()
            if (token.isEmpty()) {
                throw PluginException.NotAuthenticated("RomM client token is missing")
            }
            token
        } finally {
            password?.fill('\u0000')
        }
}
