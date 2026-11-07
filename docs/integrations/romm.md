# RomM

Connect a [RomM](https://romm.app) server, and its library becomes your Thor's. Every game on the server shows up beside your other games, filed by system. Press A, and Foldcade downloads it, checks it, and opens it in a [supported emulator](emulators.md). With RomM 5.4 or newer, your saves go back to the server, so you can stop on one Thor and keep going on another.

What you get:

- **Sign-in from your phone**, without typing on the Thor. Foldcade stores a token, not your password.
- **Verified downloads** that resume after an interruption when the server allows it, and stay ready for offline play.
- **[Save sync](#save-sync)** for melonDS, RetroArch, Azahar, DuckStation, ARMSX2, aPS3e and Eden, with a choice and both copies kept when two devices disagree.
- **[Firmware](#firmware)**, such as a PlayStation BIOS, placed where the emulator looks for it.
- **One tile** for a game that is in a [local folder](local-games.md) and on the server under the same system and file name, opening the copy already on your Thor.
- **[Switch updates and DLC](#updates-and-dlc)** downloaded beside the game and loaded by Eden.
- **[Switch keys and firmware](#switch-keys-and-firmware)** added to Eden when Eden is missing them.
- **RetroAchievements progress** on the other screen during a single-screen game.
- **New games on the server** appear within 15 minutes, or right away from **Sync games**.

## Before connecting

Use a reachable RomM **5.x** server, a scanned collection, and an account that can read it. Save sync needs **5.4.0 or newer**; Foldcade's save protocol is tested against major version 5.

Configure the emulator before playing. RomM can catalog more systems than Foldcade can launch, so check the game's emulator and format.

## Connect with a code

1. Choose **Sign in to RomM** on first start, or open **L1 › Settings › Library › Add › RomM server**.
2. The form leads with a QR code: **Scan with your phone to sign in**, with a phone on the same Wi-Fi. Paste or enter your RomM server address on the phone, including `https://` or `http://`.
3. Tap **Continue to RomM**, then **Approve Foldcade on RomM**. Sign in on RomM and approve the code. One scan handles both steps. Foldcade also shows the approval QR code on the Thor.
4. Wait for the catalog to load, then choose a game from its system folder or **All**.

While the empty form is open, Foldcade looks nearby for a few seconds. A verified server offers **Use this server**: press **A** to choose it, then scan its approval code. This checks at most twelve likely addresses on the current IPv4 /24 at port 80, the host port in RomM’s default Compose example, using its unauthenticated `/api/heartbeat`. It does not scan other ports or the whole network. RomM has no standard Zeroconf advertisement; servers behind custom ports or reverse proxies usually need the phone path. A server address kept by a restored backup is already filled in and goes straight to approval.

The phone page exists only while this form is shown. Its random, one-time URL accepts one address and closes; it never asks for a password or token. Only `http://` or `https://` addresses without embedded credentials are accepted, with normal certificate checks for HTTPS. The LAN handoff uses plain HTTP on your own network. If Wi-Fi is unavailable or client isolation blocks it, type the address into **Server** on the Thor. Typing, under **Or type it**, and the optional token field remain available.

Foldcade stores a token, not your RomM password.

If the phone cannot continue to the approval page, scan the approval QR on the Thor instead. An approval link to a different server address is not opened by the phone page.

An address typed on the Thor gets its code once Foldcade can use it. Until then the code's side says what to fix: an `http://` address outside your home network needs `https://`, and an address without `https://` needs it added.

If the code expires or is declined, choose **Pair with phone** for a new one. If RomM gives no code at all, check the address, or scan the QR to send a new one from your phone, then choose **Try again**. To use a token instead, paste it into **Token (optional)**; the button then reads **Connect**. See [RomM's token guide](https://docs.romm.app/latest/developers/client-api-tokens/).

A manual token needs `roms.read`, `platforms.read`, `assets.read`, `assets.write`, `devices.read`, `devices.write`, and `firmware.read`.

## Add or change a server

Foldcade keeps one RomM server. Once it is saved, **Add** offers only folders.

Select the RomM tile in **Settings › Library** to open its source page, then **Edit connection** or **Sign out**. **B** from Edit connection returns to that page; **B** again returns to the RomM tile. Sign out asks first: saves stop syncing and RomM's games leave Home.

While signed in, changing to a different server address asks for that server's token. Your existing token stays on its own server until the new connection is checked. A setup restored without a sign-in can use phone pairing for a different address.

Pairing uses a separate connection until approval and sign-in checks finish. Backing out, declining, or letting a code expire keeps the saved library and its saves on their existing server.

## Signed out

After restoring a setup backup, which leaves sign-ins out, or once RomM refuses the saved token, RomM waits for a sign-in, and saves wait on the device until then. Foldcade says so everywhere RomM shows:

- R1 keeps a notice, **RomM: sign in to sync saves**, that has no Dismiss. **A** opens the sign-in form.
- The RomM tile in **Settings › Library** wears the not-syncing mark, and its page reads **Signed out**, with **Sign in** and **Remove**.
- A restored RomM game on Home is a gap until then. Focused, the top screen reads **Waiting for RomM** and **Sign in to bring it back**; **A**, or **Sign in** in its **Y** menu, opens the form.

If you are signed in but RomM's catalog has not arrived, retained game tiles show the RomM cloud and **Waiting for RomM**. A name kept in play history or cached metadata appears above that state, before and after you try the game. Waiting tiles use names that become available later too, without needing a catalog refresh. **A Play** retries loading the game; a failed request explains how to check the connection. The game returns to its usual tile when the catalog arrives.

Each Foldcade install registers as its own device in RomM and keeps it across restarts, so RomM keeps its sync history. Reinstalling Foldcade registers a new device.

The catalog loads at startup, after connecting or editing a connection, and again every 15 minutes while Home is shown. After adding games, let RomM finish its scan, then choose **Settings › Library › Sync games** to see them right away. The row reads **Syncing** while it works, then **Synced**, or **Can’t reach RomM** when the server didn’t answer; RomM’s tile wears the not-syncing mark until a sync gets through.

## Downloads and offline play

Press A or X on a game. Foldcade downloads its files and checks each one against the size and checksum RomM lists before launch, showing progress on the tile and top screen. An archive is checked by size, since RomM's checksum describes what is inside it. An interrupted download resumes where it stopped when the server allows it, and starts over otherwise. Verified downloads are reused. Brief connection failures and temporary server errors retry automatically with backoff. Connection and sign-in checks stay quick when the server is offline, so a downloaded game does not wait through retry backoff for its save-sync probe. **B** cancels a launch while it waits or downloads. Cancelling a download keeps you in the same folder and briefly says **Download stopped** on the hero. Save warnings remain visible throughout a download; the tile uses a thicker progress track over its art. Pressing A again on the same pending game keeps its single launch.

Press a saved tile before the startup catalog arrives and **Finding your game…** appears above it, with **Finding** on the tile. Foldcade keeps the best name it knows: the catalog title, its remembered play name, then the offline catalog title. Cached cover art or a cover kept with play history stays visible when available. Foldcade waits up to a minute for the existing sync or starts one, then launches as soon as the game resolves.

If RomM answers that it is preparing a file, the tile reads **Preparing** and the top screen says **Warming up on RomM…**. Foldcade respects the server’s retry delay, or backs off from two to thirty seconds without one, for up to thirty minutes, then opens the game automatically. **B** cancels that wait too.

Downloads are kept in `Android/media/app.foldcade/romm` on internal storage, where emulators that open games by path, such as [RetroArch](retroarch.md), can read them. Android does not clear them to free space. **Y › Delete download** frees the space a downloaded game takes. Its saves stay, and its tile shows the cloud again until the next launch downloads it. Uninstalling Foldcade removes its downloads too.

A game you also have in a [local folder](local-games.md) is one tile when the two files have the same name and system. It opens the folder's copy, so it does not download, and **Y › Delete download** frees a copy downloaded earlier without taking the tile away.

### Updates and DLC

A game can be a folder in RomM with its update and DLC beside it, such as a Switch game with the base `.nsp` at the top and its update in `updates/`. When RomM files the extras as **update** or **DLC**, Foldcade downloads the game file and each of them into one folder, and Eden loads them from there when the game starts: there is nothing to install in Eden. Other files in the folder, such as manuals, are not downloaded. When RomM replaces an update, the next launch downloads the new one and deletes the old.

Eden reads that folder through Foldcade, and only for the game it was launched with.

### Disc games

A disc game can be a folder in RomM with a `.cue` sheet and its `.bin` tracks, a Dreamcast `.gdi` or CloneCD `.ccd` with its files, or an `.m3u` naming each disc. When every file is at the top of the folder, Foldcade downloads each one under its own name into one folder and opens the sheet, so the emulator finds the tracks beside it. An `.m3u` opens over the discs it lists. Several discs with no `.m3u` open the first by name.

Every game is handed to its emulator as a file in its own download folder, and the emulator can read only that folder.

A folder that RomM does not split in one of these ways still downloads as RomM sends it.

Downloaded games with a cached catalog can be played offline. New downloads need the server. Provide a format the emulator can open; Foldcade does not request a different format, but waits if the server says its file is still being prepared.

## Switch keys and firmware

The first time you launch a game in Eden without its folder held, Foldcade asks for the save folder. Choose **Choose folder**, then tap **Use this folder** as soon as Eden's picker opens. The same folder grants access to saves, keys and firmware. **Play without syncing**, or cancelling the picker, skips this launch only; the next launch asks again. **B** closes the ask and cancels the launch. You can also choose it in **Settings › Emulators › Eden › Saves, keys & firmware**. That row is there while Eden is installed and a RomM server is signed in. A wrong folder offers **Try again** or **Play without syncing**, which continues this launch without syncing; **B** cancels it.

Before the game starts, Foldcade looks at Eden's folder and adds only what is missing. `keys/prod.keys` is written when it is absent or empty. `keys/title.keys` is written in that same pass when RomM has the file, and only when `prod.keys` is missing or the firmware is missing. A missing `title.keys` on its own is left as it is. Firmware is the `.nca` files in `nand/system/Contents/registered/`. Foldcade installs the highest-numbered firmware zip RomM still has on disk for Switch, and only when Eden has none. A firmware file RomM lists as missing from its disk is skipped. A keys file or firmware that is already there is left as it is.

After Foldcade adds files, it asks you to restart Eden once. That prompt returns on each Switch launch until Eden has been force-stopped or the device restarted. Choose **Continue** to open Eden's App info. Then: 1. tap **Force stop**, then **OK**; 2. press **B** to come back; 3. start the game again. **Not now** only closes the prompt. Eden reads keys and firmware only when it starts, and Foldcade can't restart it.

The firmware download shows on the game's tile. If RomM cannot be reached, or a file does not check out, the game still starts, and the next launch tries again. A firmware zip Eden can't use is skipped until it changes on RomM.

## Save sync

Save sync supports **melonDS `.sav` files**, **RetroArch `.srm` battery saves**, **Azahar 3DS saves**, **DuckStation memory cards**, **ARMSX2 folder memory cards**, **aPS3e `savedata` folders**, **Eden Switch account saves**, **PPSSPP game saves**, **Dolphin GameCube GCI bundles**, and **EX-family battery saves (including GBC and SNES RTC)** with RomM **5.4.0 or newer**.

Select **Choose folder** to point Foldcade at the emulator's save folder. **Play without syncing**, or cancelling the folder picker, skips sync for this launch only. Foldcade asks again next time. DuckStation's hosted folder and ARMSX2's card setup offer **Set up** and **Play without syncing** the same way. **B** on any of these asks closes it and cancels the launch, so a reflex press never starts a game without its saves.

A game whose saves are not protected keeps an amber badge and **Saves not syncing** on the top screen. The badge sits over the tile's rounded rim and stays clear of the focus ring, including in Find. R1 shows one setup notice for the emulators your Home games play on, such as **3 emulators need setup**. **Settings › Emulators › Set up saves** and the page's own head count the same ones (**3 to go**). The notice opens **Set up saves**: for several emulators at the first that needs you, for one on that emulator. Dismiss it for now and launching an affected emulator brings it back. It disappears once set up. Settings › Emulators shows **Not syncing** in amber on rows that need setup for an emulator your Home games play on, and a quiet **Not set up** for one they don't, a check beside rows that are set, and **›** on every row that opens something.

**Set up saves** walks every emulator Settings › Emulators lists, one tile each on the bottom screen, with the focused one's next step on the top screen. The emulators your Home games play on that still need a step come first and wear the amber cloud, then the ones that sync, then the ones no Home game plays on, which read **Not set up** without a cloud. The order holds while you work through the page. A step is **Let Foldcade see its saves** (choose **Allow**, then **Use this folder** and **Allow** in Android's picker, which opens where the folder is when Foldcade knows it), **Let Foldcade see RetroArch** for its cores, or opening the emulator for a step only it can do, such as DuckStation's memory-card folder or ARMSX2's folder card. Steps are read from the emulator's state each time Foldcade comes back, so a step done in the emulator checks off on return. While all-files access is off and could find a folder, **Auto-find saves** leads the strip. The bottom button skips a tile for this run only: the notice, the count and the amber badges stay, and the page ends on **Done for now** with how many still don't sync and **Finish any time in Settings › Emulators**, never **All set**. The ring stays on the first skipped tile there; any direction on the D-pad reopens it. A folder Foldcade finds on its own posts **Saves found** in R1, which opens the page on that emulator, where it says where the folder was found. Confirm on a tile that syncs opens its Settings page. Open it any time from **Settings › Emulators › Set up saves**; Foldcade also opens it after you sign in to a new RomM server while an emulator still needs a step.

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

For PPSSPP, Foldcade finds the PSP folder and syncs its game-ID savedata folders as slot `savedata`. ISO, CSO v1 and PBP metadata are supported. See [PPSSPP](emulators.md#ppsspp-psp).

For Dolphin, Foldcade syncs the default GameCube Slot A GCI folder for ISO/GCM games as slot `gci`. Raw cards, RVZ, Wii NAND saves, custom card settings and MMJR do not sync yet. See [Dolphin](emulators.md#dolphin-gamecube).

The remaining emulators and sources manage their own saves. Save-state sync is not implemented.

Temporary failures reading a save or preparing its sync retry briefly. Save uploads are never blindly repeated: a failed upload stays queued for a fresh negotiation, and failed downloads leave the existing save intact. The not-syncing and waiting-to-upload notices remain visible.

Offline changes stay on the device for a later sync. When copies conflict, Foldcade asks which to play and keeps both: the bottom screen shows both saves on one card, with who made each, when, and which is newer. Press **A** on a save to play it, or on **Leave as is**; **B** decides later.

## Choose a save

On a game with a server copy, press **Y › Saves**. The bottom screen lists every RomM save, newest first, across slots, emulators and devices. Each row shows the slot, how long ago it was saved, and where it came from when that wasn't this device. **Playing** marks the save Foldcade holds for the emulator. The top screen shows the focused save over the game's art: its screenshot when one is available, its date and time, source, slot and size. The chip beside **Saves** says how the game's saves are doing. When saves aren't syncing or are waiting to upload, the page opens on that chip, and **A** sets up or syncs. With no saves on RomM yet and saves not syncing, the list's place holds a **Set up saves** button instead. Saves in a slot with no name a player chose, such as RetroArch's `srm` or Argosy's `autosave`, are named by their day. When the Thor has no network, the page says so over a **Wi-Fi** button; it reloads once the network is back. A RomM game started with no network says **No internet** and offers **Wi-Fi** too.

Press **A** on a save, then confirm **Play** to start the game right away. Foldcade syncs your current progress, verifies the chosen save, downloads the game if needed, and launches it fresh. Choosing an older save uploads the same bytes as a new head in Foldcade's slot. Connect to RomM before choosing. A save from another emulator may not load; the confirmation warns you. It also warns when an open game must restart. Your current save stays on RomM. A server-only game needs no download for melonDS, RetroArch or DuckStation save placement. Azahar, ARMSX2, aPS3e, PPSSPP and Dolphin need the ROM contents to find their saves; download the game first.

Press **Y** on a save for **Copy**, choose another slot, and confirm. The confirmation names whose slot it is, such as **Copy to Argosy’s save?**, and which save there is kept. This can copy progress back to an Argosy slot. Copying into Foldcade's own slot makes an open game restart the next time you play; the confirmation warns you. The source and the save already in that slot stay on RomM. Foldcade’s kept copies read **Backup**. There is no delete action. If a connection drops, reconnect before trying again; Foldcade keeps both copies and reconciles unfinished choices.

## Firmware

Firmware you add to a platform in RomM, such as a PlayStation BIOS, goes to the emulators that ask for it. Foldcade checks each download against RomM's size and checksum, then stages and verifies it before publishing it in the emulator's folder. Existing BIOS files are never overwritten. A short R1 notice says when a BIOS was placed. Folder access stays optional: when Foldcade cannot inspect a player's folder, the game still launches and R1 offers setup instead of claiming the BIOS is missing. Firmware preparation also runs when you choose **Play without syncing**, once Foldcade has access to the destination. An ungranted folder never causes a large unused BIOS download before play.

Skipping DuckStation's card setup or ARMSX2's folder-card setup still prepares the game and its BIOS. An unresolved PSP or GameCube save keeps the same firmware check before play, without acknowledging or writing an unreachable save. A known incoming save still needs your explicit **Play without syncing** choice first.

DuckStation and ARMSX2 share the same bounded, CRC-checked ZIP extraction path: both accept individual dumps or ZIPs, including nested dump folders. DuckStation identifies supported BIOS images by its upstream hash database. ARMSX2 keeps its existing console-ROMVER and region selection. RetroArch receives the files for its selected core in `RetroArch/system`; see [RetroArch BIOS](retroarch.md#bios). aPS3e receives `PS3UPDAT.PUP` at `aPS3e › firmware`, then installs it in its own UI. Its own setup guidance explains that install; Foldcade keeps an already staged PUP. Cached firmware remains available offline.

Local BIOS folders are searched as well, even without a RomM connection: see [local BIOS](local-games.md#bios). Copyrighted BIOS files only come from your own server or storage. Foldcade does not fetch BIOS from public websites.

melonDS Android can read external DS and DSi BIOS folders, but exposes no launch extra or public setting API to select them. Foldcade does not stage DS or DSi BIOS, firmware or NAND, so default DS play does no firmware search, ZIP extraction or NAND read. Configure your own external dumps in **melonDS Settings › System › Custom BIOS and firmware** if desired. Existing console type and folder choices stay untouched. This follows upstream [folder selection](https://github.com/rafaelvcaetano/melonDS-android/blob/c42995caff0144f37ad9791dc9bc96d82e8a6f65/app/src/main/java/me/magnum/melonds/impl/SharedPreferencesSettingsRepository.kt#L537-L546) and [file verification](https://github.com/rafaelvcaetano/melonDS-android/blob/c42995caff0144f37ad9791dc9bc96d82e8a6f65/app/src/main/java/me/magnum/melonds/impl/FileSystemConfigurationDirectoryVerifier.kt).

Switch keys and firmware for Eden work differently, through Eden's own folder: see [Switch keys and firmware](#switch-keys-and-firmware).

For Eden, choose its own data folder once for saves, keys and firmware. Slot `savedata`, emulator `eden`, is a zip with layout `<TITLEID>/…`, for example `0100755017EE0000/systemdata`. The device's user folder is outside the zip: each device maps the save to Eden's current profile. Only account saves sync; device-save-data and BCAT stay on the device. See [Eden](emulators.md#eden-nintendo-switch) for title-id sources, profile mapping and unresolved saves. Keys and firmware are checked first; a required restart stops the launch before save placement.

## If something goes wrong

- **Cannot connect:** Try the address from the Thor, check network access, and get a new code or check the token's permissions.
- **Missing game:** Check RomM's scan and account access, then choose **Settings › Library › Sync games**. If it is already in All, check [Home placement](README.md#a-game-is-in-all-but-missing-from-home).
- **Will not launch:** Check the [emulator and format](emulators.md). **aPS3e setup: Not syncing** in **Foldcade Settings › Emulators** means aPS3e still needs its first-run setup or PlayStation 3 firmware; select it for the steps.
- **Saves do not sync:** Check the server version, the shared melonDS directory, that Azahar's save folder is its user folder, and that **Foldcade Settings › Emulators** shows DuckStation's memory cards and ARMSX2's memory card as **Syncing**. **Play without syncing** at the save-folder prompt allows this launch with the emulator's own saves. **Not syncing** on ARMSX2's row opens the steps for its folder card. The old file card stays untouched.

[All integration guides](README.md)
