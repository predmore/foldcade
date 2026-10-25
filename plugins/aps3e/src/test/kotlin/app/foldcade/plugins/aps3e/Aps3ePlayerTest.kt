package app.foldcade.plugins.aps3e

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.FirmwareOffer
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.GameFileReader
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.LaunchRequest
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PluginEntry
import app.foldcade.api.plugin.PluginException
import app.foldcade.api.plugin.SaveDeclaration
import app.foldcade.api.plugin.SaveFolderAccess
import app.foldcade.api.plugin.SaveFolderEntry
import app.foldcade.api.plugin.SaveFolderSearch
import app.foldcade.api.plugin.StartDisplay
import java.util.ServiceLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Aps3ePlayerTest {
    private val player = Aps3ePlayer()

    private val game = Game(
        backendId = "romm",
        remoteKey = "38",
        platformId = "playstation-3",
        availability = Availability.Cached,
        label = "Skate 3",
    )

    private fun launch(uri: String, packageName: String = Aps3ePlayer.PACKAGES.first()) = player.launchIntent(
        LaunchRequest(game = game, target = LaunchTarget.ContentUri(uri), resolvedPackage = packageName),
    )

    @Test
    fun serviceFileLoadsTheEntry() {
        val loaded = ServiceLoader.load(PluginEntry::class.java, Aps3ePlayerTest::class.java.classLoader)
            .filterIsInstance<Aps3eEntry>()
            .single()
        assertEquals(listOf("aps3e"), loaded.players.map { it.id })
        assertTrue(loaded.platforms.isEmpty())
        val player = loaded.players.single()
        assertEquals("aPS3e", player.displayName)
        assertEquals("playstation-3", player.platformId)
        assertEquals(listOf("aenu.aps3e", "aenu.aps3e.premium"), player.packageNames)
        assertEquals(StartDisplay.PickerChoice, player.startDisplay)
        assertFalse(player.occupiesBothDisplays)
        assertFalse(player.requiresImportedGame)
        assertTrue(player.needsLocalFile)
        assertEquals(0x1000, player.firmwareHeaderSize)
    }

    @Test
    fun theFolderPromptOpensOnAps3esOwnProvider() {
        assertEquals(emptyList<SaveDeclaration>(), player.saveDeclarations(game))
        assertFalse(player.hasSaveFolder())
        player.bindSaveFolder("file:///sdcard/Android/data/aenu.aps3e/files/aps3e")
        assertFalse(player.hasSaveFolder())
        player.bindSaveFolder("content://aenu.aps3e.provider/tree/aps3e")
        assertTrue(player.hasSaveFolder())
        assertEquals(
            listOf(SaveDeclaration("savedata", "content://aenu.aps3e.provider/tree/aps3e")),
            player.saveDeclarations(game),
        )
        assertEquals("content://aenu.aps3e.provider/root/root", player.suggestedSaveFolder)
        assertEquals(setOf("config"), player.saveFolderMarkers)
    }

    @Test
    fun aPupIsStagedWhereTheInstallerLooks() {
        assertEquals("firmware/PS3UPDAT.PUP", player.firmwareFileName("PS3UPDAT.PUP"))
        assertEquals("firmware/PS3UPDAT.PUP", player.firmwareFileName("update.pup"))
        assertEquals("firmware/PS3UPDAT.PUP", player.firmwareFileName("Update.PUP"))
        for (name in listOf("", " ", ".", "..", "notes.txt", "a/b.pup", "a\\b.pup", "firmware/PS3UPDAT.PUP")) {
            assertNull(name, player.firmwareFileName(name))
        }
    }

    @Test
    fun theNewestPupInTheHeaderIsTheOneWritten() {
        val older = offer("4.91.pup", pup("4.91\n"))
        val newest = offer("4.93.pup", pup("4.93\n"))
        val tied = offer("also-4.93.pup", pup("4.93\n"))
        val longer = offer("4.100.pup", pup("4.100\n"))
        val finer = offer("4.93.1.pup", pup("4.93.1\n"))
        val garbage = offer("notes.bin", ByteArray(16) { 1 })
        val unreadable = offer("later.pup", pup(version = null))
        val words = offer("words.pup", pup("not a version\n"))
        assertEquals(listOf(newest), player.chooseFirmware(listOf(older, newest, garbage)))
        assertEquals(listOf(older), player.chooseFirmware(listOf(older, offer("second.pup", pup("4.91\n")))))
        assertEquals(listOf(newest), player.chooseFirmware(listOf(newest, tied)))
        assertEquals(listOf(longer), player.chooseFirmware(listOf(newest, longer)))
        assertEquals(listOf(finer), player.chooseFirmware(listOf(newest, finer)))
        assertEquals(listOf(older), player.chooseFirmware(listOf(words, older)))
        assertEquals(listOf(words), player.chooseFirmware(listOf(words, unreadable)))
        assertTrue(player.chooseFirmware(listOf(garbage)).isEmpty())
        assertTrue(player.chooseFirmware(emptyList()).isEmpty())
    }

    @Test
    fun setupWaitsForTheWizardAndFirmware() {
        assertNull(player.setupSteps(folder("config/default_config.yml", "config/dev_flash/.installed")))
        assertEquals(Aps3ePlayer.NEEDS_FIRMWARE, player.setupSteps(folder()))
        assertEquals(Aps3ePlayer.NEEDS_FIRMWARE, player.setupSteps(folder("config/default_config.yml")))
        assertEquals(
            Aps3ePlayer.FINISH_SETUP,
            player.setupSteps(folder("firmware/PS3UPDAT.PUP", "config/dev_flash/.installed")),
        )
        assertEquals(
            Aps3ePlayer.INSTALL_FIRMWARE,
            player.setupSteps(folder("firmware/PS3UPDAT.PUP", "config/default_config.yml")),
        )
        assertFalse(player.needsFirmware(folder("config/dev_flash/.installed")))
        assertTrue(player.needsFirmware(folder()))
        assertTrue(player.needsFirmware(folder("config/default_config.yml", "firmware/PS3UPDAT.PUP")))
    }

    @Test
    fun savesAreTheFoldersNamedWithTheTitleId() {
        val image = iso("PS3_GAME", "PARAM.SFO", sfo(mapOf("TITLE_ID" to "BLUS30464")))
        val found = player.findSaveFolders("savedata", Bytes(image), folder(), forLaunch = true)
        assertEquals(SaveFolderSearch("config/dev_hdd0/home/00000001/savedata", "BLUS30464"), found)
        assertNull(player.findSaveFolders("other", Bytes(image), folder(), forLaunch = true))
        assertNull(player.findSaveFolders("savedata", Bytes(ByteArray(32)), folder(), forLaunch = false))
    }

    @Test
    fun eachPackageLaunchesADecryptedIso() {
        val uri = "content://app.foldcade.romm.folders/document/Skate3.dec.iso"
        val encoded = "content://aenu.aps3e.provider/document/%2Fstorage%2Femulated%2F0%2Froms%2FSkate3.dec.iso"
        for (packageName in Aps3ePlayer.PACKAGES) {
            for (target in listOf(uri, encoded, "content://games/Title.ISO")) {
                val intent = launch(target, packageName)
                assertEquals(packageName, intent.packageName)
                assertEquals("aenu.aps3e.EmulatorActivity", intent.componentClass)
                assertEquals("aenu.intent.action.APS3E", intent.action)
                assertEquals(target, intent.dataUri)
                assertEquals(listOf(PlayerExtra.Text("iso_uri", target)), intent.extras)
                assertTrue(intent.grantReadUri)
                assertEquals(setOf(LaunchFlag.NewTask, LaunchFlag.ClearTop, LaunchFlag.ClearTask), intent.flags)
            }
        }
    }

    @Test
    fun anythingButADecryptedIsoIsRefused() {
        for (uri in listOf("file:///sdcard/Game.iso", "/sdcard/Game.iso", "content://games/Game.iso.chd", "content://games/folder")) {
            val error = runCatching { launch(uri) }.exceptionOrNull()
            assertTrue(uri, error is PluginException.NotFound)
        }
        val message = runCatching { launch("content://games/Game.bin") }.exceptionOrNull()
        assertEquals("aPS3e opens PlayStation 3 games as decrypted .iso files.", message?.message)
        assertTrue(runCatching { launch("content://games/a.iso", "com.armsx2") }.exceptionOrNull() is PluginException.NotFound)
        val appRef = runCatching {
            player.launchIntent(
                LaunchRequest(game, LaunchTarget.AppRef(mapOf("app_id" to "1")), Aps3ePlayer.PACKAGES.first()),
            )
        }
        assertTrue(appRef.exceptionOrNull() is PluginException.NotFound)
        assertEquals("aPS3e needs a content URI.", appRef.exceptionOrNull()?.message)
    }

    private class Bytes(private val data: ByteArray) : GameFileReader {
        override fun read(offset: Long, length: Int): ByteArray? {
            if (offset < 0 || length < 0 || offset > data.size) return null
            val start = offset.toInt()
            val end = (start.toLong() + length).coerceAtMost(data.size.toLong()).toInt()
            return data.copyOfRange(start, end)
        }
    }
}

private fun offer(name: String, header: ByteArray) = FirmwareOffer(name, header)

/** A PUP header with `version.txt` at [version], or with that entry past the header when null. */
private fun pup(version: String?): ByteArray {
    val header = ByteArray(0x1000)
    byteArrayOf(0x53, 0x43, 0x45, 0x55, 0x46, 0, 0, 0).copyInto(header)
    putU64(header, 24, 1)
    putU64(header, 48, 0x100)
    if (version == null) {
        putU64(header, 56, 0x2000)
        putU64(header, 64, 4)
    } else {
        val bytes = version.toByteArray(Charsets.UTF_8)
        putU64(header, 56, 80)
        putU64(header, 64, bytes.size.toLong())
        bytes.copyInto(header, 80)
    }
    return header
}

private fun putU64(bytes: ByteArray, at: Int, value: Long) {
    for (index in 7 downTo 0) bytes[at + (7 - index)] = (value ushr (index * 8)).toByte()
}

private class Listed(private val paths: Set<String>) : SaveFolderAccess {
    override fun list(path: String): List<SaveFolderEntry>? {
        val prefix = if (path.isEmpty()) "" else "$path/"
        if (path.isNotEmpty() && paths.none { it == path || it.startsWith(prefix) }) return null
        val names = paths.mapNotNull { file ->
            val rest = if (path.isEmpty()) file else file.removePrefix(prefix).takeIf { file.startsWith(prefix) } ?: return@mapNotNull null
            rest.substringBefore('/').takeIf { it.isNotEmpty() }
        }.distinct()
        return names.map { name ->
            val child = if (path.isEmpty()) name else "$path/$name"
            SaveFolderEntry(name, paths.any { it.startsWith("$child/") }, 0)
        }
    }

    override fun read(path: String, limit: Int): ByteArray? = null
    override fun read(path: String, offset: Long, length: Int): ByteArray? = null
    override fun write(path: String, bytes: ByteArray) = false
    override fun createFolder(path: String) = false
    override fun moveToBackup(path: String) = false
}

private fun folder(vararg paths: String) = Listed(paths.toSet())
