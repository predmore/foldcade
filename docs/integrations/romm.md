# RomM

Connect a [RomM](https://romm.app) server, and its library becomes your Thor's. Every game on the server shows up beside your other games, filed by system. Press A, and Foldcade downloads it, checks it, and opens it in a [supported emulator](emulators.md). With RomM 5.4 or newer, your saves go back to the server, so you can stop on one Thor and keep going on another.

What you get:

- **Sign-in with a code** you approve on your phone. Foldcade stores a token, not your password.
- **Verified downloads** that resume after an interruption when the server allows it, and stay ready for offline play.
- **[Save sync](#save-sync)** for melonDS, RetroArch, Azahar and DuckStation, with a choice and both copies kept when two devices disagree.
- **[Firmware](#firmware)**, such as a PlayStation BIOS, placed where the emulator looks for it.
- **One tile** for a game that is in a [local folder](local-games.md) and on the server under the same system and file name, opening the copy already on your Thor.
- **[Switch updates and DLC](#updates-and-dlc)** downloaded beside the game and loaded by Eden.
- **[Switch keys and firmware](#switch-keys-and-firmware)** added to Eden when Eden is missing them.
- **RetroAchievements progress** on the other screen during a single-screen game.
- **Any number of servers**, each with its own sign-in, on one Home.

## Before connecting

Use a reachable RomM **5.x** server, a scanned collection, and an account that can read it. Save sync needs **5.4.0 or newer**; Foldcade's save protocol is tested against major version 5.

Configure the emulator before playing. RomM can catalog more systems than Foldcade can launch, so check the game's emulator and format.

## Connect with a code

1. In Foldcade, open **L1 › Settings › Library › Add a RomM server**.
2. Enter its address, including `https://` or `http://`, such as `https://romm.example.com`.
3. Open the verification address shown in Foldcade on your phone or computer, sign in, and approve the code.
4. Wait for the catalog to load, then choose a game from its system folder or **All**.

Foldcade stores a token, not your RomM password.

If approval fails, choose **Get a new code**, or paste a **Client API token** and choose **Save token**. See [RomM's token guide](https://docs.romm.app/latest/developers/client-api-tokens/).

A manual token needs `roms.read`, `platforms.read`, `assets.read`, `assets.write`, `devices.read`, `devices.write`, and `firmware.read`.

## Add or change a server

Use **Add a RomM server** again for another server. Connections have separate catalogs and credentials; the same server cannot be added twice under different accounts.

Select a server in **Settings › Library** to **Edit connection** or **Sign out**. Other connections are unaffected.

Each Foldcade install registers as its own device in RomM and keeps it across restarts, so RomM keeps its sync history. Reinstalling Foldcade registers a new device.

The catalog loads at startup and after connecting or editing a connection. After adding games, finish RomM's scan and [fully restart Foldcade](README.md#discovery-and-updates).

## Downloads and offline play

Press A or X on a game. Foldcade downloads its files and checks each one against the size and checksum RomM lists before launch, showing progress on the tile and top screen. An archive is checked by size, since RomM's checksum describes what is inside it. An interrupted download resumes where it stopped when the server allows it, and starts over otherwise. Verified downloads are reused.

Downloads are kept in `Android/media/app.foldcade/romm` on internal storage, where emulators that open games by path, such as [RetroArch](retroarch.md), can read them. Android does not clear them to free space. **Y › Delete download** frees the space a downloaded game takes. Its saves stay, and its tile shows the cloud again until the next launch downloads it. Uninstalling Foldcade removes its downloads too.

A game you also have in a [local folder](local-games.md) is one tile when the two files have the same name and system. It opens the folder's copy, so it does not download, and **Y › Delete download** frees a copy downloaded earlier without taking the tile away.

### Updates and DLC

A game can be a folder in RomM with its update and DLC beside it, such as a Switch game with the base `.nsp` at the top and its update in `updates/`. When RomM files the extras as **update** or **DLC**, Foldcade downloads the game file and each of them into one folder, and Eden loads them from there when the game starts: there is nothing to install in Eden. Other files in the folder, such as manuals, are not downloaded. When RomM replaces an update, the next launch downloads the new one and deletes the old.

Eden reads that folder through Foldcade, and only for the game it was launched with.

### Disc games

A disc game can be a folder in RomM with a `.cue` sheet and its `.bin` tracks, a Dreamcast `.gdi` or CloneCD `.ccd` with its files, or an `.m3u` naming each disc. When every file is at the top of the folder, Foldcade downloads each one under its own name into one folder and opens the sheet, so the emulator finds the tracks beside it. An `.m3u` opens over the discs it lists. Several discs with no `.m3u` open the first by name.

Every game is handed to its emulator as a file in its own download folder, and the emulator can read only that folder.

A folder that RomM does not split in one of these ways still downloads as RomM sends it.

Downloaded games with a cached catalog can be played offline. New downloads need the server. Provide a format the emulator can open; Foldcade does not handle server-side conversion.

## Switch keys and firmware

When a connected RomM server has Switch `prod.keys` or a firmware zip, the first time you start a game in Eden, Foldcade asks to set that up. Choose **Continue**, then on the next screen choose **Use this folder** at the bottom, without opening a folder inside it. Foldcade keeps that folder. **Not now** starts the game anyway, and you can choose the folder later from **Settings › Emulators › Eden keys and firmware**. That row is there while Eden is installed and a RomM server is signed in. While Foldcade holds the folder, the row reads **From RomM**. If the folder you choose is not Eden's, Foldcade offers **Try again** and **Not now**. **Try again** opens the picker at Eden's folder. **Not now** starts the game.

Before the game starts, Foldcade looks at Eden's folder and adds only what is missing. `keys/prod.keys` is written when it is absent or empty. `keys/title.keys` is written in that same pass when RomM has the file, and only when `prod.keys` is missing or the firmware is missing. A missing `title.keys` on its own is left as it is. Firmware is the `.nca` files in `nand/system/Contents/registered/`. Foldcade installs the highest-numbered firmware zip RomM still has on disk for Switch, and only when Eden has none. A firmware file RomM lists as missing from its disk is skipped. A keys file or firmware that is already there is left as it is.

After Foldcade adds files, it asks you to restart Eden once. That prompt returns on each Switch launch until Eden has been force-stopped or the device restarted. Choose **Continue** to open Eden's App info, then **Force stop**, then **OK**. Press **B** to come back and start the game again. **Not now** only closes the prompt. Eden reads keys and firmware only when it starts, and Foldcade can't restart it.

The firmware download shows on the game's tile. If RomM cannot be reached, or a file does not check out, the game still starts, and the next launch tries again. A firmware zip Eden can't use is skipped until it changes on RomM.

## Save sync

Save sync supports **melonDS `.sav` files**, **RetroArch `.srm` battery saves**, **Azahar 3DS saves**, **DuckStation memory cards**, **ARMSX2 folder memory cards**, and **aPS3e `savedata` folders** with RomM **5.4.0 or newer**.

Select **Choose folder** to point Foldcade at the emulator's save folder. **Play without syncing**, or cancelling the folder picker, skips sync for this launch only. Foldcade asks again next time. DuckStation's hosted folder and ARMSX2's card setup offer **Set up** and **Play without syncing** the same way.

A game whose saves are not protected keeps an amber badge and **Saves not syncing** on the top screen. R1 lists one setup notice per emulator; select it to fix the setup. Dismiss it for now and it returns after the next launch of that emulator's game. It disappears once set up. Settings › Emulators shows **Not syncing** on rows that need setup.

**Saves waiting to upload** and the same badge mean Foldcade holds progress RomM has not received. If it is still waiting after 45 seconds, R1 says so too. The state survives a restart and clears when the save reaches RomM. Emulators Foldcade cannot sync, and games with no server copy, read **Saves stay on this device**.

For melonDS:

1. In melonDS, select a shared save directory and turn **Save next to ROM file** off.
2. Select the same directory in **Foldcade Settings › Emulators › melonDS**, or at the save-folder prompt, and grant access.
3. Launch the game from Foldcade's RomM catalog. Foldcade transfers saves before launch and when you return.

Keep filenames consistent: `Puzzle.nds` uses `Puzzle.sav`.

For RetroArch, choose `RetroArch/saves` in **Foldcade Settings › Emulators › RetroArch** and keep RetroArch's default save layout; see [RetroArch saves](retroarch.md#saves). Every system shares that one folder.

For Azahar, choose Azahar's user folder, the one that holds `sdmc`, `nand` and `config`, in **Foldcade Settings › Emulators › Azahar** or at the save-folder prompt. Foldcade finds each game's save there from the title id in its `.cci` header, and syncs the save data and the game's extra data together as one zip, in the layout Argosy also uploads. A `.zcci` has no title id Foldcade can read, so its saves stay with Azahar.

For DuckStation, point its **Memory Cards** folder at the one Foldcade keeps for it when Foldcade asks. Its data folder is optional for save sync; choose it in **Settings › Emulators › DuckStation** to let Foldcade place a BIOS. See [DuckStation](emulators.md#bios-and-memory-cards). Each game's card 1 syncs as slot `card1`.

For ARMSX2, pick its data folder, the one that holds `bios` and `memcards`. Foldcade syncs slot 1 when that slot is a folder card, one zip of the game's folders. ISO, BIN, CUE/BIN, multi-disc `.m3u`, and CHD games sync. chdman's defaults include FLAC as one choice per block. A CHD syncs when the part of the disc holding the serial (the start of its data) isn't stored with FLAC or Zstandard, which is how chdman usually compresses it, since FLAC suits audio. A CHD where that part is FLAC or Zstandard still launches, and its saves stay on the device. See [ARMSX2](emulators.md#memory-cards). The slot syncs as `card1`.

For aPS3e, pick its own folder, the one the picker opens on. Foldcade syncs the `savedata` folders whose names contain the game's title id, such as `BLUS30464-ALIAS_SKATER`. Trophies are not synced. See [aPS3e](emulators.md#saves).

A game that is also in a [local folder](local-games.md) syncs through RomM too, whichever copy opens. Its first launch after the two become one tile hands the emulator's save to RomM as progress, so a save made from the folder is kept. If another device saved since, Foldcade asks which to play and keeps both.

Other emulators and sources manage their own saves. Save-state sync is not implemented.

Offline changes stay on the device for a later sync. When copies conflict, Foldcade asks which to play and keeps both.

## Firmware

Firmware you add to a platform in RomM, such as a PlayStation BIOS, goes to the emulators that ask for it: Foldcade downloads each file, checks it against RomM's size and checksum, and writes it into the emulator's folder before the game starts. A zip is checked the same way, and the files inside it are offered too, under the zip's name. DuckStation takes any PlayStation BIOS into its `bios` folder, and does not take files out of a zip. ARMSX2 takes one PlayStation 2 BIOS from a file or from a zip of dumps. aPS3e takes one PlayStation 3 `PS3UPDAT.PUP` and stages it at `aPS3e › firmware`; aPS3e installs that file itself, and Foldcade does not write it again after aPS3e has. Foldcade replaces a file there only when Foldcade put it there. A new copy in RomM replaces the old one, and a BIOS you put there yourself stays. The checked copy, zip and unpacked files, is kept for offline launches. The emulator needs its save folder chosen in Foldcade, since that is where the firmware goes.

Switch keys and firmware for Eden work differently, through Eden's own folder: see [Switch keys and firmware](#switch-keys-and-firmware).

## If something goes wrong

- **Cannot connect:** Try the address from the Thor, check network access, and get a new code or check the token's permissions.
- **Missing game:** Check RomM's scan and account access, then restart Foldcade. If it is already in All, check [Home placement](README.md#a-game-is-in-all-but-missing-from-home).
- **Will not launch:** Check the [emulator and format](emulators.md). **aPS3e setup: Not syncing** in **Foldcade Settings › Emulators** means aPS3e still needs its first-run setup or PlayStation 3 firmware; select it for the steps.
- **Saves do not sync:** Check the server version, the shared melonDS directory, that Azahar's save folder is its user folder, and that **Foldcade Settings › Emulators** shows DuckStation's memory cards and ARMSX2's memory card as **Syncing**. **Play without syncing** at the save-folder prompt allows this launch with the emulator's own saves. **Not syncing** on ARMSX2's row opens the steps for its folder card. The old file card stays untouched.

[All integration guides](README.md)
