# Standalone emulators

Set up an emulator on your Thor, then add its games through [local folders](local-games.md) or [RomM](romm.md). Foldcade detects supported installed emulators; it does not import their game lists.

## Supported emulators and formats

Foldcade uses the first installed emulator in each row. There is currently no emulator selector in Settings.

[RetroArch](retroarch.md) with a core for a system goes ahead of these emulators, except on the Nintendo DS. It also runs systems this table doesn't list, such as the Sega CD, PC Engine, and Atari consoles.

| System | Example folder | Recognized game formats | Emulators, in priority order |
| --- | --- | --- | --- |
| Game Boy | `gb` | `.gb` | My OldBoy!, Pizza Boy GBC, GBC.emu |
| Game Boy Color | `gbc` | `.gbc` | My OldBoy!, Pizza Boy GBC, GBC.emu |
| Game Boy Advance | `gba` | `.gba` | My Boy!, Pizza Boy GBA, GBA.emu |
| NES / Famicom | `nes` | `.nes`, `.fds` | NES.emu |
| Super Nintendo | `snes` | `.sfc`, `.smc` | Snes9x EX+ |
| Nintendo 64 | `n64` | `.n64`, `.z64`, `.v64` | M64Plus FZ, Mupen64Plus AE |
| GameCube | `gamecube` | `.gcm`, `.iso`, `.rvz` | Dolphin, Dolphin MMJR |
| Wii | `wii` | `.wbfs`, `.iso`, `.rvz` | Dolphin, Dolphin MMJR |
| Nintendo Switch | `switch` | `.nsp`, `.xci` | Eden |
| Nintendo DS / DSi | `ds` | `.nds`, `.dsi` | melonDS |
| Nintendo 3DS | `3ds` | `.cci`, `.zcci`, `.3ds` | Azahar |
| PlayStation | `ps1` | `.cue`, `.chd`, `.pbp`, `.img`, `.iso`, `.m3u` | DuckStation |
| PlayStation 2 | `ps2` | `.iso`, `.chd`, `.m3u` | NetherSX2 / AetherSX2, ARMSX2 |
| PSP | `psp` | `.cso`, `.iso`, `.pbp` | PPSSPP |
| Genesis / Mega Drive | `genesis` | `.gen`, `.smd`, `.md` | MD.emu, Pizza Boy SC |
| Master System | `sms` | `.sms` | MD.emu, Pizza Boy SC |
| Game Gear | `gg` | `.gg` | Pizza Boy SC |
| Saturn | `saturn` | `.cue`, `.chd`, `.iso`, `.ccd`, `.mds`, `.m3u` | Yaba Sanshiro 2, Saturn.emu |
| Dreamcast | `dreamcast` | `.gdi`, `.cdi`, `.chd`, `.cue`, `.iso`, `.m3u` | Flycast, Redream |

These are formats Foldcade recognizes; the emulator must support them too. `.zip` and `.7z` archives are also listed inside system folders; extract them if the emulator requires it.

Shared formats need a [system folder](local-games.md#folder-names-and-formats).

## Azahar: Nintendo 3DS

Install [Azahar](https://github.com/azahar-emu/azahar), complete its setup, and configure its layout for the Thor's two screens. Add your `3ds` folder or a RomM server, then select a game. Foldcade starts Azahar on the main display and reserves both screens.

Prefer decrypted `.cci` or `.zcci` games. `.3ds` is best-effort; Foldcade does not decrypt or convert it. `.cia` files are not listed by the local-folder scanner.

Both regular and Google Play builds are recognized; regular has priority when both are installed.

For [RomM save sync](romm.md#save-sync), choose Azahar's user folder, the one that holds `sdmc`, `nand` and `config`, in **Foldcade Settings › Emulators › Azahar**. Foldcade moves a game's save data and extra data there before launch and reads them back when you return. Choose **Not now** at the save-folder prompt to play with Azahar's own saves.

## melonDS: Nintendo DS and DSi

Install [melonDS Android](https://github.com/rafaelvcaetano/melonDS-android) 2.0.0 or newer. Configure the DS top screen on the Thor's main display and its touch screen on the other display. Add your `ds` folder or a RomM server, then select a game. Foldcade reserves both screens; melonDS controls the layout.

Stable and nightly builds are recognized; stable has priority when both are installed.

For [RomM save sync](romm.md#save-sync), choose the same save directory in melonDS and **Foldcade Settings › Emulators › melonDS**, with **Save next to ROM file** off. A ROM named `Puzzle.nds` uses `Puzzle.sav`; keep filenames consistent across devices.

Choose **Not now** at the save-folder prompt to play with melonDS's own saves.

## Other systems

Complete the emulator's firmware, game-directory, controller, and graphics setup, and check that the game plays there before adding it to Foldcade.

Disc tracks and other related files may need the emulator's own game-directory access. These emulators keep their own saves; Foldcade does not currently transfer them to RomM.

## If Foldcade does not find an installed emulator

Forks with different Android package names are not detected automatically. Install a supported build or report the build you use.

Recognized packages are listed in the source for [standalone emulators](../../plugins/emulators/src/main/kotlin/app/foldcade/plugins/emulators/Emulators.kt), [Azahar](../../plugins/azahar/src/main/kotlin/app/foldcade/plugins/azahar/AzaharEntry.kt), [melonDS](../../plugins/melonds/src/main/kotlin/app/foldcade/plugins/melonds/MelonDsEntry.kt), and [RetroArch](../../plugins/retroarch/src/main/kotlin/app/foldcade/plugins/retroarch/RetroArchEntry.kt).

[All integration guides](README.md)
