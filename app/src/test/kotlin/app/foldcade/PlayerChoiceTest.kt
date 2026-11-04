package app.foldcade

import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.plugins.melonds.MelonDsPlayer
import app.foldcade.plugins.retroarch.CoreChoice
import app.foldcade.plugins.retroarch.RetroArchEntry
import app.foldcade.plugins.retroarch.RetroArchPlayer
import org.junit.Assert.*
import org.junit.Test

class PlayerChoiceTest {
    @Test fun extractingSavePlayersPreservesStandalonePreference() {
        val all = app.foldcade.plugins.explus.ExPlusEntry().players + app.foldcade.plugins.flycast.FlycastEntry().players +
            app.foldcade.plugins.emulators.EmulatorsEntry().players
        fun ordered(platform: String) = all.filter { it.platformId == platform }.sortedBy(::playerRank).map { it.displayName }
        assertEquals(listOf("My Boy!", "Pizza Boy GBA", "GBA.emu"), ordered("game-boy-advance"))
        assertEquals(listOf("My OldBoy!", "Pizza Boy GBC", "GBC.emu"), ordered("game-boy"))
        assertEquals(listOf("MD.emu", "Pizza Boy SC"), ordered("genesis"))
        assertEquals(listOf("Flycast", "Redream"), ordered("dreamcast"))
    }
    private val retro = RetroArchEntry()
    private val ds = retro.players.filterIsInstance<RetroArchPlayer>().first { it.platformId == "nintendo-ds" }
    private val melon = MelonDsPlayer()
    private val players = listOf<Player>(ds, melon)
    private val core = PlayerChoice(ds.id, "melonds", "melonDS (RetroArch)")
    private val standalone = PlayerChoice(melon.id, label = melon.displayName)
    private val files = setOf("melondsds_libretro_android.so", "melonds_libretro_android.so")
    private val game = Game("romm", "42", "nintendo-ds", Availability.Cached, "Tetris")

    private fun resolve(
        game: PlayerChoice? = null, system: PlayerChoice? = null,
        installed: (Player) -> Boolean = { true }, visible: Set<String>? = files,
    ): PlayerResolution {
        ds.cores.update(mapOf(PKG to visible))
        return resolvePlayer(players, game, system, installed, runnable = { it !is RetroArchPlayer || it.ready(PKG) },
            rank = ::playerRank, coreFiles = { if (it is RetroArchPlayer) visible else null })
    }

    @Test fun gameThenSystemThenAutomatic() {
        assertEquals(PlayerChoiceSource.Game, resolve(core, standalone).source)
        assertEquals("melonds", (resolve(core, standalone).player as RetroArchPlayer).pinnedCore)
        assertEquals(PlayerChoiceSource.System, resolve(system = core).source)
        val auto = resolve()
        assertEquals(PlayerChoiceSource.Automatic, auto.source)
        assertSame(melon, auto.player)
        assertSame(preferredPlayer(players, runnable = { true }, rank = ::playerRank, installed = { true }), auto.player)
    }

    @Test fun missingRegistrationAndRetiredCoresFallThroughButMissingDownloadsKeepTheChoice() {
        val gone = PlayerChoice("old.plugin", label = "Old emulator")
        val missingCore = core.copy(core = "desmume", label = "DeSmuME (RetroArch)")
        val result = resolve(gone, missingCore)
        assertEquals("desmume", (result.player as RetroArchPlayer).pinnedCore)
        assertTrue((result.player as RetroArchPlayer).coreChoice(PKG) is CoreChoice.Missing)
        assertEquals(listOf(gone), result.skipped)
        val packageGone = resolve(standalone, core, installed = { it is RetroArchPlayer })
        assertEquals(PlayerChoiceSource.System, packageGone.source)
        assertEquals(listOf(standalone), packageGone.skipped)
        val coreGoneAfterUpdate = resolve(core.copy(core = "retired-core"), standalone, visible = null)
        assertEquals(PlayerChoiceSource.System, coreGoneAfterUpdate.source)
    }

    @Test fun unknownCoresKeepThePinAndTheOfferCoresGate() {
        val result = resolve(core, visible = null)
        val player = result.player as RetroArchPlayer
        assertEquals(PlayerChoiceSource.Game, result.source)
        assertTrue(result.skipped.isEmpty())
        assertEquals("melonds", (player.coreChoice(PKG) as CoreChoice.Unknown).assumed.file)
        assertEquals(RetroArchGate.OfferCores, retroArchGate(player, PKG, RetroArchStorage.Granted, coresOffered = false))
    }

    @Test fun automaticCoreUsesTheInjectedVisibilityWithoutReadingMutablePluginState() {
        val result = resolvePlayer(players, installed = { it is RetroArchPlayer }, rank = ::playerRank,
            coreFiles = { setOf("melonds_libretro_android.so") })
        assertEquals("melonds", (result.player as RetroArchPlayer).pinnedCore)
    }

    @Test fun nothingInstalledStillNamesTheFirstAutomaticPlayer() {
        val result = resolve(core, standalone, installed = { false })
        assertSame(melon, result.player)
        assertEquals(PlayerChoiceSource.Automatic, result.source)
        assertEquals(listOf(core, standalone), result.skipped)
        assertNull(resolvePlayer(emptyList(), core, installed = { false }).player)
    }

    @Test fun optionsExpandVisibleCoresAndKeepRankOrder() {
        val options = playerOptions(players, installed = { true }, rank = ::playerRank,
            coreFiles = { if (it is RetroArchPlayer) files else null })
        assertEquals(listOf(standalone, PlayerChoice(ds.id, "melondsds", "melonDS DS (RetroArch)"), core), options)
        assertEquals(listOf(standalone, PlayerChoice(ds.id, label = "RetroArch")),
            playerOptions(players, installed = { true }, rank = ::playerRank))
        assertEquals(listOf(standalone), playerOptions(players, installed = { true }, rank = ::playerRank, coreFiles = { emptySet() }))
        assertTrue(playerOptions(players, installed = { false }).isEmpty())
    }

    @Test fun pinnedCoreReachesLaunchPlanAndKeepsTheSameSaveFolderAfterCoreChanges() {
        val player = resolve(core).player as RetroArchPlayer
        ds.bindSaveFolder("content://saves")
        assertTrue(player.hasSaveFolder())
        assertEquals(ds.id, player.id)
        assertSame(ds.cores, player.cores)
        val plan = planPlayerLaunch(player, game, LaunchTarget.SharedFile("/roms/Tetris.nds", "content://roms/Tetris.nds"), setOf(PKG), false, false) as PlayerLaunch.Ready
        assertEquals("/data/user/0/$PKG/cores/melonds_libretro_android.so", plan.intent.extras.filterIsInstance<PlayerExtra.Text>().first { it.key == "LIBRETRO" }.value)
        assertEquals("melonDS/Tetris.srm", player.saveFileName("srm", "Tetris.nds"))
        assertFalse(plan.occupiesBothDisplays)
        ds.cores.update(mapOf(PKG to setOf("melondsds_libretro_android.so")))
        assertTrue(player.coreChoice(PKG) is CoreChoice.Missing)
        assertEquals("melonDS/Tetris.srm", player.saveFileName("srm", "Tetris.nds"))
    }

    @Test fun processDeathRestoresTheLaunchedCoreEvenAfterSettingsAndCoresChange() {
        val launched = resolve(core).player!!
        val stored = LibraryReturn("romm", game, launched).encode()
        ds.cores.update(mapOf(PKG to setOf("melondsds_libretro_android.so")))
        val restored = decodeLibraryReturn(stored) { id -> players.firstOrNull { it.id == id } }!!
        assertEquals("melonds", (restored.player as RetroArchPlayer).pinnedCore)
        assertEquals("melonDS/Tetris.srm", restored.player.saveFileName("srm", "Tetris.nds"))
        // Older records that predate core pins still decode.
        assertNotNull(decodeLibraryReturn(LibraryReturn("romm", game, melon).encode()) { melon })
    }

    @Test fun choiceStoresSurviveRestartAndDelimitersAndCanBeClearedIndependently() {
        val prefs = MemoryPrefs()
        val unusual = PlayerChoice("plugin|\n🥚", "core|file", "Name · café|\n")
        SessionStore(prefs).apply {
            setSystemPlayerChoice("system|id", unusual)
            setGamePlayerChoice("game|id\n", core)
        }
        val restarted = SessionStore(prefs)
        assertEquals(unusual, restarted.systemPlayerChoice("system|id"))
        assertEquals(core, restarted.gamePlayerChoice("game|id\n"))
        restarted.setGamePlayerChoice("game|id\n", null)
        assertNull(SessionStore(prefs).gamePlayerChoice("game|id\n"))
        assertEquals(unusual, SessionStore(prefs).systemPlayerChoice("system|id"))
        restarted.setSystemPlayerChoice("system|id", null)
        assertNull(SessionStore(prefs).systemPlayerChoice("system|id"))
        prefs.edit().putString("game_player:YnJva2Vu", "1|%|x|y").apply()
        assertNull(restarted.gamePlayerChoice("broken"))
    }

    @Test fun choicesTravelInASetupBackup() {
        val prefs = MemoryPrefs()
        SessionStore(prefs).apply {
            setSystemPlayerChoice("game-boy-advance", core)
            setGamePlayerChoice("tile", core)
        }
        val keys = prefs.all.keys
        assertEquals(2, keys.size)
        // Both are the player's own settings, so a backup carries them with the board they name.
        assertEquals(keys, app.foldcade.host.backup.BackupSettings.portable(prefs.all).keys)
    }

    companion object { const val PKG = "com.retroarch.aarch64" }
}
