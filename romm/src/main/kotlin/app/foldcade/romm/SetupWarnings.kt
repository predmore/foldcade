package app.foldcade.romm

/**
 * Shown in setup when the origin is `http://`.
 * Local-LAN RomM over http is allowed. The warning is the gate, not a block.
 */
const val CLEARTEXT_CREDENTIAL_WARNING =
    "This server is using http, not https. Credentials travel in cleartext on the network."

/**
 * Shown when an http origin answers with a redirect onto https.
 * Matches the note on issue #18. The client does not follow that redirect.
 */
const val TRY_HTTPS_HINT = "try https://"

fun cleartextCredentialWarning(origin: String): String? {
    val text = origin.trim()
    if (!text.startsWith("http://")) return null
    return CLEARTEXT_CREDENTIAL_WARNING
}

/**
 * [location] is the HTTP Location header. A non-redirect status returns null.
 */
fun httpsRedirectHint(status: Int, location: String?): String? {
    if (status !in 300..399) return null
    val target = location?.trim().orEmpty()
    if (!target.startsWith("https://")) return null
    return TRY_HTTPS_HINT
}

/** [RommClient.normalizeOrigin], or null when [raw] is not an http(s) URL. */
fun normalizeSetupOrigin(raw: String): String? = try {
    RommClient.normalizeOrigin(raw)
} catch (_: IllegalArgumentException) {
    null
}
