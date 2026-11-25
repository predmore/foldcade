package app.foldcade.plugins.retroarch

import app.foldcade.api.plugin.SignalStrength
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveConfigTest {
    private val config = RetroArchPlayer.configPath("com.retroarch.aarch64")
    private val rom = "$SHARED_STORAGE/ROMs/GBA/Title.v2.gba"
    private val core = "mGBA"
    private val overrideDir = "$SHARED_STORAGE/RetroArch/config/$core"
    private val saveDir = "$SHARED_STORAGE/RetroArch/saves"
    private val global = """
        rgui_config_directory = "/sdcard/RetroArch/config"
        savefile_directory = "/storage/self/primary/RetroArch/saves"
        sort_savefiles_enable = "true"
        sort_savefiles_by_content_enable = "false"
        savefiles_in_content_dir = "false"
    """.trimIndent()

    private fun resolve(vararg overrides: Pair<String, RetroArchConfigFile>, base: String = global,
        isDirectory: (String) -> Boolean = { true }): RetroArchSavePath {
        val files = mapOf(config to RetroArchConfigFile.Read(base), *overrides)
        return retroArchSavePath(config, rom, core, { files[it] ?: RetroArchConfigFile.Missing }, isDirectory)
    }

    private fun proves(path: String) = RetroArchSavePath(path, SignalStrength.Proves)
    private fun cfg(text: String) = RetroArchConfigFile.Read(text.trimIndent())

    @Test fun globalCoreContentDirectoryAndGameOverridesStackInOrder() {
        val coreFile = "$overrideDir/mGBA.cfg" to cfg("""
            savefile_directory = "/sdcard/Core Saves"
            sort_savefiles_enable = false
        """)
        val contentFile = "$overrideDir/GBA.cfg" to cfg("""
            savefile_directory = "/sdcard/Content Saves"
            sort_savefiles_by_content_enable = 1
        """)
        val gameFile = "$overrideDir/Title.v2.cfg" to cfg("""
            savefile_directory = "/sdcard/Game Saves"
            sort_savefiles_enable = "true" # game takes precedence over core
        """)
        assertEquals(proves("$saveDir/mGBA/Title.v2.srm"), resolve())
        assertEquals(proves("$SHARED_STORAGE/Core Saves/Title.v2.srm"), resolve(coreFile))
        assertEquals(proves("$SHARED_STORAGE/Content Saves/GBA/Title.v2.srm"), resolve(coreFile, contentFile))
        assertEquals(proves("$SHARED_STORAGE/Game Saves/GBA/mGBA/Title.v2.srm"), resolve(coreFile, contentFile, gameFile))
    }

    @Test fun besideContentReplacesTheBaseBeforeContentAndCoreSorting() {
        val base = global.replace("savefiles_in_content_dir = \"false\"", "savefiles_in_content_dir = true")
        assertEquals(proves("$SHARED_STORAGE/ROMs/GBA/mGBA/Title.v2.srm"), resolve(base = base))
        val game = "$overrideDir/Title.v2.cfg" to cfg("sort_savefiles_by_content_enable = true")
        assertEquals(proves("$SHARED_STORAGE/ROMs/GBA/GBA/mGBA/Title.v2.srm"), resolve(game, base = base))
        assertEquals(proves("$SHARED_STORAGE/ROMs/GBA/Title.v2.srm"), resolve(base = base.replace("= \"true\"", "= false")))
    }

    @Test fun overrideActivationAndDirectoryComeFromTheGlobalConfig() {
        assertEquals(proves("$saveDir/mGBA/Title.v2.srm"), resolve(
            "$overrideDir/mGBA.cfg" to RetroArchConfigFile.Unreadable, base = "$global\nauto_overrides_enable = false"))
        val fallback = global.replace("/sdcard/RetroArch/config", "default")
        assertEquals(proves("$saveDir/Title.v2.srm"), resolve(
            "${config.substringBeforeLast('/')}/$core/Title.v2.cfg" to cfg("sort_savefiles_enable = false"), base = fallback))
        assertEquals(SignalStrength.Supports, resolve(base = global.substringAfter('\n')).strength)
    }

    @Test fun inaccessibleGlobalOrOverrideNeverProvesAPath() {
        assertEquals(RetroArchSavePath(null, SignalStrength.Supports), resolve(config to RetroArchConfigFile.Unreadable))
        assertEquals(SignalStrength.Supports, resolve("$overrideDir/Title.v2.cfg" to RetroArchConfigFile.Unreadable).strength)
        assertEquals(SignalStrength.Supports, resolve("$overrideDir/mGBA.cfg" to cfg("""#include "more.cfg"""")).strength)
        assertEquals(SignalStrength.Supports, resolve("$overrideDir/mGBA.cfg" to cfg("sort_savefiles_enable=false")).strength)
        assertEquals(SignalStrength.Supports, resolve("$overrideDir/mGBA.cfg" to cfg("sort_savefiles_enable = maybe")).strength)
    }

    @Test fun nonexistentSaveDirectoryOrSortFallbackIsUncertain() {
        assertEquals(SignalStrength.Supports, resolve(isDirectory = { false }).strength)
        assertEquals(RetroArchSavePath("$saveDir/mGBA/Title.v2.srm", SignalStrength.Supports), resolve(isDirectory = { it == saveDir }))
    }
}
