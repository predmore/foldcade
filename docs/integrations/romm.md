# RomM

Connect a [RomM](https://romm.app) server to browse its collection alongside your other games. Foldcade downloads selected games and opens them in a [supported emulator](emulators.md).

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

The catalog loads at startup and after connecting or editing a connection. After adding games, finish RomM's scan and [fully restart Foldcade](README.md#discovery-and-updates).

## Downloads and offline play

Press A or X on a game. Foldcade downloads and checks its files before launch, showing progress on the tile and top screen. Verified downloads are reused.

**Y › Delete download** frees the space a downloaded game takes. Its saves stay, and its tile shows the cloud again until the next launch downloads it.

Downloaded games with a cached catalog can be played offline. New downloads need the server. Provide a format the emulator can open; Foldcade does not handle server-side conversion.

## Save sync

Save sync currently supports **melonDS `.sav` files** with RomM **5.4.0 or newer**:

1. In melonDS, select a shared save directory and turn **Save next to ROM file** off.
2. Select the same directory in **Foldcade Settings › Saves › melonDS**, or at the save-folder prompt, and grant access.
3. Launch the game from Foldcade's RomM catalog. Foldcade transfers saves before launch and when you return.

Keep filenames consistent: `Puzzle.nds` uses `Puzzle.sav`. A separate [local-folder tile](local-games.md) is not connected to RomM save sync.

Azahar's save-folder setting does not yet enable title-save transfer. Other emulators and sources manage their own saves. Save-state sync is not implemented.

Offline changes stay on the device for a later sync. When copies conflict, Foldcade asks which to play and keeps both.

## If something goes wrong

- **Cannot connect:** Try the address from the Thor, check network access, and get a new code or check the token's permissions.
- **Missing game:** Check RomM's scan and account access, then restart Foldcade. If it is already in All, check [Home placement](README.md#a-game-is-in-all-but-missing-from-home).
- **Will not launch:** Check the [emulator and format](emulators.md).
- **Saves do not sync:** Check the server version and shared melonDS directory. **Not now** at the save-folder prompt allows play with the emulator's own saves.

[All integration guides](README.md)
