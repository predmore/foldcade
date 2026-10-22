# RomM

Connect a [RomM](https://romm.app) server, and its library becomes your Thor's. Every game on the server shows up beside your other games, filed by system. Press A, and Foldcade downloads it, checks it, and opens it in a [supported emulator](emulators.md). With RomM 5.4 or newer, your saves go back to the server, so you can stop on one Thor and keep going on another.

What you get:

- **Sign-in with a code** you approve on your phone. Foldcade stores a token, not your password.
- **Verified downloads** that resume after an interruption when the server allows it, and stay ready for offline play.
- **[Save sync](#save-sync)** for melonDS and RetroArch, with a choice and both copies kept when two devices disagree.
- **One tile** for a game that is in a [local folder](local-games.md) and on the server under the same system and file name, opening the copy already on your Thor.
- **[Switch updates and DLC](#updates-and-dlc)** downloaded beside the game and loaded by Eden.
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

A folder that RomM does not split this way still downloads as RomM sends it.

Downloaded games with a cached catalog can be played offline. New downloads need the server. Provide a format the emulator can open; Foldcade does not handle server-side conversion.

## Save sync

Save sync supports **melonDS `.sav` files**, **RetroArch `.srm` battery saves** and **Azahar 3DS saves** with RomM **5.4.0 or newer**.

For melonDS:

1. In melonDS, select a shared save directory and turn **Save next to ROM file** off.
2. Select the same directory in **Foldcade Settings › Emulators › melonDS**, or at the save-folder prompt, and grant access.
3. Launch the game from Foldcade's RomM catalog. Foldcade transfers saves before launch and when you return.

Keep filenames consistent: `Puzzle.nds` uses `Puzzle.sav`.

For RetroArch, choose `RetroArch/saves` in **Foldcade Settings › Emulators › RetroArch** and keep RetroArch's default save layout; see [RetroArch saves](retroarch.md#saves). Every system shares that one folder.

For Azahar, choose Azahar's user folder, the one that holds `sdmc`, `nand` and `config`, in **Foldcade Settings › Emulators › Azahar** or at the save-folder prompt. Foldcade finds each game's save there from the title id in its `.cci` header, and syncs the save data and the game's extra data together as one zip, in the layout Argosy also uploads. A `.zcci` has no title id Foldcade can read, so its saves stay with Azahar.

A game that is also in a [local folder](local-games.md) syncs through RomM too, whichever copy opens. Its first launch after the two become one tile hands the emulator's save to RomM as progress, so a save made from the folder is kept. If another device saved since, Foldcade asks which to play and keeps both.

Other emulators and sources manage their own saves. Save-state sync is not implemented.

Offline changes stay on the device for a later sync. When copies conflict, Foldcade asks which to play and keeps both.

## If something goes wrong

- **Cannot connect:** Try the address from the Thor, check network access, and get a new code or check the token's permissions.
- **Missing game:** Check RomM's scan and account access, then restart Foldcade. If it is already in All, check [Home placement](README.md#a-game-is-in-all-but-missing-from-home).
- **Will not launch:** Check the [emulator and format](emulators.md).
- **Saves do not sync:** Check the server version, the shared melonDS directory, and that Azahar's save folder is its user folder. **Not now** at the save-folder prompt allows play with the emulator's own saves.

[All integration guides](README.md)
