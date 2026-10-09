package app.foldcade.romm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vendored document is the schema from RomM 5.4.0-alpha.2.
 * The public demo's 5.2.0 document is not this contract.
 */
class RommOpenApiContractTest {
    @Test
    fun clientPathsAndSaveSyncFieldsAreInThePinnedSpec() {
        val text = javaClass.classLoader.getResource("fixtures/openapi-5.4.0-alpha.2.json")!!.readText()
        val root = Json.parseToJsonElement(text).jsonObject
        val info = root.getValue("info").jsonObject
        assertEquals("RomM API", info.getValue("title").jsonPrimitive.content)
        assertEquals(RommContract.TESTED_SPEC, info.getValue("version").jsonPrimitive.content)
        val paths = root.getValue("paths").jsonObject
        assertTrue("stub spec", paths.size > 100)
        listOf(
            "get" to "/api/heartbeat",
            "post" to "/api/auth/device/init",
            "post" to "/api/auth/device/token",
            "get" to "/api/platforms",
            "get" to "/api/roms",
            "get" to "/api/roms/{id}/simple",
            "get" to "/api/roms/{id}/content/{file_name}",
            "post" to "/api/devices",
            "put" to "/api/devices/{device_id}",
            "post" to "/api/sync/negotiate",
            "post" to "/api/saves",
            "get" to "/api/saves/{id}/content",
            "post" to "/api/saves/{id}/downloaded",
            "post" to "/api/sync/sessions/{session_id}/complete",
        ).forEach { (method, path) ->
            val item = paths.getValue(path).jsonObject
            assertTrue("$method $path", item.containsKey(method))
        }
        val schemas = root.getValue("components").jsonObject.getValue("schemas").jsonObject
        assertTrue(
            props(schemas, "SyncNegotiateResponse").containsAll(
                listOf("session_id", "operations", "total_upload", "total_download", "total_conflict", "total_no_op", "total_delete"),
            ),
        )
        assertTrue(
            props(schemas, "SyncNegotiatePayload").containsAll(
                listOf("device_id", "saves", "rom_ids", "restore_unlisted", "emulators"),
            ),
        )
        assertTrue(props(schemas, "DeviceCreatePayload").contains("allow_existing"))
        assertTrue(
            props(schemas, "SyncOperationSchema").containsAll(
                listOf("action", "rom_id", "save_id", "file_name", "slot", "emulator"),
            ),
        )
    }

    private fun props(schemas: JsonObject, name: String): Set<String> =
        schemas.getValue(name).jsonObject.getValue("properties").jsonObject.keys
}
