package app.foldcade.romm

/**
 * RomM major this client was tested against.
 *
 * The wire shape is the OpenAPI schema shipped with RomM 5.4.0-alpha.2
 * (`info.version` on `/openapi.json`, generated models at that tag).
 * RomM documents breaking changes on major versions only. 5.1's device-sync
 * document describes a different body; this client does not speak it.
 * Browse and download work on any 5.x. Save sync needs 5.4.0 or newer.
 */
object RommContract {
    const val TESTED_MAJOR = 5
    const val TESTED_SPEC = "5.4.0-alpha.2"
    const val CLIENT_SLUG = "foldcade"
    const val PLATFORM = "android"
    const val SYNC_MODE = "api"

    /**
     * Scopes asked for on device-code sign-in.
     * `platforms.read` is the scope `GET /api/platforms` declares.
     * `firmware.read` is reserved for a later firmware download and is not called here.
     * `roms.user.read` and `collections.read` stay off until a feature calls them.
     * Play sessions, `roms.user.write`, and `tasks.run` are not requested.
     */
    val DEVICE_AUTH_SCOPES: List<String> = listOf(
        "roms.read",
        "platforms.read",
        "assets.read",
        "assets.write",
        "devices.read",
        "devices.write",
        "firmware.read",
    )
}

data class RommVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val raw: String,
) {
    fun isAtLeast(major: Int, minor: Int, patch: Int): Boolean {
        if (this.major != major) return this.major > major
        if (this.minor != minor) return this.minor > minor
        return this.patch >= patch
    }

    companion object {
        private val leading = Regex("""^(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

        fun parse(raw: String): RommVersion? {
            val text = raw.trim()
            val match = leading.find(text) ?: return null
            if (match.range.first != 0) return null
            return try {
                RommVersion(
                    major = match.groupValues[1].toInt(),
                    minor = match.groupValues[2].toIntOrNull() ?: 0,
                    patch = match.groupValues[3].toIntOrNull() ?: 0,
                    raw = text,
                )
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}
