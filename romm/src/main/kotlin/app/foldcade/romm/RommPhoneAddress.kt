package app.foldcade.romm

import java.net.URI

/** A server location, never a login link or credentials copied from a browser. No DNS or IO. */
fun normalizeRommPhoneAddress(raw: String): String? = runCatching {
    val uri = URI(raw.trim())
    require(uri.scheme?.lowercase() in setOf("http", "https") && uri.host != null)
    require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
    normalizeSetupOrigin(uri.toASCIIString())
}.getOrNull()

/** RomM's public approval link stays on the server the player chose. */
fun rommPhoneApprovalUrl(origin: String, url: String): String? = runCatching {
    val server = URI(origin)
    val approval = URI(url)
    require(approval.scheme == server.scheme && approval.rawAuthority == server.rawAuthority)
    require(approval.rawUserInfo == null && approval.rawFragment == null)
    url
}.getOrNull()
