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
| PlayStation 2 | `ps2` | `.iso`, `.chd`, `.m3u` | ARMSX2, NetherSX2 / AetherSX2, EmuCoreX |
| PlayStation 3 | `ps3` | `.iso` (decrypted) | aPS3e |
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

For [RomM save sync](romm.md#save-sync), choose Azahar's user folder, the one that holds `sdmc`, `nand` and `config`, in **Foldcade Settings › Emulators › Azahar**. Foldcade moves a game's save data and extra data there before launch and reads them back when you return. Choose **Play without syncing** at the save-folder prompt to play with Azahar's own saves for this launch. Foldcade asks again next time.

## melonDS: Nintendo DS and DSi

Install [melonDS Android](https://github.com/rafaelvcaetano/melonDS-android) 2.0.0 or newer. Configure the DS top screen on the Thor's main display and its touch screen on the other display. Add your `ds` folder or a RomM server, then select a game. Foldcade reserves both screens; melonDS controls the layout.

Stable and nightly builds are recognized; stable has priority when both are installed.

For [RomM save sync](romm.md#save-sync), choose the same save directory in melonDS and **Foldcade Settings › Emulators › melonDS**, with **Save next to ROM file** off. A ROM named `Puzzle.nds` uses `Puzzle.sav`; keep filenames consistent across devices.

Choose **Play without syncing** at the save-folder prompt to play with melonDS's own saves for this launch. Foldcade asks again next time.

## DuckStation: PlayStation

[DuckStation](https://www.duckstation.org) is the PlayStation emulator to use on the Thor. Foldcade launches the 2026 Android rewrite. That build is on [DuckStation's beta page](https://downloads.duckstation.org/android-testing/), signed with the same key as the Play Store build.

To install it with Obtainium and get its updates, open [this link](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.github.stenzek.duckstation%22%2C%22url%22%3A%22https%3A%2F%2Fdownloads.duckstation.org%2Fandroid-testing%2F%22%2C%22author%22%3A%22stenzek%22%2C%22name%22%3A%22DuckStation%22%2C%22additionalSettings%22%3A%22%7B%5C%22customLinkFilterRegex%5C%22%3A%5C%22duckstation-%5C%5C%5C%5Cd%2B%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22sortByLastLinkSegment%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22duckstation-%28%5C%5C%5C%5Cd%2B%29%5C%5C%5C%5C.apk%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%241%5C%22%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Atrue%7D%22%2C%22overrideSource%22%3A%22HTML%22%7D) on your Thor. It tracks the beta page and compares its build number with the installed version code. Don't use a mirror of the APK: DuckStation's developer does not allow redistributing it.

Games can be `.cue` sheets with their `.bin` tracks, `.chd`, or an `.m3u` of discs. A RomM game that is a cue sheet and its tracks downloads as those files, in one folder, and DuckStation reads the tracks from there. In a local folder, add the same folder to DuckStation's game directories so it can open the tracks beside the sheet.

### BIOS and memory cards

DuckStation needs a PlayStation BIOS dumped from a console you own. Put it in RomM's firmware for the PlayStation. Foldcade then downloads it, checks it, and places it in DuckStation's `bios` folder before the game starts. You can also import one in DuckStation yourself.

Before a RomM launch, Foldcade asks for DuckStation's data folder while its saves are not set up. Once its hosted memory cards are in use, this folder is optional for save sync. The picker opens at DuckStation's own data folder, so tap **Use this folder** and **Allow**. That is where the BIOS goes, and nothing in DuckStation has to change for it. You can change the folder later in **Foldcade Settings › Emulators › DuckStation**. A folder you pick has to hold DuckStation's `bios` or `memcards`.

Foldcade syncs each game's memory card through [RomM](romm.md#save-sync). DuckStation writes cards that only DuckStation can read, so Foldcade keeps them for it in a folder of its own. Foldcade asks on each server launch until DuckStation uses that folder. **Set up** opens DuckStation:

1. In DuckStation, open **App Settings**, turn on **Show Advanced Settings** in the ⋮ menu, and open **Folders › Memory Cards › Choose New Location**.
2. Open the picker's menu, pick **Foldcade** (*DuckStation memory cards*), and tap **Use this folder** and **Allow**.
3. Choose **Copy Files** to bring the cards you already have.

**Foldcade Settings › Emulators** shows **DuckStation memory cards: Syncing** once DuckStation uses the folder. Until then, **Not syncing** there shows the steps again. **Play without syncing** plays with DuckStation's own cards for this launch. Foldcade asks again next time. The cards are in Foldcade's storage, so uninstalling Foldcade removes the copies on the device. The ones on RomM stay.

Foldcade syncs memory card 1, DuckStation's card per game. With its default type, **Separate Card Per Game (Title)**, DuckStation names that card after the game's title in its database. For Redump file names, as RomM keeps them, that title is the game file's name: `Castlevania - Symphony of the Night (USA).cue` uses `Castlevania - Symphony of the Night (USA)_1.mcd`. If your files are named otherwise, set memory card 1's type, in DuckStation's **Memory Cards** settings, to **Separate Card Per Game (File Title)** so the names match. A BIOS file you put in `bios` yourself is never replaced. Save states stay with DuckStation.

DuckStation pauses in the background and writes a changed card when it resumes or quits. A save made just before you switch to Foldcade syncs on the next return or launch.

A multi-disc RomM game downloads with an `.m3u` of its discs, named after the set, so DuckStation can swap discs. It also keeps one card for the whole set.

Older DuckStation builds, from before the rewrite, read the game differently and do not start from Foldcade.

## ARMSX2: PlayStation 2

[ARMSX2](https://github.com/ARMSX2/ARMSX2) is the PlayStation 2 emulator to use on the Thor. Foldcade launches the GitHub build, then the Play Store build, then the nightly build, when more than one is installed. NetherSX2, AetherSX2, and EmuCoreX still launch when ARMSX2 is not.

To install the GitHub build with Obtainium and get its updates, open [this link](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.armsx2%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FARMSX2%2FARMSX2%22%2C%22author%22%3A%22ARMSX2%22%2C%22name%22%3A%22ARMSX2%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22a13-armv8%5C%5C%5C%5C.2-sdk33%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D) on your Thor. It tracks [ARMSX2/ARMSX2](https://github.com/ARMSX2/ARMSX2) and takes the `a13-armv8.2-sdk33` APK. Open ARMSX2 once and finish its first-run setup before you launch a game from Foldcade.

### BIOS

ARMSX2 needs a PlayStation 2 BIOS dumped from a console you own. Put it in RomM's firmware for the PlayStation 2, under any file name, or put a zip of dumps there. Foldcade downloads it, checks it, and places one BIOS in ARMSX2's `bios` folder before the game starts. From a zip it keeps one console BIOS: Japan on a device set to Japan, the Americas BIOS in the US, Canada, and Latin America, and the Europe BIOS everywhere else. A newer version of that region wins, and then the later date. Devkit dumps, arcade dumps, DVD-player firmware, and the MagicGate key files (`civ.bin`, `cks.bin`, `eks.bin`, `kek.bin`) are not written. Those keys are not used. You can also put a BIOS in the folder yourself. A file you put there stays.

The first time you play a RomM game with ARMSX2, Foldcade asks for ARMSX2's folder. The picker opens at the GitHub build's data folder, so tap **Use this folder** and **Allow**. That is where the BIOS goes. You can change the folder later in **Foldcade Settings › Emulators › ARMSX2**. A folder you pick has to hold `bios` or `memcards`. The Play Store and nightly builds keep their own data folders. If you use one of those, pick that build's folder instead.

### Memory cards

Foldcade syncs each game's saves on ARMSX2's slot 1 folder card through [RomM](romm.md#save-sync). The saves for one game are the folders on that card whose names contain the game's serial, such as `BASLUS-210651` for `SLUS-21065`.

When slot 1 has no card, or its card is an empty 8 MB file card, Foldcade makes slot 1 a folder card before the game starts. The empty card is copied into Foldcade's save backups first. Any other file card, such as one that holds saves, is left as it is. **Foldcade Settings › Emulators** then shows **ARMSX2 memory card: Set up**. Select it for these steps and **Open ARMSX2**:

1. In ARMSX2, open ☰ › **Memory Cards** and tap **＋**. Set **Type** to **Folder**, tap **Create**, then tap **Slot 1** on the new card.
2. Saves on your old card stay there and don't sync. Some games can load a save from slot 2: tap **Slot 2** on your old card, load the save in the game, and save it to slot 1.

Once slot 1 is a folder card, the row reads **ARMSX2 memory card: Syncing**. A card you set for one game in ARMSX2 is used for that game instead of slot 1's.

ISO, BIN, CUE/BIN, multi-disc `.m3u`, and CHD games sync. chdman's defaults include FLAC as one choice per block. A CHD syncs when the part of the disc holding the serial (the start of its data) isn't stored with FLAC or Zstandard, which is how chdman usually compresses it, since FLAC suits audio. A CHD where that part is FLAC or Zstandard still launches, and its saves stay on the device. Save states stay with ARMSX2. Restoring happens before a game starts, so quit the game in ARMSX2 before playing it on another device.

## aPS3e: PlayStation 3

[aPS3e](https://github.com/aenu1/aps3e) is the PlayStation 3 emulator Foldcade launches. The GitHub build and the free Play build are both `aenu.aps3e`. A premium Play build, `aenu.aps3e.premium`, is recognized after that one.

To install the GitHub build with Obtainium and get its updates, open [this link](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22aenu.aps3e%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Faenu1%2Faps3e%22%2C%22author%22%3A%22aenu1%22%2C%22name%22%3A%22aPS3e%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22%5E%5C%5C%5C%5Cd%2B%5C%5C%5C%5C.%5C%5C%5C%5Cd%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D) on your Thor. It tracks [aenu1/aps3e](https://github.com/aenu1/aps3e) and takes the `<version>.apk` asset, such as `2.43.apk`.

Foldcade starts aPS3e with a decrypted ISO only. Many decrypted images are named with `.dec.iso`, such as `Skate 3 (USA).dec.iso`; any `.iso` name works. An encrypted Redump image does not open, and Foldcade does not start a JB folder.

### Firmware

Put `PS3UPDAT.PUP` in RomM's firmware for the PlayStation 3. Foldcade checks it and puts it in `aPS3e › firmware`. aPS3e installs firmware itself; Foldcade does not. Until aPS3e has finished its first-run setup and installed that file, Foldcade does not start the game, from RomM or from a folder. A game started before then leaves aPS3e with no renderer, and its setup does not fix that later. Foldcade shows the step that is left, and **Open** opens aPS3e:

- No aPS3e folder picked: "Foldcade checks aPS3e’s setup through aPS3e’s folder. In Foldcade’s Settings › Emulators › aPS3e, choose Use this folder where the picker opens. Then start the game again."
- No firmware staged or installed: "aPS3e needs PlayStation 3 firmware. Add PS3UPDAT.PUP to the PlayStation 3 firmware on your RomM server, then start the game again."
- Setup is not finished: "Open aPS3e and finish its setup. For firmware, tap ☰ in the file picker, then aPS3e › firmware › PS3UPDAT.PUP, which Foldcade put there from RomM. For the font, choose From Firmware. For the GPU driver, turn off Use Custom Driver. Then start the game again." On the Thor the font and GPU driver pages start on choices that need a file, and their Next stays grey until you change them. When firmware is already installed, the firmware sentence is left out.
- Setup is finished, and firmware is not installed: "In aPS3e, open the menu and choose Install Firmware. Tap ☰ in the file picker, then aPS3e › firmware › PS3UPDAT.PUP. Foldcade put it there from RomM. Then start the game again."

Once aPS3e's folder is picked, **Foldcade Settings › Emulators** shows **aPS3e setup: Set up** while any of those is still true. Select it for the same steps.

The first time you play a game with aPS3e, Foldcade asks for aPS3e's folder. aPS3e can't start a game until it has one, so declining the folder offer only closes it, and the next start asks again. The picker opens at aPS3e's own folder, so tap **Use this folder** and **Allow**. With the premium build, open **aPS3e Premium** in the picker's list first: the picker opens at the free build's folder. You can change the folder later in **Foldcade Settings › Emulators › aPS3e**. A folder you pick has to hold `config` or `logs`; a new install of aPS3e has `logs` before you open it.

### Saves

Foldcade syncs each game's `savedata` folders through [RomM](romm.md#save-sync). Those folders are named with the game's title id, such as `BLUS30464-ALIAS_SKATER` for `BLUS30464`. Trophies are not synced. ISO games can't take updates or DLC in aPS3e yet. That limit is in aPS3e's FAQ.

### Settings that worked on the Thor

Skate 3 reached playable skating with these settings. They are what worked on that device, not a guarantee for every game.

- **Core › Max LLVM Compile Threads:** 1. 4 ran out of memory compiling Skate 3.
- **LLVM Precompilation:** off.
- **SPU Decoder:** Interpreter (dynamic). The LLVM SPU decoder hung Skate 3.
- **Video › Strict Rendering Mode:** off.
- **VRAM allocation limit:** 2048 MB.
- A Turnip driver, installed in aPS3e from a driver file. Mr. Purple T30 worked. T23 crashed at start. The stock driver reached about 25 FPS at the start, then 8–10 FPS in PCU Tour, and ran out of memory.

Skate 3 ran at about 19–27 FPS: playable, not full speed. With these settings aPS3e also closed Skate 3 when Home paused it. A on the game's tile then starts it again, from its last save.

## Other systems

Complete the emulator's firmware, game-directory, controller, and graphics setup, and check that the game plays there before adding it to Foldcade. Eden can take its Switch keys and firmware from [RomM](romm.md#switch-keys-and-firmware) when they are missing: choose Eden's folder once, and files already there are left as they are.

Disc tracks and other related files may need the emulator's own game-directory access. These emulators keep their own saves; Foldcade does not currently transfer them to RomM.

## If Foldcade does not find an installed emulator

Forks with different Android package names are not detected automatically. Install a supported build or report the build you use.

Recognized packages are listed in the source for [standalone emulators](../../plugins/emulators/src/main/kotlin/app/foldcade/plugins/emulators/Emulators.kt), [aPS3e](../../plugins/aps3e/src/main/kotlin/app/foldcade/plugins/aps3e/Aps3eEntry.kt), [ARMSX2](../../plugins/armsx2/src/main/kotlin/app/foldcade/plugins/armsx2/ArmsX2Entry.kt), [Azahar](../../plugins/azahar/src/main/kotlin/app/foldcade/plugins/azahar/AzaharEntry.kt), [DuckStation](../../plugins/duckstation/src/main/kotlin/app/foldcade/plugins/duckstation/DuckStationEntry.kt), [melonDS](../../plugins/melonds/src/main/kotlin/app/foldcade/plugins/melonds/MelonDsEntry.kt), and [RetroArch](../../plugins/retroarch/src/main/kotlin/app/foldcade/plugins/retroarch/RetroArchEntry.kt).

[All integration guides](README.md)
