# DroidDeck

Add [DroidDeck](https://github.com/Droid-Deck/DroidDeck) games through individual home-screen shortcuts or a synced folder of game files.

## Add an individual game

1. Set up DroidDeck and check that the game plays there.
2. In Foldcade, open **L1 › Settings › System › Home app** and choose Foldcade as the default home app.
3. Open the game's menu in DroidDeck and choose **Add to home screen**.
4. Return to Foldcade. The game appears in the **DroidDeck** folder.

A new pin goes on Home even when **New games on Home** is off.

## Discover games from a synced folder

1. In DroidDeck's frontend-file options, choose **Sync all game files to folder**.
2. Select a shared folder, such as `ROMs/DroidDeck`, and let the export finish.
3. In Foldcade, open **L1 › Settings › Library › Add a folder** and select that same folder.
4. Find the games in All or, with **New games on Home** enabled, the DroidDeck folder.

DroidDeck maintains `.steam` launch references and a hidden `.droiddeck-sync.json` inventory. Keep the inventory beside the files so they open in DroidDeck even when GameNative is installed. The games themselves stay in DroidDeck.

The current [export format](https://github.com/Droid-Deck/DroidDeck/blob/main/app/src/main/java/com/droiddeck/launcher/frontend/GameFiles.kt) is `.steam`. If your build exports `.droiddeck` files or lacks sync, use **Add to home screen**; Foldcade does not scan `.droiddeck` files.

## Updates

DroidDeck keeps the export folder in step with its library. Foldcade reads it at startup, when a library folder is added or removed, and in the background when you return to Foldcade. After DroidDeck finishes a sync, press Home to load the changes.

Installing DroidDeck alone adds its app tile to **Emulators**; Foldcade does not read its private game library.

## Launching, saves, and removal

Press **A** or **X** to launch on the chosen screen. DroidDeck starts Steam if needed and manages game settings and saves. Foldcade does not sync these saves through RomM.

A game found through both a pin and an export gets one entry. See the shared [artwork setup](README.md#artwork) for more art.

**Y › Remove from Home** removes the tile and its pin. Choose **Add to home screen** in DroidDeck to restore it. A synced export keeps the game in All. Remove the export folder's row in **Settings › Library** to stop reading it; its files and installed games stay on the device.

## If a game is missing or opens the wrong app

- **Shortcut is missing:** Make Foldcade the default home app and request it again. Pins from another launcher are not imported.
- **Synced games are missing:** Check the selected `.steam` folder and return to Foldcade after sync finishes. For games already in All, check [Home placement](README.md#a-game-is-in-all-but-missing-from-home).
- **Opens in GameNative:** Keep `.droiddeck-sync.json` beside the original exports; copying only `.steam` files loses their source marker. DroidDeck skips the inventory when the folder already held matching `.steam` files, for example from **Export game file** or a GameNative export. Delete those files, let DroidDeck sync again, then return to Foldcade.
- **Game is unavailable or a session is busy:** Open DroidDeck and resolve it there.

[All integration guides](README.md)
