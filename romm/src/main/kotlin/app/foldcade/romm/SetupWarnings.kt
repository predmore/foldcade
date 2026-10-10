package app.foldcade.romm

/**
 * Shown in setup when the origin is an allowed LAN http address.
 * The platform cannot name that host in the network-security config.
 * The warning is visible before a token is saved.
 */
const val CLEARTEXT_CREDENTIAL_WARNING =
    "This server is using http, not https. Credentials travel in cleartext on the network."

/**
 * Shown when an http origin is not a LAN address.
 * [RommClient.normalizeOrigin] rejects it. The setup screen does not save it.
 */
const val CLEARTEXT_LAN_ONLY =
    "Cleartext is only for a LAN address. Use https:// for this server."

/**
 * Shown when an http origin answers with a redirect onto https.
 * Matches the note on issue #18. The client does not follow that redirect.
 */
const val TRY_HTTPS_HINT = "try https://"

fun cleartextCredentialWarning(origin: String): String? = when (cleartextOriginKind(origin)) {
    CleartextOriginKind.Lan -> CLEARTEXT_CREDENTIAL_WARNING
    CleartextOriginKind.Public -> CLEARTEXT_LAN_ONLY
    CleartextOriginKind.NotHttp -> null
}

internal enum class CleartextOriginKind { NotHttp, Lan, Public }

/**
 * An http origin [RommClient] will accept is [CleartextOriginKind.Lan].
 * Http that is not a LAN address is [CleartextOriginKind.Public].
 * A username or password is not copied into the result.
 */
internal fun cleartextOriginKind(raw: String): CleartextOriginKind {
    val text = raw.trim()
    val http = "http://"
    if (text.length < http.length || !text.regionMatches(0, http, 0, http.length, ignoreCase = true)) {
        return CleartextOriginKind.NotHttp
    }
    if (runCatching { RommClient.normalizeOrigin(text) }.isSuccess) return CleartextOriginKind.Lan
    val host = httpHostIgnoringUserInfo(text)
    if (host != null && isLanHost(host)) return CleartextOriginKind.Lan
    return CleartextOriginKind.Public
}

private fun httpHostIgnoringUserInfo(raw: String): String? {
    val marker = "://"
    val split = raw.indexOf(marker)
    if (split < 0) return null
    var rest = raw.substring(split + marker.length)
    val cut = rest.indexOfAny(charArrayOf('/', '?', '#'))
    if (cut >= 0) rest = rest.substring(0, cut)
    val at = rest.lastIndexOf('@')
    if (at >= 0) rest = rest.substring(at + 1)
    if (rest.startsWith("[")) {
        val end = rest.indexOf(']')
        if (end < 2) return null
        return rest.substring(1, end)
    }
    val colon = rest.lastIndexOf(':')
    if (colon > 0 && rest.substring(colon + 1).all { it.isDigit() }) rest = rest.substring(0, colon)
    return rest.ifEmpty { null }
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

/**
 * [RommClient.normalizeOrigin], or null when [raw] is not an http(s) URL,
 * includes a username or password, or is cleartext outside the LAN.
 */
fun normalizeSetupOrigin(raw: String): String? = try {
    RommClient.normalizeOrigin(raw)
} catch (_: IllegalArgumentException) {
    null
}
