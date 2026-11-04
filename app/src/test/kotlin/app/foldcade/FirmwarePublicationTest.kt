package app.foldcade

import org.junit.Assert.*
import org.junit.Test

class FirmwarePublicationTest {
    private class Documents : SaveDocuments {
        val files = linkedMapOf("source" to "whole BIOS".toByteArray())
        var copying: () -> Unit = {}
        var corrupt = false
        var opaque = false
        val names = mutableMapOf<String,String>()
        override fun displayName(uri: String) = names[uri] ?: uri.substringAfterLast('/')
        override fun find(folderUri: String, name: String) = files.keys.firstOrNull { it == "$folderUri/$name" || names[it] == name }
        override fun create(folderUri: String, name: String): String = "$folderUri/$name".also { files[it] = byteArrayOf(); names[it] = name.substringAfterLast('/') }
        override fun read(uri: String) = files[uri]
        override fun write(uri: String, bytes: ByteArray) { files[uri] = bytes }
        override fun delete(uri: String): Boolean { names.remove(uri); return files.remove(uri) != null }
        override fun copy(sourceUri: String, targetUri: String): Boolean {
            copying()
            files[targetUri] = if (corrupt) byteArrayOf(1) else files.getValue(sourceUri)
            return true
        }
        override fun renameFirmware(uri: String, leaf: String): String {
            check(files.keys.none { it == "folder/$leaf" })
            if (opaque) { names[uri] = leaf; return uri }
            val dest = "folder/$leaf"
            files[dest] = files.remove(uri)!!
            names.remove(uri)
            return dest
        }
    }
    private fun publish(docs: Documents) = publishNewFirmware(docs, "folder", "bios.bin", "source", md5Hex(docs.files.getValue("source")))

    @Test fun readsBackBeforePublicationAndLeavesNoPartialOnFailure() {
        val docs = Documents().apply { corrupt = true }
        assertFalse(publish(docs))
        assertEquals(setOf("source"), docs.files.keys)
        docs.corrupt = false
        assertTrue(publish(docs))
        assertArrayEquals(docs.files["source"], docs.files["folder/bios.bin"])
    }
    @Test fun existingAndConcurrentlyAddedBiosAlwaysWin() {
        val docs = Documents()
        docs.files["folder/bios.bin"] = "player".toByteArray()
        assertFalse(publish(docs))
        assertEquals("player", String(docs.files.getValue("folder/bios.bin")))
        docs.files.remove("folder/bios.bin")
        docs.copying = { docs.files["folder/bios.bin"] = "new player".toByteArray() }
        assertFalse(publish(docs))
        assertEquals(setOf("source", "folder/bios.bin"), docs.files.keys)
        assertEquals("new player", String(docs.files.getValue("folder/bios.bin")))
    }
    @Test fun providerWhoseOpaqueIdSurvivesRenameKeepsThePublishedBios() {
        val docs = Documents().apply { opaque = true }
        assertTrue(publish(docs))
        assertTrue(docs.names.containsValue("bios.bin"))
        assertEquals(2, docs.files.size)
    }
}
