package app.foldcade.plugins.romm

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Credential
import app.foldcade.api.plugin.CredentialStore
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.MemoryCredentialStore
import app.foldcade.romm.RomFileSummary
import app.foldcade.romm.RomSummary
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RommSetupTest {
    @Test
    fun theSameCanonicalOriginIsOneServer() {
        val stored = listOf("https://host/")
        assertTrue(rommOriginAlreadyConfigured(stored, "https://host/api"))
        assertTrue(rommOriginAlreadyConfigured(stored, "https://host"))
        assertFalse(rommOriginAlreadyConfigured(stored, "https://other.example"))
        assertEquals(
            RommData.canonicalOrigin("https://host/api"),
            RommData.canonicalOrigin("https://host/"),
        )
    }

    @Test
    fun removingOneServerForgetsThatKeyOnly() = runBlocking {
        val delegate = MemoryCredentialStore()
        val store = object : CredentialStore by delegate {
            var forgotPlugin = false
            override suspend fun forgetPlugin(pluginId: String) {
                forgotPlugin = true
                delegate.forgetPlugin(pluginId)
            }
        }
        store.put(ROMM_LIBRARY_ID, rommCredentialKey(ROMM_LIBRARY_ID), Credential.ApiToken("rmm_default"))
        store.put(ROMM_LIBRARY_ID, rommCredentialKey("romm#2"), Credential.ApiToken("rmm_second"))
        forgetRommInstance(store, "romm#2")
        assertFalse(store.forgotPlugin)
        assertEquals(
            "rmm_default",
            (store.lookup(ROMM_LIBRARY_ID, "access-token") as app.foldcade.api.plugin.CredentialLookup.Present)
                .credential.let { (it as Credential.ApiToken).value },
        )
        assertTrue(store.lookup(ROMM_LIBRARY_ID, "access-token@romm#2") is app.foldcade.api.plugin.CredentialLookup.Absent)
        forgetRommInstance(store, ROMM_LIBRARY_ID)
        assertFalse(store.forgotPlugin)
        assertTrue(store.keys(ROMM_LIBRARY_ID).isEmpty())
    }

    @Test
    fun metadataRoutesEachInstanceByBackendId() {
        val root = Files.createTempDirectory("romm-meta")
        try {
            RommPlugins.installSupply(RommSupply(cacheRoot = root, dataRoot = root.resolve("data")))
            val first = RommLibrary.open(ROMM_LIBRARY_ID)
            val second = RommLibrary.open("romm#2")
            first.configure(mapOf(ROMM_ORIGIN_KEY to "https://one.example"))
            second.configure(mapOf(ROMM_ORIGIN_KEY to "https://two.example"))
            first.connection.catalog.rememberRoms(listOf(rom(42, "One")))
            second.connection.catalog.rememberRoms(listOf(rom(42, "Two")))
            val metadata = RommMetadata { id ->
                listOf(first, second).firstOrNull { it.instanceId == id }?.connection
            }
            assertEquals("One", metadata.cached(game(ROMM_LIBRARY_ID))?.title)
            assertEquals("Two", metadata.cached(game("romm#2"))?.title)
            assertNull(metadata.cached(game("local-folder")))
        } finally {
            RommPlugins.clear()
            root.toFile().deleteRecursively()
        }
    }
}

private fun game(backendId: String) = Game(
    backendId = backendId,
    remoteKey = "42",
    platformId = "snes",
    availability = Availability.RemoteOnly,
    label = backendId,
)

private fun rom(id: Long, name: String) = RomSummary(
    id = id,
    name = name,
    fsName = "$name.sfc",
    fsExtension = "sfc",
    fsSizeBytes = 1,
    platformId = 1,
    platformSlug = "snes",
    platformFsSlug = "snes",
    platformDisplayName = "SNES",
    alternativeNames = emptyList(),
    pathCoverSmall = null,
    pathCoverLarge = null,
    urlCover = null,
    hasSimpleSingleFile = true,
    hasNestedSingleFile = false,
    hasMultipleFiles = false,
    files = listOf(RomFileSummary(1, "$name.sfc", 1)),
)
