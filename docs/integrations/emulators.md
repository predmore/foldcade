# Standalone emulators

Set up an emulator on your Thor, then add its games through [local folders](local-games.md) or [RomM](romm.md). Foldcade detects supported installed emulators; it does not import their game lists.

**Settings › Emulators** shows a tile for each installed emulator. A check means its saves sync. The amber cloud means one of its rows needs you and a game on Home plays on it; those tiles come first. An emulator that still needs a step but has no games on Home wears no mark and comes last: no saves are at stake until one of its games lands on Home, and then it wears the cloud. Up to eight tiles fit on screen. With more, the shelf scrolls sideways like the Home grid, with a bar under it showing where you are.

## Supported emulators and formats

With more than one emulator installed for a system, you choose which one opens its games:

- **Settings › Emulators › Systems** lists each system you have games for and more than one emulator to run. **Automatic**, shown as *Auto · melonDS*, picks the first installed emulator in the order below. Left and right step through the others; A and a tap step forward.
- **Open with**, in a game's **Y** menu on Home or in Games, chooses for that game only. **Default** follows the system's choice and clears the game's own.
- RetroArch is listed once per core Foldcade can see, as *mGBA (RetroArch)*. Until you choose RetroArch's folder in **Settings › Emulators › RetroArch**, it is listed once, and starts the system's first core.

A game's choice comes before its system's. When the chosen emulator or core is not installed, the game opens with the next choice, and R1 says which one it used. The choice stays saved, so reinstalling the emulator brings it back. When no emulator for a game is installed at all, its dialog offers **Get** and the emulator's name, which opens the download page this guide links for it. A game that is still open in an emulator resumes there, and Restart and Close act on it there; a new choice applies once it is closed. RetroArch is the exception, as before: Foldcade does not keep track of the game RetroArch has open.

Saves stay with each emulator. Switching does not move a save from one emulator to another, though [RomM save sync](romm.md#save-sync) places the server's save in whichever emulator syncs it. RetroArch keeps saves per core, so a different core uses a different save.

[RetroArch](retroarch.md) with a core for a system goes ahead of these emulators, except on the Nintendo DS and the PlayStation. It also runs systems this table doesn't list, such as the Sega CD, PC Engine, and Atari consoles.

RetroArch sync uses the standard core-sorted `.srm` file. If settings or an override redirect that file, Foldcade can stop the affected game's synced launch and guide you back to the watched folder. Core-specific save formats and companions have their own limits. See [RetroArch saves](retroarch.md#saves).

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
| Sega CD | `segacd` | `.cue`, `.chd`, `.iso` | MD.emu |
| Genesis / Mega Drive | `genesis` | `.gen`, `.smd`, `.md` | MD.emu, Pizza Boy SC |
| Master System | `sms` | `.sms` | MD.emu, Pizza Boy SC |
| Game Gear | `gg` | `.gg` | Pizza Boy SC |
| Saturn | `saturn` | `.cue`, `.chd`, `.iso`, `.ccd`, `.mds`, `.m3u` | Yaba Sanshiro 2, Saturn.emu |
| Dreamcast | `dreamcast` | `.gdi`, `.cdi`, `.chd`, `.cue`, `.iso`, `.m3u` | Flycast, Redream |

These are formats Foldcade recognizes; the emulator must support them too. `.zip` and `.7z` archives are also listed inside system folders; extract them if the emulator requires it.

Shared formats need a [system folder](local-games.md#folder-names-and-formats).

## Resume and Move on the Thor

Press **A** on a game Foldcade left open to return to its existing session. **Y › Restart** explicitly starts over. Choosing a save to load also requires a fresh launch. For Azahar, ARMSX2, Eden and melonDS, Foldcade can recognize when Android removes the game activity. The next **A** starts through the save check. A confirmed open game resumes with one press, even without usage access. If a resume opens the emulator's menu, return to Foldcade and use **Y › Restart**. When Foldcade has already confirmed the game closed, its tile offers **Play** through the save check.

**Move** is offered only for a tested one-screen emulator that preserves its running game on the other panel. DuckStation keeps its existing support. Both-screen games keep their emulator's own layout; changing a task's display does not by itself prove that its second screen survives.

These checks used the Thor on 2026-11-04, with saves backed up before launch. A repeated start here removes clear-task/clear-top/multiple-task flags. Launcher resume uses `NEW_TASK | RESET_TASK_IF_NEEDED`; a display re-start uses those flags and `launchDisplayId`. No emulator settings were changed.

| Emulator and test game | Re-send the game start | Launcher resume | Re-start on the other display | Foldcade route |
| --- | --- | --- | --- | --- |
| Azahar 2126.2; Sonic the Hedgehog 2 (3DS Virtual Console) | Tears down the emulated process; can stall loading | Keeps the running title screen and drawer | Moves the activity, but tears down/reloads the game and disrupts the second display | Launcher; no Move |
| melonDS 2.0.1 GH; Tetris DS under a temporary filename | Asks to stop emulation and load a ROM, even for the same URI | Keeps the session | Activity survives a round trip, but the stop/reload prompt and changed two-screen layout make this unsuitable | Launcher; no Move |
| ARMSX2 2.8.4; Need for Speed: Underground 2 | Starts loading again | Returns to the running game's pause menu | Moves the activity but starts loading again | Launcher; no Move |
| Eden 1f6734c; Fantasy Life i | Starts a ROM swap and stops the current game | Keeps the game and its drawer | Starts another ROM swap | Launcher; no Move |
| aPS3e 2.43; Skate 3 | Native `:emu` process exited during the test | Native process also exited; launcher then opened its library | A later start moved the new activity, but preserving a live game could not be verified | Existing game-start resume; no Move; see [Thor settings and pause limitation](#settings-that-worked-on-the-thor) |
| RetroArch 1.22.2_GIT / Genesis Plus GX; Gain Ground | Identical ROM/core strings keep the live game | Initially returns to the game, but changes the intent used to identify it; a subsequent identical game start can exit/reload it | A reliable running-game round trip was not established; activity identity alone is insufficient | Existing `ReplaceRunning` route; no Move |

The declarations stay conservative: Azahar, melonDS, ARMSX2 and Eden do not claim `SameStartResumes` or `MovesBetweenScreens`. melonDS's newer upstream [matching-start check](https://github.com/rafaelvcaetano/melonDS-android/blob/c42995caff0144f37ad9791dc9bc96d82e8a6f65/app/src/main/java/me/magnum/melonds/ui/emulator/EmulatorActivity.kt#L684) does not establish that behavior in the installed 2.0.1 build. Eden's [intent handler](https://git.eden-emu.dev/eden-emu/eden/src/commit/5df599128d6828fa0e42968909da848c255d22b1/src/android/app/src/main/java/org/yuzu/yuzu_emu/activities/EmulationActivity.kt) explicitly swaps the ROM when an intent has data.

RetroArch must keep its exact ROM and core strings through the existing resume/replacement protocol. Do not substitute its launcher intent: [MainMenuActivity](https://github.com/libretro/RetroArch/blob/9b546c8a7cd91ee812416ef93cf5be63ca738d89/pkg/android/phoenix/src/com/retroarch/browser/mainmenu/MainMenuActivity.java) forwards to the game activity, whose [onNewIntent](https://github.com/libretro/RetroArch/blob/9b546c8a7cd91ee812416ef93cf5be63ca738d89/pkg/android/phoenix/src/com/retroarch/browser/retroactivity/RetroActivityFuture.java) replaces its remembered intent even when the new one has no ROM/core. One production Close (`startActivities` with the launcher's `NEW_TASK | CLEAR_TASK` intent followed by Foldcade Home) deadlocked the installed build on a clean game launch: [`android_app_free`](https://github.com/libretro/RetroArch/blob/69a4f0ea1e8aaf442ae4858f2e7f2b31a1776576/frontend/drivers/platform_unix.c#L265) holds the app mutex while joining its still-running thread. The captured ANR shows `NativeActivity.onDestroy` waiting in `pthread_join` while the native loop waits for that mutex in `android_display_has_focus`; the next game launch stays on its splash screen. Foldcade tracks RetroArch for same-intent resume and uses RetroArch's own replacement protocol for a fresh start. Task clearing and screen Move remain unavailable for RetroArch. Quit RetroArch through its own menu.

For this device test only, two explicitly authorized RetroArch-only force-stops recovered the earlier failure and the single-Close reproduction. All 58 original save files matched their SHA-256 backups before recovery. The final cold launch from the test build drew on the top display, accepted physical Back and touch input, and exited through RetroArch's own Quit menu. Its settings were unchanged. Product code never force-stops an emulator.

## Azahar: Nintendo 3DS

Install [Azahar](https://github.com/azahar-emu/azahar) and complete its setup. Add your `3ds` folder or a RomM server, then select a game. Foldcade starts Azahar on the main display and reserves both screens.

Prefer decrypted `.cci` or `.zcci` games. `.3ds` is best-effort; Foldcade does not decrypt or convert it. `.cia` files are not listed by the local-folder scanner.

Both regular and Google Play builds are recognized; regular has priority when both are installed.

### Thor setup

The row is **Both screens** on Azahar's page in Settings › Emulators.

For **Azahar 2126.2**, Foldcade sets untouched options before a game starts: one 3DS screen on each Thor panel, **5×** resolution, and **accurate multiplication**. Choose Azahar’s active user folder for saves first. Foldcade compares each setting with that version’s upstream Android default and keeps any different value. An existing Thor setup stays byte-for-byte as it was. This is a first-time preset: later edits in Azahar, including resetting a value to its default, are never re-applied.

**Settings › Emulators › Azahar › Both screens** shows what Foldcade set: **Set up** before, **On** once applied. If it says **Close Azahar**, finish playing and save first. Then open its **App info**, choose **Force stop**, and return to **Both screens › Set up**, or start the next game. Background and paused apps can still overwrite their config on exit, so Foldcade only changes a file while Android marks Azahar stopped. You can then launch normally. If the folder does not permit a direct atomic replacement, the row offers the existing optional **Auto-find saves** file access; configuring the layout in Azahar remains available. Other Azahar versions are left to Azahar until their defaults are verified.

Foldcade stages and syncs the whole new file, then atomically replaces `config/config.ini`. Before changing it, it keeps the original beside the config as `config.ini.foldcade-original-…`, and in Foldcade’s private `files/emulator-presets` folder with a record of the changed keys. **Both screens › Restore original** asks before restoring the entire original file. It also backs up the current file in both places, and disables future automatic presets for that user folder. A newly created config restores to no file. Uninstalling Foldcade removes its private copy and record; the backups beside Azahar’s config survive and prevent re-application after reinstall. Restore those copies manually if Foldcade’s private record is gone.

The **Vulkan backend and GPU driver stay your choice**. On the Thor, Vulkan with Mr. Purple Turnip T30 worked well; install and choose that driver in Azahar. The ZIP in `gpu_drivers` is only an archive: Azahar extracts its selected driver into private app storage, which Foldcade’s user-folder grant cannot configure. Foldcade never points at a missing driver or treats that archive as an installed driver.

For [RomM save sync](romm.md#save-sync), choose Azahar's user folder, the one that holds `sdmc`, `nand` and `config`, in **Foldcade Settings › Emulators › Azahar**. Foldcade moves a game's save data and extra data there before launch and reads them back when you return. Choose **Play without syncing** at the save-folder prompt to play with Azahar's own saves for this launch. Foldcade asks again next time.

## melonDS: Nintendo DS and DSi

Install [melonDS Android](https://github.com/rafaelvcaetano/melonDS-android) 2.0.0 or newer. Configure the DS top screen on the Thor's main display and its touch screen on the other display. Add your `ds` folder or a RomM server, then select a game. Foldcade reserves both screens; melonDS controls the layout.

Stable and nightly builds are recognized; stable has priority when both are installed.

melonDS 2.0.1 keeps layout, renderer and audio preferences in private SharedPreferences. Its release build disables settings backup/restore. The save-folder grant reaches saves, not those settings, so Foldcade cannot apply a Thor preset. The verified manual tuning is OpenGL, 6× resolution, Linear filtering and Cubic audio; keep the two-screen layout step above.

For [RomM save sync](romm.md#save-sync), choose the same save directory in melonDS and **Foldcade Settings › Emulators › melonDS**, with **Save next to ROM file** off. A ROM named `Puzzle.nds` uses `Puzzle.sav`; keep filenames consistent across devices.

The **Check** row says **Can’t read config** until Foldcade observes a save write in its watched folder. Foldcade also checks `saves/nds`, `saves/melonDS`, and the ROM’s parent when it already has read access. [melonDS 2.0.1’s scanner](https://github.com/rafaelvcaetano/melonDS-android/blob/5ec3648d68382dea9c17d6fbf02544e5c2c0afed/app/src/main/java/me/magnum/melonds/impl/FileSystemRomsRepository.kt#L241-L253) records each ROM’s immediate folder, including ROMs in subfolders. If the game writes beside the ROM, **Fix it** takes you to **Settings › Save Files** in melonDS to turn off **Save next to ROM file** and select the folder Foldcade was watching. Then confirm that folder in Foldcade. A launch whose ROM has an unknown parent may use melonDS’s private fallback, which Foldcade cannot inspect. Foldcade’s RomM download provider opens files read-only, so a launch that cannot write beside the ROM supplies no evidence of that setting.

Choose **Play anyway** at the save-folder prompt to play with melonDS's own saves for this launch. Foldcade asks again next time.

## DuckStation: PlayStation

[DuckStation](https://www.duckstation.org) is the PlayStation emulator to use on the Thor. Foldcade launches the 2026 Android rewrite. That build is on [DuckStation's beta page](https://downloads.duckstation.org/android-testing/), signed with the same key as the Play Store build.

To install it with Obtainium and get its updates, open [this link](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.github.stenzek.duckstation%22%2C%22url%22%3A%22https%3A%2F%2Fdownloads.duckstation.org%2Fandroid-testing%2F%22%2C%22author%22%3A%22stenzek%22%2C%22name%22%3A%22DuckStation%22%2C%22additionalSettings%22%3A%22%7B%5C%22customLinkFilterRegex%5C%22%3A%5C%22duckstation-%5C%5C%5C%5Cd%2B%5C%5C%5C%5C.apk%24%5C%22%2C%5C%22sortByLastLinkSegment%5C%22%3Atrue%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22duckstation-%28%5C%5C%5C%5Cd%2B%29%5C%5C%5C%5C.apk%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%241%5C%22%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Atrue%7D%22%2C%22overrideSource%22%3A%22HTML%22%7D) on your Thor. It tracks the beta page and compares its build number with the installed version code. Don't use a mirror of the APK: DuckStation's developer does not allow redistributing it.

Games can be `.cue` sheets with their `.bin` tracks, `.chd`, or an `.m3u` of discs. A RomM game that is a cue sheet and its tracks downloads as those files, in one folder, and DuckStation reads the tracks from there. In a local folder, add the same folder to DuckStation's game directories so it can open the tracks beside the sheet.

### BIOS and memory cards

DuckStation needs a PlayStation BIOS dumped from a console you own. Put it in RomM's firmware for the PlayStation, individually or in a ZIP, or in a [local BIOS folder](local-games.md#bios). Foldcade identifies supported dumps by their known hashes, checks them, and places them in DuckStation's `bios` folder before the game starts. You can also import one in DuckStation yourself.

DuckStation's data folder is optional for save sync. Choose it in **Foldcade Settings › Emulators › DuckStation** to let Foldcade place a BIOS and check DuckStation's configured Memory Cards folder. The picker opens at DuckStation's own data folder, so tap **Use this folder** and **Allow**. A folder you pick has to hold DuckStation's `bios` or `memcards`.

Foldcade syncs each game's memory card through [RomM](romm.md#save-sync). DuckStation writes cards that only DuckStation can read, so Foldcade keeps them for it in a folder of its own. Foldcade asks on each server launch until DuckStation uses that folder. **Set up** opens DuckStation:

1. In DuckStation, open **App Settings**, turn on **Show Advanced Settings** in the ⋮ menu, and open **Folders › Memory Cards › Choose New Location**.
2. Open the picker's menu, pick **Foldcade** (*DuckStation memory cards*), and tap **Use this folder** and **Allow**.
3. Choose **Copy Files** to bring the cards you already have.

**Foldcade Settings › Emulators** shows **Set** when DuckStation has access. Foldcade checks the folder and each game's card when it sees DuckStation open the expected card during play. Without access, **Not syncing** shows the setup steps. **Play without syncing** uses DuckStation's own cards for that launch. The hosted cards are in Foldcade's storage, so uninstalling Foldcade removes those copies from the device. The ones on RomM stay.

Foldcade syncs memory card 1, DuckStation's card per game. With its default type, **Separate Card Per Game (Title)**, DuckStation names that card after the game's title in its database. For Redump file names, as RomM keeps them, that title is the game file's name: `Castlevania - Symphony of the Night (USA).cue` uses `Castlevania - Symphony of the Night (USA)_1.mcd`. If your files are named otherwise, set memory card 1's type, in DuckStation's **Memory Cards** settings, to **Separate Card Per Game (File Title)** so the names match. A BIOS file you put in `bios` yourself is never replaced. Save states stay with DuckStation.

Pick **Foldcade** in DuckStation to grant access to the hosted folder. If Foldcade can read the data folder and sees DuckStation configured to use a different Memory Cards folder, it stops the next ordinary launch and shows how to restore the hosted folder.

If DuckStation opens a different card, Foldcade names it in the game's notice. When it can identify one per-game card, Foldcade keeps a readable copy as a RomM **Backup** while preserving the existing slot head. Save a copy of your card before changing its naming type, including any setting DuckStation applies to that game.

On devices where Android blocks DuckStation's data folder, **Memory card settings** says **Can’t read config**. Foldcade can still check cards DuckStation opens. With Usage Access, a fresh RomM launch that plays for at least 20 seconds without opening its existing hosted card can show **DuckStation may be saving somewhere else**. Check the Memory Cards folder or dismiss the notice with **It’s fine**.

DuckStation pauses in the background and writes a changed card when it resumes or quits. A save made just before you switch to Foldcade syncs on the next return or launch.

A multi-disc RomM game downloads with an `.m3u` of its discs, named after the set, so DuckStation can swap discs. It also keeps one card for the whole set.

Older DuckStation builds, from before the rewrite, read the game differently and do not start from Foldcade.

## ARMSX2: PlayStation 2

[ARMSX2](https://github.com/ARMSX2/ARMSX2) is the PlayStation 2 emulator to use on the Thor. Foldcade launches the GitHub build, then the Play Store build, then the nightly build, when more than one is installed. NetherSX2, AetherSX2, and EmuCoreX still launch when ARMSX2 is not.

Leave ARMSX2’s **Exit to launcher on close** setting on (the default) so Foldcade can detect when a game closes.

To install the GitHub build with Obtainium and get its updates, open [this link](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.armsx2%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FARMSX2%2FARMSX2%22%2C%22author%22%3A%22ARMSX2%22%2C%22name%22%3A%22ARMSX2%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22a13-armv8%5C%5C%5C%5C.2-sdk33%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D) on your Thor. It tracks [ARMSX2/ARMSX2](https://github.com/ARMSX2/ARMSX2) and takes the `a13-armv8.2-sdk33` APK. Open ARMSX2 once and finish its first-run setup before you launch a game from Foldcade.

### BIOS

ARMSX2 needs a PlayStation 2 BIOS dumped from a console you own. Put it in RomM's firmware for the PlayStation 2, under any file name, or put a zip of dumps there. [Local BIOS folders](local-games.md#bios) work the same way without RomM. Foldcade downloads it, checks it, and places one BIOS in ARMSX2's `bios` folder before the game starts. From a zip it keeps one console BIOS: Japan on a device set to Japan, the Americas BIOS in the US, Canada, and Latin America, and the Europe BIOS everywhere else. A newer version of that region wins, and then the later date. Devkit dumps, arcade dumps, DVD-player firmware, and the MagicGate key files (`civ.bin`, `cks.bin`, `eks.bin`, `kek.bin`) are not written. Those keys are not used. You can also put a BIOS in the folder yourself. A file you put there stays.

The first time you play a RomM game with ARMSX2, Foldcade asks for ARMSX2's data folder. Choose it and tap **Use this folder**, then **Allow**. You can change the folder later in **Foldcade Settings › Emulators › ARMSX2**. Foldcade recognizes the folder by `PCSX2-Android.ini`, `bios`, or `memcards`. The Play Store and nightly builds keep their own data folders. If you use one of those, pick that build’s folder.

With ARMSX2's **App Data Folder › Custom folder**, ARMSX2 still loads its BIOS from its private folder. Foldcade places a missing BIOS in the picked folder's `bios` folder and tells you to import it in ARMSX2 through ☰ › **Setup / Change Folders › Select your BIOS**; the game still launches.

### Memory cards

Foldcade syncs each game's saves on ARMSX2's slot 1 folder card through [RomM](romm.md#save-sync). The saves for one game are the folders on that card whose names contain the game's serial, such as `BASLUS-210651` for `SLUS-21065`.

When slot 1 has no card, or its card is an empty 8 MB file card, Foldcade makes slot 1 a folder card before the game starts. The empty card is copied into Foldcade's save backups first. Any other file card, such as one that holds saves, is left as it is. **Foldcade Settings › Emulators** then shows **ARMSX2 memory card: Set up**. Select it for these steps and **Open ARMSX2**:

1. In ARMSX2, open ☰ › **Memory Cards** and tap **＋**. Set **Type** to **Folder**, tap **Create**, then tap **Slot 1** on the new card.
2. Saves on your old card stay there and don't sync. Some games can load a save from slot 2: tap **Slot 2** on your old card, load the save in the game, and save it to slot 1.

Foldcade uses the slot 1 folder card selected for each game. A per-game choice or disabled slot takes precedence over the global choice in the mirror. Under `[Folders]`, `MemoryCards` in `PCSX2-Android.ini` can name a relative or absolute folder inside the chosen data folder. Foldcade uses that folder for placement and read-back. Stock Android 11 and later restricts access to another app’s `Android/data` folder. Choose ARMSX2’s shared **App Data Folder** when the private folder is unavailable.

ARMSX2 2.8.4’s reachable `armsx2-settings.json` is a backup mirror of private preferences. It is imported only for fresh-install recovery, then rewritten when ARMSX2 saves settings. Editing it cannot safely switch a populated file card to a folder card. Foldcade keeps the existing empty-card conversion; populated cards still use the steps above, so their saves remain accessible.

The **Check** row reads **Can’t read config** when Foldcade cannot read the native INI. With readable settings, Foldcade checks the folder after it sees a change to the game’s save during play. A config or its timestamp alone leaves the folder unconfirmed. A confirmed move to another ARMSX2 data root affects every game. A confirmed write to an outside card folder affects the game that wrote it. Its next ordinary launch offers **Fix it** or **Play anyway**. To fix the card folder, close ARMSX2, set `MemoryCards = memcards` under `[Folders]` in `PCSX2-Android.ini`, then choose the data folder again in Foldcade. Leave the outside card in place so other games’ saves stay available. Foldcade keeps suspect copies as verified RomM **Backups**, or keeps them on the device for local-only games.

The `xyz.aethersx2.android` and `xyz.aethersx2.tturnip` packages launch through Foldcade’s NetherSX2 player. That player leaves saves on the device.

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

The first time you play a game with aPS3e, Foldcade asks for aPS3e's folder. Declining the offer closes it, and the next start asks again. The picker opens at the installed build's own folder. Tap **Use this folder** and **Allow** at its root. You can pick it again in **Foldcade Settings › Emulators › aPS3e**.

### Saves

Foldcade checks the root when you allow the installed aPS3e build's provider. A reinstall invalidates that check and opens **Allow** to grant the folder again. For RomM games, Foldcade can stage `PS3UPDAT.PUP` again after you regrant access. Finish aPS3e's firmware and first-run setup before playing.

Foldcade syncs `savedata` under the default `dev_hdd0` and account `00000001` through [RomM](romm.md#save-sync). For `BLUS30464`, a matching folder might be named `BLUS30464-ALIAS_SKATER`. Remapped VFS paths, other accounts, aliases without the title ID, and trophies are outside this slot. The Check row explains remapped or unsupported VFS settings and shows **Can’t read config** when the file cannot be read. ISO games can't take updates or DLC in aPS3e yet. That limit is in aPS3e's FAQ.

aPS3e’s provider grant reaches `config/config.yml` and `config/default_config.yml`. Accessing that provider starts aPS3e’s process, which can cache settings and write them back. Foldcade does not patch that live configuration or its profiles. Apply tuning in aPS3e itself.

### Settings that worked on the Thor

Skate 3 reached playable skating with these settings. They are what worked on that device, not a guarantee for every game.

- **Core › Max LLVM Compile Threads:** 1. 4 ran out of memory compiling Skate 3.
- **LLVM Precompilation:** off.
- **SPU Decoder:** Interpreter (dynamic). The LLVM SPU decoder hung Skate 3.
- **Video › Strict Rendering Mode:** off.
- **VRAM allocation limit:** 2048 MB.
- A Turnip driver, installed in aPS3e from a driver file. Mr. Purple T30 worked. T23 crashed at start. The stock driver reached about 25 FPS at the start, then 8–10 FPS in PCU Tour, and ran out of memory.

Skate 3 ran at about 19–27 FPS: playable, not full speed. With these settings aPS3e also closed Skate 3 when Home paused it. A on the game's tile then starts it again, from its last save.

## Eden (Nintendo Switch)

Foldcade syncs Eden's account saves for the current profile through [RomM](romm.md#save-sync). If Foldcade already has read and write access to the installed Eden's `Android/data/<package>/files` folder or a folder above it, it finds the data folder there and announces the find in R1. The folder must hold `config`, `keys`, or `nand`. An Eden folder you explicitly chose stays first.

Otherwise choose **Settings › Emulators › Eden › Saves, keys & firmware**, or **Choose folder** before a launch, then **Use this folder** where Eden's picker opens. Pick the root of the installed Eden's own documents provider, without opening a folder inside it. This is the same grant Eden already uses for [keys and firmware](romm.md#switch-keys-and-firmware). **Play without syncing**, or cancelling the picker, applies to this launch only.

From Settings, choosing or cancelling this picker returns to Eden's same row so you can see whether the folder is allowed.

Foldcade reads **Save Data Directory** from Eden's `config/config.ini`. An empty setting uses Eden's effective NAND directory, which defaults to `nand` in its data folder. If a custom save or NAND folder needs a separate grant, **Fix saves** opens the picker there. Choose the folder and tap **Use this folder › Allow**. Eden keeps its path settings.

Foldcade maps `[System] current_user` through `system/save/8000000000000010/su/avators/profiles.dat` in the effective NAND. In the save directory it prefers `user/save/account/<UUID>/<TITLEID>/0` when present, then uses `user/save/0000000000000000/<UUID>/<TITLEID>` for the legacy layout. The ZIP contains `<TITLEID>/…` so each device can place it under its own current user. Missing, unreadable, or conflicting path settings leave save placement unresolved and clear the folder's check. On the first check after updating, different progress in the newly resolved folder is kept as a **Backup** before placement.

The title id comes first from a 16-hex filename tag such as `[0100755017EE0000]`, then from a ticket's rights-id filename in an NSP/NSZ. Otherwise Foldcade reads NCA headers inside an NSP/NSZ or an XCI/XCZ's secure partition, using `header_key` from Eden's `keys/prod.keys`. It does not need title keys. Update, DLC, and multi-program ids map to the base application. Several different applications in one source, malformed files, missing header keys, or an archive with more than 16 NCA headers leave the save unresolved. Its saves then read **Saves not syncing**. If RomM has a save to load, the launch stops with **Couldn’t load the save**; **Play without syncing** bypasses sync for this launch only. RomM metadata alone cannot identify a Switch save.

Before launch, Foldcade checks keys and firmware and any required Eden restart, then pulls and places saves for the selected user. If that user changes during play, Foldcade reports the change and keeps readable progress from the original account as a **Backup** in **Y › Saves**. Check Eden's **Profile Manager** before choosing a save. An existing save is set aside before replacement. If two devices advanced, the conflict screen keeps both copies. **B** leaves the decision for later.

A fresh Eden with readable settings and no profile can start when there is no incoming save, then sync progress once Eden creates its first user and save. If a save is waiting, Foldcade asks you to open Eden and set up its user first. If the server cannot be checked while the destination is unresolved, Foldcade offers **Play without syncing** for that launch. Readable progress from a bypassed session can be kept as a **Backup**.

Only account saves for Eden's current profile sync. Other profiles, device-save-data, BCAT, and save caches do not sync. A remote-only game's saves still appear in **Y › Saves**, but placing one on this device requires downloading the ROM when its title id cannot be resolved by name. Copies to other server slots do not need that ROM.

## PPSSPP: PSP

Install [official PPSSPP](https://www.ppsspp.org/download/) and finish its first-run Memory Stick setup. Free and Gold builds are recognized, with Gold first when both are installed.

Foldcade looks for the **PSP** folder using access it already holds, including **Auto-find saves** all-files access. PPSSPP must hold access to that exact folder before Foldcade chooses it automatically. A stale or ambiguous copy asks. If needed, use **Settings › Emulators › PPSSPP** and choose the PSP folder that holds **SAVEDATA** and **SYSTEM**. Custom Memory Stick locations can be picked there too. The found path is shown in Settings and announced in R1.

PPSSPP uses the folder set in its own **Settings › System › Memory Stick Folder**. Choose the same **PSP** folder in Foldcade. A game save written there during a Foldcade session checks the folder. If Foldcade detects a save in another readable PSP folder, use **Fix it** to keep the copies and bind the folder PPSSPP uses.

Game saves sync through RomM as slot `savedata`. ISO, CSO v1, and PBP games are identified by `DISC_ID` in `PARAM.SFO`; a save archive contains all matching `SAVEDATA/<DISC_ID…>/` folders, under their original names. Games with unreadable metadata or nonstandard save-directory IDs cannot be matched by filename or title. Their files stay on the device; unsupported game images show **Saves not syncing**. Save states do not sync.

If Foldcade cannot resolve a save destination and already holds or finds an incoming save, choose **Play without syncing** to start that launch anyway. When RomM is unreachable and Foldcade holds no incoming save, the game can play offline without syncing; the amber warnings stay.

## Dolphin: GameCube

Install [official Dolphin](https://dolphin-emu.org/download/) and open it once. Its default **GameCube Slot A Device: GCI Folder** keeps one or more `.gci` files per game. Foldcade syncs those for **ISO/GCM** games through RomM, in slot `gci`. It reads the game ID and region from the disc, then checks the GCI headers. Other games on that card and Slot B stay untouched.

Choose **Settings › Emulators › Dolphin › Save folder** and allow the root of Dolphin's own provider. Foldcade checks that root for the current installation. A reinstall requires a fresh grant. The provider reaches Dolphin's data on stock Android 11 and later, where all-files access cannot read another app's `Android/data`. With access already held, Foldcade can also look for a matching GCI write in the shared `dolphin-emu` folder. A shared folder alone leaves its location unconfirmed.

A zip contains the game's `.gci` files directly at the root, matching Argosy's GameCube bundle layout. Foldcade keeps its own slot/emulator names; other clients' slots remain available through **Y › Saves**. A loose GCI must be packaged in this layout before it can be placed through this folder-archive slot.

Foldcade checks `Config/Dolphin.ini` and the ISO/GCM's applicable `GameSettings` INIs. An unreadable configuration shows **Can’t read config**. A confirmed custom GCI destination in Dolphin's own config stops the affected game's ordinary launch and opens a guided Fix. A matching GCI write in another readable root can also stop launches. Foldcade keeps readable suspect GCI copies as verified **Backups** in RomM, or on the device for local-only games.

Raw `.raw` cards can hold many games. Preserve the old card before choosing **Settings › Config › GameCube › GameCube Slot A Device › GCI Folder** in Dolphin. For a custom GCI path, keep a copy of the game's `.gci` files, then remove the `GCIFolderAPath` lines from `Config/Dolphin.ini` and the applicable game settings INI. Dolphin's desktop **Memory Card Manager** can export saves as GCI files into `GC/USA/Card A`, `GC/JAP/Card A`, or `GC/EUR/Card A`. Foldcade leaves Dolphin's card settings to you.

**Save sync covers official Dolphin's default GameCube Slot A GCI folder on ISO/GCM.** RVZ, Wii, MMJR, raw cards, custom GCI destinations, Slot B, and save states stay with the emulator. When a known incoming save has no safe destination, **Play anyway** bypasses sync for that launch.

The [candidate audit](../save-sync-candidates.md) lists the next emulators and verified formats.

## Flycast: Dreamcast

**Flycast stays launch-only.** The official Android app’s live home is `Android/data/com.flycast.emulator/files`; Home Folder Import/Export only makes shared copies. It cannot be redirected to a live shared folder through that UI. All-files access cannot open the private home. On Thor, Android’s picker granted read/write access to the exact live home, but creating a throwaway VMU through that grant failed with **Permission denied**. No root-free writable route was verified, so Foldcade does not claim that a shared export or a nominal grant is syncing.

The format audit confirms `<sanitized IP.BIN product number>_vmu_save_A1.bin`; upstream isolates only A1, and every other port remains shared. Flycast also buffers VMU writes until game unload, so safe replacement needs an unload handoff as well as writable access. Shared VMUs must never be assigned to one game. The verified paths, formats and source links are kept in the [candidate audit](../save-sync-candidates.md#flycast) for a follow-up. Dreamcast VMUs also stay local when playing through the RetroArch core.

## EX family: Nintendo and Sega

Foldcade syncs **GBA.emu**, **GBC.emu**, **NES.emu**, **Snes9x EX+** and **MD.emu** through RomM, in slot `battery`. Every file in a bundle is matched by the exact game basename, with its case preserved. Keep ROM filenames the same across devices. If you rename a ROM, its older bundle stays on RomM and launch offers **Play without syncing** for that launch. Restore the original name to reconnect those saves; Foldcade never renames or reassigns them by guessing.

| App | Battery files |
| --- | --- |
| GBA.emu | `<game>.sav` |
| GBC.emu | `<game>.sav` and the four-byte `<game>.rtc` clock companion |
| NES.emu | `<game>.sav`; FDS disk changes use `<game>.fds.sav` |
| Snes9x EX+ | `<game>.srm` and the 20-byte `<game>.rtc` companion for S-RTC/SPC7110 clocks |
| MD.emu | `<game>.srm` for cartridges; `<game>.brm` for Sega CD |

For NES, SNES SRAM and MD, the bundle also keeps named battery slots at `<game>/saves/<slot>/auto.<extension>`. GBA/GBC batteries and both clock companions stay at the game’s root, even when a named state slot is active. Save states, cheats, other games and all other files stay out. GBA.emu, GBC.emu and Snes9x EX+ also sync ZIP/7z games using the archive’s basename, matching upstream. NES.emu and MD.emu archives remain unresolved because their battery type depends on the file inside; multi-disc playlists remain unresolved too. An unreachable folder or unsupported mapping says **Saves aren’t syncing** in Y › Saves and names the folder, file type and layout checks.

EX defaults to the ROM’s directory, unless you select a save directory. **App Folder** uses `Android/data/<package>/files/EmuEx/<system>/saves` on current Android, which all-files access cannot open. The system names are `GBA`, `GB`, `FC-NES`, `SFC-SNES` and `MD-Genesis`. This private app folder has no public documents provider.

For a shared save directory, open the emulator’s **Options › File Paths › Saves › Select Folder**, choose a shared folder such as `saves/GBC.emu`, and choose that same folder in **Foldcade Settings › Emulators › GBC.emu** (or the matching app). Existing saves stay where you put them; move copies using your file manager if needed. Known `saves/<app>` and legacy shared `EmuEx/<system>/saves` locations can be found through held grants or **Auto-find saves** when the emulator holds that exact folder too. Custom locations can always be picked manually. A retained grant cannot reveal EX’s private active-path setting, so confirm the selected folder in EX if you have changed its paths. Foldcade never rewrites it.

If GBC.emu needs a folder set up again, re-pick it in GBC.emu first, then give Foldcade the same folder. The **Check** row says **Can’t read config** until a save write verifies the watched folder. The return can also detect a write to a readable candidate such as `EmuEx` or beside the ROM. **Same As Content** only produces evidence when the emulator can write there. **Fix it** for a beside-ROM save asks you to switch to **Select Folder** in EX, check the game’s own save path, and confirm the watched folder in Foldcade. **Main Slot (No State)** remains part of EX setup.

Y › Saves refuses raw saves or incompatible archives before making them this emulator’s slot head, with a format explanation. Such saves remain available on RomM to copy to another server slot.

For battery sync, set EX’s **Options › System › Launch Mode** to **Main Slot (No State)**. An autosave state can resume older runtime progress, and its older-state warning does not cover every resume. Foldcade cannot read or change this private setting. **Set up saves** shows this requirement on each EX app’s tile. NES, SNES SRAM and MD can keep separate named batteries; Foldcade carries those files together. Save states stay local.

**Saturn.emu is still launch-only.** Its `.bkr`, `.smpc` clock and cartridge saves require a separate, MD5-aware mapping.

## Deferred: M64Plus and Pizza Boy

**M64Plus FZ / Mupen64Plus AE:** upstream writes `.eep`, `.sra`, `.fla` and `.mpk` inside private `files/GameData`. The manifest has no exported save documents provider. FZ’s external-storage mode copies into that private working directory before play and copies back only after the emulation session exits. A crash or a still-running game can leave newer private progress and a stale external copy; Foldcade cannot inspect or back up the private copy without root. This revision does not treat a backup/export as active storage or declare it syncing. Use RetroArch for supported N64 battery sync.

**Pizza Boy GBA/GBC:** no public upstream save writer or RTC implementation was found. The official distribution links lead to Google Play; no official free standalone APK was available for a store-free QA install. Reports of `.sav` and RTC files do not verify current Basic/Pro naming, contents or active folder. These apps stay launch-only; no guessed declarations are added. Use GBA.emu/GBC.emu or RetroArch for supported sync.

Verified implementation sources and the remaining candidates are linked in the [candidate audit](../save-sync-candidates.md).

## Other systems

Complete the emulator's firmware, game-directory, controller, and graphics setup, and check that the game plays there before adding it to Foldcade.

Disc tracks and other related files may need the emulator's own game-directory access. The remaining launch-only emulators in the table keep their saves on the device; Foldcade does not transfer those saves to RomM.

## If Foldcade does not find an installed emulator

Forks with different Android package names are not detected automatically. Install a supported build or report the build you use.

Recognized packages are listed in the source for [Eden](../../plugins/eden/src/main/kotlin/app/foldcade/plugins/eden/EdenEntry.kt), [PPSSPP](../../plugins/ppsspp/src/main/kotlin/app/foldcade/plugins/ppsspp/PpssppEntry.kt), [Dolphin](../../plugins/dolphin/src/main/kotlin/app/foldcade/plugins/dolphin/DolphinEntry.kt), [EX family](../../plugins/explus/src/main/kotlin/app/foldcade/plugins/explus/ExPlusEntry.kt), [standalone emulators](../../plugins/emulators/src/main/kotlin/app/foldcade/plugins/emulators/Emulators.kt), [aPS3e](../../plugins/aps3e/src/main/kotlin/app/foldcade/plugins/aps3e/Aps3eEntry.kt), [ARMSX2](../../plugins/armsx2/src/main/kotlin/app/foldcade/plugins/armsx2/ArmsX2Entry.kt), [Azahar](../../plugins/azahar/src/main/kotlin/app/foldcade/plugins/azahar/AzaharEntry.kt), [DuckStation](../../plugins/duckstation/src/main/kotlin/app/foldcade/plugins/duckstation/DuckStationEntry.kt), [melonDS](../../plugins/melonds/src/main/kotlin/app/foldcade/plugins/melonds/MelonDsEntry.kt), and [RetroArch](../../plugins/retroarch/src/main/kotlin/app/foldcade/plugins/retroarch/RetroArchEntry.kt).



## Preset implementation references

These are the upstream formats and defaults behind the Thor setup decisions:

- [Azahar 2126.2 Android config reader](https://github.com/azahar-emu/azahar/blob/219d743a1e698101fe1ab8bef43e6563126c3c27/src/android/app/src/main/jni/config.cpp): Android layout default 2, secondary layout default 0, accurate multiplication default false. Layout keys are read from `[Layout]`, even when the generated template places some under `[Storage]`.
- [Azahar settings defaults](https://github.com/azahar-emu/azahar/blob/219d743a1e698101fe1ab8bef43e6563126c3c27/src/common/settings.h) and [GPU driver helper](https://github.com/azahar-emu/azahar/blob/219d743a1e698101fe1ab8bef43e6563126c3c27/src/android/app/src/main/java/org/citra/citra_emu/utils/GpuDriverHelper.kt): native resolution defaults to 1; Vulkan defaults on Android; the selected driver is installed privately.
- [ARMSX2 2.8.4 ConfigStore](https://github.com/ARMSX2/ARMSX2/blob/25e7084fdefcf1e9069481a583c38c103b03e8f1/platforms/android/app/src/main/java/com/armsx2/config/ConfigStore.kt): `writeBackupMirror` and the guarded `reconcileReusedFolder` import.
- [melonDS 2.0.1 preference repository](https://github.com/rafaelvcaetano/melonDS-android/blob/5ec3648d68382dea9c17d6fbf02544e5c2c0afed/app/src/main/java/me/magnum/melonds/impl/SharedPreferencesSettingsRepository.kt): its authoritative settings are private preferences; [release feature switches](https://github.com/rafaelvcaetano/melonDS-android/blob/5ec3648d68382dea9c17d6fbf02544e5c2c0afed/app/src/main/res/values/feature_switches.xml) disable settings backup/restore.
- [Android FileSystemProvider](https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r1/core/java/com/android/internal/content/FileSystemProvider.java): `renameDocument` builds a unique name instead of replacing an existing document. Two document renames leave a gap, so Foldcade does not use them as an atomic replacement.

Eden’s provider grant reaches `config/config.ini`, keys and the default NAND. The provider can start Eden and initialize cached state when queried. Foldcade reads the configured paths and selected profile to resolve save sync.

[All integration guides](README.md)
