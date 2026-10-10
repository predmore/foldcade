package app.foldcade

import app.foldcade.plugins.romm.ROMM_LIBRARY_ID
import app.foldcade.plugins.romm.ROMM_METADATA_ID

/**
 * Ids the RomM connect screen and a RomM plugin share.
 * The merged client stores a client API token or a device-code access token.
 * It has no password login, so [app.foldcade.api.plugin.Credential.Password] is not written for RomM.
 */
object RommCredentials {
    const val PLUGIN_ID = ROMM_LIBRARY_ID
    const val METADATA_ID = ROMM_METADATA_ID
    const val ACCESS_TOKEN = "access-token"

    /** Built-in slot ids. A third-party entry cannot register these. */
    val RESERVED_IDS = setOf(PLUGIN_ID, METADATA_ID)
}
