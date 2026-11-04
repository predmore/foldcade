# RetroArch

[RetroArch](https://www.retroarch.com) runs dozens of systems through cores, and signs in to [RetroAchievements](https://retroachievements.org) for all of them. Install it. Foldcade gets missing cores and opens your games in it, from [local folders](local-games.md) and from [RomM](romm.md).

## Set up RetroArch

1. Install the 64-bit RetroArch from [retroarch.com](https://www.retroarch.com/?page=platforms) (`com.retroarch.aarch64`) or from F-Droid (`com.retroarch`). Google Play's RetroArch can't open games from other apps.
2. Allow RetroArch access to files. If it needs this at launch, **Allow file access** opens its Android App info page; enable file access under Permissions, then return to Foldcade to carry on.
3. To earn achievements, sign in under **Settings › Achievements** in RetroArch. Foldcade passes nothing to it. Hardcore mode, on by default, turns off save states and rewind.

## Let Foldcade see your cores

RetroArch keeps its cores where other apps can't look. Once your games include a system RetroArch plays, Foldcade asks once to see them, on Home, before you start anything. If you start a RetroArch game before it asked, it asks then. Choose **Continue**, then **Use this folder** and **Allow** on RetroArch's folder. You can do the same any time from **Settings › Emulators › RetroArch › Cores**, which then shows how many cores Foldcade sees.

This one folder is still needed. RetroArch has no other way to tell an app which cores it has. Its core installer only copies a core in. Without the folder, Foldcade would have to guess. It could replace a core you installed, or play with a different core than yours, whose saves live in another folder.

Foldcade reads the cores again before every RetroArch launch, so a core you just installed counts. Choose RetroArch's top folder, the one that holds `cores`; Foldcade says so if you pick another.

Without that folder, Foldcade starts the first core in the table and can't warn you when it's missing. RetroArch then stays on a black screen.

## Systems and cores

Foldcade uses the first core in each row that is installed. If none is installed, press **A** as usual: the tile and top screen say `Getting <core>…`, the bar fills, and the game starts when it is ready. **B** cancels the download. A core you chose explicitly is fetched if it is missing, rather than changing your choice. For every system except the Nintendo DS and the PlayStation, RetroArch with a core goes ahead of a standalone emulator. melonDS keeps the DS because it draws the touch screen on the bottom display, and DuckStation keeps the PlayStation because Foldcade brings it the BIOS and memory cards from RomM. Without a core, a system's standalone emulator plays as before.

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

### How cores arrive

Foldcade uses [libretro’s Android nightly feed](https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/), for the installed RetroArch’s ARM architecture. The [1.22.2 stable Android directory](https://buildbot.libretro.com/stable/1.22.2/android/) provides APKs, not matching core archives. Core releases have their own versions; they do not need to share RetroArch’s frontend version. Foldcade checks the download length, ZIP entry name, expanded size, CRC, and ELF architecture before handing it over.

The verified core is staged under `Android/media/<Foldcade package>/retroarch-cores`. RetroArch’s exported [CoreSideloadActivity](https://github.com/libretro/RetroArch/blob/v1.22.2/pkg/android/phoenix/src/com/retroarch/browser/debug/CoreSideloadActivity.java) copies it into its core directory and starts the game in one handoff, after Foldcade prepares saves. No `.info` download, updater visit, core-list refresh, or writable folder grant is needed. Installed cores are left alone; the existing ordered table and standalone-emulator preferences still apply. Thor uses the `arm64-v8a` feed, with no special core override.

Android protects its direct runtime-permissions page from normal apps on Thor, so the 1.22.2 file-access shortcut opens App info. This step cannot be fully automatic.

Disc systems need their BIOS files in RetroArch's system folder (`RetroArch/system`). Check each core's documentation for the file names.

## Saves

RetroArch keeps its own saves, so they work without any setup in Foldcade. For [RomM save sync](romm.md#save-sync), choose RetroArch's save folder once under **Settings › Emulators › RetroArch**. The picker opens at `RetroArch/saves`; choose **Use this folder**. One folder covers every system. Before a server launch, **Play without syncing** skips this setup for that launch. Foldcade asks again next time.

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

- `Couldn’t get <core>`: choose **Try again**. **Open RetroArch** keeps the manual route available: **Online Updater › Core Downloader**. Builds without the exported core installer, and unsupported processor architectures, use this manual route.
- **"RetroArch can’t read games yet"**: choose **Allow file access**, allow it under Android’s Permissions, then return to Foldcade.
- **RetroArch shows a black screen or its menu**: the core or BIOS is missing, or the folder from **Let Foldcade see your cores** isn't set.
- **A save didn't sync**: check that the save folder is `RetroArch/saves`, and that the sorting setting above is on.

[All integration guides](README.md)
