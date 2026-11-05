package app.foldcade.romm

import java.net.URI

/** A server location, never a login link or credentials copied from a browser. No DNS or IO. */
fun normalizeRommPhoneAddress(raw: String): String? = runCatching {
    val uri = URI(raw.trim())
    require(uri.scheme?.lowercase() in setOf("http", "https") && uri.host != null)
    require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
    normalizeSetupOrigin(uri.toASCIIString())
}.getOrNull()

/**
 * RomM 5.4.1 returns a relative path, independent of any public base URL:
 * https://github.com/rommapp/romm/blob/5.4.1/backend/utils/device_auth.py#L55
 * Accept an equivalent explicit default port, but do not trust a different host based on
 * heartbeat fields: every RomM instance has those fields, not a unique authenticated identity.
 */
fun rommPhoneApprovalUrl(origin: String, url: String): String? = runCatching {
    val server = URI(origin)
    val approval = URI(url)
    require(approval.scheme.equals(server.scheme, ignoreCase = true) && approval.host != null)
    require(approval.host.equals(server.host, ignoreCase = true) && effectivePort(approval) == effectivePort(server))
    require(approval.rawUserInfo == null && approval.rawFragment == null)
    url
}.getOrNull()

private fun effectivePort(uri: URI): Int = if (uri.port >= 0) uri.port else when (uri.scheme?.lowercase()) {
    "https" -> 443
    "http" -> 80
    else -> -1
}
