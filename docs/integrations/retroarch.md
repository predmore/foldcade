# RetroArch

[RetroArch](https://www.retroarch.com) runs dozens of systems through cores, and signs in to [RetroAchievements](https://retroachievements.org) for all of them. Install it and its cores. Foldcade then opens your games in it, from [local folders](local-games.md) and from [RomM](romm.md).

## Set up RetroArch

1. Install the 64-bit RetroArch from [retroarch.com](https://www.retroarch.com/?page=platforms) (`com.retroarch.aarch64`) or from F-Droid (`com.retroarch`). Google Play's RetroArch can't open games from other apps.
2. Open RetroArch once and allow access to files. RetroArch reads your games by their place on the device, so it needs that permission.
3. Install a core for each system: **Main Menu › Online Updater › Core Downloader**. The table below lists the cores Foldcade starts.
4. To earn achievements, sign in under **Settings › Achievements** in RetroArch. Foldcade passes nothing to it. Hardcore mode, on by default, turns off save states and rewind.

## Let Foldcade see your cores

RetroArch keeps its cores where other apps can't look. The first time you start a RetroArch game, Foldcade asks to see them. Choose **Continue**, then **Use this folder** and **Allow** on RetroArch's folder. You can do the same any time from **Settings › Emulators › RetroArch cores**, which then shows how many cores Foldcade sees.

Foldcade reads the cores again before every RetroArch launch, so a core you just installed counts. Choose RetroArch's top folder, the one that holds `cores`; Foldcade says so if you pick another.

Without that folder, Foldcade starts the first core in the table and can't warn you when it's missing. RetroArch then stays on a black screen.

## Systems and cores

Foldcade uses the first core in each row that is installed. If none is installed, it says which core to install. For every system except the Nintendo DS and the PlayStation, RetroArch with a core goes ahead of a standalone emulator. melonDS keeps the DS because it draws the touch screen on the bottom display, and DuckStation keeps the PlayStation because Foldcade brings it the BIOS and memory cards from RomM. Without a core, a system's standalone emulator plays as before.

| System | Example folder | Cores, in order |
| --- | --- | --- |
| Game Boy, Game Boy Color | `gb`, `gbc` | Gambatte, SameBoy, mGBA |
| Game Boy Advance | `gba` | mGBA, VBA-M, gpSP |
| Nintendo DS | `ds` | melonDS DS, melonDS, DeSmuME |
| Virtual Boy | `virtualboy` | Beetle VB |
| NES / Famicom | `nes` | Mesen, FCEUmm, Nestopia |
| Super Nintendo | `snes` | Snes9x, Snes9x 2010, bsnes |
| Nintendo 64 | `n64` | Mupen64Plus-Next, ParaLLEl N64 |
| Genesis / Mega Drive | `genesis` | Genesis Plus GX, PicoDrive |
| Master System | `sms` | Genesis Plus GX, PicoDrive, Gearsystem |
| Game Gear | `gg` | Genesis Plus GX, Gearsystem |
| Sega CD / Mega-CD | `segacd` | Genesis Plus GX, PicoDrive |
| Sega 32X | `sega32x` | PicoDrive |
| Saturn | `saturn` | YabaSanshiro, Beetle Saturn, Yabause |
| Dreamcast | `dreamcast` | Flycast |
| PlayStation | `ps1` | PCSX-ReARMed, SwanStation, Beetle PSX HW, Beetle PSX |
| PSP | `psp` | PPSSPP |
| PC Engine / TurboGrafx-16 | `pcengine`, `tg16` | Beetle PCE Fast, Beetle PCE, Beetle SuperGrafx |
| PC Engine CD | `pcenginecd` | Beetle PCE Fast, Beetle PCE |
| Atari 2600 | `atari2600` | Stella, Stella 2014 |
| Atari 7800 | `atari7800` | ProSystem |
| Atari Lynx | `atarilynx` | Handy, Beetle Lynx |
| Neo Geo Pocket, Neo Geo Pocket Color | `ngp`, `ngpc` | Beetle NeoPop, RACE |
| WonderSwan, WonderSwan Color | `wonderswan`, `wonderswancolor` | Beetle WonderSwan |
| ColecoVision | `colecovision` | Gearcoleco, blueMSX |

Disc systems need their BIOS files in RetroArch's system folder (`RetroArch/system`). Check each core's documentation for the file names.

## Saves

RetroArch keeps its own saves, so they work without any setup in Foldcade. For [RomM save sync](romm.md#save-sync), choose RetroArch's save folder once under **Settings › Emulators › RetroArch**. The picker opens at `RetroArch/saves`; choose **Use this folder**. One folder covers every system.

Foldcade syncs battery saves, the `.srm` files RetroArch writes to `saves/<core name>/<game name>.srm`. That is RetroArch's default layout. Keep **Settings › Saving › Sort Saves into Folders by Core Name** on, and **Write Saves to Content Directory** off. Save states stay on the device.

## Playing

Press **A** to play on the top screen or **X** on the bottom. RetroArch opens straight into the game.

Home leaves RetroArch running, and starting the same game again picks up where you were. When the game has a newer save from another device, Foldcade starts it fresh so the synced save loads.

Starting a different game makes RetroArch 1.22 quit, and Foldcade starts it again with the new game, so you may see a short flash. RetroArch writes battery saves every 10 seconds, so after saving in a game, give it a moment before switching games. Nightly builds also save when they are put in the background.

## Where your games are

RetroArch opens a game by its place on the device:

- **Local folders** on internal storage or an SD card work as they are.
- **RomM downloads** go to `Android/media/app.foldcade/romm`, which RetroArch can read. They stay until you choose **Y › Delete download**.
- A folder from a cloud or network provider has no place RetroArch can open, so Foldcade tells you rather than start it.

## If something goes wrong

- **"RetroArch has no core for …"**: install the named core, then start the game again. **Open RetroArch** takes you there.
- **"RetroArch can't read games yet"**: open RetroArch and allow access to files.
- **RetroArch shows a black screen or its menu**: the core or BIOS is missing, or the folder from **Let Foldcade see your cores** isn't set.
- **A save didn't sync**: check that the save folder is `RetroArch/saves`, and that the sorting setting above is on.

[All integration guides](README.md)
