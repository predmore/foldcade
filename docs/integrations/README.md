# Add your games to Foldcade

Choose a guide for where your games live. You can combine sources on the same Home and in **All**.

| Your games | Guide | How Foldcade finds them |
| --- | --- | --- |
| A library on your RomM server | [RomM](romm.md) | Loads the server's catalog after sign-in, downloads games as you play them, and syncs saves. |
| ROMs on internal storage or an SD card | [Local games](local-games.md) | Finds `ROMs` and `Emulation/roms` once you allow file access, and scans any folder you add. |
| Games for Azahar, melonDS, Dolphin, PPSSPP, and other emulators | [Emulators](emulators.md) | Detects supported emulators; games come from folders or RomM. |
| Games for RetroArch and its cores | [RetroArch](retroarch.md) | Uses the cores you install; games come from folders or RomM. |
| Android games and apps | [Android](android.md) | Discovers installed apps automatically. |
| PC games installed in GameNative | [GameNative](gamenative.md) | Home shortcuts and automatically found store exports. |
| Steam and added games in DroidDeck | [DroidDeck](droiddeck.md) | Reads pinned game shortcuts or scans its synced export folder. |
| Games streamed from your computer | [Moonlight](moonlight.md) | Reads the game shortcuts you create in Moonlight. |

## Start here

With a `ROMs` or `Emulation/roms` folder, Foldcade asks once at the start to read it, and its games fill Home. Open **L1 › Settings › Library › Add** to add other game folders or a RomM server. If your games are on a RomM server, start there: one sign-in brings the whole library, and with RomM 5.4 or newer your saves follow you between Thors. A game in a folder with the same system and file name as a RomM game joins its tile instead of doubling it. For Moonlight, choose your computer's tile in the same place and turn on **Games** to list all its games. Moonlight and DroidDeck shortcuts reach only Android's default home app, so with either installed Foldcade asks once to be Home; a Moonlight computer's page and **Settings › System** keep **Set as Home** until it is.

For a saved RomM tile, press A even while the startup catalog is arriving. Foldcade shows **Finding your game…**, waits for the sync, and opens it when ready. Downloads and server preparation show progress on the tile and top screen, retry brief connection failures, and can be cancelled with **B**.

Finish setup in the source app or emulator and check that your game plays there. In Foldcade, press **A** to play on the top screen or **X** on the bottom. Azahar, melonDS, PXPlay, and DroidDeck use both screens; configure their layout in the app.

## Discovery and updates

| Source | When the list updates |
| --- | --- |
| Local folders, GameNative exports, and DroidDeck exports | At startup, when you add or remove a library folder, and in the background when you return to Foldcade. Found ROM folders are looked for again at the same times, so a card put in later is found on your next return. |
| RomM | At startup, when a connection is added or changed, and every 15 minutes while Home is shown. |
| Android apps | At startup, when you return to Foldcade, and when apps are installed, removed, or changed. |
| Moonlight and DroidDeck pinned shortcuts | At startup, when you return to Foldcade, and after Foldcade accepts a new game shortcut. |
| Moonlight computers Foldcade paired with | Right after pairing, then every few minutes while Home is shown and the computer answers. |

Folders are not watched continuously. Returning to Foldcade rescans them in the background; Home keeps showing the current list until the scan finds a change. Returns within five seconds share a folder scan. **Settings › Library › Sync games** looks again immediately.

## A game is in All but missing from Home

Check **L1 › Edit home › New games on Home**. When it is off, discovered games stay in **All** until you select one and choose **Y › Add to › Home**, which also files it in its folder. Games hidden or taken off Home can be brought back the same way.

A new Moonlight or DroidDeck pin goes on Home even with that switch off. Hide keeps its Android shortcut and Show in Hidden restores it. Hold A on Delete shortcut to remove its Android shortcut and Home tile. Request the shortcut again in the source app to recreate it; a DroidDeck game with a synced export remains in All.

System and source folders appear with their first game. An emulator's own tile goes in **Emulators** and opens the app; its games need to be added separately.

## Artwork

Turn on **Settings › Library › Game art** to fetch artwork from libretro, Steam, and other public sources. RomM supplies its own covers and can find covers for other games too. Tall covers use their busiest square for the tile; a short title band crossing its edge keeps its nearby quiet margin, so the crop does not stop halfway through the logo.

For more artwork, add a free SteamGridDB API key in **Settings › Library › SteamGridDB**. You don't have to type it: the form leads with a QR code, so scan it with a phone on the same Wi-Fi. That opens a one-time page from Foldcade with a link to [your SteamGridDB API page](https://www.steamgriddb.com/profile/preferences/api). Copy the key there, paste it back on Foldcade's page and tap **Send to Foldcade**. Foldcade checks the key and saves it. The page only works while the SteamGridDB form is open and accepts one key. A refused key gets a new code. Without Wi-Fi, the QR code opens SteamGridDB's page so you can read the key and type it. You can always type it in the **API key** field. With a key saved, the same row reads **Saved**; pressing it asks first, and the key stays unless you choose **Forget key** in that dialog. Games can launch without artwork setup.

In **Y › Art**, the current image comes first with a check, including RomM and local media. L1/R1 switch Tile, Hero and Logo; L2/R2 choose a SteamGridDB match beside its name on the top screen. A applies an alternative; the current image needs no action. X returns that kind to Automatic, and B closes the picker. With no alternatives, try another match or Automatic. Art arriving in another tab keeps your selection in place. **Y › About** shows the game’s cover beside its page, with the platform beneath its name.

[Back to the Foldcade README](../../README.md)
