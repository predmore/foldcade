# Standalone emulators

Set up an emulator on your Thor, then add its games through [local folders](local-games.md) or [RomM](romm.md). Foldcade detects supported installed emulators; it does not import their game lists.

## Supported emulators and formats

Foldcade uses the first installed emulator in each row. There is currently no emulator selector in Settings.

[RetroArch](retroarch.md) with a core for a system goes ahead of these emulators, except on the Nintendo DS and the PlayStation. It also runs systems this table doesn't list, such as the Sega CD, PC Engine, and Atari consoles.

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

## DuckStation: PlayStation

[DuckStation](https://www.duckstation.org) is the PlayStation emulator to use on the Thor. Foldcade launches the 2026 Android rewrite. That build is on [DuckStation's beta page](https://downloads.duckstation.org/android-testing/), signed with the same key as the Play Store build.

To install it with Obtainium and get its updates, open [this link](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.github.stenzek.duckstation%22%2C%22url%22%3A%22https%3A%2F%2Fdownloads.duckstation.org%2Fandroid-testing%2F%22%2C%22author%22%3A%22stenzek%22%2C%22name%22%3A%22DuckStation%22%2C%22additionalSettings%22%3A%22%7B%5C%22customLinkFilterRegex%5C%22%3A%5C%22duckstation-%5C%5C%5C%5Cd%2B%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22sortByLastLinkSegment%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22duckstation-%28%5C%5C%5C%5Cd%2B%29%5C%5C%5C%5C.apk%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%241%5C%22%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Atrue%7D%22%2C%22overrideSource%22%3A%22HTML%22%7D) on your Thor. It tracks the beta page and compares its build number with the installed version code. Don't use a mirror of the APK: DuckStation's developer does not allow redistributing it.

Games can be `.cue` sheets with their `.bin` tracks, `.chd`, or an `.m3u` of discs. A RomM game that is a cue sheet and its tracks downloads as those files, in one folder, and DuckStation reads the tracks from there. In a local folder, add the same folder to DuckStation's game directories so it can open the tracks beside the sheet.

### BIOS and memory cards

DuckStation needs a PlayStation BIOS dumped from a console you own. Put it in RomM's firmware for the PlayStation. Foldcade then downloads it, checks it, and places it in DuckStation's `bios` folder before the game starts. You can also import one in DuckStation yourself.

The first time you play a RomM game with DuckStation, Foldcade asks for DuckStation's folder. The picker opens at DuckStation's own data folder, so tap **Use this folder** and **Allow**. That is where the BIOS goes, and nothing in DuckStation has to change for it. You can change the folder later in **Foldcade Settings › Emulators › DuckStation**. A folder you pick has to hold DuckStation's `bios` or `memcards`.

Foldcade syncs each game's memory card through [RomM](romm.md#save-sync). DuckStation writes cards that only DuckStation can read, so Foldcade keeps them for it in a folder of its own. Foldcade asks you once to point DuckStation at that folder, and **Open DuckStation** takes you there:

1. In DuckStation, open **App Settings**, turn on **Show Advanced Settings** in the ⋮ menu, and open **Folders › Memory Cards › Choose New Location**.
2. Open the picker's menu, pick **Foldcade** (*DuckStation memory cards*), and tap **Use this folder** and **Allow**.
3. Choose **Copy Files** to bring the cards you already have.

**Foldcade Settings › Emulators** shows **DuckStation memory cards: Syncing** once DuckStation uses the folder. Until then, **Set up** there shows the steps again. **Not now** plays with DuckStation's own cards, unsynced. The cards are in Foldcade's storage, so uninstalling Foldcade removes the copies on the device. The ones on RomM stay.

Foldcade syncs memory card 1, DuckStation's card per game. With its default type, **Separate Card Per Game (Title)**, DuckStation names that card after the game's title in its database. For Redump file names, as RomM keeps them, that title is the game file's name: `Castlevania - Symphony of the Night (USA).cue` uses `Castlevania - Symphony of the Night (USA)_1.mcd`. If your files are named otherwise, set memory card 1's type, in DuckStation's **Memory Cards** settings, to **Separate Card Per Game (File Title)** so the names match. A BIOS file you put in `bios` yourself is never replaced. Save states stay with DuckStation.

DuckStation pauses in the background and writes a changed card when it resumes or quits. A save made just before you switch to Foldcade syncs on the next return or launch.

A multi-disc RomM game downloads with an `.m3u` of its discs, named after the set, so DuckStation can swap discs. It also keeps one card for the whole set.

Older DuckStation builds, from before the rewrite, read the game differently and do not start from Foldcade.

## Other systems

Complete the emulator's firmware, game-directory, controller, and graphics setup, and check that the game plays there before adding it to Foldcade. Eden can take its Switch keys and firmware from [RomM](romm.md#switch-keys-and-firmware) when they are missing: choose Eden's folder once, and files already there are left as they are.

Disc tracks and other related files may need the emulator's own game-directory access. These emulators keep their own saves; Foldcade does not currently transfer them to RomM.

## If Foldcade does not find an installed emulator

Forks with different Android package names are not detected automatically. Install a supported build or report the build you use.

Recognized packages are listed in the source for [standalone emulators](../../plugins/emulators/src/main/kotlin/app/foldcade/plugins/emulators/Emulators.kt), [Azahar](../../plugins/azahar/src/main/kotlin/app/foldcade/plugins/azahar/AzaharEntry.kt), [DuckStation](../../plugins/duckstation/src/main/kotlin/app/foldcade/plugins/duckstation/DuckStationEntry.kt), [melonDS](../../plugins/melonds/src/main/kotlin/app/foldcade/plugins/melonds/MelonDsEntry.kt), and [RetroArch](../../plugins/retroarch/src/main/kotlin/app/foldcade/plugins/retroarch/RetroArchEntry.kt).

[All integration guides](README.md)
