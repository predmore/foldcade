package app.foldcade

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ThorPresetsTest {
    private val defaults = "[Renderer]\nresolution_factor = 1\nshaders_accurate_mul = false\ngraphics_api = 2\n[Layout]\nlayout_option = 2\nsecondary_display_layout = 0\n"

    @Test fun `patch only upstream defaults and preserve unrelated bytes`() {
        val original = "# Mine\r\n[Renderer]\r\nresolution_factor = 3\r\nshaders_accurate_mul = false\r\ngraphics_api = 1\r\n[Layout]\r\nlayout_option = 5\r\nsecondary_display_layout = 0\r\n[Storage]\r\nsecondary_display_layout = 2\r\n"
        val plan = azaharThorPreset(original.toByteArray())!!
        assertEquals(setOf("shaders_accurate_mul", "secondary_display_layout"), plan.edits.map { it.key }.toSet())
        assertEquals(original.replace("shaders_accurate_mul = false", "shaders_accurate_mul = true")
            .replace("secondary_display_layout = 0", "secondary_display_layout = 4"), plan.bytes.toString(Charsets.UTF_8))
    }

    @Test fun `case insensitive native keys preserve custom choices and detect duplicate keys`() {
        val text = "[layout]\nLAYOUT_OPTION = 5\nSECONDARY_DISPLAY_LAYOUT = 2\n[renderer]\nRESOLUTION_FACTOR = 6\nSHADERS_ACCURATE_MUL = true\n"
        assertTrue(azaharThorPreset(text.toByteArray())!!.edits.isEmpty())
        assertNull(azaharThorPreset("[Layout]\nlayout_option=2\nLAYOUT_OPTION=5".toByteArray()))
        assertNull(azaharThorPreset("[Layout]\n[LAYOUT]".toByteArray()))
    }

    @Test fun `missing and blank native values use android defaults in Layout`() {
        val plan = azaharThorPreset("[Renderer]\nresolution_factor = \n[Storage]\nsecondary_display_layout = \n".toByteArray())!!
        assertEquals(4, plan.edits.size)
        val output = plan.bytes.toString(Charsets.UTF_8)
        assertTrue(output.contains("[Layout]\nlayout_option = 1\nsecondary_display_layout = 4"))
        assertTrue(output.contains("[Storage]\nsecondary_display_layout = \n"))
        assertTrue(azaharThorPreset(plan.bytes)!!.edits.isEmpty())
    }

    @Test fun `ambiguous malformed and non UTF8 config stays intact`() {
        for (text in listOf("[Layout]\nlayout_option=2\nlayout_option=5", "[Layout]\n[Layout]", "[Layout", "bad line", "[Layout]\n\u0000")) {
            assertNull(azaharThorPreset(text.toByteArray()))
        }
        assertNull(azaharThorPreset(byteArrayOf(0xc3.toByte(), 0x28)))
    }

    @Test fun `record is never reapplied even after player returns to defaults`() {
        val root = Files.createTempDirectory("thor-presets")
        val file = MemoryFile(defaults.toByteArray())
        assertEquals(ThorPresetStatus.Applied, ThorPresets(root).apply("azahar", file) { true })
        val original = Files.walk(root).use { it.filter { p -> p.fileName.toString() == "original.ini" }.findFirst().get() }
        assertArrayEquals(defaults.toByteArray(), Files.readAllBytes(original))
        assertArrayEquals(defaults.toByteArray(), file.backups.values.first())
        file.bytes = defaults.toByteArray()
        assertEquals(ThorPresetStatus.Applied, ThorPresets(root).apply("azahar", file) { true })
        assertArrayEquals(defaults.toByteArray(), file.bytes)
        assertEquals(1, file.writes)
        assertEquals(4, ThorPresets(root).edits("azahar").size)
    }

    @Test fun `already tuned config is byte identical and consumed once`() {
        val file = MemoryFile(azaharThorPreset(defaults.toByteArray())!!.bytes)
        val root = Files.createTempDirectory("thor-presets")
        assertEquals(ThorPresetStatus.Kept, ThorPresets(root).apply("azahar", file) { true })
        file.bytes = defaults.toByteArray()
        assertEquals(ThorPresetStatus.Kept, ThorPresets(root).apply("azahar", file) { true })
        assertEquals(0, file.writes)
        assertFalse(ThorPresets(root).canRestore("azahar"))
    }

    @Test fun `running emulator is left alone and can apply after it stops`() {
        val root = Files.createTempDirectory("thor-presets")
        val file = MemoryFile(defaults.toByteArray())
        assertEquals(ThorPresetStatus.CloseEmulator, ThorPresets(root).apply("azahar", file) { false })
        assertTrue(file.backups.isEmpty())
        assertEquals(ThorPresetStatus.Ready, ThorPresets(root).status("azahar", file))
        assertEquals(ThorPresetStatus.Applied, ThorPresets(root).apply("azahar", file) { true })
    }

    @Test fun `last moment launch or config change prevents replacement`() {
        for (launch in listOf(true, false)) {
            val root = Files.createTempDirectory("thor-presets")
            val file = MemoryFile(defaults.toByteArray())
            file.beforeReplace = { if (!launch) file.bytes = "player changed this".toByteArray() }
            var checks = 0
            assertEquals(ThorPresetStatus.Failed, ThorPresets(root).apply("azahar", file) { ++checks == 1 || !launch })
            assertEquals(0, file.writes)
        }
    }

    @Test fun `known failure with unchanged file can retry using its existing adjacent backup`() {
        val folder = Files.createTempDirectory("thor-retry").toRealPath()
        val path = folder.resolve("config.ini")
        Files.writeString(path, defaults)
        val file = LocalPresetFile(path)
        val presets = ThorPresets(folder.resolve("private"))
        var checks = 0
        assertEquals(ThorPresetStatus.Failed, presets.apply("azahar", file) { ++checks == 1 })
        assertEquals(ThorPresetStatus.Failed, presets.status("azahar", file))
        assertFalse(presets.canRestore("azahar"))
        assertTrue(file.hasPriorBackup())
        assertTrue(presets.canRetry("azahar"))
        assertEquals(ThorPresetStatus.Applied, ThorPresets(folder.resolve("private")).apply("azahar", file) { true })
    }

    @Test fun `death after atomic replacement does not reapply on restart`() {
        val root = Files.createTempDirectory("thor-presets")
        val file = MemoryFile(defaults.toByteArray())
        file.afterReplace = { throw SimulatedDeath() }
        try { ThorPresets(root).apply("azahar", file) { true }; fail() } catch (_: SimulatedDeath) { }
        assertEquals(ThorPresetStatus.Applied, ThorPresets(root).status("azahar", file))
        file.bytes = defaults.toByteArray()
        file.afterReplace = {}
        assertEquals(ThorPresetStatus.Kept, ThorPresets(root).apply("azahar", file) { true })
        assertEquals(1, file.writes)
        assertTrue(ThorPresets(root).canRestore("azahar"))
    }

    @Test fun `backup failure never changes external config`() {
        val file = MemoryFile(defaults.toByteArray())
        file.failBackup = true
        try { ThorPresets(Files.createTempDirectory("thor-presets")).apply("azahar", file) { true }; fail() }
        catch (_: IllegalStateException) { }
        assertEquals(0, file.writes)
        assertArrayEquals(defaults.toByteArray(), file.bytes)
    }

    @Test fun `full restore keeps later player choices in both backups and disables reapply`() {
        val root = Files.createTempDirectory("thor-presets")
        val file = MemoryFile(defaults.toByteArray())
        val presets = ThorPresets(root)
        presets.apply("azahar", file) { true }
        val player = "[Renderer]\nresolution_factor=7\n".toByteArray()
        file.bytes = player
        assertEquals(ThorPresetStatus.CloseEmulator, presets.restore("azahar", file) { false })
        assertEquals(ThorPresetStatus.Restored, presets.restore("azahar", file) { true })
        assertArrayEquals(defaults.toByteArray(), file.bytes)
        assertTrue(file.backups.values.any { it.contentEquals(player) })
        assertTrue(Files.walk(root).use { it.anyMatch { p -> Files.isRegularFile(p) && Files.readAllBytes(p).contentEquals(player) } })
        assertFalse(presets.canRestore("azahar"))
        assertEquals(ThorPresetStatus.Restored, ThorPresets(root).apply("azahar", file) { true })
        assertEquals(2, file.writes)
    }

    @Test fun `absent config can be preset and restored to absent`() {
        val file = MemoryFile(null)
        val presets = ThorPresets(Files.createTempDirectory("thor-presets"))
        assertEquals(ThorPresetStatus.Applied, presets.apply("azahar", file) { true })
        assertEquals(ThorPresetStatus.Restored, presets.restore("azahar", file) { true })
        assertNull(file.bytes)
    }

    @Test fun `surviving adjacent backup prevents reapplication after reinstall`() {
        val folder = Files.createTempDirectory("thor-reinstall").toRealPath()
        val path = folder.resolve("config.ini")
        Files.writeString(path, defaults)
        val file = LocalPresetFile(path)
        assertEquals(ThorPresetStatus.Applied, ThorPresets(folder.resolve("first-install")).apply("azahar", file) { true })
        Files.writeString(path, defaults)
        assertEquals(ThorPresetStatus.Kept, ThorPresets(folder.resolve("reinstall")).apply("azahar", file) { true })
        assertEquals(defaults, Files.readString(path))
    }

    @Test fun `local replacement and restore leave no partial files`() {
        val folder = Files.createTempDirectory("thor-local").toRealPath()
        val path = folder.resolve("config.ini")
        Files.writeString(path, defaults)
        val presets = ThorPresets(folder.resolve("private"))
        val file = LocalPresetFile(path)
        assertEquals(ThorPresetStatus.Applied, presets.apply("azahar", file) { true })
        assertEquals(ThorPresetStatus.Restored, presets.restore("azahar", file) { true })
        assertEquals(defaults, Files.readString(path))
        assertFalse(Files.list(folder).use { it.anyMatch { p -> p.fileName.toString().endsWith(".tmp") } })
    }

    private class SimulatedDeath : Error()
    private class MemoryFile(var bytes: ByteArray?) : PresetFile {
        val backups = linkedMapOf<String, ByteArray>()
        var writes = 0
        var failBackup = false
        var beforeReplace: () -> Unit = {}
        var afterReplace: () -> Unit = {}
        override fun read() = bytes?.copyOf()
        override fun backup(name: String, bytes: ByteArray) { check(!failBackup); backups[name] = bytes.copyOf() }
        override fun replace(expected: ByteArray?, bytes: ByteArray?, stopped: () -> Boolean): Boolean {
            beforeReplace()
            if (!((expected == null && this.bytes == null) || (expected != null && this.bytes?.contentEquals(expected) == true)) || !stopped()) return false
            this.bytes = bytes?.copyOf()
            writes++
            afterReplace()
            return true
        }
    }
}
