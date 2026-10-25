# Games in local folders

Foldcade scans folders on your Thor's internal storage or SD card. You can add several folders, and it remembers them for future starts.

## Add a folder

1. Set up a [supported emulator](emulators.md) and check that your game plays there.
2. Put your games in folders named for their systems. For example:

   ```text
   ROMs/
     3ds/
       Adventure.cci
     ds/
       Puzzle.nds
     ps1/
       Adventure.cue
       Adventure.bin
     psp/
       Racing.iso
   ```

3. In Foldcade, open **L1 › Settings › Library › Add a folder**.
4. Select `ROMs`, or an individual system folder, in Android's folder picker and grant access.
5. Wait for the scan, then open the system's folder on Home or find the game in **All**.

Foldcade scans subfolders too. It needs access only to the tree you choose.

## Folder names and formats

A unique extension such as `.nds` or `.gba` identifies its system. Shared extensions such as `.iso`, `.chd`, `.rvz`, `.zip`, and `.7z` need a system folder: put a PSP `.iso` in `psp`, a PS2 `.iso` in `ps2`, and a PS3 `.iso` in `ps3`.

The [emulator table](emulators.md#supported-emulators-and-formats) lists folder names and recognized formats. The emulator must also support the format; unpack archives if it cannot open them directly.

Save files, artwork, documents, and `.bin` tracks are not game tiles. Keep disc tracks beside their `.cue` file and launch the `.cue`.

For multi-disc PlayStation, PlayStation 2, Saturn, or Dreamcast games, save a playlist such as `Adventure.m3u` in the system folder:

```text
Adventure (Disc 1).chd
Adventure (Disc 2).chd
```

Foldcade shows the playlist as one game and hides the files it names. The emulator must support playlists.

## New games and removed folders

Folders are scanned at startup, when you add or remove one, and [in the background when you return to Foldcade](README.md#discovery-and-updates).

Remove a folder's row in **Settings › Library** to stop reading it; its files stay on the device. **Y › Hide** on a game takes it off Home; it remains in All.

For exported `.steam` files, follow [GameNative](gamenative.md) or [DroidDeck](droiddeck.md).

## Saves

The emulator keeps its saves. When a [RomM](romm.md#save-sync) server has the same file, with the same name and system, the folder's game and the server's are one tile with one save: it opens the folder's copy and syncs saves through RomM with a supported save setup. Its play time, places, and art carry over from both. A file renamed later is a new game.

## If a game is missing or will not start

- **Missing from All:** Check the extension, system folder, and Android folder grant, then return to Foldcade.
- **In All but missing from Home:** Check [New games on Home](README.md#a-game-is-in-all-but-missing-from-home) or add the tile back with Y.
- **Wrong system:** Move a shared-format file into the correct system folder and return to Foldcade.
- **Will not launch:** Check the [supported emulator](emulators.md) and try the game directly in it.

[All integration guides](README.md)
