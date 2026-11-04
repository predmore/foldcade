package app.foldcade

import app.foldcade.language.LibraryFolder
import app.foldcade.language.PickerModel
import app.foldcade.language.PlayerSaveSetting
import app.foldcade.language.StreamHostSetting

/**
 * Debug builds only: a Settings shelf full of stand-in emulators and library sources, so a
 * long shelf can be seen on the Thor without installing a dozen apps. HomeActivity turns it
 * on from `--ei foldcade.shelfPreview <count>` and 0 turns it off. The stand-ins wear real
 * packages' art; every third is not syncing, and one folder waits for its grant.
 */
internal object ShelfPreview {
    private const val ID = "preview."
    private const val URI = "preview://folder/"

    private val emulators = listOf(
        "PPSSPP" to "org.ppsspp.ppsspp",
        "Dolphin" to "org.dolphinemu.dolphinemu",
        "Flycast" to "com.flycast.emulator",
        "M64Plus FZ" to "org.mupen64plusae.v3.fzurita",
        "Pizza Boy" to "it.dbtecno.pizzaboypro",
        "Snes9x EX+" to "com.explusalpha.Snes9xPlus",
        "GBA.emu" to "com.explusalpha.GbaEmu",
        "MD.emu" to "com.explusalpha.MdEmu",
        "NES.emu" to "com.explusalpha.NesEmu",
        "Saturn.emu" to "com.explusalpha.SaturnEmu",
        "Redream" to "io.recompiled.redream",
        "Yaba Sanshiro" to "org.devmiyax.yabasanshioro2",
        "Citra" to "org.citra.citra_emu",
        "AetherSX2" to "xyz.aethersx2.android",
        "My Boy!" to "com.fastemulator.gba",
        "Winlator" to "com.winlator",
    )

    private val folders = listOf("ROMs", "Emulation", "SD card", "Downloads", "Arcade", "Handhelds")
    private val hosts = listOf("game-win", "living-room", "steam-deck")

    /** [model] with stand-ins topping its own emulators up to [count] tiles, and its library to [count] sources. */
    fun apply(model: PickerModel, count: Int): PickerModel {
        val players = model.playerSaves.filterNot { it.playerId.startsWith(ID) }
        val sources = model.folders.filterNot { it.uri.startsWith(URI) }
        val computers = model.streamHosts.filterNot { it.key.startsWith(ID) }
        if (count <= 0) {
            if (players.size == model.playerSaves.size && sources.size == model.folders.size && computers.size == model.streamHosts.size) return model
            return model.copy(playerSaves = players, folders = sources, streamHosts = computers)
        }
        val real = players.count { it.installed }
        val fakes = emulators.take((count - real).coerceAtLeast(0)).mapIndexed { index, (label, packageName) ->
            PlayerSaveSetting("$ID$index", label, chosen = index % 3 != 1, installed = true, packages = listOf(packageName))
        }
        // RomM, real folders and computers, and Add already take some of the library's places.
        val room = (count - 1 - sources.size - computers.size - model.signedIn.size).coerceAtLeast(0)
        val extraHosts = hosts.take(room / 3).mapIndexed { index, name -> StreamHostSetting("$ID$index", name, paired = index != 1) }
        val extraFolders = folders.take(room - extraHosts.size).mapIndexed { index, label ->
            LibraryFolder("$URI$index", label, pickAgain = index == 2, games = 12 + index * 7)
        }
        return model.copy(playerSaves = players + fakes, folders = sources + extraFolders, streamHosts = computers + extraHosts)
    }
}
