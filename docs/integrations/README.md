# Add your games to Foldcade

Choose a guide for where your games live. You can combine sources on the same Home and in **All**.

| Your games | Guide | How Foldcade finds them |
| --- | --- | --- |
| A library on your RomM server | [RomM](romm.md) | Loads the server's catalog after sign-in, downloads games as you play them, and syncs saves. |
| ROMs on internal storage or an SD card | [Local games](local-games.md) | Scans the folders you select. |
| Games for Azahar, melonDS, Dolphin, PPSSPP, and other emulators | [Emulators](emulators.md) | Detects supported emulators; games come from folders or RomM. |
| Games for RetroArch and its cores | [RetroArch](retroarch.md) | Uses the cores you install; games come from folders or RomM. |
| Android games and apps | [Android](android.md) | Discovers installed apps automatically. |
| Steam games installed in GameNative | [GameNative](gamenative.md) | Scans exported `.steam` files. |
| Steam and added games in DroidDeck | [DroidDeck](droiddeck.md) | Reads pinned game shortcuts or scans its synced export folder. |
| Games streamed from your computer | [Moonlight](moonlight.md) | Reads the game shortcuts you create in Moonlight. |

## Start here

Open **L1 › Settings › Library** to add game folders or a RomM server. If your games are on a RomM server, start there: one sign-in brings the whole library, and with RomM 5.4 or newer your saves follow you between Thors. A game in a folder with the same system and file name as a RomM game joins its tile instead of doubling it. For Moonlight, turn on **Games from** your computer in the same place to list all its games. For Moonlight or DroidDeck shortcuts, first choose Foldcade under **Settings › System › Home app**; Android sends shortcut requests to its default home app.

Finish setup in the source app or emulator and check that your game plays there. In Foldcade, press **A** to play on the top screen or **X** on the bottom. Azahar and melonDS use both screens; configure their layout in the emulator.

## Discovery and updates

| Source | When the list updates |
| --- | --- |
| Local folders, GameNative exports, and DroidDeck exports | At startup, when you add or remove a library folder, and in the background when you return to Foldcade. |
| RomM | At startup and when a connection is added or changed. |
| Android apps | At startup, when you return to Foldcade, and when apps are installed, removed, or changed. |
| Moonlight and DroidDeck pinned shortcuts | At startup, when you return to Foldcade, and after Foldcade accepts a new game shortcut. |
| Moonlight computers Foldcade paired with | Right after pairing, then every few minutes while Home is shown and the computer answers. |

Folders are not watched continuously. Returning to Foldcade rescans them in the background; Home keeps showing the current list until the scan finds a change. After changing a server's catalog, fully restart Foldcade: open Android's **App info** for Foldcade, choose **Force stop**, then reopen it.

## A game is in All but missing from Home

Check **Settings › Library › New games on Home**. When it is off, discovered games stay in **All** until you select one and choose **Y › Add to › Home**, which also files it in its folder. Games hidden or taken off Home can be brought back the same way.

A new Moonlight or DroidDeck pin goes on Home even with that switch off. Hide keeps its Android shortcut and Show in Hidden restores it. Hold A on Delete shortcut to remove its Android shortcut and Home tile. Request the shortcut again in the source app to recreate it; a DroidDeck game with a synced export remains in All.

System and source folders appear with their first game. An emulator's own tile goes in **Emulators** and opens the app; its games need to be added separately.

## Artwork

Turn on **Settings › Library › Game art** to fetch artwork from libretro, Steam, and other public sources. RomM supplies its own covers and can find covers for other games too.

For more artwork, add a free API key from [SteamGridDB Preferences](https://www.steamgriddb.com/profile/preferences) under **Settings › Library › SteamGridDB**. With a key saved, the same row reads **Forget key** and asks first; the key stays unless you choose **Forget key** in that dialog. Games can launch without artwork setup.

[Back to the Foldcade README](../../README.md)
