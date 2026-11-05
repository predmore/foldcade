# Games in local folders

Foldcade scans folders on your Thor's internal storage or SD card. It finds the usual ROM folders by itself, you can add any other folder, and it remembers them for future starts.

## Let Foldcade find your ROMs

Foldcade looks for these folders at the top of internal storage and of each SD card, in any case:

- `ROMs` (ES-DE's default) and `roms` (Batocera's);
- `Emulation/roms` (EmuDeck's).

A folder counts when it holds at least one system folder, such as `gba`, `psx`, `megadrive` or `Nintendo - Game Boy Advance` (ES-DE, Batocera and RetroArch names all work). A `ROMs` folder that holds only Moonlight's `moonlight` shortcuts is left alone.

1. Set up a [supported emulator](emulators.md) and check that your game plays there.
2. Open Foldcade. When it sees one of these folders it asks once, naming the folder and its systems. Choose **Allow**. Android's page opens on that screen: tap **Allow access to manage all files**, then press Back. Foldcade's other screen says so while the page is open. With the D-pad, move down to the switch first, then press A.
3. Foldcade scans the folders in the background. R1 says **Found 214 games in ROMs**, and the games join Home and **Games**, sorted by system.

You can also allow it later. Until you have a folder, Home ends in an **Add games** tile with **Find games** first. **L1 › Settings › Library › Auto-find games** is the same Android switch as **Settings › Emulators › Auto-find saves**. Each found folder gets a tile in **Settings › Library**, with where it is and how many games it holds. Its **Remove** stops reading it for good: Foldcade never finds that folder again by itself. Its files stay on the device. Turning all-files access off takes the found folders' games off Home until you allow it again.

Foldcade reads found folders and hands games to emulators read-only. It skips a found folder that a folder you added already covers.

## Add a folder

Use this for a folder Foldcade doesn't find, or to keep all-files access off.

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

3. In Foldcade, choose **Add a folder** on Home's **Add games** tile, or the **Add** tile in **L1 › Settings › Library**. With a RomM server already added, **Add** opens the picker straight away; otherwise choose **Folder**.
4. Select `ROMs`, or an individual system folder, in Android's folder picker and grant access. When there is a `ROMs`, `roms` or `Emulation/roms` folder that no source reads yet, even an empty one, the picker opens there, so **Use this folder** and **Allow** are all it takes. Otherwise it opens at the top of internal storage.
5. Wait for the scan, then open the system's folder on Home or find the game in **All**. A folder with no games says so right away, with **Add a folder** to pick another. Its page in **Settings › Library** shows where it is and how many games it holds. While it holds none, the page also offers **Pick again**: the folder you pick there takes its place. Hold **A** on **Remove** to stop reading it.

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

Choose a folder's tile in **Settings › Library**, then **Remove**, to stop reading it; its files stay on the device. **Y › Hide** on a game takes it off Home; it remains in All.

For exported `.steam` files, follow [GameNative](gamenative.md) or [DroidDeck](droiddeck.md).

## BIOS

Foldcade looks in `bios` and `BIOS` inside or beside your library folders, including ES-DE and Batocera layouts, and in `RetroArch/system` for standalone players. Existing readable grants are reused. Optional all-files access also covers shared roots such as `bios`, `ROMs/bios`, `ES-DE/bios`, `Batocera/bios`, and `Emulation/bios`. SD-card BIOS roots work through the library's grant too.

Choose the common parent of your games and `bios` if you want Foldcade to find both with one grant. Android does not let a grant on a system folder reach a sibling outside it. BIOS files can be individual dumps or ZIPs. Known hashes identify renamed dumps where upstream documents them; PS2 uses the console's ROM directory. Existing emulator BIOS files stay untouched. When Foldcade can read the emulator's folder and required BIOS is missing, the message names what is missing and the one place to put it. An ungranted folder never blocks play; R1 offers access as optional setup. Installed required BIOS uses metadata only; cores with built-in BIOS skip discovery entirely. Local file size and modification times avoid rereading unchanged dumps. ZIP copies and hash stamps are reused after process restarts too; filtered extractions keep only files that fit the selected core. Files with an unknown size are skipped, and every firmware stream is bounded at 512 MiB. Local archives use a `.zip` extension; one launch copies at most 256 MiB of archives and unpacks at most 512 MiB. Larger sets can be supplied as individual files.

## Saves

The emulator keeps its saves. When a [RomM](romm.md#save-sync) server has the same file, with the same name and system, the folder's game and the server's are one tile with one save: it opens the folder's copy and syncs saves through RomM with a supported save setup. Its play time, places, and art carry over from both. A file renamed later is a new game.

## If a game is missing or will not start

- **Missing from All:** Check the extension, system folder, and Android folder grant, then return to Foldcade. A folder Foldcade should find needs a system folder at its top, and **Auto-find games** on.
- **In All but missing from Home:** Check [New games on Home](README.md#a-game-is-in-all-but-missing-from-home) or add the tile back with Y.
- **Wrong system:** Move a shared-format file into the correct system folder and return to Foldcade.
- **Will not launch:** Check the [supported emulator](emulators.md) and try the game directly in it.

[All integration guides](README.md)
