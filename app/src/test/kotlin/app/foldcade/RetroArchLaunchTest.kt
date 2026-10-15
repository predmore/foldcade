package app.foldcade

import android.content.pm.PackageInfo
import app.foldcade.api.plugin.Availability
import app.foldcade.api.plugin.Game
import app.foldcade.api.plugin.LaunchTarget
import app.foldcade.api.plugin.Player
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.plugins.emulators.EmulatorsEntry
import app.foldcade.plugins.melonds.MelonDsPlayer
import app.foldcade.plugins.retroarch.Core
import app.foldcade.plugins.retroarch.RetroArchEntry
import app.foldcade.plugins.retroarch.RetroArchPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroArchLaunchTest {
    private val retroArch = RetroArchEntry()
    private val emulators = EmulatorsEntry()

    private fun retroArch(platformId: String) =
        retroArch.players.single { it.platformId == platformId } as RetroArchPlayer

    private fun game(platformId: String) = Game(
        backendId = "local-folder",
        remoteKey = "content://games/title",
        platformId = platformId,
        availability = Availability.LocalOnly,
        label = "Title",
    )

    private fun pick(players: List<Player>, installed: Set<String>): Player? = preferredPlayer(
        players,
        runnable = { runnableWith(it, it.packageNames.firstOrNull { name -> name in installed }) },
        rank = ::playerRank,
        installed = { it in installed },
    )

    private val gba get() = emulators.players.filter { it.platformId == "game-boy-advance" } + retroArch("game-boy-advance")

    @Test
    fun retroArchWithACoreGoesAheadOfAStandaloneEmulator() {
        retroArch.cores.update(mapOf(AARCH64 to setOf("mgba_libretro_android.so")))
        assertEquals("retroarch.game-boy-advance", pick(gba, setOf(MY_BOY, AARCH64))?.id)
    }

    @Test
    fun retroArchWithoutAKnownCoreGivesWay() {
        retroArch.cores.update(mapOf(AARCH64 to setOf("snes9x_libretro_android.so")))
        assertEquals("my-boy.game-boy-advance", pick(gba, setOf(MY_BOY, AARCH64))?.id)
        retroArch.cores.update(mapOf(AARCH64 to null))
        assertEquals("my-boy.game-boy-advance", pick(gba, setOf(MY_BOY, AARCH64))?.id)
        // Alone, it still opens, so the launch can say which core is missing.
        assertEquals("retroarch.game-boy-advance", pick(gba, setOf(AARCH64))?.id)
        // With neither installed, the missing-player dialog names RetroArch first.
        assertEquals("retroarch.game-boy-advance", pick(gba, emptySet())?.id)
    }

    @Test
    fun melonDsKeepsTheDs() {
        retroArch.cores.update(mapOf(AARCH64 to setOf("melondsds_libretro_android.so")))
        // RetroArch registered first must still give way.
        val ds = listOf(retroArch("nintendo-ds"), MelonDsPlayer())
        assertEquals("melonds", pick(ds, setOf(AARCH64, MelonDsPlayer.STABLE_PACKAGE))?.id)
        assertEquals("retroarch.nintendo-ds", pick(ds, setOf(AARCH64))?.id)
    }

    @Test
    fun storageComesBeforeCoresAndAnUnknownCoreIsOfferedOnce() {
        val genesis = retroArch("genesis")
        retroArch.cores.update(mapOf(AARCH64 to null))
        assertEquals(
            RetroArchGate.Storage(RetroArchStorage.NotAllowed),
            retroArchGate(genesis, AARCH64, RetroArchStorage.NotAllowed, coresOffered = false),
        )
        assertEquals(RetroArchGate.OfferCores, retroArchGate(genesis, AARCH64, RetroArchStorage.Granted, coresOffered = false))
        assertEquals(RetroArchGate.Ready, retroArchGate(genesis, AARCH64, RetroArchStorage.Granted, coresOffered = true))
        retroArch.cores.update(mapOf(AARCH64 to emptySet()))
        assertEquals(
            RetroArchGate.MissingCore(Core("genesis_plus_gx", "Genesis Plus GX")),
            retroArchGate(genesis, AARCH64, RetroArchStorage.Granted, coresOffered = true),
        )
        retroArch.cores.update(mapOf(AARCH64 to setOf("picodrive_libretro_android.so")))
        assertEquals(RetroArchGate.Ready, retroArchGate(genesis, AARCH64, RetroArchStorage.Granted, coresOffered = false))
    }

    @Test
    fun storageFollowsWhatRetroArchAsksFor() {
        fun info(vararg permissions: Pair<String, Boolean>) = PackageInfo().apply {
            requestedPermissions = permissions.map { it.first }.toTypedArray()
            requestedPermissionsFlags = permissions.map {
                if (it.second) PackageInfo.REQUESTED_PERMISSION_GRANTED else 0
            }.toIntArray()
        }
        val read = "android.permission.READ_EXTERNAL_STORAGE"
        val write = "android.permission.WRITE_EXTERNAL_STORAGE"
        assertEquals(RetroArchStorage.Granted, retroArchStorage(info(write to true, read to true)))
        assertEquals(RetroArchStorage.NotAllowed, retroArchStorage(info(write to false, read to false)))
        assertEquals(
            RetroArchStorage.Granted,
            retroArchStorage(info("android.permission.MANAGE_EXTERNAL_STORAGE" to false, read to false)),
        )
        assertEquals(RetroArchStorage.Never, retroArchStorage(info("android.permission.INTERNET" to true)))
    }

    @Test
    fun aContentUriBecomesItsSharedPathOrBlocksTheLaunch() {
        retroArch.cores.update(mapOf(AARCH64 to setOf("mgba_libretro_android.so")))
        val player = retroArch("game-boy-advance")
        val uri = "content://com.android.externalstorage.documents/document/primary%3AROMs%2Fgba%2FGame.gba"
        val ready = planPlayerLaunch(
            player = player,
            game = game(player.platformId),
            target = LaunchTarget.ContentUri(uri),
            installedPackages = setOf(AARCH64),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
            saveFolderSettled = true,
            sharedFile = { SharedPaths.externalStoragePath(it)?.let { path -> LaunchTarget.SharedFile(path, it) } },
        ) as PlayerLaunch.Ready
        assertTrue(PlayerExtra.Text("ROM", "/storage/emulated/0/ROMs/gba/Game.gba") in ready.intent.extras)
        val blocked = planPlayerLaunch(
            player = player,
            game = game(player.platformId),
            target = LaunchTarget.ContentUri("content://app.foldcade.romm.cache/romm/roms/1/Game.gba"),
            installedPackages = setOf(AARCH64),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
            saveFolderSettled = true,
        ) as PlayerLaunch.Blocked
        assertEquals(LaunchBlock.NoSharedPath, blocked.block)
        assertEquals("RetroArch", blocked.playerName)
    }

    @Test
    fun theSaveFolderIsAskedForOnceForEverySystem() {
        val ids = retroArch.players.map { it.saveFolderId() }.toSet()
        assertEquals(setOf("retroarch"), ids)
        assertEquals("melonds", MelonDsPlayer().saveFolderId())
        val decision = planPlayerLaunch(
            player = retroArch("snes"),
            game = game("snes"),
            target = LaunchTarget.SharedFile("/storage/emulated/0/ROMs/snes/Game.sfc", "content://x"),
            installedPackages = setOf(AARCH64),
            anotherBothPanelRunning = false,
            closeConfirmed = false,
            saveFolderSettled = false,
        ) as PlayerLaunch.Blocked
        assertEquals(LaunchBlock.SaveFolder, decision.block)
    }

    @Test
    fun everyRetroArchSystemIsAFolderPlatformAndEveryPackageIsQueried() {
        val folder = app.foldcade.localfolder.LocalFolderEntry().platforms.map { it.id }.toSet()
        retroArch.players.forEach { assertTrue(it.id, it.platformId in folder) }
        val xml = java.io.File("src/main/AndroidManifest.xml").readText()
        app.foldcade.plugins.retroarch.PACKAGES.forEach { name ->
            assertTrue(name, xml.contains("android:name=\"$name\""))
        }
    }

    private companion object {
        const val AARCH64 = "com.retroarch.aarch64"
        const val MY_BOY = "com.fastemulator.gba"
    }
}
