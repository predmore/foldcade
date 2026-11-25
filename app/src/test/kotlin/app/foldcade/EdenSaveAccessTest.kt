package app.foldcade

import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderEntry
import app.foldcade.language.EvidenceKind
import app.foldcade.language.FolderRef
import app.foldcade.language.RootVerdict
import app.foldcade.plugins.eden.EdenProfile
import org.junit.Assert.*
import org.junit.Test

class EdenSaveAccessTest {
    private val data = Folder()
    private val external = Folder()
    private val grants = mutableListOf<SaveFolderGrant>()
    private val bound = FolderRef("$PACKAGE.user:root", "Eden")

    @Test fun defaultUsesTheProviderAndExplicitSaveDirUsesTheDefaultNandProfile() {
        data.config()
        data.profile("nand", 1)
        val default = resolve() as EdenSaveAccess.Ready
        assertEquals("nand", default.save.prefix)
        assertEquals(default.nand, default.save)
        assertEquals(EdenProfile.User("01" + "00".repeat(15)), default.profile)
        assertEquals(EvidenceKind.OwnProvider, (verdict(default) as RootVerdict.Uses).evidence.kind)

        data.config(save = "/storage/emulated/0/$DATA_PATH/custom")
        data.directories.add("custom")
        val custom = resolve() as EdenSaveAccess.Ready
        assertEquals("custom", custom.save.prefix)
        assertEquals("nand", custom.nand.prefix)
        assertEquals(default.profile, custom.profile)
        assertEquals(EvidenceKind.Config, (verdict(custom) as RootVerdict.Uses).evidence.kind)
    }

    @Test fun customNandSuppliesBothSavesAndProfilesWhenSaveDirIsEmpty() {
        data.config(nand = "/mnt/media_rw/ABCD-1234/Eden/NAND")
        data.profile("nand", 1)
        assertEquals(EdenSaveAccess.NeedsGrant("ABCD-1234:Eden/NAND"), resolve())
        grants.add(grant("ABCD-1234:Eden"))
        external.profile("", 2)
        val result = resolve() as EdenSaveAccess.Ready
        assertEquals("ABCD-1234:Eden/NAND", result.save.identity)
        assertEquals(result.nand, result.save)
        assertEquals(EdenProfile.User("02" + "00".repeat(15)), result.profile)
    }

    @Test fun customSaveRequiresAHeldWritableGrantAndTakesPrecedenceOverNand() {
        data.config(nand = "/sdcard/$DATA_PATH/nand2", save = "/sdcard/Saves/Eden")
        data.profile("nand2", 1)
        data.directories.add("nand2")
        external.profile("", 2)
        grants.add(grant("primary:Saves", write = false))
        grants.add(grant("primary:Saves/EdenOther"))
        val denied = resolve()
        assertEquals(EdenSaveAccess.NeedsGrant("primary:Saves/Eden"), denied)
        val incident = verdict(denied) as RootVerdict.UsesOther
        assertEquals(FolderRef("primary:Saves/Eden", "Eden"), incident.live)
        assertEquals(EvidenceKind.Config, incident.evidence.kind)

        val granted = grant("primary:Saves")
        grants.add(granted)
        val ready = resolve() as EdenSaveAccess.Ready
        assertEquals(folderDocumentUri(granted.uri, "primary:Saves/Eden"), ready.save.uri)
        assertEquals("nand2", ready.nand.prefix)
        assertEquals(EdenProfile.User("01" + "00".repeat(15)), ready.profile)
    }

    @Test fun corruptOrAbsentConfigProvidesNoPositiveEvidence() {
        data.profile("nand", 1)
        assertEquals(RootVerdict.NoNews, verdict(resolve()))
        data.files["config/config.ini"] = "[Data Storage\nsave_directory=/sdcard/Other".toByteArray()
        assertEquals(RootVerdict.NoNews, verdict(resolve()))
    }

    private fun resolve() = resolveEdenSaveAccess(DATA_URI, { uri -> if (uri == DATA_URI) data else external }, { edenExternalGrant(it, grants) })
    private fun verdict(access: EdenSaveAccess) = edenRootVerdict(bound, access, PACKAGE, 10L)
    private fun grant(id: String, write: Boolean = true) = SaveFolderGrant(treeUri("com.android.externalstorage.documents", id), true, write)

    private class Folder : SaveFolderAccess {
        val files = mutableMapOf<String, ByteArray>()
        val directories = mutableSetOf("")

        fun config(nand: String = "", save: String = "") {
            files["config/config.ini"] = "[Data%20Storage]\nnand_directory=$nand\nsave_directory=$save\n[System]\ncurrent_user=0".toByteArray()
        }

        fun profile(prefix: String, firstByte: Int) {
            val path = listOf(prefix, "system/save/8000000000000010/su/avators/profiles.dat").filter { it.isNotEmpty() }.joinToString("/")
            directories.add(path.substringBeforeLast('/'))
            files[path] = ByteArray(0x650).apply { this[0x10] = firstByte.toByte() }
        }

        override fun list(path: String) = directories.takeIf { path in it }?.let {
            files.filterKeys { it.substringBeforeLast('/', "") == path }
                .map { (name, bytes) -> SaveFolderEntry(name.substringAfterLast('/'), false, bytes.size.toLong()) }
        }
        override fun read(path: String, limit: Int) = files[path]?.takeIf { it.size <= limit }
        override fun read(path: String, offset: Long, length: Int): ByteArray? = error("Unexpected partial read")
        override fun write(path: String, bytes: ByteArray): Boolean = error("Read only")
        override fun createFolder(path: String): Boolean = error("Read only")
        override fun moveToBackup(path: String): Boolean = error("Read only")
    }

    private companion object {
        const val PACKAGE = "dev.eden.eden_emulator"
        const val DATA_URI = "content://$PACKAGE.user/tree/root"
        const val DATA_PATH = "Android/data/$PACKAGE/files"
    }
}
